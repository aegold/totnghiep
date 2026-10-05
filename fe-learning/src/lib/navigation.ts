import {
  BookOpen,
  BrainCircuit,
  ClipboardCheck,
  FileStack,
  FlaskConical,
  GraduationCap,
  LayoutDashboard,
  Network,
  Settings,
  ShieldCheck,
  TrendingUp,
  UserCog,
  Users,
  type LucideIcon,
} from 'lucide-react';

import { ROUTES } from '@/lib/routes';
import { RoleType } from '@/types/auth';

export interface NavigationItem {
  label: string;
  to: string;
  icon: LucideIcon;
  /** Visible in the sidebar, collapsed or not. */
  showInSidebar: boolean;
}

/** Per-role navigation. Order here is the order shown in the sidebar. */
const NAVIGATION_BY_ROLE: Readonly<Record<RoleType, readonly NavigationItem[]>> = {
  [RoleType.STUDENT]: [
    { label: 'Bảng điều khiển', to: ROUTES.student.dashboard, icon: LayoutDashboard, showInSidebar: true },
    { label: 'Môn học của tôi', to: ROUTES.student.courses, icon: BookOpen, showInSidebar: true },
    { label: 'Kỳ thi của tôi', to: ROUTES.student.exams, icon: ClipboardCheck, showInSidebar: true },
    { label: 'Trợ giảng AI', to: ROUTES.student.aiTutor, icon: BrainCircuit, showInSidebar: true },
    { label: 'Tiến độ học tập', to: ROUTES.student.progress, icon: TrendingUp, showInSidebar: true },
    { label: 'Cây tri thức thích ứng', to: ROUTES.student.skills, icon: Network, showInSidebar: true },
    { label: 'Trình soạn câu hỏi', to: ROUTES.student.enginePreview, icon: FlaskConical, showInSidebar: true },
  ],
  [RoleType.TEACHER]: [
    { label: 'Bảng điều khiển', to: ROUTES.teacher.dashboard, icon: LayoutDashboard, showInSidebar: true },
    { label: 'Môn học của tôi', to: ROUTES.teacher.courses, icon: BookOpen, showInSidebar: true },
    { label: 'Kỳ thi', to: ROUTES.teacher.exams, icon: FileStack, showInSidebar: true },
    { label: 'Chấm điểm', to: ROUTES.teacher.grading, icon: ClipboardCheck, showInSidebar: true },
    { label: 'Sinh viên', to: ROUTES.teacher.students, icon: Users, showInSidebar: true },
  ],
  [RoleType.ADMIN]: [
    { label: 'Bảng điều khiển', to: ROUTES.admin.dashboard, icon: LayoutDashboard, showInSidebar: true },
    { label: 'Người dùng', to: ROUTES.admin.users, icon: UserCog, showInSidebar: true },
    { label: 'Môn học', to: ROUTES.admin.courses, icon: GraduationCap, showInSidebar: true },
    { label: 'Cài đặt hệ thống', to: ROUTES.admin.settings, icon: Settings, showInSidebar: true },
  ],
};

/**
 * Flattens the navigation of every role the principal holds.
 * Duplicates (identical `to`) are removed while preserving first-seen order.
 */
export const buildNavigation = (roles: readonly RoleType[]): NavigationItem[] => {
  const seen = new Set<string>();
  const items: NavigationItem[] = [];

  for (const role of roles) {
    for (const item of NAVIGATION_BY_ROLE[role] ?? []) {
      if (!seen.has(item.to)) {
        seen.add(item.to);
        items.push(item);
      }
    }
  }

  return items;
};

/** Path prefixes a role is allowed to enter. */
export const ROLE_HOME_PREFIX: Readonly<Record<RoleType, string>> = {
  [RoleType.ADMIN]: ROUTES.admin.root,
  [RoleType.TEACHER]: ROUTES.teacher.root,
  [RoleType.STUDENT]: ROUTES.student.root,
};

/** Every guarded path prefix, used by the catch-all guard. */
export const ALL_GUARDED_PREFIXES: readonly string[] = [
  ROLE_HOME_PREFIX[RoleType.STUDENT],
  ROLE_HOME_PREFIX[RoleType.TEACHER],
  ROLE_HOME_PREFIX[RoleType.ADMIN],
];

export const ROLE_ICON: Readonly<Record<RoleType, LucideIcon>> = {
  [RoleType.ADMIN]: ShieldCheck,
  [RoleType.TEACHER]: GraduationCap,
  [RoleType.STUDENT]: BookOpen,
};

export const ROLE_LABEL: Readonly<Record<RoleType, string>> = {
  [RoleType.ADMIN]: 'Administrator',
  [RoleType.TEACHER]: 'Teacher',
  [RoleType.STUDENT]: 'Student',
};