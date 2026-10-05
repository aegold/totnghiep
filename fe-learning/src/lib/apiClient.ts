import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';

import { authStoreApi, useAuthStore } from '@/stores/authStore';
import { ApiError } from '@/types/api';
import type { ApiEnvelope } from '@/types/api';
import type { AuthResponse, RefreshTokenRequest } from '@/types/auth';

/**
 * Where the Spring Boot API lives.
 *
 * Read from the environment so local development can point the browser straight
 * at :8080 (which genuinely exercises CORS) while a deployment behind a reverse
 * proxy keeps the relative default and stays same-origin. The relative fallback
 * is also what the Vitest suite resolves to, so tests never depend on a backend
 * being up.
 */
export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1';

export const AUTH_ENDPOINTS = {
  login: '/auth/login',
  register: '/auth/register',
  refresh: '/auth/refresh',
  logout: '/auth/logout',
  me: '/auth/me',
} as const;

/** Endpoints whose failures must never enter the refresh pipeline. */
const AUTH_FREE_PATHS: readonly string[] = [
  AUTH_ENDPOINTS.login,
  AUTH_ENDPOINTS.register,
  AUTH_ENDPOINTS.refresh,
] as const;

/** Marker stamped on a config once it has already survived one refresh cycle. */
interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  __isRetry?: boolean;
}

/**
 * A single failed request parked while the access token is renewed.
 * Once the renewal settles, every parked entry is replayed (or rejected)
 * with the same outcome, so the UI stays consistent.
 */
interface QueuedRequest {
  config: RetriableRequestConfig;
  resolve: (value: unknown) => void;
  reject: (reason: ApiError) => void;
}

/**
 * Single-flight guard. Guarantees exactly ONE `/auth/refresh` call is in flight
 * no matter how many in-flight requests 401 at the same instant. Subsequent
 * callers simply park in `pendingRequests` and are drained by the leader.
 */
let isRefreshing: boolean = false;
let pendingRequests: QueuedRequest[] = [];

/**
 * Drains the parked queue. On success every entry is replayed with the new
 * token; on failure every entry is rejected with the same normalised error.
 */
const flushQueue = (accessToken: string | null, error: ApiError | null): void => {
  const queued: QueuedRequest[] = pendingRequests;
  pendingRequests = [];

  for (const entry of queued) {
    if (accessToken !== null && error === null) {
      const headers = AxiosHeaders.from(entry.config.headers);
      headers.set('Authorization', `Bearer ${accessToken}`);
      entry.config.headers = headers;
      entry.resolve(apiClient.request(entry.config));
    } else if (error !== null) {
      entry.reject(error);
    }
  }
};

/** Terminates the session and sends the visitor back to the login screen. */
const purgeSessionAndRedirect = (): void => {
  useAuthStore.getState().clearAuth();

  const { pathname, search }: Location = window.location;

  if (pathname !== '/login') {
    const redirect: string = encodeURIComponent(`${pathname}${search}`);
    window.location.assign(`/login?redirect=${redirect}`);
  }
};

/**
 * Bare client reserved for token renewal. It intentionally has NO interceptors,
 * so a failing refresh can never recurse back into the response pipeline.
 */
const refreshClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

/**
 * Unwraps the backend's `ApiResponse` envelope down to the payload `T`.
 *
 * ## Why this exists
 * The Spring service wraps every body in `{ success, data, message, ... }`.
 * Feature code should never see that wrapper, so the five `http` helpers below
 * call this once and every call site keeps reading `T` exactly as before.
 *
 * ## Why it tolerates a bare payload
 * `unwrapEnvelope` is applied to every response, including any endpoint that
 * might later return a raw body. Detecting the wrapper by the presence of a
 * boolean `success` flag keeps the client working against both shapes rather
 * than silently handing `undefined` to a feature that expected `T`.
 *
 * @throws ApiError when the server reports failure on a 2xx status, which would
 *         otherwise reach feature code as a null payload with no explanation.
 */
const unwrapEnvelope = <T>(body: unknown): T => {
  if (typeof body !== 'object' || body === null || !('success' in body)) {
    return body as T;
  }

  const envelope = body as ApiEnvelope<T>;

  if (envelope.success === false) {
    throw new ApiError(
      200,
      envelope.message,
      envelope.fieldErrors ?? {},
    );
  }

  return envelope.data as T;
};

/** Normalises any Axios/network failure onto the `ApiError` contract. */
export const toApiError = (error: unknown): ApiError => {
  if (error instanceof ApiError) {
    return error;
  }

  if (error instanceof AxiosError) {
    const status: number = error.response?.status ?? 0;
    const data: unknown = error.response?.data;

    if (typeof data === 'object' && data !== null) {
      const payload = data as Record<string, unknown>;
      const message: string =
        typeof payload['message'] === 'string' ? payload['message'] : error.message;
      const rawFieldErrors: unknown = payload['fieldErrors'];
      const fieldErrors: Record<string, string> =
        typeof rawFieldErrors === 'object' && rawFieldErrors !== null
          ? (rawFieldErrors as Record<string, string>)
          : {};

      return new ApiError(status, message, fieldErrors);
    }

    return new ApiError(status, error.message || 'Không thể kết nối tới máy chủ.');
  }

  return new ApiError(0, 'Không thể kết nối tới máy chủ.');
};

