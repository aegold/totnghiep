import { describe, expect, it } from 'vitest';

import { buildNavigation } from '@/lib/navigation';
import { RoleType } from '@/types/auth';

describe('buildNavigation', () => {
  it('returns the role-specific menu in declared order', () => {
    const items = buildNavigation([RoleType.STUDENT]);
    const labels = items.map((item) => item.label);

    /* Labels are localized for display; the `to` paths stay English. */
    expect(labels[0]).toBe('Bảng điều khiển');
    expect(labels).toContain('Trợ giảng AI');
    expect(labels).not.toContain('Sinh viên');
  });

  it('merges menus for multi-role principals without duplicating entries', () => {
    const items = buildNavigation([RoleType.TEACHER, RoleType.ADMIN]);
    const paths = items.map((item) => item.to);

    expect(new Set(paths).size).toBe(paths.length);
    expect(paths).toContain('/admin/users');
    expect(paths).toContain('/teacher/grading');
  });

  it('returns an empty menu for a principal with no roles', () => {
    expect(buildNavigation([])).toEqual([]);
  });
});