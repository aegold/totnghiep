import { AlertCircle, Lock, Mail } from 'lucide-react';
import type { FormEvent, ReactElement } from 'react';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';

import { AuthLayout } from '@/features/auth/components/AuthLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/hooks/useAuth';
import { readRedirectTarget, resolveHome, ROUTES } from '@/lib/routes';
import { useAuthStore } from '@/stores/authStore';
import { ApiError } from '@/types/api';

export function LoginPage(): ReactElement {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const roles = useAuthStore((state) => state.user?.roles) ?? [];

  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  /* Already signed in -> skip the form. */
  if (isAuthenticated) {
    return <Navigate to={resolveHome(roles)} replace />;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setIsSubmitting(true);

    try {
      await login({ username: username.trim(), password });
      /* Honour the `?redirect=` intent, falling back to the role landing page. */
      const target: string = readRedirectTarget(location.search);
      navigate(target, { replace: true });
    } catch (caught: unknown) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setFieldErrors(caught.fieldErrors);
      } else {
        setError('Không thể đăng nhập. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Chào mừng trở lại"
      subtitle="Đăng nhập để tiếp tục vào bảng điều khiển của bạn."
      footer={
        <>
          Chưa có tài khoản?{' '}
          <Link to={ROUTES.register} className="font-medium text-brand-600 hover:text-brand-700">
            Đăng ký ngay
          </Link>
        </>
      }
    >
      <form onSubmit={(event: FormEvent<HTMLFormElement>) => void handleSubmit(event)} noValidate>
        {error !== null && (
          <div
            role="alert"
            className="mb-5 flex items-start gap-2.5 rounded-[--radius-field] border border-red-200 bg-red-50 p-3 text-sm text-red-700"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}

        <div className="space-y-4">
          <Input
            label="Tên đăng nhập hoặc email"
            name="username"
            autoComplete="username"
            placeholder="nguyenvana"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            leftIcon={<Mail aria-hidden="true" />}
            error={fieldErrors['username']}
            required
          />

          <Input
            label="Mật khẩu"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            leftIcon={<Lock aria-hidden="true" />}
            error={fieldErrors['password']}
            required
          />
        </div>

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting} className="mt-6">
          Đăng nhập
        </Button>
      </form>
    </AuthLayout>
  );
}

export default LoginPage;