import { BrainCircuit, CheckCircle2 } from 'lucide-react';
import type { ReactElement, ReactNode } from 'react';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}

const HIGHLIGHTS: readonly string[] = [
  'Lộ trình học tập thích ứng theo năng lực từng sinh viên',
  'Trợ giảng AI giải thích 24/7 dựa trên tài liệu môn học',
  'Chấm điểm tự động và phản hồi tức thì',
] as const;

/** Split-screen shell shared by the sign-in and sign-up screens. */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps): ReactElement {
  return (
    <div className="flex min-h-screen bg-canvas">
      {/* Brand panel — hidden on small screens */}
      <aside className="relative hidden w-1/2 flex-col justify-between bg-slate-900 p-12 lg:flex">
        <div
          className="absolute inset-0 opacity-40"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, rgb(99 102 241 / 0.45), transparent 45%), radial-gradient(circle at 80% 70%, rgb(56 189 248 / 0.25), transparent 45%)',
          }}
          aria-hidden="true"
        />

        <div className="relative flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-brand-600">
            <BrainCircuit className="size-5 text-white" aria-hidden="true" />
          </div>
          <div>
            <p className="text-base font-semibold text-white">Hệ thống LMS Thích ứng</p>
            <p className="text-xs text-slate-400">Nền tảng Trợ giảng AI</p>
          </div>
        </div>

        <div className="relative max-w-md">
          <h2 className="text-3xl font-semibold leading-tight tracking-tight text-white">
            Học thông minh hơn.
            <br />
            Thích ứng nhanh hơn.
          </h2>
          <ul className="mt-8 space-y-3.5">
            {HIGHLIGHTS.map((item: string) => (
              <li key={item} className="flex items-start gap-3 text-sm text-slate-300">
                <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-brand-400" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-slate-500">
          © {new Date().getFullYear()} Hệ thống LMS Thích ứng. Bảo lưu mọi quyền.
        </p>
      </aside>

      {/* Form panel */}
      <main className="flex w-full items-center justify-center px-4 py-10 sm:px-8 lg:w-1/2">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-xl bg-brand-600">
                <BrainCircuit className="size-5 text-white" aria-hidden="true" />
              </div>
              <p className="text-base font-semibold text-slate-900">Hệ thống LMS Thích ứng</p>
            </div>
          </div>

          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>

          <div className="mt-8">{children}</div>

          <div className="mt-6 text-center text-sm text-slate-500">{footer}</div>
        </div>
      </main>
    </div>
  );
}