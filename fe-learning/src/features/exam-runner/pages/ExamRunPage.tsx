import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ExamScreen } from '@/features/exam-runner/ExamScreen';
import { examApi } from '@/features/exam-runner/api/examApi';
import { Button } from '@/components/ui/button';
import { Loader2 } from 'lucide-react';
import type { ExamSession } from '@/features/exam-runner/types';
import { ROUTES } from '@/lib/routes';
import { toError } from '@/lib/utils';
import { useExamSessionStore } from '@/stores/examSessionStore';

/**
 * Route wrapper for a running attempt.
 *
 * Loads the paper, then hands control to `ExamScreen`. Once the exam is in
 * progress this page renders OUTSIDE the normal app layout — the runner owns
 * the whole viewport so nothing competes with the timer or the questions.
 */
export function ExamRunPage(): ReactElement {
  const { examId } = useParams<{ examId: string }>();
  const navigate = useNavigate();

  const [session, setSession] = useState<ExamSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const reset = useExamSessionStore((state) => state.reset);

  useEffect(() => {
    let cancelled = false;

    const load = async (): Promise<void> => {
      try {
        const parsedId: number = Number.parseInt(examId ?? '', 10);
        if (!Number.isInteger(parsedId)) {
          throw new Error('Mã đề thi không hợp lệ.');
        }

        const loaded: ExamSession = await examApi.startExam(parsedId);
        if (!cancelled) {
          setSession(loaded);
        }
      } catch (caught: unknown) {
        if (!cancelled) {
          setError(toError(caught).message);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [examId]);

  const handleExit = useCallback((): void => {
    /* Leaving the attempt tears down the session so the next one is clean. */
    reset();
    navigate(ROUTES.student.exams);
  }, [navigate, reset]);

  if (error !== null) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-canvas px-4 text-center">
        <h1 className="text-lg font-semibold text-slate-900">Không thể bắt đầu đề thi này</h1>
        <p className="text-sm text-slate-500">{error}</p>
        <Button variant="secondary" onClick={handleExit}>
          Quay lại danh sách bài thi
        </Button>
      </div>
    );
  }

  if (session === null) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-canvas" role="status">
        <Loader2 className="size-7 animate-spin text-brand-600" aria-hidden="true" />
        <p className="text-sm text-slate-500">Đang chuẩn bị đề thi…</p>
      </div>
    );
  }

  return <ExamScreen session={session} onExit={handleExit} />;
}

export default ExamRunPage;