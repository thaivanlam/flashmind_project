import { FC } from 'react';
import { Edit2, Trash2, Sparkles } from 'lucide-react';
import { Flashcard } from '@/types/deck.types';

interface Props {
  card: Flashcard;
  onEdit: (card: Flashcard) => void;
  onDelete: (id: number) => void;
}

const FlashcardItem: FC<Props> = ({ card, onEdit, onDelete }) => {
  return (
    <div className="bg-white border border-gray-100 rounded-lg p-4 hover:shadow-sm transition">
      <div className="flex justify-between items-start gap-2 mb-2">
        <div className="flex-1 min-w-0">
          <p className="font-medium text-gray-900 mb-1 break-words">
            {card.front}
          </p>
          <p className="text-sm text-gray-600 break-words">{card.back}</p>
          {card.hint && (
            <p className="text-xs text-gray-400 mt-1 italic">
              Gợi ý: {card.hint}
            </p>
          )}
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {card.isAiGenerated && (
            <span className="text-primary-500" title="AI tạo">
              <Sparkles size={14} />
            </span>
          )}
          <button
            onClick={() => onEdit(card)}
            className="text-gray-400 hover:text-primary-600 p-1"
          >
            <Edit2 size={14} />
          </button>
          <button
            onClick={() => {
              if (window.confirm('Xóa thẻ này?')) onDelete(card.id);
            }}
            className="text-gray-400 hover:text-red-500 p-1"
          >
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </div>
  );
};

export default FlashcardItem;
