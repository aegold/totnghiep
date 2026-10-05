import { CircleUser, PanelLeftClose, PanelLeftOpen, X, type LucideIcon } from 'lucide-react';
import type { ReactElement } from 'react';
import { NavLink } from 'react-router-dom';

import { Badge } from '@/components/ui/badge';
import { buildNavigation, ROLE_ICON, ROLE_LABEL } from '@/lib/navigation';
import { cn, shortLabel } from '@/lib/utils';
import { useAuthStore } from '@/stores/authStore';
import { useUiStore } from '@/stores/uiStore';
import type { RoleType } from '@/types/auth';

interface AppSidebarProps {
  /** `false` renders the mobile drawer (always expanded, with a close button). */
  isDesktop: boolean;
}

/** Primary navigation rail. Collapses to icons on desktop. */
export function AppSidebar({ isDesktop }: AppSidebarProps): ReactElement {
  const roles = useAuthStore((state) => state.user?.roles) ?? [];
  const isCollapsed = useUiStore((state) => state.isSidebarCollapsed);
  const toggleSidebar = useUiStore((state) => state.toggleSidebar);
  const setMobileSidebarOpen = useUiStore((state) => state.setMobileSidebarOpen);

  const items = buildNavigation(roles);
  const primaryRole: RoleType | undefined = roles[0];
  /* Icon component for the primary role's badge; null-safe placeholder. */
  const RoleIcon: LucideIcon = primaryRole !== undefined
    ? ROLE_ICON[primaryRole]
    : CircleUser;

  /* On mobile the drawer is always fully expanded. */
  const collapsed: boolean = isDesktop && isCollapsed;
  const closeMobileDrawer = (): void => setMobileSidebarOpen(false);

  return (
    <div
      className={cn(
        'flex h-full flex-col border-r border-slate-200 bg-white transition-[width] duration-200 ease-out',
        isDesktop ? (collapsed ? 'w-[4.5rem]' : 'w-64') : 'w-72',
      )}
    >
      {/* Brand */}
      <div
        className={cn(
          'flex h-16 shrink-0 items-center border-b border-slate-200',
          collapsed ? 'justify-center px-2' : 'gap-2.5 px-4',
        )}
      >
        <div className="flex size-8 shrink-0 items-center justify-center rounded-[--radius-field] bg-brand-600 text-sm font-bold text-white">
          A
        </div>
        {!collapsed && (
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold leading-tight text-slate-900">
              Hệ thống LMS Thích ứng
            </p>
            <p className="truncate text-xs leading-tight text-slate-500">
              Nền tảng Trợ giảng AI
            </p>
          </div>
        )}
        {!isDesktop && (
          <button
            type="button"
            onClick={closeMobileDrawer}
            className="rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
            aria-label="Đóng menu điều hướng"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        )}
      </div>

      {/* Role badge */}
      {primaryRole !== undefined && (
        <div
          className={cn(
            'shrink-0 border-b border-slate-100 py-3',
            collapsed ? 'px-2' : 'px-4',
          )}
        >
          {collapsed ? (
            <div className="flex justify-center">
              <Badge variant="neutral" className="px-1.5 py-0.5 text-[10px]">
                {shortLabel(primaryRole)}
              </Badge>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Badge variant="default">
                {primaryRole !== undefined && (
                  <RoleIcon className="size-3" aria-hidden="true" />
                )}
                {ROLE_LABEL[primaryRole]}
              </Badge>
              {roles.length > 1 && (
                <span className="text-xs text-slate-400">+{roles.length - 1}</span>
              )}
            </div>
          )}
        </div>
      )}

      {/* Navigation */}
      <nav
        className="scrollbar-slim flex-1 space-y-1 overflow-y-auto p-3"
        aria-label="Điều hướng chính"
      >
        {items.map((item) => {
          const Icon = item.icon;

          /* Collapsed rail: icon-only, tooltip supplied via the native title. */
          if (collapsed) {
            return (
              <NavLink
                key={item.to}
                to={item.to}
                title={item.label}
                aria-label={item.label}
                className={({ isActive }) =>
                  cn(
                    'flex items-center justify-center rounded-[--radius-field] py-2.5 transition-colors',
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-500 hover:bg-slate-100 hover:text-slate-900',
                  )
                }
              >
                <Icon className="size-5" aria-hidden="true" />
              </NavLink>
            );
          }

          return (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={closeMobileDrawer}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-[--radius-field] px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon
                    className={cn(
                      'size-[1.125rem] shrink-0',
                      isActive ? 'text-brand-600' : 'text-slate-400',
                    )}
                    aria-hidden="true"
                  />
                  <span className="truncate">{item.label}</span>
                </>
              )}
            </NavLink>
          );
        })}
      </nav>

      {/* Collapse toggle (desktop only) */}
      {isDesktop && (
        <div className="shrink-0 border-t border-slate-200 p-3">
          <button
            type="button"
            onClick={toggleSidebar}
            className={cn(
              'flex w-full items-center gap-3 rounded-[--radius-field] px-3 py-2 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900',
              collapsed && 'justify-center px-0',
            )}
            aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'}
            aria-expanded={!collapsed}
          >
            {collapsed ? (
              <PanelLeftOpen className="size-[1.125rem]" aria-hidden="true" />
            ) : (
              <>
                <PanelLeftClose className="size-[1.125rem]" aria-hidden="true" />
                <span>Thu gọn</span>
              </>
            )}
          </button>
        </div>
      )}
    </div>
  );
}