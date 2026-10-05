import { RoleType } from '@/types/auth';

/**
 * Single source of truth for every URL in the SPA.
 * Never hard-code a path in a component — reference it from here.
 */
export const ROUTES = {
  root: '/',
  login: '/login',
  register: '/register',
  forbidden: '/forbidden',
  /** Legacy alias kept for inbound links/bookmarks. */
  unauthorized: '/unauthorized',
  student: {
    root: '/student',
    dashboard: '/student/dashboard',
    courses: '/student/courses',
    exams: '/student/exams',
    aiTutor: '/student/ai-tutor',
    progress: '/student/progress',
    /** Sandbox that mounts every question type from local fixtures. */
    enginePreview: '/student/engine-preview',
    /** Interactive learning workspace: `/student/courses/:courseId/learn`. */
    learnPattern: '/student/courses/:courseId/learn',
    learn: (courseId: number | string): string => `/student/courses/${courseId}/learn`,
    /** Skill tree — needs the full viewport for panning/zooming. */
    skills: '/student/skills',
    /** Live exam attempt: `/student/exams/:examId/run`. */
    examRunPattern: '/student/exams/:examId/run',
    examRun: (examId: number | string): string => `/student/exams/${examId}/run`,
  },
  teacher: {
    root: '/teacher',
    dashboard: '/teacher/dashboard',
    courses: '/teacher/courses',
    exams: '/teacher/exams',
    grading: '/teacher/grading',
    students: '/teacher/students',
    /** Visual question authoring tool. */
    questionBuilder: '/teacher/questions/new',
    /** Cohort analytics dashboard (pulls in Recharts). */
    analytics: '/teacher/analytics',
  },
  admin: {
    root: '/admin',
    dashboard: '/admin/dashboard',
    users: '/admin/users',
    courses: '/admin/courses',
    settings: '/admin/settings',
  },
} as const;

/** Query parameter carrying the originally requested path. */
export const REDIRECT_PARAM = 'redirect';

/** Builds a login URL that returns the visitor to where they were headed. */
export const buildLoginUrl = (from: string): string =>
  `${ROUTES.login}?${REDIRECT_PARAM}=${encodeURIComponent(from)}`;

/** Reads the post-login destination from the query string, guarding open redirects. */
export const readRedirectTarget = (search: string): string => {
  const params: URLSearchParams = new URLSearchParams(search);
  const target: string | null = params.get(REDIRECT_PARAM);

  // Only same-origin absolute paths are honoured (blocks `//evil.com` and friends).
  if (target === null || !target.startsWith('/') || target.startsWith('//')) {
    return ROUTES.root;
  }

  return target;
};

/** Landing page for each role, used by post-login and post-logout navigation. */
export const homeForRole = (role: RoleType): string => {
  switch (role) {
    case RoleType.ADMIN:
      return ROUTES.admin.dashboard;
    case RoleType.TEACHER:
      return ROUTES.teacher.dashboard;
    case RoleType.STUDENT:
      return ROUTES.student.dashboard;
  }
};

/**
 * Best landing page for a principal holding one or more roles.
 * ADMIN wins over TEACHER, which wins over STUDENT.
 */
export const resolveHome = (roles: readonly RoleType[]): string => {
  if (roles.includes(RoleType.ADMIN)) {
    return homeForRole(RoleType.ADMIN);
  }
  if (roles.includes(RoleType.TEACHER)) {
    return homeForRole(RoleType.TEACHER);
  }
  if (roles.includes(RoleType.STUDENT)) {
    return homeForRole(RoleType.STUDENT);
  }
  return ROUTES.login;
};