import * as AvatarPrimitive from '@radix-ui/react-avatar';
import type { HTMLAttributes } from 'react';

import { cn, initialsFrom } from '@/lib/utils';

export interface UserAvatarProps extends HTMLAttributes<HTMLSpanElement> {
  name: string;
  src?: string | null;
  size?: 'sm' | 'default' | 'lg' | 'xl';
}

const sizeClasses: Record<NonNullable<UserAvatarProps['size']>, string> = {
  sm: 'size-8 text-xs',
  default: 'size-9 text-sm',
  lg: 'size-11 text-base',
  xl: 'size-14 text-lg',
};

export function UserAvatar({ name, src, size = 'default', className, ...props }: UserAvatarProps) {
  return (
    <AvatarPrimitive.Root
      className={cn(
        'relative inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-brand-100 font-semibold uppercase text-brand-700 ring-1 ring-inset ring-slate-900/5',
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {src !== null && src !== undefined && src.length > 0 && (
        <AvatarPrimitive.Image
          src={src}
          alt={name}
          className="aspect-square size-full object-cover"
        />
      )}
      <AvatarPrimitive.Fallback delayMs={200} className="leading-none">
        {initialsFrom(name)}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );
}