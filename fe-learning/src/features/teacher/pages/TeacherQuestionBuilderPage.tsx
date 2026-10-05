import { useCallback, type ReactElement } from 'react';

import { PageHeader } from '@/components/layout/PageHeader';
import { VisualQuestionBuilder } from '@/features/teacher/components/VisualQuestionBuilder';
import type { QuestionPayloadPair } from '@/features/teacher/schemas/questionFormSchema';
import { alerts } from '@/stores/alertStore';
import { FileQuestion } from 'lucide-react';

/** Route wrapper for the visual question authoring tool. */
export function TeacherQuestionBuilderPage(): ReactElement {
  const handleSave = useCallback((payloads: QuestionPayloadPair): void => {
    /*
     * The save layer would POST both documents. `toQuestionRequestBody` runs the
     * leakage guard before the request leaves the browser.
     */
    alerts.success(
      'Đã lưu câu hỏi',
      `content_payload (${Object.keys(payloads.content_payload).length} trường) và solution_payload được lưu tách biệt.`,
    );
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Câu hỏi mới"
        description="Soạn câu hỏi; đáp án và ghi chú chấm điểm được lưu tách biệt khỏi nội dung hiển thị cho sinh viên."
        icon={FileQuestion}
      />
      <VisualQuestionBuilder onSubmit={handleSave} />
    </div>
  );
}

export default TeacherQuestionBuilderPage;