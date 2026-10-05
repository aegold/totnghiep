import { useNavigate } from 'react-router-dom';
import type { ReactElement } from 'react';

import KnowledgeGraphView from '@/features/knowledge-graph/KnowledgeGraphView';
import type { KnowledgeNodeDetail } from '@/features/knowledge-graph/types';
import { ROUTES } from '@/lib/routes';
import { alerts } from '@/stores/alertStore';

/**
 * Route wrapper for the Skill Tree.
 *
 * The graph needs the full viewport, so this is registered outside
 * `AppLayout` — same reasoning as the exam runner and learning workspace.
 * The CTAs are wired to the routes built in earlier stages.
 */
export function SkillTreePage(): ReactElement {
  const navigate = useNavigate();

  return (
    <KnowledgeGraphView
      onContinueLesson={(detail: KnowledgeNodeDetail) => {
        void navigate(ROUTES.student.learn(1));
        alerts.info('Đang mở bài học', `Đang tải tài liệu cho "${detail.title}".`);
      }}
      onTakeDiagnostic={(detail: KnowledgeNodeDetail) => {
        void navigate(ROUTES.student.examRun(1));
        alerts.info('Bắt đầu kiểm tra', `Đang chuẩn bị bài kiểm tra cho "${detail.title}".`);
      }}
    />
  );
}

export default SkillTreePage;