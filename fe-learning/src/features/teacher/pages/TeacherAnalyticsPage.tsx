import { BarChart3 as BarChartIcon, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState, type ReactElement } from 'react';

import { PageHeader } from '@/components/layout/PageHeader';
import { AnalyticsDashboard } from '@/features/teacher/components/AnalyticsDashboard';
import { toCourseAnalytics } from '@/features/teacher/api/analyticsApi';
import { http } from '@/lib/apiClient';
import type { CourseAnalytics, StudentPerformance } from '@/features/teacher/types';
import { alerts } from '@/stores/alertStore';

/**
 * Cohort analytics dashboard, served by the live analytics endpoints.
 *
 * ## How the course and paper are chosen
 * The route carries no identifier, so the page resolves one: it reads
 * `courseId`/`examId` from the query string when present and otherwise asks the
 * backend for the signed-in teacher's first course. That keeps the demo path
 * (`/teacher/analytics`) working with no navigation wiring while still letting a
 * deep link pin a specific cohort.
 *
 * The seeded dataset has one course and one paper, so the default resolves.
 */
export function TeacherAnalyticsPage(): ReactElement {
  const [analytics, setAnalytics] = useState<CourseAnalytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams(window.location.search);
      let courseId = params.get('courseId');

      if (courseId === null) {
        // `/teacher/courses/mine` returns the teacher's own courses; the outline
        // DTO carries the id, so no extra endpoint is needed to discover one.
        const mine = await http.get<{ courseId: number }[]>('/teacher/courses/mine');
        courseId = mine.length > 0 ? String(mine[0]?.courseId) : null;
      }

      if (courseId === null) {
        setError('Chưa có khóa học nào để phân tích. Hãy tạo khóa học trước.');
        return;
      }

      const examId = params.get('examId');
      setAnalytics(await toCourseAnalytics(courseId, examId ?? undefined));
    } catch (cause: unknown) {
      const message =
        cause instanceof Error ? cause.message : 'Không tải được dữ liệu thống kê.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleMessage = useCallback((student: StudentPerformance): void => {
    alerts.info(
      'Soạn tin nhắn',
      `Đang mở tin nhắn mới tới ${student.studentName} (${student.email}).`,
    );
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Phân tích học tập"
        description={
          analytics === null
            ? 'Đang tải dữ liệu từ máy chủ...'
            : analytics.courseTitle
        }
        icon={BarChartIcon}
      />

      {error !== null && (
        <div
          role="alert"
          className="flex items-center justify-between rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
        >
          <span>{error}</span>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1 rounded px-2 py-1 font-medium hover:bg-rose-100"
          >
            <RefreshCw size={14} aria-hidden="true" />
            Thử lại
          </button>
        </div>
      )}

      {loading && <p className="text-sm text-slate-500">Đang tải thống kê...</p>}

      {!loading && analytics !== null && (
        <AnalyticsDashboard analytics={analytics} onMessage={handleMessage} />
      )}
    </div>
  );
}

export default TeacherAnalyticsPage;