import {
  BookOpen,
  BrainCircuit,
  ClipboardCheck,
  Flame,
  TrendingUp,
} from 'lucide-react';
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

interface UpcomingExam {
  id: number;
  title: string;
  courseName: string;
  startsAt: string;
  durationMinutes: number;
}

const UPCOMING_EXAMS: readonly UpcomingExam[] = [
  { id: 1, title: 'Giữa kỳ · Cấu trúc dữ liệu', courseName: 'CS201', startsAt: '2026-03-14T09:00:00Z', durationMinutes: 90 },
  { id: 2, title: 'Trắc nghiệm · Phân phối chuẩn', courseName: 'MA210', startsAt: '2026-03-17T13:30:00Z', durationMinutes: 45 },
  { id: 3, title: 'Thực hành · Phép nối trong SQL', courseName: 'CS305', startsAt: '2026-03-21T08:00:00Z', durationMinutes: 120 },
] as const;

export function StudentDashboard(): ReactElement {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Chào mừng trở lại, ${user?.fullName?.split(' ')[0] ?? 'bạn'}`}
        description="Đây là tình trạng học tập của bạn hôm nay."
        icon={TrendingUp}
        actions={
          <Button asChild>
            <Link to={ROUTES.student.aiTutor}>
              <BrainCircuit aria-hidden="true" />
              Hỏi trợ giảng AI
            </Link>
          </Button>
        }
      />

      {/* KPIs */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Môn học đã đăng ký" value={6} icon={BookOpen} trend={12} hint="so với học kỳ trước" />
        <StatCard label="Bài thi đã hoàn thành" value={18} icon={ClipboardCheck} trend={8} tone="success" />
        <StatCard label="Điểm trung bình" value="87%" icon={TrendingUp} trend={3} tone="success" />
        <StatCard label="Chuỗi học tập" value="12 ngày" icon={Flame} tone="warning" />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Upcoming assessments */}
        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>Các bài đánh giá sắp tới</CardTitle>
            <Button asChild variant="ghost" size="sm">
              <Link to={ROUTES.student.exams}>Xem tất cả</Link>
            </Button>
          </CardHeader>
          <CardContent className="space-y-2.5">
            {UPCOMING_EXAMS.map((exam: UpcomingExam) => (
              <div
                key={exam.id}
                className="flex items-center justify-between gap-4 rounded-[--radius-field] border border-slate-100 p-3.5 transition-colors hover:border-slate-200 hover:bg-slate-50"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">{exam.title}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {exam.courseName} · {exam.durationMinutes} phút · {formatDate(exam.startsAt)}
                  </p>
                </div>
                <Badge variant="neutral">{exam.courseName}</Badge>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Adaptive insight */}
        <Card className="bg-slate-900 text-white">
          <CardHeader>
            <CardTitle className="text-white">Gợi ý thích nghi</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-slate-300">
              Mức độ nắm vững của bạn về <span className="font-medium text-white">đệ quy</span> thấp hơn mức
              trung bình của lớp. Chúng tôi đã bổ sung hai nội dung ôn tập vào lộ trình của bạn.
            </p>
            <Button asChild variant="subtle" size="sm" className="w-full">
              <Link to={ROUTES.student.courses}>Xem nội dung gợi ý</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default StudentDashboard;