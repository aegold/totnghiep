import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { AuthState, AuthTokens, PersistedAuth, User } from '@/types/auth';
import { RoleType } from '@/types/auth';

const STORAGE_KEY = 'adaptive-lms.auth.v1';

/**
 * Public contract of the auth store: read model + actions.
 * Kept separate from the implementation so consumers can depend on a type,
 * not on Zustand internals.
 */
export interface AuthStore extends AuthState {
  /** Establishes a session after login/register/refresh. */
  setAuth: (user: User, tokens: AuthTokens) => void;
  /** Replaces only the token pair (used by the silent-refresh interceptor). */
  setTokens: (tokens: AuthTokens) => void;
  /** Updates the cached principal without touching tokens. */
  setUser: (user: User) => void;
  /** Wipes credentials from memory and from storage. */
  clearAuth: () => void;
  /** Exact authority check. */
  hasRole: (role: RoleType) => boolean;
  /** Intersection check — a principal may hold several roles. */
  hasAnyRole: (roles: readonly RoleType[]) => boolean;
  /** Marks the persisted session as rehydrated so guards stop showing a spinner. */
  markInitialized: () => void;
}

/**
 * Session is derived, never stored: a user with no tokens is not authenticated.
 * This keeps the read model impossible to desynchronise from the credentials.
 */
const deriveIsAuthenticated = (user: User | null, tokens: AuthTokens | null): boolean =>
  user !== null && tokens !== null && tokens.accessToken.length > 0;

const initialState: AuthState = {
  user: null,
  tokens: null,
  isAuthenticated: false,
  isInitialized: false,
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      ...initialState,

      setAuth: (user, tokens) =>
        set({
          user,
          tokens,
          isAuthenticated: deriveIsAuthenticated(user, tokens),
        }),

      setTokens: (tokens) => {
        const { user } = get();
        set({
          tokens,
          isAuthenticated: deriveIsAuthenticated(user, tokens),
        });
      },

      setUser: (user) =>
        set({
          user,
          isAuthenticated: deriveIsAuthenticated(user, get().tokens),
        }),

      clearAuth: () => set({ ...initialState, isInitialized: true }),

      hasRole: (role) => get().user?.roles.includes(role) ?? false,

      hasAnyRole: (roles) => {
        const granted: readonly RoleType[] = get().user?.roles ?? [];
        return roles.some((role) => granted.includes(role));
      },

      markInitialized: () => set({ isInitialized: true }),
    }),
    {
      name: STORAGE_KEY,
      version: 1,
      storage: createJSONStorage<PersistedAuth>(() => localStorage),
      /**
       * Only the principal + token pair is written to disk. Transient UI flags
       * such as `isInitialized` are re-derived on boot.
       */
      partialize: (state): PersistedAuth => ({
        user: state.user,
        tokens: state.tokens,
      }),
      /**
       * Guards against tampered or stale payloads: anything that does not
       * satisfy the shape contract is discarded and the user starts signed out.
       */
      merge: (persisted: unknown, current: AuthStore): AuthStore => {
        const candidate = persisted as Partial<PersistedAuth> | undefined;
        const user: User | null = isUser(candidate?.user) ? candidate.user : null;
        const tokens: AuthTokens | null = isAuthTokens(candidate?.tokens)
          ? candidate.tokens
          : null;

        return {
          ...current,
          user,
          tokens,
          isAuthenticated: deriveIsAuthenticated(user, tokens),
          isInitialized: true,
        };
      },
    },
  ),
);

/* -------------------------------------------------------------------------- */
/*  Runtime type guards                                                         */
/* -------------------------------------------------------------------------- */

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isRoleType(value: unknown): value is RoleType {
  return (
    value === RoleType.ADMIN || value === RoleType.TEACHER || value === RoleType.STUDENT
  );
}

function isUser(value: unknown): value is User {
  if (!isRecord(value)) {
    return false;
  }

  const roles: unknown = value['roles'];

  return (
    typeof value['userId'] === 'number' &&
    typeof value['username'] === 'string' &&
    typeof value['email'] === 'string' &&
    typeof value['fullName'] === 'string' &&
    (typeof value['avatarUrl'] === 'string' || value['avatarUrl'] === null) &&
    Array.isArray(roles) &&
    roles.length > 0 &&
    roles.every(isRoleType)
  );
}

function isAuthTokens(value: unknown): value is AuthTokens {
  return (
    isRecord(value) &&
    typeof value['accessToken'] === 'string' &&
    value['accessToken'].length > 0 &&
    typeof value['refreshToken'] === 'string' &&
    value['refreshToken'].length > 0
  );
}

/* -------------------------------------------------------------------------- */
/*  Non-reactive accessors — safe to call from Axios interceptors              */
/*  (reading state outside the React render cycle, never triggers a rerender)   */
/* -------------------------------------------------------------------------- */

/** Snapshot accessor used by the HTTP layer to read the bearer token. */
export const authStoreApi = {
  getState: (): AuthStore => useAuthStore.getState(),
  getAccessToken: (): string | null => useAuthStore.getState().tokens?.accessToken ?? null,
  getRefreshToken: (): string | null => useAuthStore.getState().tokens?.refreshToken ?? null,
  isAuthenticated: (): boolean => useAuthStore.getState().isAuthenticated,
};