import { AlertTriangle, BarChart3, GraduationCap, Mail, Percent, Users } from 'lucide-react';
import { useMemo, type ReactElement, type ReactNode } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  buildAtRiskStudents,
  buildScoreBuckets,
} from '@/features/teacher/mock/mockAnalytics';
import type {
  AtRiskStudent,
  CourseAnalytics,
  DifficultNode,
  ScoreBucket,
  StudentPerformance,
} from '@/features/teacher/types';
import { RISK_THRESHOLDS } from '@/features/teacher/types';
import { cn } from '@/lib/utils';

/** Colour per score band; also used in the legend so colour is never alone. */
const BUCKET_COLOURS: readonly string[] = ['#f87171', '#fbbf24', '#60a5fa', '#34d399'];

/* -------------------------------------------------------------------------- */
/*  Metric card                                                                 */
/* -------------------------------------------------------------------------- */

interface MetricCardProps {
  label: string;
  value: string;
  hint: string;
  icon: typeof Users;
  tone: 'brand' | 'success' | 'warning' | 'danger';
}

const TONES: Record<MetricCardProps['tone'], string> = {
  brand: 'bg-brand-50 text-brand-600',
  success: 'bg-emerald-50 text-emerald-600',
  warning: 'bg-amber-50 text-amber-600',
  danger: 'bg-red-50 text-red-600',
};

function MetricCard({ label, value, hint, icon: Icon, tone }: MetricCardProps): ReactElement {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-slate-500">{label}</p>
          <p className="mt-1.5 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
        </div>
        <div className={cn('flex size-10 shrink-0 items-center justify-center rounded-xl', TONES[tone])}>
          <Icon className="size-5" aria-hidden="true" />
        </div>
      </div>
      <p className="mt-3 text-xs text-slate-400">{hint}</p>
    </Card>
  );
}

/* -------------------------------------------------------------------------- */
/*  Accessible chart shell                                                      */
/* -------------------------------------------------------------------------- */

interface ChartFigureProps {
  title: string;
  description: string;
  /** Accessible rows mirroring the chart, rendered visually hidden. */
  tableHead: readonly string[];
  tableRows: readonly (readonly (string | number)[])[];
  children: ReactNode;
  className?: string;
}

/**
 * Wraps a Recharts figure with the accessibility Recharts does not provide.
 *
 * Recharts renders SVG with no roles or labels, so a screen reader announces
 * nothing at all. This supplies:
 *  - `role="img"` + `aria-label` on the figure
 *  - a `<figcaption>` describing what the chart shows
 *  - a visually-hidden data table with the same figures, so the data is
 *    reachable without sight of the graphic
 */
