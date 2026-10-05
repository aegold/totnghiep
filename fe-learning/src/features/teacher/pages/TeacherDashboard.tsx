import { BookOpen, ClipboardCheck, GraduationCap, Users } from 'lucide-react';
import type { ReactElement } from 'react';
import { Link } from 'react-router-dom';

import { PageHeader } from '@/components/layout/PageHeader';
import { StatCard } from '@/components/layout/StatCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/hooks/useAuth';
import { ROUTES } from '@/lib/routes';

interface PendingSubmission {
  id: number;
  studentName: string;
  assessment: string;
  submittedAgo: string;
}

const PENDING_GRADING: readonly PendingSubmission[] = [
  { id: 1, studentName: 'Nguyễn Linh', assessment: 'Bài luận · Nhiệt động học', submittedAgo: '12 phút trước' },
  { id: 2, studentName: 'Trần Minh', assessment: 'Bài tập 4 · Đại số tuyến tính', submittedAgo: '1 giờ trước' },
  { id: 3, studentName: 'Lê Thu', assessment: 'Báo cáo thực hành · Hoá học hữu cơ', submittedAgo: '3 giờ trước' },
] as const;

export function TeacherDashboard(): ReactElement {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Chào mừng, ${user?.fullName?.split(' ')[0] ?? 'thầy cô'}`}
        description="Lớp học, các bài đánh giá và hàng đợi chấm điểm của bạn trong nháy mắt."
        icon={GraduationCap}
        actions={
          <Button asChild>
            <Link to={ROUTES.teacher.courses}>Quản lý môn học</Link>
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Môn học đang mở" value={4} icon={BookOpen} trend={0} hint="không thay đổi" />
        <StatCard label="Sinh viên đã đăng ký" value={128} icon={Users} trend={6} tone="success" />
        <StatCard label="Bài đánh giá" value={9} icon={ClipboardCheck} tone="warning" />
        <StatCard label="Chờ chấm điểm" value={14} icon={ClipboardCheck} tone="neutral" />
      </div>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>Chờ chấm điểm</CardTitle>
          <Button asChild variant="ghost" size="sm">
            <Link to={ROUTES.teacher.grading}>Mở hàng đợi</Link>
          </Button>
        </CardHeader>
        <CardContent className="space-y-2.5">
          {PENDING_GRADING.map((item: PendingSubmission) => (
            <div
              key={item.id}
              className="flex items-center justify-between gap-4 rounded-[--radius-field] border border-slate-100 p-3.5 transition-colors hover:border-slate-200 hover:bg-slate-50"
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">{item.studentName}</p>
                <p className="mt-0.5 truncate text-xs text-slate-500">
                  {item.assessment} · {item.submittedAgo}
                </p>
              </div>
              <Badge variant="warning">Đang chờ</Badge>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

export default TeacherDashboard;