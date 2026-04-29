import { FC, useState } from 'react';
import { Upload, Loader2, Sparkles } from 'lucide-react';
import toast from 'react-hot-toast';
import { deckApi } from '@/api/deck.api';
import { Flashcard } from '@/types/deck.types';
import Button from '../common/Button';

interface Props {
  deckId: number;
  onGenerated: (cards: Flashcard[]) => void;
}

const AiGenerateForm: FC<Props> = ({ deckId, onGenerated }) => {
  const [file, setFile] = useState<File | null>(null);
  const [count, setCount] = useState<number>(10);
  const [loading, setLoading] = useState<boolean>(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (!selected) return;

    if (selected.size > 5 * 1024 * 1024) {
      toast.error('File phải nhỏ hơn 5MB');
      return;
    }
    setFile(selected);
  };

  const handleGenerate = async () => {
    if (!file) {
      toast.error('Hãy chọn file');
      return;
    }
    setLoading(true);
    try {
      const cards = await deckApi.generateAi(deckId, file, count);
      toast.success(`Đã sinh ${cards.length} thẻ từ AI`);
      onGenerated(cards);
      setFile(null);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Lỗi sinh flashcard');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-gradient-to-br from-primary-50 to-white border border-primary-100
                    rounded-xl p-5 space-y-4">
      <div className="flex items-center gap-2 mb-2">
        <Sparkles className="text-primary-600" size={20} />
        <h3 className="font-semibold text-gray-900">Sinh flashcard bằng AI</h3>
      </div>
      <p className="text-sm text-gray-600">
        Upload file PDF hoặc TXT để AI tự động trích xuất các khái niệm thành flashcard.
      </p>

      <div>
        <label className="flex flex-col items-center justify-center gap-2 px-4 py-6
                          border-2 border-dashed border-gray-300 rounded-lg cursor-pointer
                          hover:border-primary-400 transition">
          <Upload className="text-gray-400" size={28} />
          <span className="text-sm text-gray-600">
            {file ? file.name : 'Kéo thả hoặc click để chọn file'}
          </span>
          <span className="text-xs text-gray-400">PDF / TXT, tối đa 5MB</span>
          <input
            type="file"
            accept=".pdf,.txt"
            onChange={handleFileChange}
            className="hidden"
          />
        </label>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Số lượng flashcard: {count}
        </label>
        <input
          type="range"
          min={5}
          max={20}
          value={count}
          onChange={(e) => setCount(Number(e.target.value))}
          className="w-full"
        />
      </div>

      <Button onClick={handleGenerate} disabled={loading || !file} fullWidth>
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <Loader2 className="animate-spin" size={16} />
            Đang sinh...
          </span>
        ) : (
          'Sinh flashcard'
        )}
      </Button>
    </div>
  );
};

export default AiGenerateForm;
