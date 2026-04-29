import { FC, FormEvent, useState } from 'react';
import { DeckRequest, Deck } from '@/types/deck.types';
import Button from '../common/Button';

interface Props {
  initial?: Deck;
  onSubmit: (req: DeckRequest) => void;
  onCancel: () => void;
}

const DeckForm: FC<Props> = ({ initial, onSubmit, onCancel }) => {
  const [title, setTitle] = useState<string>(initial?.title ?? '');
  const [description, setDescription] = useState<string>(
    initial?.description ?? ''
  );
  const [language, setLanguage] = useState<string>(initial?.language ?? 'vi');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    onSubmit({ title: title.trim(), description, language });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Tiêu đề
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 focus:border-transparent outline-none"
          placeholder="VD: Tiếng Anh giao tiếp"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Mô tả
        </label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 focus:border-transparent outline-none"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Ngôn ngữ
        </label>
        <select
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg
                     focus:ring-2 focus:ring-primary-500 outline-none"
        >
          <option value="vi">Tiếng Việt</option>
          <option value="en">English</option>
          <option value="ja">日本語</option>
          <option value="zh">中文</option>
        </select>
      </div>
      <div className="flex gap-2 justify-end pt-2">
        <Button type="button" variant="ghost" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit">
          {initial ? 'Lưu' : 'Tạo'}
        </Button>
      </div>
    </form>
  );
};

export default DeckForm;
