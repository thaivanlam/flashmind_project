import { FC, useEffect, useState } from 'react';
import { Plus, BookOpen } from 'lucide-react';
import toast from 'react-hot-toast';
import { useAppDispatch, useAppSelector } from '@/hooks/useAppDispatch';
import { fetchDecks, createDeck, deleteDeck } from '@/store/deckSlice';
import { DeckRequest } from '@/types/deck.types';
import Layout from '@/components/layout/Layout';
import Button from '@/components/common/Button';
import Modal from '@/components/common/Modal';
import DeckForm from '@/components/deck/DeckForm';
import DeckCard from '@/components/deck/DeckCard';

const DecksPage: FC = () => {
  const dispatch = useAppDispatch();
  const { decks, loading } = useAppSelector((state) => state.deck);
  const [modalOpen, setModalOpen] = useState<boolean>(false);

  useEffect(() => {
    dispatch(fetchDecks());
  }, [dispatch]);

  const handleCreate = async (req: DeckRequest) => {
    const result = await dispatch(createDeck(req));
    if (createDeck.fulfilled.match(result)) {
      toast.success('Tạo bộ thẻ thành công');
      setModalOpen(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await dispatch(deleteDeck(id));
    if (deleteDeck.fulfilled.match(result)) {
      toast.success('Đã xóa bộ thẻ');
    }
  };

  return (
    <Layout>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Bộ thẻ của tôi</h1>
          <p className="text-gray-600 text-sm mt-1">
            {decks.length} bộ thẻ
          </p>
        </div>
        <Button onClick={() => setModalOpen(true)}>
          <span className="flex items-center gap-1">
            <Plus size={16} />
            Tạo mới
          </span>
        </Button>
      </div>

      {loading ? (
        <div className="text-center py-12 text-gray-500">Đang tải...</div>
      ) : decks.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-xl border border-gray-100">
          <BookOpen className="mx-auto text-gray-300 mb-3" size={48} />
          <p className="text-gray-600 mb-4">Bạn chưa có bộ thẻ nào</p>
          <Button onClick={() => setModalOpen(true)}>Tạo bộ thẻ đầu tiên</Button>
        </div>
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {decks.map((deck) => (
            <DeckCard key={deck.id} deck={deck} onDelete={handleDelete} />
          ))}
        </div>
      )}

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Tạo bộ thẻ mới"
      >
        <DeckForm onSubmit={handleCreate} onCancel={() => setModalOpen(false)} />
      </Modal>
    </Layout>
  );
};

export default DecksPage;
