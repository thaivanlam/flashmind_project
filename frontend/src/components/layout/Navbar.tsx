import { FC } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Brain, LogOut } from 'lucide-react';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import { logout } from '@/store/authSlice';

const Navbar: FC = () => {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const location = useLocation();

  const links = [
    { path: '/dashboard', label: 'Dashboard' },
    { path: '/decks', label: 'Bộ thẻ' },
    { path: '/review', label: 'Ôn tập' },
    { path: '/analytics', label: 'Thống kê' },
  ];

  const isActive = (path: string) => location.pathname.startsWith(path);

  return (
    <nav className="bg-white border-b sticky top-0 z-30">
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
        <div className="flex items-center gap-8">
          <Link to="/dashboard" className="flex items-center gap-2 font-semibold text-lg">
            <Brain className="text-primary-600" size={24} />
            FlashMind
          </Link>
          <div className="flex gap-1">
            {links.map((link) => (
              <Link
                key={link.path}
                to={link.path}
                className={`px-3 py-2 rounded-lg text-sm font-medium transition
                  ${isActive(link.path)
                    ? 'bg-primary-50 text-primary-700'
                    : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'}`}
              >
                {link.label}
              </Link>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-600 hidden sm:inline">
            {user?.fullName}
          </span>
          <button
            onClick={() => dispatch(logout())}
            className="text-gray-500 hover:text-red-500 p-2 rounded-lg hover:bg-gray-100"
            title="Đăng xuất"
          >
            <LogOut size={18} />
          </button>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