/**
 * Renews the token pair and commits it to the store.
 * Exported so the auth feature can proactively extend a session.
 */
export const refreshAccessToken = async (): Promise<AuthResponse> => {
  const refreshToken: string | null = authStoreApi.getRefreshToken();

  if (refreshToken === null) {
    throw new ApiError(401, 'Không có token làm mới để sử dụng.');
  }

  const payload: RefreshTokenRequest = { refreshToken };
  const response = await refreshClient.post<ApiEnvelope<AuthResponse>>(
    AUTH_ENDPOINTS.refresh,
    payload,
  );

  const auth = unwrapEnvelope<AuthResponse>(response.data);
  const { user, tokens } = auth;
  useAuthStore.getState().setAuth(user, tokens);

  return auth;
};

/**
 * Leader-only routine: performs ONE renewal then drains the queue.
 * Must only ever be invoked when `isRefreshing` transitions false -> true.
 */
const performRefresh = async (): Promise<void> => {
  try {
    const auth: AuthResponse = await refreshAccessToken();
    flushQueue(auth.tokens.accessToken, null);
  } catch (error: unknown) {
    const apiError: ApiError = toApiError(error);
    flushQueue(null, apiError);
    purgeSessionAndRedirect();
  } finally {
    isRefreshing = false;
  }
};

/* -------------------------------------------------------------------------- */
/*  Application instance                                                        */
/* -------------------------------------------------------------------------- */

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

/* ------------------------------ Request phase ----------------------------- */

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const accessToken: string | null = authStoreApi.getAccessToken();

    if (accessToken !== null) {
      // Axios >= 1.x exposes a typed header bag, so assign a normalised instance.
      const headers = AxiosHeaders.from(config.headers);
      headers.set('Authorization', `Bearer ${accessToken}`);
      config.headers = headers;
    }

    return config;
  },
  (error: unknown): Promise<never> => Promise.reject(toApiError(error)),
);

/* ----------------------------- Response phase ----------------------------- */

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown): Promise<unknown> => {
    const apiError: ApiError = toApiError(error);
    const originalConfig: RetriableRequestConfig | undefined =
      error instanceof AxiosError
        ? ((error.config as RetriableRequestConfig | undefined) ?? undefined)
        : undefined;

    /* Not a 401, or nothing to replay -> terminal failure. */
    if (apiError.status !== 401 || originalConfig === undefined) {
      return Promise.reject(apiError);
    }

    const requestUrl: string = originalConfig.url ?? '';
    const isAuthEndpoint: boolean = AUTH_FREE_PATHS.some((path: string) =>
      requestUrl.includes(path),
    );

    /* Login/register/refresh failures can never be repaired by renewing. */
    if (isAuthEndpoint) {
      purgeSessionAndRedirect();
      return Promise.reject(apiError);
    }

    /* Already survived one refresh and still 401 -> session is genuinely dead. */
    if (originalConfig.__isRetry === true) {
      purgeSessionAndRedirect();
      return Promise.reject(apiError);
    }

    /* Stamp now so a second 401 during replay cannot re-enter the queue. */
    originalConfig.__isRetry = true;

    return new Promise<unknown>((resolve, reject) => {
      pendingRequests.push({ config: originalConfig, resolve, reject });

      /* Become the leader, or join the renewal already in flight. */
      if (!isRefreshing) {
        isRefreshing = true;
        void performRefresh();
      }
    });
  },
);

/* -------------------------------------------------------------------------- */
/*  Ergonomic typed wrappers                                                    */
/* -------------------------------------------------------------------------- */

export const http = {
  get: async <T>(url: string, config?: InternalAxiosRequestConfig): Promise<T> => {
    const response = await apiClient.get<ApiEnvelope<T>>(url, config);
    return unwrapEnvelope<T>(response.data);
  },
  post: async <T>(
    url: string,
    data?: unknown,
    config?: InternalAxiosRequestConfig,
  ): Promise<T> => {
    const response = await apiClient.post<ApiEnvelope<T>>(url, data, config);
    return unwrapEnvelope<T>(response.data);
  },
  put: async <T>(
    url: string,
    data?: unknown,
    config?: InternalAxiosRequestConfig,
  ): Promise<T> => {
    const response = await apiClient.put<ApiEnvelope<T>>(url, data, config);
    return unwrapEnvelope<T>(response.data);
  },
  patch: async <T>(
    url: string,
    data?: unknown,
    config?: InternalAxiosRequestConfig,
  ): Promise<T> => {
    const response = await apiClient.patch<ApiEnvelope<T>>(url, data, config);
    return unwrapEnvelope<T>(response.data);
  },
  delete: async <T>(url: string, config?: InternalAxiosRequestConfig): Promise<T> => {
    const response = await apiClient.delete<ApiEnvelope<T>>(url, config);
    return unwrapEnvelope<T>(response.data);
  },
};

export { ApiError };