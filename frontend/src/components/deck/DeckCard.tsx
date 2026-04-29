import { FC } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Trash2 } from 'lucide-react';
import { Deck } from '@/types/deck.types';
import { formatDate } from '@/utils/formatDate';

interface Props {
  deck: Deck;
  onDelete: (id: number) => void;
}

const DeckCard: FC<Props> = ({ deck, onDelete }) => {
  const handleDelete = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (window.confirm(`Xóa bộ thẻ "${deck.title}"?`)) {
      onDelete(deck.id);
    }
  };

  return (
    <Link
      to={`/decks/${deck.id}`}
      className="block bg-white rounded-xl shadow-sm hover:shadow-md transition p-5
                 border border-gray-100 hover:border-primary-200 group"
    >
      <div className="flex items-start justify-between mb-3">
        <BookOpen className="text-primary-600" size={20} />
        <button
          onClick={handleDelete}
          className="opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-500
                     transition p-1"
        >
          <Trash2 size={16} />
        </button>
      </div>
      <h3 className="font-semibold text-gray-900 mb-1 line-clamp-1">
        {deck.title}
      </h3>
      <p className="text-sm text-gray-500 mb-3 line-clamp-2 min-h-[2.5rem]">
        {deck.description || 'Không có mô tả'}
      </p>
      <div className="flex justify-between text-xs text-gray-400">
        <span>{deck.cardCount} thẻ</span>
        <span>{formatDate(deck.createdAt)}</span>
      </div>
    </Link>
  );
};

export default DeckCard;