function ChartFigure({
  title,
  description,
  tableHead,
  tableRows,
  children,
  className,
}: ChartFigureProps): ReactElement {
  return (
    <figure className={cn('space-y-3', className)} role="img" aria-label={`${title}. ${description}`}>
      <figcaption>
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        <p className="mt-0.5 text-xs text-slate-500">{description}</p>
      </figcaption>

      {children}

      {/* Screen-reader equivalent of the chart above. */}
      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            {tableHead.map((heading: string) => (
              <th key={heading} scope="col">
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {tableRows.map((row, rowIndex) => (
            <tr key={`row-${rowIndex}`}>
              {row.map((cell, cellIndex) => (
                <td key={`cell-${cellIndex}`}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/** Shared tooltip styling so both charts read as one system. */
const tooltipStyle = {
  contentStyle: {
    borderRadius: '0.5rem',
    border: '1px solid #e2e8f0',
    fontSize: '0.75rem',
  },
} as const;

export interface AnalyticsDashboardProps {
  analytics: CourseAnalytics;
  /** Opens a direct-message composer for an at-risk student. */
  onMessage?: (student: StudentPerformance) => void;
}

/**
 * Cohort analytics for one course.
 *
 * Every figure derives from `analytics.students` in a single `useMemo`, so the
 * metric cards, both charts, and the at-risk table describe the same cohort
 * rather than drifting apart.
 */
export function AnalyticsDashboard({
  analytics,
  onMessage,
}: AnalyticsDashboardProps): ReactElement {
  // Server aggregates win when present; fixture-derived buckets stay for the mock path.
  const buckets: ScoreBucket[] = useMemo(
    () => analytics.scoreBuckets ?? buildScoreBuckets(analytics.students),
    [analytics.students],
  );

  const atRisk: AtRiskStudent[] = useMemo(
    () => analytics.atRisk ?? buildAtRiskStudents(analytics.students),
    [analytics.students],
  );

  const weakNodes: DifficultNode[] = analytics.difficultNodes;

  return (
    <div className="space-y-6">
      {/* ---------------------------- Metrics ---------------------------- */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Tổng số đã đăng ký"
          value={String(analytics.totalEnrolled)}
          hint="Đang học môn này"
          icon={Users}
          tone="brand"
        />
        <MetricCard
          label="Điểm trung bình"
          value={`${analytics.averageScore}%`}
          hint="Trung bình trên tất cả lượt thi đã chấm"
          icon={GraduationCap}
          tone="success"
        />
        <MetricCard
          label="Tỉ lệ hoàn thành"
          value={`${Math.round(analytics.completionRate * 100)}%`}
          hint="Tỉ lệ tài liệu đã học xong"
          icon={Percent}
          tone="brand"
        />
        <MetricCard
          label="Sinh viên có nguy cơ"
          value={String(atRisk.length)}
          hint={`Dưới ${RISK_THRESHOLDS.lowMastery}% thành thạo hoặc có cờ cảnh báo`}
          icon={AlertTriangle}
          tone="danger"
        />
      </div>

      {/* ---------------------------- Charts ----------------------------- */}
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <ChartFigure
            title="Phân bố điểm"
            description={`Cách ${analytics.totalEnrolled} sinh viên đã đăng ký phân bố trên bốn khoảng điểm.`}
            tableHead={['Khoảng điểm', 'Số sinh viên']}
            tableRows={buckets.map((bucket: ScoreBucket) => [bucket.label, bucket.count])}
          >
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={buckets} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={{ fontSize: 12, fill: '#64748b' }}
                    axisLine={{ stroke: '#e2e8f0' }}
                    tickLine={false}
                  />
                  <YAxis
                    tick={{ fontSize: 12, fill: '#64748b' }}
                    axisLine={false}
                    tickLine={false}
                    allowDecimals={false}
                  />
                  <RechartsTooltip {...tooltipStyle} cursor={{ fill: '#f1f5f9' }} />
                  <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                    {buckets.map((bucket: ScoreBucket, index: number) => (
                      /* Cell colouring is decorative; the band label carries meaning. */
                      <Cell key={bucket.label} fill={BUCKET_COLOURS[index % 4]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* Legend doubles as the colour key. */}
            <ul className="flex flex-wrap gap-3 text-xs text-slate-500">
              {buckets.map((bucket: ScoreBucket, index: number) => (
                <li key={bucket.label} className="flex items-center gap-1.5">
                  <span
                    className="size-2.5 rounded-sm"
                    style={{ backgroundColor: BUCKET_COLOURS[index % 4] }}
                    aria-hidden="true"
                  />
                  {bucket.label} ({bucket.count})
                </li>
              ))}
            </ul>
          </ChartFigure>
        </Card>

        <Card className="p-5">
          <ChartFigure
            title="Các nút kiến thức còn yếu"
            description="Mức độ thành thạo trung bình của mỗi nút kiến thức trong lớp, xếp từ thấp nhất."
            tableHead={['Nút kiến thức', 'Thành thạo (%)', 'Số sinh viên']}
            tableRows={weakNodes.map((node: DifficultNode) => [
              node.label,
              node.mastery,
              node.studentCount,
            ])}
          >
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                {/* layout="vertical" produces the horizontal bars this view needs. */}
                <BarChart data={weakNodes} layout="vertical" margin={{ top: 8, right: 16, bottom: 0, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                  <XAxis
                    type="number"
                    domain={[0, 100]}
                    tick={{ fontSize: 12, fill: '#64748b' }}
                    axisLine={{ stroke: '#e2e8f0' }}
                    tickLine={false}
                    unit="%"
                  />
                  <YAxis
                    type="category"
                    dataKey="label"
                    width={140}
                    tick={{ fontSize: 11, fill: '#64748b' }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <RechartsTooltip {...tooltipStyle} cursor={{ fill: '#f1f5f9' }} />
                  <Bar dataKey="mastery" fill="#f59e0b" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </ChartFigure>
        </Card>
      </div>

      {/* ------------------------- Difficult questions ------------------------ */}
      <Card className="p-5">
        <ChartFigure
          title="Những câu hỏi bị sai nhiều nhất"
          description="Tỉ lệ lượt làm bài bị trả lời sai, xếp từ cao nhất."
          tableHead={['Câu hỏi', 'Tỉ lệ sai (%)', 'Số lượt làm']}
          tableRows={analytics.difficultQuestions.map((question) => [
            question.questionText,
            Math.round(question.failureRate * 100),
            question.attempts,
          ])}
        >
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={analytics.difficultQuestions}
                layout="vertical"
                margin={{ top: 8, right: 16, bottom: 0, left: 8 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                <XAxis
                  type="number"
                  domain={[0, 100]}
                  tick={{ fontSize: 12, fill: '#64748b' }}
                  axisLine={{ stroke: '#e2e8f0' }}
                  tickLine={false}
                  unit="%"
                />
                <YAxis
                  type="category"
                  dataKey="questionText"
                  width={220}
                  tick={{ fontSize: 10, fill: '#64748b' }}
                  axisLine={false}
                  tickLine={false}
                />
                <RechartsTooltip {...tooltipStyle} cursor={{ fill: '#f1f5f9' }} />
                <Bar dataKey="failureRate" fill="#ef4444" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </ChartFigure>
      </Card>

      {/* --------------------------- At-risk table --------------------------- */}
      <Card className="overflow-hidden">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 p-5">
          <div>
            <h3 className="text-sm font-semibold text-slate-900">
              Sinh viên có nguy cơ ({atRisk.length})
            </h3>
            <p className="mt-0.5 text-xs text-slate-500">
              Mức độ thành thạo thấp, tiến độ học chậm, hoặc có vi phạm quy chế thi. Xếp theo mức độ rủi ro
              giảm dần.
            </p>
          </div>
          <BarChart3 className="size-5 shrink-0 text-slate-300" aria-hidden="true" />
        </div>

        <div className="scrollbar-slim overflow-x-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">
              Các sinh viên được đánh dấu để hỗ trợ, kèm điểm rủi ro và lý do
            </caption>
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Sinh viên', 'Rủi ro', 'Thành thạo', 'Hoàn thành', 'Chuyển tab', 'Lý do', ''].map(
                  (heading: string, index: number) => (
                    <th
                      key={heading || index}
                      scope="col"
                      className="px-5 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500"
                    >
                      {heading === '' ? <span className="sr-only">Thao tác</span> : heading}
                    </th>
                  ),
                )}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {atRisk.map((entry: AtRiskStudent) => (
                <tr
                  key={entry.student.studentId}
                  className="transition-colors hover:bg-slate-50"
                >
                  <td className="px-5 py-3">
                    <p className="font-medium text-slate-900">{entry.student.studentName}</p>
                    <p className="text-xs text-slate-500">{entry.student.email}</p>
                  </td>

                  <td className="px-5 py-3">
                    <Badge
                      variant={entry.riskScore >= 60 ? 'danger' : 'warning'}
                      className="tabular-nums"
                    >
                      {entry.riskScore}
                    </Badge>
                  </td>

                  <td className="px-5 py-3 tabular-nums text-slate-700">
                    {entry.student.weakestNodeMastery}%
                  </td>
                  <td className="px-5 py-3 tabular-nums text-slate-700">
                    {Math.round(entry.student.completionRate * 100)}%
                  </td>
                  <td className="px-5 py-3 tabular-nums text-slate-700">
                    {entry.student.tabSwitchCount}
                  </td>

                  <td className="px-5 py-3">
                    <ul className="space-y-0.5">
                      {entry.reasons.map((reason: string) => (
                        <li key={reason} className="text-xs text-slate-600">
                          • {reason}
                        </li>
                      ))}
                    </ul>
                  </td>

                  <td className="px-5 py-3 text-right">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onMessage?.(entry.student)}
                    >
                      <Mail aria-hidden="true" />
                      Nhắn tin
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

export default AnalyticsDashboard;
