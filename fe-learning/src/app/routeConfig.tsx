import { BookOpen, ClipboardCheck, FileStack, Gauge, GraduationCap, Settings, TrendingUp, UserCog, Users, type LucideIcon } from 'lucide-react';
import { lazy, Suspense, type ComponentType } from 'react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/ui/button';
import { PlaceholderPage } from '@/features/shared/PlaceholderPage';
/**
 * The preview page is lazily loaded because it pulls in KaTeX + react-markdown
 * (~250 kB gzipped). Keeping it out of the initial chunk means the rest of the
 * LMS — including any host that does not render questions — pays nothing.
 */
export const QuestionEnginePreviewPage = lazy<ComponentType>(
  () => import('@/features/question-engine/pages/QuestionEnginePreviewPage'),
);

/*
 * Both teacher tools are lazy: the analytics page pulls in Recharts (~110 kB
 * gzipped), which no other part of the LMS needs.
 */
export const TeacherAnalyticsPage = lazy<ComponentType>(
  () => import('@/features/teacher/pages/TeacherAnalyticsPage'),
);

export const TeacherQuestionBuilderPage = lazy<ComponentType>(
  () => import('@/features/teacher/pages/TeacherQuestionBuilderPage'),
);
import { ROUTES } from '@/lib/routes';
import { RoleType } from '@/types/auth';

/** Route-level code splitting: only landing dashboards load lazily. */
export const StudentDashboard = lazy<ComponentType>(
  () => import('@/features/student/pages/StudentDashboard'),
);
export const TeacherDashboard = lazy<ComponentType>(
  () => import('@/features/teacher/pages/TeacherDashboard'),
);
export const AdminDashboard = lazy<ComponentType>(
  () => import('@/features/admin/pages/AdminDashboard'),
);

export interface SubtreePage {
  /** Absolute path, e.g. `/student/courses`. */
  path: string;
  /** Label exposed to breadcrumb tooling via `handle`. */
  crumb: string;
  /** Inline module for screens landing in a later stage. */
  render: () => JSX.Element;
}

export interface SubtreeConfig {
  role: RoleType;
  /** Parent path itself, which also renders the dashboard as its index route. */
  indexRoute: string;
  dashboardPath: string;
  dashboard: ComponentType;
  pages: readonly SubtreePage[];
}

/** Icons reserved for the upcoming feature slices. */
export const FUTURE_ICONS: Readonly<Record<string, LucideIcon>> = {
  courses: BookOpen,
  exams: ClipboardCheck,
  grading: Gauge,
  tutor: GraduationCap,
  progress: TrendingUp,
  users: Users,
  accounts: UserCog,
  settings: Settings,
  assessments: FileStack,
};

export const STUDENT_SUBTREE: SubtreeConfig = {
  role: RoleType.STUDENT,
  indexRoute: ROUTES.student.root,
  dashboardPath: ROUTES.student.dashboard,
  dashboard: StudentDashboard,
  pages: [
    {
      path: ROUTES.student.courses,
      crumb: 'Môn học của tôi',
      render: () => (
        <PlaceholderPage
          title="Môn học của tôi"
          description="Các mô đun đã đăng ký, tiến độ học và gợi ý học tập thích nghi."
          icon={BookOpen}
          action={
            <Button asChild>
              <Link to={ROUTES.student.learn(1)}>
                <BookOpen aria-hidden="true" />
                Mở không gian học tập
              </Link>
            </Button>
          }
        />
      ),
    },
    {
      path: ROUTES.student.exams,
      crumb: 'Bài thi của tôi',
      render: () => (
        <PlaceholderPage
          title="Bài thi của tôi"
          description="Các bài đánh giá đã lên lịch, lượt làm bài và kết quả."
          icon={ClipboardCheck}
          action={
            <Button asChild>
              <Link to={ROUTES.student.examRun(1)}>
                <ClipboardCheck aria-hidden="true" />
                Bắt đầu bài thi mẫu
              </Link>
            </Button>
          }
        />
      ),
    },
    {
      path: ROUTES.student.aiTutor,
      crumb: 'Trợ giảng AI',
      render: () => (
        <PlaceholderPage
          title="Trợ giảng AI"
          description="Trò chuyện gia sư nhận biết ngữ cảnh, dựa trên tài liệu môn học của bạn."
          icon={GraduationCap}
        />
      ),
    },
    {
      path: ROUTES.student.progress,
      crumb: 'Tiến độ',
      render: () => (
        <PlaceholderPage
          title="Tiến độ"
          description="Phân tích mức độ thành thạo và lộ trình học tập thích nghi của bạn."
          icon={TrendingUp}
        />
      ),
    },
    {
      path: ROUTES.student.enginePreview,
      crumb: 'Trình soạn câu hỏi',
      render: () => (
        <Suspense fallback={<EnginePreviewSkeleton />}>
          <QuestionEnginePreviewPage />
        </Suspense>
      ),
    },
  ],
};

