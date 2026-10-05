import { useCallback } from 'react';

import { authApi } from '@/features/auth/api/authApi';
import { resolveHome } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { ApiError } from '@/types/api';
import type { AuthTokens, LoginRequest, RegisterRequest, User } from '@/types/auth';

export interface UseAuthResult {
  user: User | null;
  tokens: AuthTokens | null;
  isAuthenticated: boolean;
  isInitialized: boolean;
  login: (payload: LoginRequest) => Promise<string>;
  register: (payload: RegisterRequest) => Promise<string>;
  logout: () => Promise<void>;
  hasRole: (role: Parameters<ReturnType<typeof useAuthStore.getState>['hasRole']>[0]) => boolean;
}

/**
 * Thin, strongly-typed facade over the auth store + transport layer.
 * Components depend on this hook rather than on Zustand directly.
 */
export function useAuth(): UseAuthResult {
  const user = useAuthStore((state) => state.user);
  const tokens = useAuthStore((state) => state.tokens);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isInitialized = useAuthStore((state) => state.isInitialized);

  const setAuth = useAuthStore((state) => state.setAuth);
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const hasRole = useAuthStore((state) => state.hasRole);

  const login = useCallback(
    async (payload: LoginRequest): Promise<string> => {
      try {
        const { user: principal, tokens: issued } = await authApi.login(payload);
        setAuth(principal, issued);
        return resolveHome(principal.roles);
      } catch (error: unknown) {
        throw error instanceof ApiError ? error : new ApiError(0, 'Không thể đăng nhập.');
      }
    },
    [setAuth],
  );

  const register = useCallback(
    async (payload: RegisterRequest): Promise<string> => {
      try {
        const { user: principal, tokens: issued } = await authApi.register(payload);
        setAuth(principal, issued);
        return resolveHome(principal.roles);
      } catch (error: unknown) {
        throw error instanceof ApiError
          ? error
          : new ApiError(0, 'Không thể tạo tài khoản.');
      }
    },
    [setAuth],
  );

  const logout = useCallback(async (): Promise<void> => {
    await authApi.logout();
    clearAuth();
  }, [clearAuth]);

  return { user, tokens, isAuthenticated, isInitialized, login, register, logout, hasRole };
}