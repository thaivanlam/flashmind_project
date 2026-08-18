import { Flashcard } from './deck.types';

export type { Flashcard };

export type Quality = 0 | 1 | 2 | 3 | 4 | 5;

export interface CardReview {
  id: number;
  cardId: number;
  /**
   * Có thể null nếu thẻ đã bị xóa mà review chưa được dọn (review mồ côi).
   * Backend đã lọc các bản ghi này, nhưng type vẫn phản ánh đúng hợp đồng API.
   */
  card: Flashcard | null;
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
