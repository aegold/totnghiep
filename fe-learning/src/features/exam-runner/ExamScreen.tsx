import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Flag,
  Loader2,
  Send,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ExamProgressBar, ExamTimerDisplay } from '@/features/exam-runner/components/ExamTimerDisplay';
import { QuestionPalette } from '@/features/exam-runner/components/QuestionPalette';
import { SubmitReviewDialog } from '@/features/exam-runner/components/SubmitReviewDialog';
import { buildSubmissionPayload, examApi } from '@/features/exam-runner/api/examApi';
import { useExamProctoring } from '@/hooks/useExamProctoring';
import { useExamTimer } from '@/hooks/useExamTimer';
import { useIsDesktop } from '@/hooks/useMediaQuery';
import { cn, toError } from '@/lib/utils';
import { alerts } from '@/stores/alertStore';
import { flushExamCache, useExamSessionStore, type SessionQuestion } from '@/stores/examSessionStore';
import {
  DynamicQuestionRenderer,
  createEmptyResponse,
  hasAnswer,
} from '@/features/question-engine';
import type { AnyQuestionResponse } from '@/features/question-engine';
import type { ExamSession } from '@/features/exam-runner/types';

export interface ExamScreenProps {
  session: ExamSession;
  onExit: () => void;
}

/**
 * Full-screen examination interface.
 *
 * Deliberately rendered OUTSIDE `AppLayout`: during an exam there is no app
 * navigation, and the Tab key is reserved for flagging rather than focus moves.
 */
