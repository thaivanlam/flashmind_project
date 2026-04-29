import { FC, useState } from 'react';
import { Lightbulb } from 'lucide-react';
import { Flashcard, Quality } from '@/types/review.types';

interface Props {
  card: Flashcard;
  onSubmit: (quality: Quality) => void;
  disabled?: boolean;
}

const qualityConfig: Record<Quality, { label: string; color: string }> = {
  0: { label: 'Quên hoàn toàn', color: 'bg-red-100 text-red-700 hover:bg-red-200' },
  1: { label: 'Sai, mơ hồ', color: 'bg-red-50 text-red-600 hover:bg-red-100' },
  2: { label: 'Sai, gần đúng', color: 'bg-orange-50 text-orange-700 hover:bg-orange-100' },
  3: { label: 'Đúng, khó', color: 'bg-yellow-50 text-yellow-700 hover:bg-yellow-100' },
  4: { label: 'Đúng, hơi nghĩ', color: 'bg-green-50 text-green-700 hover:bg-green-100' },
  5: { label: 'Đúng, dễ dàng', color: 'bg-green-100 text-green-800 hover:bg-green-200' },
};

const ReviewCard: FC<Props> = ({ card, onSubmit, disabled = false }) => {
  const [flipped, setFlipped] = useState<boolean>(false);
  const [showHint, setShowHint] = useState<boolean>(false);

  const handleSubmit = (quality: Quality) => {
    if (disabled) return;
    onSubmit(quality);
    setFlipped(false);
    setShowHint(false);
  };

  return (
    <div className="max-w-2xl mx-auto w-full">
      <div
        onClick={() => setFlipped(!flipped)}
        className={`bg-white rounded-2xl shadow-lg p-8 min-h-[280px] cursor-pointer
                    flex flex-col items-center justify-center text-center
                    border-2 transition
                    ${flipped ? 'border-primary-300' : 'border-gray-100 hover:border-primary-200'}`}
      >
        <div className="text-xs uppercase tracking-wide text-gray-400 mb-3">
          {flipped ? 'Đáp án' : 'Câu hỏi'}
        </div>
        <p className="text-xl font-medium text-gray-900 leading-relaxed">
          {flipped ? card.back : card.front}
        </p>
        {!flipped && card.hint && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              setShowHint(!showHint);
            }}
            className="mt-4 flex items-center gap-1 text-sm text-primary-600 hover:underline"
          >
            <Lightbulb size={14} />
            {showHint ? card.hint : 'Hiện gợi ý'}
          </button>
        )}
        {!flipped && (
          <div className="mt-6 text-xs text-gray-400">Click để xem đáp án</div>
        )}
      </div>

      {flipped && (
        <div className="mt-6">
          <p className="text-sm text-gray-600 text-center mb-3">
            Bạn nhớ thẻ này thế nào?
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {([0, 1, 2, 3, 4, 5] as Quality[]).map((q) => (
              <button
                key={q}
                onClick={() => handleSubmit(q)}
                disabled={disabled}
                className={`py-3 px-2 rounded-lg text-sm font-medium transition
                  disabled:opacity-50 disabled:cursor-not-allowed
                  ${qualityConfig[q].color}`}
              >
                <div className="font-bold">{q}</div>
                <div className="text-xs">{qualityConfig[q].label}</div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ReviewCard;
