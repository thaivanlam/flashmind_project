import { FC, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, Plus } from 'lucide-react';
import toast from 'react-hot-toast';
import { deckApi } from '@/api/deck.api';
import { Deck, Flashcard, FlashcardRequest } from '@/types/deck.types';
import Layout from '@/components/layout/Layout';
import Button from '@/components/common/Button';
import Modal from '@/components/common/Modal';
import FlashcardItem from '@/components/flashcard/FlashcardItem';
import FlashcardForm from '@/components/flashcard/FlashcardForm';
import AiGenerateForm from '@/components/deck/AiGenerateForm';

const DeckDetailPage: FC = () => {
  const { id } = useParams<{ id: string }>();
  const deckId = Number(id);

  const [deck, setDeck] = useState<Deck | null>(null);
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [modalOpen, setModalOpen] = useState<boolean>(false);
  const [editingCard, setEditingCard] = useState<Flashcard | undefined>();

  const loadData = async () => {
    setLoading(true);
    try {
      const [d, c] = await Promise.all([
        deckApi.get(deckId),
        deckApi.getCards(deckId),
      ]);
      setDeck(d);
      setCards(c);
    } catch {
      toast.error('Không thể tải dữ liệu');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [deckId]);

  const handleCreateOrUpdate = async (req: FlashcardRequest) => {
    try {
      if (editingCard) {
        const updated = await deckApi.updateCard(editingCard.id, req);
        setCards(cards.map((c) => (c.id === updated.id ? updated : c)));
        toast.success('Đã cập nhật');
      } else {
        const created = await deckApi.createCard(deckId, req);
        setCards([...cards, created]);
        toast.success('Đã thêm thẻ');
      }
      setModalOpen(false);
      setEditingCard(undefined);
    } catch {
      toast.error('Có lỗi xảy ra');
    }
  };

  const handleDelete = async (cardId: number) => {
    try {
      await deckApi.deleteCard(cardId);
      setCards(cards.filter((c) => c.id !== cardId));
      toast.success('Đã xóa');
    } catch {
      toast.error('Không thể xóa');
    }
  };

  const handleAiGenerated = (newCards: Flashcard[]) => {
    setCards([...cards, ...newCards]);
  };

  if (loading) {
    return (
      <Layout>
        <div className="text-center py-12 text-gray-500">Đang tải...</div>
      </Layout>
    );
  }

  if (!deck) return null;

  return (
    <Layout>
      <Link
        to="/decks"
        className="inline-flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900 mb-4"
      >
        <ArrowLeft size={16} />
        Quay lại
      </Link>

      <div className="bg-white rounded-xl p-6 border border-gray-100 mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-1">{deck.title}</h1>
        {deck.description && (
          <p className="text-gray-600 mb-3">{deck.description}</p>
        )}
        <div className="text-sm text-gray-500">
          {cards.length} thẻ · Ngôn ngữ: {deck.language ?? 'N/A'}
        </div>
      </div>

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-900">Danh sách thẻ</h2>
            <Button
              onClick={() => {
                setEditingCard(undefined);
                setModalOpen(true);
              }}
            >
              <span className="flex items-center gap-1">
                <Plus size={14} />
                Thêm thẻ
              </span>
            </Button>
          </div>

          {cards.length === 0 ? (
            <div className="text-center py-12 bg-white rounded-xl border border-gray-100">
              <p className="text-gray-500">Chưa có thẻ nào</p>
            </div>
          ) : (
            <div className="space-y-2">
              {cards.map((card) => (
                <FlashcardItem
                  key={card.id}
                  card={card}
                  onEdit={(c) => {
                    setEditingCard(c);
                    setModalOpen(true);
                  }}
                  onDelete={handleDelete}
                />
              ))}
            </div>
          )}
        </div>

        <div>
          <AiGenerateForm deckId={deckId} onGenerated={handleAiGenerated} />
        </div>
      </div>

      <Modal
        isOpen={modalOpen}
        onClose={() => {
          setModalOpen(false);
          setEditingCard(undefined);
        }}
        title={editingCard ? 'Sửa thẻ' : 'Thêm thẻ mới'}
      >
        <FlashcardForm
          initial={editingCard}
          onSubmit={handleCreateOrUpdate}
          onCancel={() => {
            setModalOpen(false);
            setEditingCard(undefined);
          }}
        />
      </Modal>
    </Layout>
  );
};

export default DeckDetailPage;
