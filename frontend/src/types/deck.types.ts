export interface Deck {
  id: number;
  userId: number;
  title: string;
  description: string | null;
  language: string | null;
  cardCount: number;
  createdAt: string;
}

export interface DeckRequest {
  title: string;
  description?: string;
  language?: string;
}

export interface Flashcard {
  id: number;
  deckId: number;
  front: string;
  back: string;
  hint: string | null;
  isAiGenerated: boolean;
}

export interface FlashcardRequest {
  front: string;
  back: string;
  hint?: string;
}
