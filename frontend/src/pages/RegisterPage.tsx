import { FC, FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Brain } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import { register } from '@/store/authSlice';
import Button from '@/components/common/Button';

const RegisterPage: FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { loading } = useAppSelector((state) => state.auth);

  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [fullName, setFullName] = useState<string>('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (password.length < 6) {
      toast.error('Mật khẩu phải ít nhất 6 ký tự');
      return;
    }
    const result = await dispatch(register({ email, password, fullName }));
    if (register.fulfilled.match(result)) {
      toast.success('Đăng ký thành công!');
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
          <h1 className="text-2xl font-bold text-gray-900">Tạo tài khoản</h1>
          <p className="text-sm text-gray-500 mt-1">Bắt đầu hành trình học tập</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Họ và tên
            </label>
            <input
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 outline-none"
            />
          </div>
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
              Mật khẩu (tối thiểu 6 ký tự)
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 outline-none"
            />
          </div>
          <Button type="submit" fullWidth disabled={loading}>
            {loading ? 'Đang đăng ký...' : 'Đăng ký'}
          </Button>
        </form>

        <p className="text-center text-sm text-gray-600 mt-6">
          Đã có tài khoản?{' '}
          <Link to="/login" className="text-primary-600 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
