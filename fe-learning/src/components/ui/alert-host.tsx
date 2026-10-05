import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import type { ComponentType, SVGProps } from 'react';

import { cn } from '@/lib/utils';
import { useAlertStore, type Alert, type AlertTone } from '@/stores/alertStore';

type IconComponent = ComponentType<SVGProps<SVGSVGElement>>;

const toneStyles: Record<AlertTone, { icon: IconComponent; accent: string }> = {
  success: { icon: CheckCircle2, accent: 'text-emerald-600' },
  error: { icon: AlertCircle, accent: 'text-danger' },
  warning: { icon: TriangleAlert, accent: 'text-amber-600' },
  info: { icon: Info, accent: 'text-brand-600' },
};

/**
 * Renders the queued notifications. Mounted once by `AppLayout` so any
 * feature can raise an alert through `alerts.*` without prop drilling.
 */
export function AlertHost() {
  const alerts: Alert[] = useAlertStore((state) => state.alerts);
  const dismiss = useAlertStore((state) => state.dismiss);

  if (alerts.length === 0) {
    return null;
  }

  return (
    <div
      role="region"
      aria-label="Notifications"
      className="pointer-events-none fixed bottom-4 right-4 z-[100] flex w-[calc(100vw-2rem)] max-w-sm flex-col gap-2"
    >
      {alerts.map((alert) => {
        const { icon: Icon, accent } = toneStyles[alert.tone];

        return (
          <div
            key={alert.id}
            role="alert"
            aria-live={alert.tone === 'error' ? 'assertive' : 'polite'}
            className="pointer-events-auto flex animate-slide-up items-start gap-3 rounded-[--radius-field] border border-slate-200 bg-white p-3.5 shadow-overlay"
          >
            <Icon className={cn('mt-0.5 size-5 shrink-0', accent)} aria-hidden="true" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-slate-900">{alert.title}</p>
              {alert.description !== undefined && (
                <p className="mt-0.5 text-sm text-slate-500">{alert.description}</p>
              )}
            </div>
            <button
              type="button"
              onClick={() => dismiss(alert.id)}
              className="-m-1 rounded p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              aria-label="Đóng thông báo"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>
        );
      })}
    </div>
  );
}