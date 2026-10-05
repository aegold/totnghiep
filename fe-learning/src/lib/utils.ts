import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

import {
  KNOWLEDGE_STATUS_LABEL,
  MATERIAL_TYPE_LABEL,
  QUESTION_TYPE_LABEL,
  ROLE_LABEL,
  ROLE_LABEL_SHORT,
} from '@/lib/i18n/labels';
import { RoleType } from '@/types/auth';

/**
 * Merges conditional class names and resolves Tailwind conflicts.
 * Later classes win, so component overrides behave predictably.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** Locale used for every user-facing date, time, and number. */
export const APP_LOCALE = 'vi-VN';

/**
 * Formats an ISO timestamp as `DD/MM/YYYY HH:mm`.
 *
 * `dateStyle`/`timeStyle` cannot be combined with explicit `day`/`month` parts,
 * so the parts are listed explicitly to guarantee the DD/MM/YYYY order rather
 * than relying on locale data.
 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }

  const parsed: Date = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return iso;
  }

  return new Intl.DateTimeFormat(APP_LOCALE, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(parsed);
}

/**
 * Resolves a domain token to its Vietnamese label.
 *
 * Enum members stay English on the wire and in storage; only the rendered text
 * is translated. Unknown tokens fall back to the raw value rather than a
 * mangled transform, so a missing entry is visible instead of silently wrong.
 */
export function humanizeToken(value: string): string {
  const known: ReadonlyMap<string, string> = new Map<string, string>([
    ...Object.entries(QUESTION_TYPE_LABEL),
    ...Object.entries(KNOWLEDGE_STATUS_LABEL),
    ...Object.entries(MATERIAL_TYPE_LABEL),
    ...Object.entries(ROLE_LABEL),
  ]);

  const direct: string | undefined = known.get(value);
  if (direct !== undefined) {
    return direct;
  }

  /* Tolerate the `ROLE_` prefix used by Spring Security authorities. */
  if (value.startsWith('ROLE_')) {
    return ROLE_LABEL[value as RoleType] ?? value;
  }

  return value;
}

/**
 * Compact label for the collapsed sidebar rail.
 *
 * Truncation is deliberately avoided: Vietnamese words are longer than their
 * English counterparts, so `"Quản trị viên".slice(0, 3)` would read "Qua".
 */
export function shortLabel(value: string): string {
  if (value.startsWith('ROLE_')) {
    return ROLE_LABEL_SHORT[value as RoleType] ?? value;
  }
  return humanizeToken(value).slice(0, 3);
}

/** Initials for avatar fallbacks, max 2 characters. */
export function initialsFrom(fullName: string): string {
  const parts: string[] = fullName.trim().split(/\s+/).filter(Boolean);
  const first: string = parts[0]?.charAt(0) ?? '';
  const second: string = parts.length > 1 ? (parts[parts.length - 1]?.charAt(0) ?? '') : '';
  return `${first}${second}`.toUpperCase() || 'U';
}

/**
 * Normalises unknown thrown values into a `Error`.
 * Axios rejections and non-Error throws both flow through here.
 */
export function toError(value: unknown): Error {
  if (value instanceof Error) {
    return value;
  }
  if (typeof value === 'string') {
    return new Error(value);
  }
  return new Error('An unexpected error occurred.');
}