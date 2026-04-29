import { FC, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Brain, TrendingUp, Calendar } from 'lucide-react';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import { fetchDecks } from '@/store/deckSlice';
import { fetchTodayReviews } from '@/store/reviewSlice';
import { analyticsApi } from '@/api/analytics.api';
import { Analytics } from '@/types/review.types';
import Layout from '@/components/layout/Layout';

const DashboardPage: FC = () => {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { decks } = useAppSelector((state) => state.deck);
  const { todayCards } = useAppSelector((state) => state.review);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);

  useEffect(() => {
    dispatch(fetchDecks());
    dispatch(fetchTodayReviews());
    analyticsApi.get().then(setAnalytics).catch(console.error);
  }, [dispatch]);

  const stats = [
    {
      icon: <Calendar className="text-orange-500" size={20} />,
      label: 'Cần ôn hôm nay',
      value: todayCards.length,
      bg: 'bg-orange-50',
    },
    {
      icon: <BookOpen className="text-primary-500" size={20} />,
      label: 'Bộ thẻ',
      value: decks.length,
      bg: 'bg-primary-50',
    },
    {
      icon: <TrendingUp className="text-green-500" size={20} />,
      label: 'Streak',
      value: analytics?.currentStreak ?? 0,
      bg: 'bg-green-50',
    },
    {
      icon: <Brain className="text-purple-500" size={20} />,
      label: 'Đã thuộc',
      value: analytics?.masteredCards ?? 0,
      bg: 'bg-purple-50',
    },
  ];

  return (
    <Layout>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Xin chào, {user?.fullName?.split(' ').pop()}! 👋
        </h1>
        <p className="text-gray-600 mt-1">Hôm nay có gì thú vị nào?</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {stats.map((s) => (
          <div
            key={s.label}
            className={`${s.bg} rounded-xl p-4 border border-gray-100`}
          >
            <div className="flex items-center justify-between mb-2">
              {s.icon}
            </div>
            <div className="text-2xl font-bold text-gray-900">{s.value}</div>
            <div className="text-xs text-gray-600 mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        {todayCards.length > 0 && (
          <div className="bg-white rounded-xl p-5 border border-gray-100">
            <h2 className="font-semibold text-gray-900 mb-3">Bài ôn hôm nay</h2>
            <p className="text-sm text-gray-600 mb-4">
              Bạn có {todayCards.length} thẻ cần ôn để duy trì trí nhớ.
            </p>
            <Link
              to="/review"
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary-600
                         text-white rounded-lg text-sm font-medium hover:bg-primary-700"
            >
              Bắt đầu ôn
            </Link>
          </div>
        )}

        <div className="bg-white rounded-xl p-5 border border-gray-100">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-gray-900">Bộ thẻ gần đây</h2>
            <Link
              to="/decks"
              className="text-sm text-primary-600 hover:underline"
            >
              Xem tất cả
            </Link>
          </div>
          {decks.length === 0 ? (
            <div className="text-center py-8 text-gray-500 text-sm">
              Chưa có bộ thẻ nào.{' '}
              <Link to="/decks" className="text-primary-600 hover:underline">
                Tạo ngay
              </Link>
            </div>
          ) : (
            <div className="space-y-2">
              {decks.slice(0, 5).map((deck) => (
                <Link
                  key={deck.id}
                  to={`/decks/${deck.id}`}
                  className="block p-3 rounded-lg hover:bg-gray-50 transition"
                >
                  <div className="font-medium text-gray-900">{deck.title}</div>
                  <div className="text-xs text-gray-500 mt-0.5">
                    {deck.cardCount} thẻ
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
};

export default DashboardPage;
