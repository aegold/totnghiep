import { Activity, BookOpen, Server, UserCog } from 'lucide-react';
import type { ReactElement } from 'react';
import { Link } from 'react-router-dom';

import { PageHeader } from '@/components/layout/PageHeader';
import { StatCard } from '@/components/layout/StatCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/hooks/useAuth';
import { ROUTES } from '@/lib/routes';
import { formatDate } from '@/lib/utils';

interface AuditEntry {
  id: number;
  actor: string;
  action: string;
  target: string;
  at: string;
}

const RECENT_ACTIVITY: readonly AuditEntry[] = [
  { id: 1, actor: 'admin.nguyen', action: 'đã phê duyệt môn học', target: 'Giải thuật nâng cao', at: '2026-03-09T08:12:00Z' },
  { id: 2, actor: 'teacher.tran', action: 'đã xuất bản đề thi', target: 'Giữa kỳ · Mạng máy tính', at: '2026-03-09T07:40:00Z' },
  { id: 3, actor: 'admin.nguyen', action: 'đã cập nhật vai trò', target: 'user: pham.an', at: '2026-03-08T16:05:00Z' },
] as const;

export function AdminDashboard(): ReactElement {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tổng quan nền tảng"
        description={`Đang đăng nhập với tài khoản ${user?.email ?? 'quản trị viên'}.`}
        icon={Server}
        actions={
          <Button asChild>
            <Link to={ROUTES.admin.users}>
              <UserCog aria-hidden="true" />
              Quản lý người dùng
            </Link>
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Tổng số người dùng" value={1482} icon={UserCog} trend={4} tone="brand" />
        <StatCard label="Môn học đã xuất bản" value={214} icon={BookOpen} trend={9} tone="success" />
        <StatCard label="Phiên đang hoạt động" value={386} icon={Activity} tone="neutral" />
        <StatCard label="Tình trạng hệ thống" value="99,98%" icon={Server} tone="success" />
      </div>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>Hoạt động gần đây trên nền tảng</CardTitle>
          <Button asChild variant="ghost" size="sm">
            <Link to={ROUTES.admin.users}>Xem nhật ký kiểm tra</Link>
          </Button>
        </CardHeader>
        <CardContent className="space-y-2.5">
          {RECENT_ACTIVITY.map((entry: AuditEntry) => (
            <div
              key={entry.id}
              className="flex items-center justify-between gap-4 rounded-[--radius-field] border border-slate-100 p-3.5 transition-colors hover:border-slate-200 hover:bg-slate-50"
            >
              <div className="min-w-0">
                <p className="truncate text-sm text-slate-900">
                  <span className="font-medium">{entry.actor}</span>{' '}
                  <span className="text-slate-500">{entry.action}</span>{' '}
                  <span className="font-medium">{entry.target}</span>
                </p>
                <p className="mt-0.5 text-xs text-slate-400">{formatDate(entry.at)}</p>
              </div>
              <Badge variant="neutral">Kiểm tra</Badge>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

export default AdminDashboard;