import { FC, FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Brain } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import { login } from '@/store/authSlice';
import Button from '@/components/common/Button';

const LoginPage: FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { loading } = useAppSelector((state) => state.auth);

  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const result = await dispatch(login({ email, password }));
    if (login.fulfilled.match(result)) {
      toast.success('Đăng nhập thành công!');
      navigate('/dashboard');
    } else {
      toast.error(result.payload as string);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br
                    from-primary-50 to-white px-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-8">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-14 h-14
                          bg-primary-100 rounded-2xl mb-3">
            <Brain className="text-primary-600" size={28} />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">FlashMind</h1>
          <p className="text-sm text-gray-500 mt-1">
            Học thông minh hơn với AI và Spaced Repetition
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Email
            </label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 outline-none"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Mật khẩu
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 outline-none"
            />
          </div>
          <Button type="submit" fullWidth disabled={loading}>
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </Button>
        </form>

        <p className="text-center text-sm text-gray-600 mt-6">
          Chưa có tài khoản?{' '}
          <Link to="/register" className="text-primary-600 hover:underline">
            Đăng ký
          </Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