/** Placeholder shown while the heavy KaTeX chunk downloads. */
function EnginePreviewSkeleton(): JSX.Element {
  return (
    <div className="space-y-4" role="status" aria-live="polite">
      <div className="h-8 w-56 animate-pulse rounded-md bg-slate-200" />
      {[0, 1, 2].map((index: number) => (
        <div key={index} className="h-40 animate-pulse rounded-[--radius-card] bg-slate-100" />
      ))}
      <span className="sr-only">Đang tải trình soạn câu hỏi…</span>
    </div>
  );
}

export const TEACHER_SUBTREE: SubtreeConfig = {
  role: RoleType.TEACHER,
  indexRoute: ROUTES.teacher.root,
  dashboardPath: ROUTES.teacher.dashboard,
  dashboard: TeacherDashboard,
  pages: [
    {
      path: ROUTES.teacher.courses,
      crumb: 'Môn học của tôi',
      render: () => (
        <PlaceholderPage
          title="Môn học của tôi"
          description="Tạo, xuất bản và quản lý tài liệu môn học của bạn."
          icon={BookOpen}
        />
      ),
    },
    {
      path: ROUTES.teacher.exams,
      crumb: 'Bài thi',
      render: () => (
        <PlaceholderPage
          title="Đánh giá"
          description="Xây dựng ngân hàng câu hỏi và lên lịch kiểm tra."
          icon={FileStack}
        />
      ),
    },
    {
      path: ROUTES.teacher.grading,
      crumb: 'Chấm điểm',
      render: () => (
        <PlaceholderPage
          title="Hàng đợi chấm điểm"
          description="Rà soát bài làm và công bố điểm số."
          icon={Gauge}
        />
      ),
    },
    {
      path: ROUTES.teacher.students,
      crumb: 'Sinh viên',
      render: () => (
        <PlaceholderPage
          title="Sinh viên"
          description="Danh sách lớp, đăng ký học và thông tin chi tiết theo từng người học."
          icon={Users}
        />
      ),
    },
    {
      path: ROUTES.teacher.questionBuilder,
      crumb: 'Trình soạn câu hỏi',
      render: () => <TeacherQuestionBuilderPage />,
    },
    {
      path: ROUTES.teacher.analytics,
      crumb: 'Phân tích',
      render: () => <TeacherAnalyticsPage />,
    },
  ],
};

export const ADMIN_SUBTREE: SubtreeConfig = {
  role: RoleType.ADMIN,
  indexRoute: ROUTES.admin.root,
  dashboardPath: ROUTES.admin.dashboard,
  dashboard: AdminDashboard,
  pages: [
    {
      path: ROUTES.admin.users,
      crumb: 'Người dùng',
      render: () => (
        <PlaceholderPage
          title="Quản lý người dùng"
          description="Tài khoản, vai trò và kiểm soát truy cập."
          icon={UserCog}
        />
      ),
    },
    {
      path: ROUTES.admin.courses,
      crumb: 'Môn học',
      render: () => (
        <PlaceholderPage
          title="Danh mục môn học"
          description="Phê duyệt, phân loại và lưu trữ các môn học."
          icon={BookOpen}
        />
      ),
    },
    {
      path: ROUTES.admin.settings,
      crumb: 'Cài đặt hệ thống',
      render: () => (
        <PlaceholderPage
          title="Cài đặt hệ thống"
          description="Cấu hình toàn hệ thống và tích hợp."
          icon={Settings}
        />
      ),
    },
  ],
};