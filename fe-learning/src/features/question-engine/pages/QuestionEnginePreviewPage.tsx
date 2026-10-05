import { FlaskConical } from 'lucide-react';
import type { ReactElement } from 'react';

import { PageHeader } from '@/components/layout/PageHeader';
import { QuestionEnginePreview } from '@/features/question-engine/pages/QuestionEnginePreview';

/** Route wrapper providing the shared page chrome for the sandbox. */
export function QuestionEnginePreviewPage(): ReactElement {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Trình soạn câu hỏi"
        description="Hiển thị từ các dữ liệu content_payload mẫu — không cần máy chủ."
        icon={FlaskConical}
      />
      <QuestionEnginePreview />
    </div>
  );
}

export default QuestionEnginePreviewPage;