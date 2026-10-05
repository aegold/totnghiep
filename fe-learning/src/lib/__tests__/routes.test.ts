import { describe, expect, it } from 'vitest';

import { buildLoginUrl, homeForRole, readRedirectTarget, resolveHome, ROUTES } from '@/lib/routes';
import { RoleType } from '@/types/auth';

describe('routes helpers', () => {
  it('maps each role to its own landing page', () => {
    expect(homeForRole(RoleType.STUDENT)).toBe(ROUTES.student.dashboard);
    expect(homeForRole(RoleType.TEACHER)).toBe(ROUTES.teacher.dashboard);
    expect(homeForRole(RoleType.ADMIN)).toBe(ROUTES.admin.dashboard);
  });

  it('resolves the highest-privilege role for multi-role principals', () => {
    expect(resolveHome([RoleType.STUDENT, RoleType.TEACHER])).toBe(ROUTES.teacher.dashboard);
    expect(resolveHome([RoleType.STUDENT, RoleType.TEACHER, RoleType.ADMIN])).toBe(
      ROUTES.admin.dashboard,
    );
    expect(resolveHome([])).toBe(ROUTES.login);
  });

  it('preserves the intended destination through the login redirect', () => {
    const url = buildLoginUrl('/teacher/grading');
    expect(url).toBe(`/login?redirect=${encodeURIComponent('/teacher/grading')}`);
    expect(readRedirectTarget(new URL(url, 'http://x').search)).toBe('/teacher/grading');
  });

  it('blocks open-redirect payloads', () => {
    expect(readRedirectTarget('?redirect=//evil.com')).toBe('/');
    expect(readRedirectTarget('?redirect=https://evil.com')).toBe('/');
    expect(readRedirectTarget('')).toBe('/');
  });
});