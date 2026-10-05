/**
 * Authentication & authorisation domain contracts.
 * These mirror the Spring Boot `UserDetails` / JWT payloads exposed by the API.
 */

/** Authority names exactly as granted by Spring Security. */
export enum RoleType {
  ADMIN = 'ROLE_ADMIN',
  TEACHER = 'ROLE_TEACHER',
  STUDENT = 'ROLE_STUDENT',
}

/** Every role in the system, useful for permission matrices and tests. */
export const ALL_ROLES: readonly RoleType[] = [
  RoleType.ADMIN,
  RoleType.TEACHER,
  RoleType.STUDENT,
] as const;

/** Authenticated principal resolved from the access token / `/auth/me`. */
export interface User {
  userId: number;
  username: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  roles: RoleType[];
}

/** JWT pair returned by login and rotated on every refresh. */
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

/**
 * Serialisable slice of the auth store that survives a page reload.
 * Held separately from `AuthState` so persistence concerns stay explicit.
 */
export interface PersistedAuth {
  user: User | null;
  tokens: AuthTokens | null;
}

/** Read model consumed by `ProtectedRoute`, the sidebar, and profile menus. */
export interface AuthState {
  user: User | null;
  tokens: AuthTokens | null;
  isAuthenticated: boolean;
  /** `true` once the persisted session has been rehydrated and validated. */
  isInitialized: boolean;
}

/* -------------------------------------------------------------------------- */
/*  Request / response DTOs                                                    */
/* -------------------------------------------------------------------------- */

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

/** Shape returned by `/auth/login`, `/auth/register`, and `/auth/refresh`. */
export interface AuthResponse {
  user: User;
  tokens: AuthTokens;
  /** Absolute expiry (ISO-8601) of the access token, when the server sends it. */
  expiresAt: string | null;
}

/** Shape returned by `/auth/logout`; always 204 in the reference backend. */
export type LogoutResponse = void;