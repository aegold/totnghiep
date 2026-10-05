import { useMemo } from 'react';
import { useLocation, useMatches } from 'react-router-dom';

export interface Crumb {
  label: string;
  /** `null` for the current page — rendered as plain text, not a link. */
  to: string | null;
}

/**
 * Turns a URL segment into a readable label, e.g. `ai-tutor` -> `Trợ giảng AI`.
 *
 * Vietnamese labels contain spaces, so a plain title-case transform produces
 * "Cau Hoi" rather than "Câu hỏi". A curated map covers the known routes and
 * anything else falls back to sentence-casing the slug.
 */
const SEGMENT_LABEL: Readonly<Record<string, string>> = {
  student: 'Sinh viên',
  teacher: 'Giảng viên',
  admin: 'Quản trị',
  dashboard: 'Bảng điều khiển',
  courses: 'Môn học',
  exams: 'Kỳ thi',
  grading: 'Chấm điểm',
  students: 'Sinh viên',
  users: 'Người dùng',
  settings: 'Cài đặt',
  progress: 'Tiến độ',
  skills: 'Cây tri thức',
  learn: 'Học tập',
  run: 'Làm bài',
  analytics: 'Phân tích',
  questions: 'Câu hỏi',
  new: 'Tạo mới',
  'ai-tutor': 'Trợ giảng AI',
  'engine-preview': 'Trình soạn câu hỏi',
  'question-builder': 'Trình soạn câu hỏi',
};

const humanize = (segment: string): string => {
  const known: string | undefined = SEGMENT_LABEL[segment];
  if (known !== undefined) {
    return known;
  }

  /* Unknown slug: strip separators and capitalise the first letter only. */
  const spaced: string = segment.replace(/[-_]/g, ' ').trim();
  if (spaced.length === 0) {
    return spaced;
  }

  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
};

/**
 * Derives the breadcrumb trail from the current pathname.
 * Kept URL-driven so it stays correct for routes that are not yet migrated
 * to the data-router `handle` API.
 */
export function useBreadcrumbs(): Crumb[] {
  const { pathname } = useLocation();
  // Subscribing keeps this hook reactive to data-router transitions.
  useMatches();

  return useMemo<Crumb[]>(() => {
    const segments: string[] = pathname.split('/').filter(Boolean);

    if (segments.length === 0) {
      return [{ label: 'Trang chủ', to: null }];
    }

    const crumbs: Crumb[] = segments.map((segment: string, index: number) => {
      const to: string = `/${segments.slice(0, index + 1).join('/')}`;
      const isLast: boolean = index === segments.length - 1;
      return { label: humanize(segment), to: isLast ? null : to };
    });

    return [{ label: 'Trang chủ', to: '/' }, ...crumbs];
  }, [pathname]);
}