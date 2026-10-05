import { lazy, Suspense, type ComponentType, type ReactElement } from 'react';
import { Navigate, type RouteObject } from 'react-router-dom';

import AppLayout from '@/components/layout/AppLayout';
import ProtectedRoute from '@/components/layout/ProtectedRoute';
import ForbiddenPage from '@/features/auth/pages/ForbiddenPage';
import LoginPage from '@/features/auth/pages/LoginPage';
import NotFoundPage from '@/features/auth/pages/NotFoundPage';
import RegisterPage from '@/features/auth/pages/RegisterPage';

/**
 * Lazy because the runner pulls in the Question Engine (KaTeX + react-markdown).
 * Keeping it out of the initial chunk means only a candidate actually taking an
 * exam pays that download.
 */
const ExamRunPage = lazy<ComponentType>(
  () => import('@/features/exam-runner/pages/ExamRunPage'),
);

/**
 * The learning workspace owns the whole viewport: a split pane cannot live
 * inside the scrolling, padded `AppLayout` content area. It is still
 * role-guarded, so only students can enter.
 */
const CourseWorkspacePage = lazy<ComponentType>(
  () => import('@/features/course-learning/pages/CourseWorkspacePage'),
);

/**
 * The Skill Tree owns the whole viewport: panning and zooming a large graph
 * inside the padded, scrolling `AppLayout` area would be unusable.
 */
const SkillTreePage = lazy<ComponentType>(
  () => import('@/features/knowledge-graph/pages/SkillTreePage'),
);
import {
  ADMIN_SUBTREE,
  STUDENT_SUBTREE,
  TEACHER_SUBTREE,
  type SubtreeConfig,
} from '@/app/routeConfig';
import { ROUTES, resolveHome } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { RoleType } from '@/types/auth';

/** Blocking fallback shown while a lazily-loaded route chunk downloads. */
export function RouteFallback(): ReactElement {
  return (
    <div
      className="flex min-h-[50vh] items-center justify-center"
      role="status"
      aria-live="polite"
    >
      <div className="size-6 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600" />
      <span className="sr-only">Đang tải…</span>
    </div>
  );
}

/** Wraps a route element in Suspense. */
const lazyPage = (element: ReactElement): ReactElement => (
  <Suspense fallback={<RouteFallback />}>{element}</Suspense>
);

/**
 * Converts one role config into a route object.
 * Guarantees every role area receives the identical
 * `AppLayout` + `ProtectedRoute` treatment — no drift between areas.
 */
const buildSubtree = (config: SubtreeConfig): RouteObject => {
  const Dashboard = config.dashboard;

  return {
    element: (
      <ProtectedRoute allowedRoles={[config.role]}>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      /* The parent path doubles as the role's index/dashboard route. */
      {
        index: true,
        path: config.indexRoute,
        element: lazyPage(<Dashboard />),
        handle: { crumb: 'Overview' },
      },
      {
        path: config.dashboardPath,
        element: lazyPage(<Dashboard />),
        handle: { crumb: 'Dashboard' },
      },
      ...config.pages.map<RouteObject>((page) => ({
        path: page.path,
        element: page.render(),
        handle: { crumb: page.crumb },
      })),
    ],
  };
};

/**
 * Public route table.
 *
 *  /login, /register           -> public entry screens
 *  /forbidden, /unauthorized   -> error screens
 *  /student/*, /teacher/*, /admin/* -> AppLayout + RBAC-gated subtrees
 *  /                           -> role-aware redirect
 *  *                           -> 404
 */
export const routes: RouteObject[] = [
  /* -------------------------------- Public ------------------------------- */
  { path: ROUTES.login, element: <LoginPage /> },
  { path: ROUTES.register, element: <RegisterPage /> },
  { path: ROUTES.forbidden, element: <ForbiddenPage /> },
  /* Legacy alias so existing bookmarks still resolve. */
  { path: ROUTES.unauthorized, element: <Navigate to={ROUTES.forbidden} replace /> },

  /* ---------------------------- Guarded areas --------------------------- */
  buildSubtree(STUDENT_SUBTREE),
  buildSubtree(TEACHER_SUBTREE),
  buildSubtree(ADMIN_SUBTREE),

  /* ---------------------------- Fullscreen exam -------------------------- */
  /*
   * Deliberately OUTSIDE `AppLayout`. An attempt owns the entire viewport: no
   * app navigation, no sidebar, and no route transition that could remount the
   * runner mid-exam. Still role-guarded, so the layout differs but the access
   * rules do not.
   */
  {
    path: ROUTES.student.examRunPattern,
    element: (
      <ProtectedRoute allowedRoles={[RoleType.STUDENT]}>
        <Suspense fallback={<RouteFallback />}>
          <ExamRunPage />
        </Suspense>
      </ProtectedRoute>
    ),
  },

  /* --------------------------- Learning workspace ----------------------- */
  {
    path: ROUTES.student.learnPattern,
    element: (
      <ProtectedRoute allowedRoles={[RoleType.STUDENT]}>
        <Suspense fallback={<RouteFallback />}>
          <CourseWorkspacePage />
        </Suspense>
      </ProtectedRoute>
    ),
  },

  /* ------------------------------ Skill tree --------------------------- */
  {
    path: ROUTES.student.skills,
    element: (
      <ProtectedRoute allowedRoles={[RoleType.STUDENT]}>
        <Suspense fallback={<RouteFallback />}>
          <SkillTreePage />
        </Suspense>
      </ProtectedRoute>
    ),
  },

  /* ------------------------------- Fallbacks ---------------------------- */
  { path: ROUTES.root, element: <RoleAwareRedirect /> },
  { path: '*', element: <NotFoundPage /> },
];

/**
 * `/` — sends an authenticated principal to the workspace for their
 * highest-privilege role, and everyone else to the login screen.
 */
function RoleAwareRedirect(): ReactElement {
  const isInitialized = useAuthStore((state) => state.isInitialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const roles = useAuthStore((state) => state.user?.roles) ?? [];

  /* Never redirect on a half-rehydrated store. */
  if (!isInitialized) {
    return <RouteFallback />;
  }

  return <Navigate to={isAuthenticated ? resolveHome(roles) : ROUTES.login} replace />;
}