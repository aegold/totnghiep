import { Compass } from 'lucide-react';
import type { ReactElement } from 'react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/ui/button';
import { ROUTES } from '@/lib/routes';

/** Catch-all 404 page. */
export function NotFoundPage(): ReactElement {
  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-slate-100">
          <Compass className="size-7 text-slate-500" aria-hidden="true" />
        </div>

        <p className="mt-6 text-sm font-semibold uppercase tracking-wide text-slate-400">404</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
          Không tìm thấy trang
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          Trang bạn đang tìm có thể đã được chuyển đi hoặc không còn tồn tại.
        </p>

        <div className="mt-8">
          <Button asChild>
            <Link to={ROUTES.root}>Về bảng điều khiển</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}

export default NotFoundPage;