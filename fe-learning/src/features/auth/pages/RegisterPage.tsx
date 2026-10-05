import { AlertCircle, AtSign, Lock, User as UserIcon } from 'lucide-react';
import type { FormEvent, ReactElement } from 'react';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';

import { AuthLayout } from '@/features/auth/components/AuthLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/hooks/useAuth';
import { ROUTES } from '@/lib/routes';
import { ApiError } from '@/types/api';

/** Client-side mirror of the backend's password policy. */
const PASSWORD_MIN_LENGTH: number = 8;
const EMAIL_PATTERN: RegExp = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const USERNAME_PATTERN: RegExp = /^[a-zA-Z0-9._-]{3,32}$/;

interface ValidationErrors {
  fullName?: string;
  username?: string;
  email?: string;
  password?: string;
}

/** Pure validator so the rules are testable without touching the DOM. */
export const validateRegistration = (values: {
  fullName: string;
  username: string;
  email: string;
  password: string;
}): ValidationErrors => {
  const errors: ValidationErrors = {};

  if (values.fullName.trim().length < 2) {
    errors.fullName = 'Vui lòng nhập họ và tên đầy đủ.';
  }
  if (!USERNAME_PATTERN.test(values.username)) {
    errors.username = 'Gồm 3–32 ký tự: chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.';
  }
  if (!EMAIL_PATTERN.test(values.email)) {
    errors.email = 'Vui lòng nhập một địa chỉ email hợp lệ.';
  }
  if (values.password.length < PASSWORD_MIN_LENGTH) {
    errors.password = `Mật khẩu phải có ít nhất ${PASSWORD_MIN_LENGTH} ký tự.`;
  }

  return errors;
};

export function RegisterPage(): ReactElement {
  const { register, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState<string>('');
  const [username, setUsername] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  if (isAuthenticated) {
    return <Navigate to={ROUTES.root} replace />;
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    const validation: ValidationErrors = validateRegistration({ fullName, username, email, password });
    setErrors(validation);

    if (Object.keys(validation).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      await register({
        fullName: fullName.trim(),
        username: username.trim(),
        email: email.trim().toLowerCase(),
        password,
      });
      navigate(ROUTES.root, { replace: true });
    } catch (caught: unknown) {
      if (caught instanceof ApiError) {
        setError(caught.message);
        setErrors(caught.fieldErrors);
      } else {
        setError('Không thể tạo tài khoản. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthLayout
      title="Tạo tài khoản mới"
      subtitle="Bắt đầu hành trình học tập cá nhân hóa của bạn."
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to={ROUTES.login} className="font-medium text-brand-600 hover:text-brand-700">
            Đăng nhập
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
            label="Họ và tên"
            name="fullName"
            autoComplete="name"
            placeholder="Nguyễn Văn An"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            leftIcon={<UserIcon aria-hidden="true" />}
            error={errors.fullName}
            required
          />

          <Input
            label="Tên đăng nhập"
            name="username"
            autoComplete="username"
            placeholder="nguyenvana"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            leftIcon={<AtSign aria-hidden="true" />}
            error={errors.username}
            required
          />

          <Input
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="nguyenvana@example.edu.vn"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            leftIcon={<AtSign aria-hidden="true" />}
            error={errors.email}
            required
          />

          <Input
            label="Mật khẩu"
            name="password"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            leftIcon={<Lock aria-hidden="true" />}
            hint={`Tối thiểu ${PASSWORD_MIN_LENGTH} ký tự.`}
            error={errors.password}
            required
          />
        </div>

        <Button type="submit" fullWidth size="lg" isLoading={isSubmitting} className="mt-6">
          Tạo tài khoản
        </Button>
      </form>
    </AuthLayout>
  );
}

export default RegisterPage;