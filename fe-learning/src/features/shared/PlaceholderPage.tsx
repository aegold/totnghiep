import type { LucideIcon } from 'lucide-react';
import type { ReactElement, ReactNode } from 'react';

import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';

interface PlaceholderPageProps {
  title: string;
  description: string;
  icon: LucideIcon;
  /** Optional call to action rendered inside the empty state. */
  action?: ReactNode;
}

/**
 * Temporary scaffold for screens scheduled for a later stage.
 * Keeps every declared route reachable so navigation and RBAC can be
 * verified end-to-end before the feature slice lands.
 */
export function PlaceholderPage({
  title,
  description,
  icon,
  action,
}: PlaceholderPageProps): ReactElement {
  return (
    <div className="space-y-6">
      <PageHeader title={title} description={description} icon={icon} />
      <EmptyState
        icon={icon}
        title="Sắp có"
        description="Tính năng này đang được xây dựng ở giai đoạn sau. Đường dẫn, bố cục và quy tắc truy cập đã được thiết lập đầy đủ."
        {...(action !== undefined ? { action } : {})}
      />
    </div>
  );
}