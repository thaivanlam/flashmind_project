import { Flashcard } from './deck.types';

export type Quality = 0 | 1 | 2 | 3 | 4 | 5;

export interface CardReview {
  id: number;
  cardId: number;
  card: Flashcard;
  interval: number;
  easinessFactor: number;
  repetitionCount: number;
  nextReviewDate: string;
}

export interface ReviewSubmitResponse {
  nextReviewDate: string;
  interval: number;
  isMastered: boolean;
}

export interface DailyStat {
  date: string;
  cardsReviewed: number;
  correctCount: number;
}

export interface Analytics {
  currentStreak: number;
  totalCardsReviewed: number;
  masteredCards: number;
  last30Days: DailyStat[];
}
