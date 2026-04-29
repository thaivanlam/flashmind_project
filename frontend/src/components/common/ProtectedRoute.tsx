import { FC, ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAppSelector } from '@/hooks/useAppDispatch';

interface Props {
  children: ReactNode;
}

const ProtectedRoute: FC<Props> = ({ children }) => {
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
};

export default ProtectedRoute;
