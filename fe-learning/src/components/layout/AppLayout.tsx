import { useEffect, type ReactElement } from 'react';
import { Outlet, useLocation } from 'react-router-dom';

import { AppHeader } from '@/components/layout/AppHeader';
import { AppSidebar } from '@/components/layout/AppSidebar';
import { AlertHost } from '@/components/ui/alert-host';
import { useIsDesktop } from '@/hooks/useMediaQuery';
import { useUiStore } from '@/stores/uiStore';

/**
 * Authenticated application shell.
 *
 * Layout contract:
 *  - Desktop: persistent (optionally collapsed) sidebar + header.
 *  - Mobile: off-canvas drawer with a scrim, closed on every navigation.
 *  - Content viewport scrolls independently so the header stays pinned.
 */
export function AppLayout(): ReactElement {
  const isDesktop: boolean = useIsDesktop();
  const isMobileSidebarOpen = useUiStore((state) => state.isMobileSidebarOpen);
  const setMobileSidebarOpen = useUiStore((state) => state.setMobileSidebarOpen);
  const { pathname } = useLocation();

  /* Close the drawer whenever the route changes on small screens. */
  useEffect(() => {
    if (!isDesktop && isMobileSidebarOpen) {
      setMobileSidebarOpen(false);
    }
  }, [pathname, isDesktop, isMobileSidebarOpen, setMobileSidebarOpen]);

  /* Lock body scroll while the mobile drawer is open. */
  useEffect(() => {
    if (isDesktop) {
      return;
    }
    document.body.style.overflow = isMobileSidebarOpen ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [isDesktop, isMobileSidebarOpen]);

  /* Escape key closes the drawer. */
  useEffect(() => {
    if (!isDesktop || !isMobileSidebarOpen) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        setMobileSidebarOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isDesktop, isMobileSidebarOpen, setMobileSidebarOpen]);

  return (
    <div className="flex h-screen w-full overflow-hidden bg-canvas">
      {/* Desktop sidebar */}
      {isDesktop && (
        <aside className="shrink-0">
          <AppSidebar isDesktop />
        </aside>
      )}

      {/* Mobile drawer + scrim */}
      {!isDesktop && isMobileSidebarOpen && (
        <div className="fixed inset-0 z-50 flex" role="dialog" aria-modal="true">
          <button
            type="button"
            className="absolute inset-0 animate-fade-in bg-slate-900/40 backdrop-blur-[1px]"
            onClick={() => setMobileSidebarOpen(false)}
            aria-label="Đóng menu điều hướng"
          />
          <div className="relative h-full animate-slide-in shadow-overlay">
            <AppSidebar isDesktop={false} />
          </div>
        </div>
      )}

      {/* Main column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <AppHeader isDesktop={isDesktop} />

        <main
          id="main-content"
          className="scrollbar-slim flex-1 overflow-y-auto overflow-x-hidden"
          tabIndex={-1}
        >
          <div className="mx-auto w-full max-w-7xl p-4 sm:p-6 lg:p-8">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Global notification host */}
      <AlertHost />
    </div>
  );
}

export default AppLayout;