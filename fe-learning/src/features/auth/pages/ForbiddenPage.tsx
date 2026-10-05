import { ShieldAlert } from 'lucide-react';
import type { ReactElement } from 'react';
import { Link, Navigate } from 'react-router-dom';

import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/stores/authStore';
import { resolveHome } from '@/lib/routes';

/**
 * 403 page. Reachable both from `ProtectedRoute` and directly, so it must
 * live OUTSIDE the protected layout — hence the local `Card` styling.
 */
export function ForbiddenPage(): ReactElement {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const roles = useAuthStore((state) => state.user?.roles) ?? [];

  /* Signed-out visitors have no business here; send them to login. */
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-amber-50">
          <ShieldAlert className="size-7 text-amber-600" aria-hidden="true" />
        </div>

        <h1 className="mt-6 text-2xl font-semibold tracking-tight text-slate-900">
          Không có quyền truy cập
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          Tài khoản của bạn không được phép xem trang này. Nếu bạn cho rằng đây là nhầm lẫn, vui
          lòng liên hệ quản trị viên.
        </p>

        <div className="mt-8 flex items-center justify-center gap-3">
          <Button asChild variant="secondary">
            <Link to="/">Về bảng điều khiển</Link>
          </Button>
          <Button asChild>
            <Link to={resolveHome(roles)}>Đến không gian làm việc của tôi</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}

export default ForbiddenPage;