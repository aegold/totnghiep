import { ChevronRight, LogOut, Menu, Settings, User as UserIcon } from 'lucide-react';
import type { ReactElement } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { UserAvatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/hooks/useAuth';
import { useBreadcrumbs, type Crumb } from '@/hooks/useBreadcrumbs';
import { ROLE_LABEL } from '@/lib/navigation';
import { ROUTES } from '@/lib/routes';
import { cn, humanizeToken } from '@/lib/utils';
import { alerts } from '@/stores/alertStore';
import { useUiStore } from '@/stores/uiStore';

interface AppHeaderProps {
  isDesktop: boolean;
}

/** Top bar: mobile trigger, breadcrumbs, and the account menu. */
export function AppHeader({ isDesktop }: AppHeaderProps): ReactElement {
  const { user, logout } = useAuth();
  const crumbs: Crumb[] = useBreadcrumbs();
  const navigate = useNavigate();
  const setMobileSidebarOpen = useUiStore((state) => state.setMobileSidebarOpen);

  const handleLogout = async (): Promise<void> => {
    await logout();
    alerts.success('Đã đăng xuất', 'Bạn đã đăng xuất khỏi hệ thống thành công.');
    navigate(ROUTES.login, { replace: true });
  };

  const openMobileNav = (): void => setMobileSidebarOpen(true);

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-slate-200 bg-white px-4 sm:px-6">
      {!isDesktop && (
        <Button variant="ghost" size="icon" onClick={openMobileNav} aria-label="Mở menu điều hướng">
          <Menu aria-hidden="true" />
        </Button>
      )}

      {/* Breadcrumbs */}
      <nav aria-label="Đường dẫn điều hướng" className="min-w-0 flex-1">
        <ol className="flex items-center gap-1 text-sm">
          {crumbs.map((crumb: Crumb, index: number) => {
            const isLast: boolean = index === crumbs.length - 1;

            return (
              <li key={`${crumb.label}-${index}`} className="flex min-w-0 items-center gap-1">
                {index > 0 && (
                  <ChevronRight
                    className="size-3.5 shrink-0 text-slate-300"
                    aria-hidden="true"
                  />
                )}
                {crumb.to !== null && !isLast ? (
                  <Link
                    to={crumb.to}
                    className="truncate rounded px-1 py-0.5 text-slate-500 transition-colors hover:text-slate-900"
                  >
                    {crumb.label}
                  </Link>
                ) : (
                  <span
                    className={cn(
                      'truncate px-1 py-0.5 font-medium',
                      isLast ? 'text-slate-900' : 'text-slate-500',
                    )}
                    aria-current={isLast ? 'page' : undefined}
                  >
                    {crumb.label}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>

      {/* Account menu */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            className="flex items-center gap-2 rounded-full p-1 pr-2 transition-colors hover:bg-slate-100"
            aria-label="Mở menu tài khoản"
          >
            <UserAvatar name={user?.fullName ?? 'Guest'} src={user?.avatarUrl} size="sm" />
            <span className="hidden text-sm font-medium text-slate-700 sm:inline">
              {user?.fullName ?? 'Guest'}
            </span>
          </button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="w-60">
          <DropdownMenuLabel>Tài khoản</DropdownMenuLabel>

          <div className="px-2.5 pb-2 pt-1">
            <p className="truncate text-sm font-semibold text-slate-900">
              {user?.fullName ?? 'Guest'}
            </p>
            <p className="truncate text-xs text-slate-500">{user?.email ?? ''}</p>
            <div className="mt-1.5 flex flex-wrap gap-1">
              {(user?.roles ?? []).map((role) => (
                <span
                  key={role}
                  className="inline-flex items-center rounded-full bg-brand-50 px-2 py-0.5 text-[10px] font-semibold text-brand-700"
                >
                  {ROLE_LABEL[role] ?? humanizeToken(role)}
                </span>
              ))}
            </div>
          </div>

          <DropdownMenuSeparator />

          <DropdownMenuItem onSelect={() => navigate(ROUTES.student.progress)}>
            <UserIcon aria-hidden="true" />
            Hồ sơ cá nhân
          </DropdownMenuItem>

          <DropdownMenuItem onSelect={() => navigate(ROUTES.student.progress)}>
            <Settings aria-hidden="true" />
            Tùy chọn hiển thị
          </DropdownMenuItem>

          <DropdownMenuSeparator />

          <DropdownMenuItem destructive onSelect={() => void handleLogout()}>
            <LogOut aria-hidden="true" />
            Đăng xuất
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}