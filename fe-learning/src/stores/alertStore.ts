import { create } from 'zustand';

export type AlertTone = 'success' | 'error' | 'warning' | 'info';

export interface Alert {
  id: string;
  tone: AlertTone;
  title: string;
  description?: string;
  /** Milliseconds before auto-dismiss; `0` keeps the alert until dismissed. */
  duration: number;
  createdAt: number;
}

export interface AlertState {
  alerts: Alert[];
  push: (alert: Omit<Alert, 'id' | 'createdAt' | 'duration'> & { duration?: number }) => string;
  dismiss: (id: string) => void;
  clear: () => void;
}

let counter: number = 0;

/** Monotonic id — avoids `crypto.randomUUID` for wider support & testability. */
const nextId = (): string => {
  counter += 1;
  return `alert-${Date.now()}-${counter}`;
};

const DEFAULT_DURATION: number = 5_000;

/**
 * Lightweight, dependency-free notification queue.
 * The layout mounts a single host component that renders whatever is queued.
 */
export const useAlertStore = create<AlertState>()((set, get) => ({
  alerts: [],

  push: ({ tone, title, description, duration }) => {
    const id: string = nextId();
    const resolvedDuration: number = duration ?? DEFAULT_DURATION;

    set({ alerts: [...get().alerts, { id, tone, title, description, duration: resolvedDuration, createdAt: Date.now() }] });

    if (resolvedDuration > 0) {
      window.setTimeout(() => {
        get().dismiss(id);
      }, resolvedDuration);
    }

    return id;
  },

  dismiss: (id) => set({ alerts: get().alerts.filter((alert) => alert.id !== id) }),

  clear: () => set({ alerts: [] }),
}));

/** Imperative helpers so non-React modules (interceptors) can raise alerts. */
export const alerts = {
  success: (title: string, description?: string): string =>
    useAlertStore.getState().push({ tone: 'success', title, ...(description !== undefined ? { description } : {}) }),
  error: (title: string, description?: string): string =>
    useAlertStore.getState().push({ tone: 'error', title, ...(description !== undefined ? { description } : {}) }),
  warning: (title: string, description?: string): string =>
    useAlertStore.getState().push({ tone: 'warning', title, ...(description !== undefined ? { description } : {}) }),
  info: (title: string, description?: string): string =>
    useAlertStore.getState().push({ tone: 'info', title, ...(description !== undefined ? { description } : {}) }),
};