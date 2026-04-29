import { ButtonHTMLAttributes, FC, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  children: ReactNode;
  fullWidth?: boolean;
}

const variantStyles: Record<Variant, string> = {
  primary: 'bg-primary-600 text-white hover:bg-primary-700',
  secondary: 'bg-gray-100 text-gray-800 hover:bg-gray-200',
  danger: 'bg-red-500 text-white hover:bg-red-600',
  ghost: 'bg-transparent text-gray-700 hover:bg-gray-100',
};

const Button: FC<Props> = ({
  variant = 'primary',
  children,
  fullWidth = false,
  className = '',
  ...rest
}) => {
  return (
    <button
      {...rest}
      className={`px-4 py-2 rounded-lg font-medium text-sm transition
        disabled:opacity-50 disabled:cursor-not-allowed
        ${variantStyles[variant]}
        ${fullWidth ? 'w-full' : ''}
        ${className}`}
    >
      {children}
    </button>
  );
};

export default Button;
