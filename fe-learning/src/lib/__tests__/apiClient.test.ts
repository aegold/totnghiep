/**
 * `apiClient` builds its interceptor-free refresh client at module load, so a
 * post-import spy on `axios.create` is too late. Mocking the module up front
 * lets us hand the refresh client a spy while the real Axios still backs the
 * primary instance (interceptors, `AxiosError`, `AxiosHeaders`).
 */
const { refreshPost } = vi.hoisted(() => ({ refreshPost: vi.fn() }));

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>();

  /** The primary client opts into `withCredentials`; the refresh client does not. */
  const create = (config?: { withCredentials?: boolean }) => {
    if (config?.withCredentials === true) {
      return actual.default.create(config);
    }
    return { post: refreshPost, get: vi.fn(), request: vi.fn(), defaults: {} };
  };

  return {
    ...actual,
    default: { ...actual.default, create },
  };
});

import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiClient } from '@/lib/apiClient';
import { useAuthStore } from '@/stores/authStore';
import { ApiError } from '@/types/api';
import { RoleType, type AuthTokens, type User } from '@/types/auth';

/** --- Test doubles ------------------------------------------------------- */

/** Minimal shape the adapters need; avoids depending on Axios internals. */
interface TestConfig {
  url?: string;
  headers: Record<string, string>;
}

const stubAdapter = (handler: (config: TestConfig) => { status: number; data: unknown }) => {
  const adapter = vi.fn(async (config: TestConfig) => {
    const { status, data } = handler(config);
    if (status >= 200 && status < 300) {
      return { status, data, statusText: 'OK', headers: {}, config };
    }
    const error = new axios.AxiosError(
      `Request failed with status code ${status}`,
      String(status),
      config as never,
      undefined,
      { status, data, statusText: 'Error', headers: {}, config } as never,
    );
    throw error;
  });
  apiClient.defaults.adapter = adapter as never;
  return adapter;
};

const TOKEN_A: AuthTokens = { accessToken: 'access-1', refreshToken: 'refresh-1' };
const TOKEN_B: AuthTokens = { accessToken: 'access-2', refreshToken: 'refresh-2' };

const makeUser = (roles: RoleType[]): User => ({
  userId: 1,
  username: 'jane.doe',
  email: 'jane@example.com',
  fullName: 'Jane Doe',
  avatarUrl: null,
  roles,
});

/** Counts calls made through the interceptor-free refresh client. */
const refreshSpy = refreshPost;

/** Tracks navigations triggered by the session-teardown path. */
let redirectSpy: ReturnType<typeof vi.fn>;

/** jsdom exposes `location` as read-only, so it must be redefined wholesale. */
const stubLocation = (): void => {
  redirectSpy = vi.fn();
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { pathname: '/student/dashboard', search: '', assign: redirectSpy, replace: vi.fn() },
  });
};

beforeEach(() => {
  /* Intercept every refresh attempt at the network boundary. */
  refreshSpy.mockReset();
  refreshSpy.mockResolvedValue({
    data: { user: makeUser([RoleType.STUDENT]), tokens: TOKEN_B, expiresAt: null },
  });

  useAuthStore.setState({
    user: makeUser([RoleType.STUDENT]),
    tokens: TOKEN_A,
    isAuthenticated: true,
    isInitialized: true,
  });

  stubLocation();
});

afterEach(() => {
  vi.restoreAllMocks();
});

/* -------------------------------------------------------------------------- */

describe('apiClient interceptors', () => {
  it('attaches the bearer token to outgoing requests', async () => {
    const adapter = stubAdapter(() => ({ status: 200, data: 'ok' }));

    await apiClient.get('/secure');

    const config = adapter.mock.calls[0]?.[0] as { headers: Record<string, string> };
    expect(config.headers['Authorization']).toBe('Bearer access-1');
  });

  it('performs ONE refresh for concurrent 401s and replays the whole queue', async () => {
    /* First attempt per endpoint is 401; the replay succeeds. */
    const seen = new Set<string>();
    stubAdapter((config) => {
      const url = config.url ?? '';
      if (seen.has(url)) {
        return { status: 200, data: 'replayed' };
      }
      seen.add(url);
      return { status: 401, data: { message: 'expired' } };
    });

    const results = await Promise.all([
      apiClient.get('/a'),
      apiClient.get('/b'),
      apiClient.get('/c'),
    ]);

    /* `axios` resolves with the full response; the wrapper `http.get` unwraps it. */
    const statuses = results.map((response) => response.status);

    expect(refreshSpy).toHaveBeenCalledTimes(1);
    expect(statuses).toEqual([200, 200, 200]);
    expect(results.map((response) => response.data)).toEqual([
      'replayed',
      'replayed',
      'replayed',
    ]);
    expect(useAuthStore.getState().tokens).toEqual(TOKEN_B);
  });

  it('replays requests with the NEW access token, not the expired one', async () => {
    const tokensUsed: string[] = [];
    let call = 0;
    apiClient.defaults.adapter = vi.fn(async (config: { url?: string; headers: Record<string, string> }) => {
      tokensUsed.push(config.headers['Authorization'] ?? '');
      call += 1;
      if (call === 1) {
        throw new axios.AxiosError('expired', '401', config as never, undefined, {
          status: 401,
          data: { message: 'expired' },
          statusText: 'Unauthorized',
          headers: {},
          config,
        } as never);
      }
      return { status: 200, data: 'ok', statusText: 'OK', headers: {}, config };
    }) as never;

    await apiClient.get('/secure');

    expect(tokensUsed[0]).toBe('Bearer access-1');
    expect(tokensUsed[1]).toBe('Bearer access-2');
  });

  it('purges the session and redirects when the refresh itself fails', async () => {
    refreshSpy.mockRejectedValueOnce(new Error('refresh rejected'));
    stubAdapter(() => ({ status: 401, data: { message: 'expired' } }));

    await expect(apiClient.get('/secure')).rejects.toBeInstanceOf(ApiError);

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().tokens).toBeNull();
    expect(redirectSpy).toHaveBeenCalled();
  });

  it('rejects queued requests when the refresh fails', async () => {
    refreshSpy.mockRejectedValueOnce(new Error('refresh rejected'));
    stubAdapter(() => ({ status: 401, data: { message: 'expired' } }));

    const settled = await Promise.allSettled([apiClient.get('/x'), apiClient.get('/y')]);

    expect(settled.every((result) => result.status === 'rejected')).toBe(true);
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  it('does not attempt a refresh for a repeated 401 on the same request', async () => {
    /* Always 401: the replay must not loop forever. */
    stubAdapter(() => ({ status: 401, data: { message: 'expired' } }));
    refreshSpy.mockRejectedValue(new Error('refresh rejected'));

    await expect(apiClient.get('/loop')).rejects.toBeInstanceOf(ApiError);

    /* Exactly one renewal attempt, then the session is torn down. */
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  it('passes non-401 failures straight through', async () => {
    stubAdapter(() => ({ status: 500, data: { message: 'server exploded' } }));

    await expect(apiClient.get('/boom')).rejects.toMatchObject({
      status: 500,
      message: 'server exploded',
    });
    expect(refreshSpy).not.toHaveBeenCalled();
  });
});