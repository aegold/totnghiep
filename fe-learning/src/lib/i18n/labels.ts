import { QuestionType } from '@/features/question-engine';
import type { MaterialType } from '@/features/course-learning/types';
import type { ViolationKind } from '@/features/exam-runner/types';
import type { KnowledgeNodeStatus } from '@/features/knowledge-graph/types';
import { RoleType } from '@/types/auth';

/**
 * Vietnamese display labels for domain tokens.
 *
 * ## Why a lookup instead of transforming the token
 * Enum members are English identifiers and must never change — they are
 * persisted to JSON and exchanged with the backend. But a machine transform
 * (`"LOCKED" -> "Locked"`) cannot produce natural Vietnamese, and truncating one
 * (`"Quản trị viên".slice(0, 3)`) yields nonsense. So every enum that reaches
 * the UI resolves through an explicit map here.
 *
 * Keys are the raw enum values, which keeps the mapping exhaustive and
 * type-safe: adding an enum member without a label is a compile error.
 */

/* ------------------------------- Question types ---------------------------- */

export const QUESTION_TYPE_LABEL: Readonly<Record<QuestionType, string>> = {
  [QuestionType.MULTIPLE_CHOICE]: 'Trắc nghiệm',
  [QuestionType.FILL_IN_BLANK]: 'Điền vào ô trống',
  [QuestionType.MATCHING]: 'Nối cặp tương ứng',
  [QuestionType.ORDERING]: 'Sắp xếp theo thứ tự',
};

/** One-word variants for the compact type selector. */
export const QUESTION_TYPE_LABEL_SHORT: Readonly<Record<QuestionType, string>> = {
  [QuestionType.MULTIPLE_CHOICE]: 'Trắc nghiệm',
  [QuestionType.FILL_IN_BLANK]: 'Điền ô trống',
  [QuestionType.MATCHING]: 'Nối cặp',
  [QuestionType.ORDERING]: 'Sắp xếp',
};

/* --------------------------------- Roles ---------------------------------- */

export const ROLE_LABEL: Readonly<Record<RoleType, string>> = {
  [RoleType.ADMIN]: 'Quản trị viên',
  [RoleType.TEACHER]: 'Giảng viên',
  [RoleType.STUDENT]: 'Sinh viên',
};

/**
 * Abbreviations for the collapsed sidebar rail.
 *
 * Deliberately NOT derived by truncation: Vietnamese words run longer than
 * English ones, so `"Quản trị viên".slice(0, 3)` renders as "Qua".
 */
export const ROLE_LABEL_SHORT: Readonly<Record<RoleType, string>> = {
  [RoleType.ADMIN]: 'QTV',
  [RoleType.TEACHER]: 'GV',
  [RoleType.STUDENT]: 'SV',
};

/* ---------------------------- Knowledge graph ------------------------------ */

export const KNOWLEDGE_STATUS_LABEL: Readonly<Record<KnowledgeNodeStatus, string>> = {
  LOCKED: 'Chưa mở khóa',
  IN_PROGRESS: 'Đang học',
  MASTERED: 'Đã thành thạo',
};

/* -------------------------------- Materials -------------------------------- */

export const MATERIAL_TYPE_LABEL: Readonly<Record<MaterialType, string>> = {
  VIDEO: 'Video',
  PDF: 'Tài liệu PDF',
  DOCUMENT: 'Bài viết',
  SLIDES: 'Bộ slide',
};

/**
 * Vietnamese name for a material type, used in the empty state when the file
 * itself is missing. Takes a lowercase noun so the sentence reads naturally:
 * `"Tài liệu PDF này chưa có tệp nguồn."`
 */
export const MATERIAL_TYPE_NOUN: Readonly<Record<MaterialType, string>> = {
  VIDEO: 'Video này',
  PDF: 'Tài liệu PDF này',
  DOCUMENT: 'Bài viết này',
  SLIDES: 'Bộ slide này',
};

/* ------------------------------- Proctoring -------------------------------- */

export const VIOLATION_LABEL: Readonly<Record<ViolationKind, string>> = {
  TAB_HIDDEN: 'Chuyển sang màn hình khác',
  CONTEXT_MENU: 'Mở menu ngữ cảnh bằng chuột phải',
  COPY_ATTEMPT: 'Cố gắng sao chép nội dung',
  DEVTOOLS_SHORTCUT: 'Sử dụng phím tắt công cụ phát triển',
  WINDOW_BLUR: 'Cửa sổ làm bài mất tiêu điểm',
};

/** Full sentence shown in the proctoring warning dialog. */
export const VIOLATION_DETAIL: Readonly<Record<ViolationKind, string>> = {
  TAB_HIDDEN: 'Phát hiện bạn đã rời khỏi màn hình làm bài thi.',
  CONTEXT_MENU: 'Trình đơn ngữ cảnh bị vô hiệu hóa trong suốt kỳ thi.',
  COPY_ATTEMPT: 'Thao tác sao chép văn bản bị vô hiệu hóa trong suốt kỳ thi.',
  DEVTOOLS_SHORTCUT:
    'Các phím tắt mở công cụ phát triển và mã nguồn trang bị vô hiệu hóa trong suốt kỳ thi.',
  WINDOW_BLUR: 'Cửa sổ làm bài thi đã mất tiêu điểm.',
};