export function ExamScreen({ session, onExit }: ExamScreenProps): ReactElement {
  const isDesktop: boolean = useIsDesktop();

  /* ------------------------------ Session store ---------------------------- */
  const submissionId = useExamSessionStore((state) => state.submissionId);
  const answers = useExamSessionStore((state) => state.answers);
  const flaggedQuestions = useExamSessionStore((state) => state.flaggedQuestions);
  const currentIndex = useExamSessionStore((state) => state.currentIndex);
  const status = useExamSessionStore((state) => state.status);
  const isCacheHealthy = useExamSessionStore((state) => state.isCacheHealthy);

  const start = useExamSessionStore((state) => state.start);
  const saveAnswer = useExamSessionStore((state) => state.saveAnswer);
  const toggleFlag = useExamSessionStore((state) => state.toggleFlag);
  const goTo = useExamSessionStore((state) => state.goTo);
  const next = useExamSessionStore((state) => state.next);
  const previous = useExamSessionStore((state) => state.previous);
  const restoreFromCache = useExamSessionStore((state) => state.restoreFromCache);
  const clearCache = useExamSessionStore((state) => state.clearCache);
  const markSubmitting = useExamSessionStore((state) => state.markSubmitting);
  const markSubmitted = useExamSessionStore((state) => state.markSubmitted);
  const markFailed = useExamSessionStore((state) => state.markFailed);

  /* ------------------------------ Local UI state ---------------------------- */
  const [isSubmitDialogOpen, setIsSubmitDialogOpen] = useState<boolean>(false);
  const [isForcedSubmit, setIsForcedSubmit] = useState<boolean>(false);

  /**
   * Live counters, kept in refs because `performSubmit` must read the newest
   * value without being re-created (and re-bound) on every strike.
   */
  const tabSwitchCountRef = useRef<number>(0);
  const violationCountRef = useRef<number>(0);

  /** Prevents a timeout auto-submit and a manual click racing each other. */
  const isSubmittingRef = useRef<boolean>(false);

  /* --------------------------- Paper bootstrap ----------------------------- */
  useEffect(() => {
    const questions: SessionQuestion[] = session.questions.map((question) => ({
      versionId: question.versionId,
      questionType: question.questionType,
      payload: question.payload,
    }));

    start(session.submissionId, questions);

    /*
     * Recover anything written before a crash or forced reload. The store
     * re-validates each answer against the CURRENT payload, so a cache written
     * against an older version cannot smuggle stale option ids back in.
     */
    const summary = restoreFromCache(session.submissionId);

    if (summary.restored) {
      alerts.info(
        'Đã khôi phục tiến độ của bạn',
        `${summary.answers} câu trả lời đã được khôi phục từ phiên làm bài gần nhất.`,
      );
    }
  }, [session, start, restoreFromCache]);

  /* ------------------------- Cache flush on exit ---------------------------- */
  useEffect(() => {
    const handleHide = (): void => {
      if (document.visibilityState === 'hidden') {
        flushExamCache();
      }
    };

    const handleBeforeUnload = (event: BeforeUnloadEvent): void => {
      /* Persist immediately, then warn that leaving ends the attempt. */
      flushExamCache();
      event.preventDefault();
      event.returnValue = '';
    };

    document.addEventListener('visibilitychange', handleHide);
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      document.removeEventListener('visibilitychange', handleHide);
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, []);

  /* ------------------------- Submit orchestration --------------------------- */
  const performSubmit = useCallback(
    async (auto: boolean): Promise<void> => {
      if (isSubmittingRef.current || submissionId === null) {
        return;
      }
      isSubmittingRef.current = true;
      markSubmitting();

      const store = useExamSessionStore.getState();

      try {
        const payload = buildSubmissionPayload({
          submissionId,
          answers: [...store.answers.entries()].map(
            ([versionId, response]) => ({ versionId, studentResponse: response }),
          ),
          tabSwitchCount: tabSwitchCountRef.current,
          violationCount: violationCountRef.current,
          flaggedVersionIds: [...store.flaggedQuestions],
          autoSubmitted: auto,
        });

        await examApi.submitExam(session.examId, payload);

        markSubmitted();
        /* Only drop the local cache once the server has acknowledged. */
        clearCache();
        alerts.success(
          auto ? 'Hết giờ — bài thi đã được nộp' : 'Đã nộp bài thi',
          'Các câu trả lời của bạn đã được ghi nhận.',
        );
        onExit();
      } catch (error: unknown) {
        markFailed();
        /* Allow a retry — the cache still holds every answer. */
        isSubmittingRef.current = false;
        alerts.error('Nộp bài thất bại', toError(error).message);
      }
    },
    [submissionId, markSubmitting, markSubmitted, markFailed, clearCache, onExit],
  );

  /* ------------------------------ Timer ------------------------------------- */
  const timer = useExamTimer(session.durationMinutes, session.serverStartTime, {
    serverEndTime: session.serverEndTime,
    onWarning: (level) => {
      if (level === 'critical') {
        alerts.warning('Còn một phút', 'Hãy nộp bài trước khi hết thời gian.');
      }
    },
    /* Both paths converge on the same idempotent submit. */
    onTimeUp: () => {
      alerts.warning('Đã hết thời gian', 'Bài thi của bạn đang được nộp tự động.');
      setIsForcedSubmit(true);
      setIsSubmitDialogOpen(true);
      void performSubmit(true);
    },
  });

  /* ----------------------------- Proctoring --------------------------------- */
  const proctoring = useExamProctoring(session.maxViolations, {
    onForceSubmit: () => {
      alerts.error(
        'Đã vượt giới hạn giám sát',
        'Bài thi của bạn đang được nộp vì đã ghi nhận quá nhiều vi phạm.',
      );
      setIsForcedSubmit(true);
      setIsSubmitDialogOpen(true);
      void performSubmit(true);
    },
  });

  /* Keep the submit path reading the newest counters without rebinding. */
  tabSwitchCountRef.current = proctoring.tabSwitchCount;
  violationCountRef.current = proctoring.violationCount;

  /* ------------------------------ Derived ----------------------------------- */
  const activeQuestion = session.questions[currentIndex];

  const stats = useMemo(() => {
    const answered: number[] = [];
    const unanswered: number[] = [];
    const flagged: number[] = [];

    session.questions.forEach((question, index: number) => {
      const response = answers.get(question.versionId);
      const isAnswered: boolean =
        response !== undefined && hasAnswer(question.payload, response);

      if (isAnswered) {
        answered.push(index + 1);
      } else {
        unanswered.push(index + 1);
      }

      if (flaggedQuestions.has(question.versionId)) {
        flagged.push(index + 1);
      }
    });

    return { answered, unanswered, flagged, answeredCount: answered.length };
  }, [session.questions, answers, flaggedQuestions]);

  const isActiveFlagged: boolean =
    activeQuestion !== undefined && flaggedQuestions.has(activeQuestion.versionId);

  const isLocked: boolean = status === 'submitting' || status === 'submitted';

  /* Current answer, or a fresh empty document for an untouched question. */
  const activeResponse: AnyQuestionResponse | undefined =
    activeQuestion !== undefined
      ? (answers.get(activeQuestion.versionId) ??
        createEmptyResponse(activeQuestion.questionType, activeQuestion.payload))
      : undefined;

  const handleAnswerChange = (next: AnyQuestionResponse): void => {
    if (activeQuestion === undefined || isLocked) {
      return;
    }
    saveAnswer(
      activeQuestion.versionId,
      activeQuestion.questionType,
      activeQuestion.payload,
      next,
    );
  };

  const handleToggleFlag = (): void => {
    if (activeQuestion === undefined) {
      return;
    }
    toggleFlag(activeQuestion.versionId);
  };

  return (
    <div className="flex h-screen w-full flex-col overflow-hidden bg-canvas">
      {/* ============================== Top bar ============================== */}
      <header className="flex h-16 shrink-0 items-center gap-4 border-b border-slate-200 bg-white px-4 sm:px-6">
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-base font-semibold text-slate-900">{session.title}</h1>
          <p className="truncate text-xs text-slate-500">
            Câu {currentIndex + 1}/{session.questions.length} · Tổng {session.totalPoints} điểm
          </p>
        </div>

        {/* Warns the learner their work is NOT being cached. */}
        {!isCacheHealthy && (
          <span
            className="hidden items-center gap-1.5 rounded-full bg-red-50 px-2.5 py-1 text-xs font-medium text-red-700 sm:inline-flex"
            role="status"
          >
            <AlertTriangle className="size-3.5" aria-hidden="true" />
            Câu trả lời chưa được lưu
          </span>
        )}

        <ExamTimerDisplay
          formatted={timer.formatted}
          isUrgent={timer.isUrgent}
          isCritical={timer.isCritical}
          isPaused={isSubmitDialogOpen && !isForcedSubmit}
        />

        <Button
          variant={isActiveFlagged ? 'subtle' : 'secondary'}
          size="sm"
          onClick={handleToggleFlag}
          disabled={isLocked || activeQuestion === undefined}
          aria-pressed={isActiveFlagged}
          className="hidden sm:inline-flex"
        >
          <Flag className={isActiveFlagged ? 'fill-current' : ''} aria-hidden="true" />
          {isActiveFlagged ? 'Đã đánh dấu' : 'Đánh dấu'}
        </Button>

        <Button size="sm" onClick={() => setIsSubmitDialogOpen(true)} disabled={isLocked}>
          <Send aria-hidden="true" />
          Nộp bài
        </Button>
      </header>

      {/* ================================ Body =============================== */}
      <div className="flex min-h-0 flex-1">
        {/* Left palette — fixed sidebar on desktop. */}
        {isDesktop && (
          <aside className="w-72 shrink-0 overflow-y-auto border-r border-slate-200 bg-white p-5">
            <QuestionPalette
              questions={session.questions}
              answers={answers}
              flagged={flaggedQuestions}
              currentIndex={currentIndex}
              onSelect={goTo}
            />

            <div className="mt-6 space-y-2 border-t border-slate-100 pt-4 text-xs text-slate-500">
              <p className="flex items-center justify-between">
                <span>Lần chuyển tab</span>
                <span className="font-medium text-slate-700">{proctoring.tabSwitchCount}</span>
              </p>
              <p className="flex items-center justify-between">
                <span>Vi phạm</span>
                <span
                  className={cn(
                    'font-medium',
                    proctoring.strikesRemaining === 0 ? 'text-red-600' : 'text-slate-700',
                  )}
                >
                  {proctoring.violationCount} / {session.maxViolations}
                </span>
              </p>
            </div>
          </aside>
        )}

        {/* Center: the active question */}
        <main className="scrollbar-slim min-w-0 flex-1 overflow-y-auto px-4 py-6 sm:px-6 lg:px-8">
          {activeQuestion === undefined || activeResponse === undefined ? (
            <div className="flex h-full items-center justify-center text-sm text-slate-500">
              Không có câu hỏi nào trong đề thi này.
            </div>
          ) : (
            <div className="mx-auto w-full max-w-3xl space-y-6">
              {!isDesktop && (
                <div className="rounded-[--radius-card] border border-slate-200 bg-white p-4">
                  <QuestionPalette
                    questions={session.questions}
                    answers={answers}
                    flagged={flaggedQuestions}
                    currentIndex={currentIndex}
                    onSelect={goTo}
                  />
                </div>
              )}

              <article className="rounded-[--radius-card] border border-slate-200 bg-white p-5 shadow-subtle sm:p-7">
                <div className="mb-5 flex items-start justify-between gap-3 border-b border-slate-100 pb-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                      Câu {currentIndex + 1}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {activeQuestion.points} điểm · Độ khó {activeQuestion.difficultyLevel}/5
                    </p>
                  </div>

                  <Button
                    variant={isActiveFlagged ? 'subtle' : 'ghost'}
                    size="sm"
                    onClick={handleToggleFlag}
                    disabled={isLocked}
                    aria-pressed={isActiveFlagged}
                    className="sm:hidden"
                  >
                    <Flag className={isActiveFlagged ? 'fill-current' : ''} aria-hidden="true" />
                    {isActiveFlagged ? 'Đã đánh dấu' : 'Đánh dấu'}
                  </Button>
                </div>

                <DynamicQuestionRenderer
                  questionType={activeQuestion.questionType}
                  payload={activeQuestion.payload}
                  value={activeResponse}
                  onChange={handleAnswerChange}
                  disabled={isLocked || timer.hasExpired}
                  idPrefix={`exam-${activeQuestion.versionId}`}
                />
              </article>

              {/* Bottom navigation */}
              <div className="space-y-4 rounded-[--radius-card] border border-slate-200 bg-white p-4 shadow-subtle">
                <ExamProgressBar
                  answeredCount={stats.answeredCount}
                  totalCount={session.questions.length}
                />

                <div className="flex items-center justify-between gap-3">
                  <Button variant="secondary" onClick={previous} disabled={currentIndex === 0 || isLocked}>
                    <ChevronLeft aria-hidden="true" />
                    Câu trước
                  </Button>

                  <span className="text-sm text-slate-500">
                    Đã trả lời {stats.answeredCount}/{session.questions.length} câu
                  </span>

                  <Button
                    onClick={next}
                    disabled={currentIndex === session.questions.length - 1 || isLocked}
                  >
                    Câu sau
                    <ChevronRight aria-hidden="true" />
                  </Button>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* ============================== Dialogs ============================== */}
      <SubmitReviewDialog
        open={isSubmitDialogOpen}
        onOpenChange={setIsSubmitDialogOpen}
        answered={stats.answered}
        unanswered={stats.unanswered}
        flagged={stats.flagged}
        isSubmitting={status === 'submitting'}
        forced={isForcedSubmit}
        onConfirm={() => {
          setIsSubmitDialogOpen(false);
          void performSubmit(isForcedSubmit);
        }}
      />

      {/* Proctoring strike dialog — must be acknowledged, cannot be dismissed. */}
      <Dialog
        open={proctoring.isWarningModalOpen && !isForcedSubmit}
        onOpenChange={(open: boolean) => {
          if (!open) {
            proctoring.acknowledgeWarning();
          }
        }}
      >
        <DialogContent
          hideCloseButton
          onEscapeKeyDown={(event: KeyboardEvent) => event.preventDefault()}
          footer={
            <Button variant="destructive" onClick={proctoring.acknowledgeWarning}>
              Tôi đã hiểu
            </Button>
          }
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertTriangle className="size-5 text-amber-600" aria-hidden="true" />
              Cảnh báo giám sát thi
            </DialogTitle>
            <DialogDescription>
              {proctoring.violations[proctoring.violations.length - 1]?.detail ??
                'Đã vi phạm một quy tắc giám sát thi.'}
            </DialogDescription>
          </DialogHeader>

          <div className="rounded-[--radius-field] bg-slate-50 p-3 text-sm">
            <p className="text-slate-700">
              Lần vi phạm{' '}
              <span className="font-semibold text-slate-900">
                {proctoring.violationCount}/{session.maxViolations}
              </span>
              . Khi đạt giới hạn, bài thi của bạn sẽ được nộp tự động.
            </p>
          </div>
        </DialogContent>
      </Dialog>

      {/* Blocking overlay while the submission is in flight. */}
      {isLocked && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/40 backdrop-blur-[1px]">
          <div className="flex items-center gap-3 rounded-[--radius-card] bg-white px-6 py-4 shadow-overlay">
            <Loader2 className="size-5 animate-spin text-brand-600" aria-hidden="true" />
            <p className="text-sm font-medium text-slate-700">Đang nộp bài thi…</p>
          </div>
        </div>
      )}
    </div>
  );
}

export default ExamScreen;
