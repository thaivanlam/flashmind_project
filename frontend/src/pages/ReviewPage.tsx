import { FC, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, ArrowLeft } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import {
  fetchTodayReviews,
  submitCardReview,
  nextCard,
} from '@/store/reviewSlice';
import { Quality } from '@/types/review.types';
import { formatRelative } from '@/utils/formatDate';
import Layout from '@/components/layout/Layout';
import ReviewCard from '@/components/flashcard/ReviewCard';

const ReviewPage: FC = () => {
  const dispatch = useAppDispatch();
  const { todayCards, currentIndex, loading, submitting } = useAppSelector(
    (state) => state.review
  );
  const [lastResult, setLastResult] = useState<{
    nextDate: string;
    interval: number;
  } | null>(null);

  useEffect(() => {
    dispatch(fetchTodayReviews());
  }, [dispatch]);

  const currentReview = todayCards[currentIndex];
  // Thẻ có thể null nếu review mồ côi lọt qua — không render ReviewCard khi đó
  const currentCard = currentReview?.card ?? null;
  const isFinished = todayCards.length > 0 && currentIndex >= todayCards.length;

  const handleSubmit = async (quality: Quality) => {
    if (!currentReview || submitting) return;
    const result = await dispatch(
      submitCardReview({ cardId: currentReview.cardId, quality })
    );
    if (submitCardReview.fulfilled.match(result)) {
      setLastResult({
        nextDate: result.payload.nextReviewDate,
        interval: result.payload.interval,
      });
      setTimeout(() => {
        dispatch(nextCard());
        setLastResult(null);
      }, 1200);
    } else {
      toast.error('Không thể submit');
    }
  };

  if (loading) {
    return (
      <Layout>
        <div className="text-center py-12 text-gray-500">Đang tải...</div>
      </Layout>
    );
  }

  if (todayCards.length === 0) {
    return (
      <Layout>
        <div className="text-center py-16 bg-white rounded-xl border border-gray-100">
          <CheckCircle2 className="mx-auto text-green-500 mb-3" size={48} />
          <h2 className="text-xl font-semibold text-gray-900 mb-1">
            Tuyệt vời!
          </h2>
          <p className="text-gray-600 mb-4">
            Bạn đã ôn xong tất cả các thẻ cần ôn hôm nay.
          </p>
          <Link
            to="/decks"
            className="inline-flex items-center gap-1 text-primary-600 hover:underline"
          >
            <ArrowLeft size={16} />
            Quay lại bộ thẻ
          </Link>
        </div>
      </Layout>
    );
  }

  if (isFinished) {
    return (
      <Layout>
        <div className="text-center py-16 bg-white rounded-xl border border-gray-100">
          <CheckCircle2 className="mx-auto text-green-500 mb-3" size={48} />
          <h2 className="text-xl font-semibold text-gray-900 mb-1">
            Hoàn thành phiên ôn!
          </h2>
          <p className="text-gray-600 mb-4">
            Bạn đã ôn xong {todayCards.length} thẻ.
          </p>
          <Link
            to="/dashboard"
            className="inline-flex items-center gap-1 text-primary-600 hover:underline"
          >
            <ArrowLeft size={16} />
            Về dashboard
          </Link>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="mb-6">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-xl font-semibold text-gray-900">Phiên ôn tập</h1>
          <span className="text-sm text-gray-500">
            {currentIndex + 1} / {todayCards.length}
          </span>
        </div>
        <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
          <div
            className="h-full bg-primary-600 transition-all"
            style={{
              width: `${(currentIndex / todayCards.length) * 100}%`,
            }}
          />
        </div>
      </div>

      {lastResult ? (
        <div className="text-center py-12 bg-green-50 rounded-2xl border border-green-100">
          <CheckCircle2 className="mx-auto text-green-500 mb-2" size={36} />
          <p className="text-gray-700">
            Lần tới ôn lại sau{' '}
            <strong>{lastResult.interval} ngày</strong>
          </p>
          <p className="text-sm text-gray-500 mt-1">
            ({formatRelative(lastResult.nextDate)})
          </p>
        </div>
      ) : (
        currentCard && (
          <ReviewCard
            card={currentCard}
            onSubmit={handleSubmit}
            disabled={submitting}
          />
        )
      )}
    </Layout>
  );
};

export default ReviewPage;
