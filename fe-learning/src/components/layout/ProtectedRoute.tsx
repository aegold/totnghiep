import { Loader2 } from 'lucide-react';
import type { ReactElement, ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { buildLoginUrl, ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import type { RoleType } from '@/types/auth';

export interface ProtectedRouteProps {
  /**
   * Roles permitted to render the subtree. When omitted, any authenticated
   * principal may enter (used by shared pages such as the course catalogue).
   */
  allowedRoles?: readonly RoleType[];
  /** Destination for authenticated users lacking the required role. */
  forbiddenRedirect?: string;
  children?: ReactNode;
}

/** Full-viewport gate shown while the persisted session is rehydrating. */
function SessionGate(): ReactElement {
  return (
    <div
      className="flex min-h-screen items-center justify-center bg-canvas"
      role="status"
      aria-live="polite"
    >
      <div className="flex flex-col items-center gap-3">
        <Loader2 className="size-7 animate-spin text-brand-600" aria-hidden="true" />
        <p className="text-sm text-slate-500">Đang khôi phục phiên làm việc…</p>
      </div>
    </div>
  );
}

/**
 * RBAC gate for a route subtree.
 *
 * Decision order:
 *  1. Session not yet rehydrated -> render a gate (never redirect on a
 *     half-initialised store, which would bounce authenticated users out).
 *  2. Not authenticated -> redirect to `/login`, preserving the target path.
 *  3. Authenticated but role not granted -> redirect to `/forbidden`.
 *  4. Otherwise render `<Outlet />` (or `children` when supplied).
 *
 * Role evaluation uses intersection semantics: a principal holding several
 * roles passes if ANY of them is allowed.
 */
export function ProtectedRoute({
  allowedRoles,
  forbiddenRedirect = ROUTES.forbidden,
  children,
}: ProtectedRouteProps): ReactElement {
  const isInitialized = useAuthStore((state) => state.isInitialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const roles = useAuthStore((state) => state.user?.roles);

  const location = useLocation();

  if (!isInitialized) {
    return <SessionGate />;
  }

  if (!isAuthenticated) {
    return <Navigate to={buildLoginUrl(`${location.pathname}${location.search}`)} replace />;
  }

  if (allowedRoles !== undefined && allowedRoles.length > 0) {
    const granted: readonly RoleType[] = roles ?? [];
    const hasRequiredRole: boolean = allowedRoles.some((role: RoleType) =>
      granted.includes(role),
    );

    if (!hasRequiredRole) {
      return <Navigate to={forbiddenRedirect} replace />;
    }
  }

  return <>{children ?? <Outlet />}</>;
}

export default ProtectedRoute;