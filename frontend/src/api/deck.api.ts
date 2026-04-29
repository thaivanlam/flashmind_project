import axiosClient from './axiosClient';
import {
  Deck,
  DeckRequest,
  Flashcard,
  FlashcardRequest,
} from '@/types/deck.types';

export const deckApi = {
  list: async (): Promise<Deck[]> => {
    const { data } = await axiosClient.get<Deck[]>('/decks');
    return data;
  },

  get: async (id: number): Promise<Deck> => {
    const { data } = await axiosClient.get<Deck>(`/decks/${id}`);
    return data;
  },

  create: async (req: DeckRequest): Promise<Deck> => {
    const { data } = await axiosClient.post<Deck>('/decks', req);
    return data;
  },

  update: async (id: number, req: DeckRequest): Promise<Deck> => {
    const { data } = await axiosClient.put<Deck>(`/decks/${id}`, req);
    return data;
  },

  delete: async (id: number): Promise<void> => {
    await axiosClient.delete(`/decks/${id}`);
  },

  getCards: async (deckId: number): Promise<Flashcard[]> => {
    const { data } = await axiosClient.get<Flashcard[]>(`/decks/${deckId}/cards`);
    return data;
  },

  createCard: async (
    deckId: number,
    req: FlashcardRequest
  ): Promise<Flashcard> => {
    const { data } = await axiosClient.post<Flashcard>(
      `/decks/${deckId}/cards`,
      req
    );
    return data;
  },

  updateCard: async (
    cardId: number,
    req: FlashcardRequest
  ): Promise<Flashcard> => {
    const { data } = await axiosClient.put<Flashcard>(`/cards/${cardId}`, req);
    return data;
  },

  deleteCard: async (cardId: number): Promise<void> => {
    await axiosClient.delete(`/cards/${cardId}`);
  },

  generateAi: async (
    deckId: number,
    file: File,
    count: number = 10
  ): Promise<Flashcard[]> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('count', String(count));
    const { data } = await axiosClient.post<Flashcard[]>(
      `/decks/${deckId}/generate-ai`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return data;
  },
};
