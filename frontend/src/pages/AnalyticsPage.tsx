import { FC, useEffect, useState } from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';
import { Flame, Trophy, Zap } from 'lucide-react';
import { analyticsApi } from '@/api/analytics.api';
import { Analytics } from '@/types/review.types';
import Layout from '@/components/layout/Layout';

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

const AnalyticsPage: FC = () => {
  const [data, setData] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    analyticsApi
      .get()
      .then(setData)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <Layout>
        <div className="text-center py-12 text-gray-500">Đang tải...</div>
      </Layout>
    );
  }

  if (!data) {
    return (
      <Layout>
        <div className="text-center py-12 text-gray-500">Không có dữ liệu</div>
      </Layout>
    );
  }

  const chartData = {
    labels: data.last30Days.map((d) => {
      const date = new Date(d.date);
      return `${date.getDate()}/${date.getMonth() + 1}`;
    }),
    datasets: [
      {
        label: 'Số thẻ ôn',
        data: data.last30Days.map((d) => d.cardsReviewed),
        backgroundColor: '#6366f1',
        borderRadius: 4,
      },
      {
        label: 'Trả lời đúng',
        data: data.last30Days.map((d) => d.correctCount),
        backgroundColor: '#10b981',
        borderRadius: 4,
      },
    ],
  };

  const stats = [
    {
      icon: <Flame className="text-orange-500" size={22} />,
      label: 'Streak hiện tại',
      value: `${data.currentStreak} ngày`,
      bg: 'bg-orange-50',
    },
    {
      icon: <Zap className="text-primary-500" size={22} />,
      label: 'Tổng số lần ôn',
      value: data.totalCardsReviewed.toLocaleString(),
      bg: 'bg-primary-50',
    },
    {
      icon: <Trophy className="text-yellow-500" size={22} />,
      label: 'Thẻ đã thuộc',
      value: data.masteredCards.toLocaleString(),
      bg: 'bg-yellow-50',
    },
  ];

  return (
    <Layout>
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Thống kê</h1>
      <p className="text-gray-600 text-sm mb-6">
        Tiến độ học tập của bạn trong 30 ngày qua
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        {stats.map((s) => (
          <div
            key={s.label}
            className={`${s.bg} rounded-xl p-5 border border-gray-100`}
          >
            <div className="flex items-center gap-3">
              <div className="bg-white rounded-lg p-2">{s.icon}</div>
              <div>
                <div className="text-2xl font-bold text-gray-900">
                  {s.value}
                </div>
                <div className="text-xs text-gray-600">{s.label}</div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-xl p-5 border border-gray-100">
        <h2 className="font-semibold text-gray-900 mb-4">
          Hoạt động 30 ngày qua
        </h2>
        <div className="h-80">
          <Bar
            data={chartData}
            options={{
              responsive: true,
              maintainAspectRatio: false,
              plugins: {
                legend: { position: 'top' as const },
              },
              scales: {
                y: { beginAtZero: true, ticks: { stepSize: 1 } },
              },
            }}
          />
        </div>
      </div>
    </Layout>
  );
};

export default AnalyticsPage;
