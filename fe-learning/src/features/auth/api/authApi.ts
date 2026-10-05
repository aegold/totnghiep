import { AUTH_ENDPOINTS, http } from '@/lib/apiClient';
import { ApiError } from '@/types/api';
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
} from '@/types/auth';

/**
 * Authentication transport layer.
 * Every function returns the normalised `ApiError` on failure so callers
 * never need to inspect raw Axios internals.
 */
export const authApi = {
  async login(payload: LoginRequest): Promise<AuthResponse> {
    try {
      return await http.post<AuthResponse>(AUTH_ENDPOINTS.login, payload);
    } catch (error: unknown) {
      throw error instanceof ApiError ? error : new ApiError(0, 'Không thể đăng nhập.');
    }
  },

  async register(payload: RegisterRequest): Promise<AuthResponse> {
    try {
      return await http.post<AuthResponse>(AUTH_ENDPOINTS.register, payload);
    } catch (error: unknown) {
      throw error instanceof ApiError ? error : new ApiError(0, 'Không thể tạo tài khoản.');
    }
  },

  /** Re-reads the principal; used to validate a persisted session on boot. */
  async me(): Promise<User> {
    return http.get<User>(AUTH_ENDPOINTS.me);
  },

  /** Best-effort server-side revocation; never blocks the local sign-out. */
  async logout(): Promise<void> {
    try {
      await http.post<void>(AUTH_ENDPOINTS.logout);
    } catch {
      // Intentionally ignored: the client session is cleared regardless.
    }
  },
};