import { FC, FormEvent, useState } from 'react';
import { Flashcard, FlashcardRequest } from '@/types/deck.types';
import Button from '../common/Button';

interface Props {
  initial?: Flashcard;
  onSubmit: (req: FlashcardRequest) => void;
  onCancel: () => void;
}

const FlashcardForm: FC<Props> = ({ initial, onSubmit, onCancel }) => {
  const [front, setFront] = useState<string>(initial?.front ?? '');
  const [back, setBack] = useState<string>(initial?.back ?? '');
  const [hint, setHint] = useState<string>(initial?.hint ?? '');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!front.trim() || !back.trim()) return;
    onSubmit({
      front: front.trim(),
      back: back.trim(),
      hint: hint.trim() || undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Mặt trước (câu hỏi)
        </label>
        <textarea
          value={front}
          onChange={(e) => setFront(e.target.value)}
          required
          rows={2}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Mặt sau (đáp án)
        </label>
        <textarea
          value={back}
          onChange={(e) => setBack(e.target.value)}
          required
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Gợi ý (tùy chọn)
        </label>
        <input
          type="text"
          value={hint}
          onChange={(e) => setHint(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 outline-none"
        />
      </div>
      <div className="flex gap-2 justify-end pt-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit">{initial ? 'Lưu' : 'Tạo'}</Button>
      </div>
    </form>
  );
};

export default FlashcardForm;
