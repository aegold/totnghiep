import {
  Loader2,
  Maximize,
  Pause,
  Play,
  RotateCcw,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';

import { LEARNING_LIMITS } from '@/features/course-learning/types';
import type { ProgressUpdate } from '@/features/course-learning/types';
import { cn } from '@/lib/utils';

/** `M:SS` (or `H:MM:SS` past an hour) for the time readout. */
export const formatTimecode = (seconds: number): string => {
  const safe: number = Math.max(0, Math.floor(seconds));
  const hours: number = Math.floor(safe / 3600);
  const minutes: number = Math.floor((safe % 3600) / 60);
  const secs: number = safe % 60;

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${minutes}:${String(secs).padStart(2, '0')}`;
};

export interface VideoPlayerProps {
  src: string;
  title: string;
  /** Resume point from `learning_progress.last_watched_seconds`. */
  lastWatchedSeconds?: number;
  /** Debounced progress write. `materialId` is filled in by the parent. */
  onProgress?: (update: ProgressUpdate) => void;
  /** Immediate write on pause/seek/visibility change/unmount. */
  onProgressFlush?: (update: ProgressUpdate) => void;
  rewindSeconds?: number;
  className?: string;
}

/**
 * Custom HTML5 video player with resume and debounced progress reporting.
 *
 * ## Why progress is debounced
 * `timeupdate` fires roughly four times per second; writing `last_watched_seconds`
 * on each event would mean thousands of rows per lecture. Playback state still
 * updates the scrubber every event — only the PERSISTED value is throttled, and
 * it is flushed on pause, seek, tab-hide, and unmount.
 */
export function VideoPlayer({
  src,
  title,
  lastWatchedSeconds = 0,
  onProgress,
  onProgressFlush,
  rewindSeconds = 10,
  className,
}: VideoPlayerProps): ReactElement {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const debounceRef = useRef<number | null>(null);
  /** Last persisted position, so the debounce only fires on real movement. */
  const lastReportedRef = useRef<number>(0);

  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [duration, setDuration] = useState<number>(0);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [isBuffering, setIsBuffering] = useState<boolean>(true);
  const [hasResumed, setHasResumed] = useState<boolean>(false);

  const buildUpdate = useCallback((): ProgressUpdate | null => {
    const video = videoRef.current;
    if (video === null || !Number.isFinite(video.duration) || video.duration <= 0) {
      return null;
    }

    return {
      materialId: 0, // filled in by the parent, which knows the material
      lastWatchedSeconds: Math.floor(video.currentTime),
      percentComplete: Math.min(1, video.currentTime / video.duration),
    };
  }, []);

  const reportNow = useCallback((): void => {
    const update: ProgressUpdate | null = buildUpdate();
    if (update === null) {
      return;
    }
    lastReportedRef.current = update.lastWatchedSeconds;
    onProgressFlush?.(update);
  }, [buildUpdate, onProgressFlush]);

  /** Trailing-edge debounce: at most one write per quiet period. */
  const reportDebounced = useCallback((): void => {
    if (debounceRef.current !== null) {
      window.clearTimeout(debounceRef.current);
    }
    debounceRef.current = window.setTimeout(() => {
      debounceRef.current = null;
      const update: ProgressUpdate | null = buildUpdate();
      if (update === null || update.lastWatchedSeconds === lastReportedRef.current) {
        return;
      }
      lastReportedRef.current = update.lastWatchedSeconds;
      onProgress?.(update);
    }, LEARNING_LIMITS.progressDebounceMs);
  }, [buildUpdate, onProgress]);

  /* Resume once metadata exists; seeking earlier has no effect. */
  const handleLoadedMetadata = useCallback((): void => {
    const video = videoRef.current;
    if (video === null) {
      return;
    }

    setDuration(video.duration);
    setIsBuffering(false);

    if (!hasResumed && lastWatchedSeconds > 0 && lastWatchedSeconds < video.duration - 5) {
      video.currentTime = lastWatchedSeconds;
      setCurrentTime(lastWatchedSeconds);
    }
    setHasResumed(true);
  }, [hasResumed, lastWatchedSeconds]);

  /* Persist the final position when the tab goes away. */
  useEffect(() => {
    const handleHide = (): void => {
      if (document.visibilityState === 'hidden') {
        reportNow();
      }
    };

    document.addEventListener('visibilitychange', handleHide);

    return () => {
      document.removeEventListener('visibilitychange', handleHide);
      if (debounceRef.current !== null) {
        window.clearTimeout(debounceRef.current);
      }
      /* Flush on unmount so navigating away does not lose the position. */
      reportNow();
    };
  }, [reportNow]);

  const togglePlay = useCallback((): void => {
    const video = videoRef.current;
    if (video === null) {
      return;
    }
    if (video.paused) {
      void video.play();
    } else {
      video.pause();
    }
  }, []);

  const toggleMute = useCallback((): void => {
    const video = videoRef.current;
    if (video === null) {
      return;
    }
    video.muted = !video.muted;
    setIsMuted(video.muted);
  }, []);

  const seekBy = useCallback(
    (delta: number): void => {
      const video = videoRef.current;
      if (video === null) {
        return;
      }
      video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + delta));
      reportNow();
    },
    [reportNow],
  );

  const handleScrub = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>): void => {
      const video = videoRef.current;
      if (video === null) {
        return;
      }
      const next: number = Number(event.target.value);
      video.currentTime = next;
      setCurrentTime(next);
      reportNow();
    },
    [reportNow],
  );

  const toggleFullscreen = useCallback((): void => {
    const container = videoRef.current?.parentElement;
    if (container === null || container === undefined) {
      return;
    }
    if (document.fullscreenElement === null) {
      void container.requestFullscreen();
    } else {
      void document.exitFullscreen();
    }
  }, []);

  const progressPercent: number = duration > 0 ? (currentTime / duration) * 100 : 0;
  const resumePercent: number =
    duration > 0 && lastWatchedSeconds > 0 ? (lastWatchedSeconds / duration) * 100 : 0;

  return (
    <div className={cn('flex h-full flex-col bg-slate-950', className)}>
      <div className="relative flex min-h-0 flex-1 items-center justify-center">
        <video
          ref={videoRef}
          src={src}
          className="max-h-full w-full object-contain"
          onLoadedMetadata={handleLoadedMetadata}
          onTimeUpdate={(event: React.SyntheticEvent<HTMLVideoElement>) => {
            setCurrentTime(event.currentTarget.currentTime);
            reportDebounced();
          }}
          onPlay={() => setIsPlaying(true)}
          onPause={() => {
            setIsPlaying(false);
            reportNow();
          }}
          onEnded={() => {
            setIsPlaying(false);
            reportNow();
          }}
          onWaiting={() => setIsBuffering(true)}
          onPlaying={() => setIsBuffering(false)}
          onClick={togglePlay}
          aria-label={title}
        >
          {/* Captions are served separately; a bare track keeps this valid. */}
          <track kind="captions" />
        </video>

        {isBuffering && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950/40">
            <Loader2 className="size-8 animate-spin text-white" aria-hidden="true" />
          </div>
        )}

        {!isPlaying && !isBuffering && (
          <button
            type="button"
            onClick={togglePlay}
            className="absolute inset-0 flex items-center justify-center bg-slate-950/30 transition-colors hover:bg-slate-950/50"
            aria-label={`Phát ${title}`}
          >
            <span className="flex size-16 items-center justify-center rounded-full bg-white/95 text-slate-900 shadow-overlay">
              <Play className="ml-1 size-7 fill-current" aria-hidden="true" />
            </span>
          </button>
        )}

        {/* Amber marker showing the resume point. */}
        {resumePercent > 1 && resumePercent < 99 && (
          <div className="pointer-events-none absolute left-0 top-0 h-1 w-full bg-white/20" aria-hidden="true">
            <div className="absolute top-0 h-1 w-0.5 bg-amber-400" style={{ left: `${resumePercent}%` }} />
          </div>
        )}
      </div>

      {/* Controls */}
      <div className="shrink-0 space-y-2 bg-slate-900 px-4 py-3 text-white">
        <div className="relative">
          <div
            className="pointer-events-none absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 overflow-hidden rounded-full bg-slate-700"
            aria-hidden="true"
          >
            <div className="h-full rounded-full bg-brand-500" style={{ width: `${progressPercent}%` }} />
          </div>
          <input
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={currentTime}
            onChange={handleScrub}
            className="relative z-10 h-1 w-full cursor-pointer appearance-none bg-transparent accent-brand-500"
            aria-label="Tua video"
            aria-valuetext={`${formatTimecode(currentTime)} trên ${formatTimecode(duration)}`}
          />
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={togglePlay}
            className="rounded-md p-1.5 transition-colors hover:bg-slate-800"
            aria-label={isPlaying ? 'Tạm dừng' : 'Phát'}
          >
            {isPlaying ? (
              <Pause className="size-5 fill-current" aria-hidden="true" />
            ) : (
              <Play className="size-5 fill-current" aria-hidden="true" />
            )}
          </button>

          <button
            type="button"
            onClick={() => seekBy(-rewindSeconds)}
            className="flex items-center gap-1 rounded-md p-1.5 transition-colors hover:bg-slate-800"
            aria-label={`Tua lại ${rewindSeconds} giây`}
          >
            <RotateCcw className="size-4" aria-hidden="true" />
            <span className="text-xs">{rewindSeconds}</span>
          </button>

          <button
            type="button"
            onClick={toggleMute}
            className="rounded-md p-1.5 transition-colors hover:bg-slate-800"
            aria-label={isMuted ? 'Bật tiếng' : 'Tắt tiếng'}
          >
            {isMuted ? (
              <VolumeX className="size-4" aria-hidden="true" />
            ) : (
              <Volume2 className="size-4" aria-hidden="true" />
            )}
          </button>

          <span className="font-mono text-xs tabular-nums text-slate-300">
            {formatTimecode(currentTime)} / {formatTimecode(duration)}
          </span>

          <button
            type="button"
            onClick={toggleFullscreen}
            className="ml-auto rounded-md p-1.5 transition-colors hover:bg-slate-800"
            aria-label="Bật chế độ toàn màn hình"
          >
            <Maximize className="size-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  );
}