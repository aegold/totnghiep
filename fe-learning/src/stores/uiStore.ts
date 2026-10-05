import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export interface UiState {
  /** Desktop sidebar collapsed to an icon rail. */
  isSidebarCollapsed: boolean;
  /** Mobile drawer currently open. */
  isMobileSidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setMobileSidebarOpen: (open: boolean) => void;
}

/**
 * Layout preferences. Only the desktop collapsed flag is persisted —
 * the mobile drawer must always start closed on a fresh load.
 */
export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      isSidebarCollapsed: false,
      isMobileSidebarOpen: false,

      toggleSidebar: () => set((state) => ({ isSidebarCollapsed: !state.isSidebarCollapsed })),
      setSidebarCollapsed: (collapsed) => set({ isSidebarCollapsed: collapsed }),
      setMobileSidebarOpen: (open) => set({ isMobileSidebarOpen: open }),
    }),
    {
      name: 'adaptive-lms.ui.v1',
      version: 1,
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state): Pick<UiState, 'isSidebarCollapsed'> => ({
        isSidebarCollapsed: state.isSidebarCollapsed,
      }),
      merge: (persisted: unknown, current: UiState): UiState => {
        const candidate = persisted as { isSidebarCollapsed?: unknown } | undefined;
        return {
          ...current,
          isSidebarCollapsed:
            typeof candidate?.isSidebarCollapsed === 'boolean'
              ? candidate.isSidebarCollapsed
              : false,
        };
      },
    },
  ),
);