import axiosClient from './axiosClient';
import {
  CardReview,
  Quality,
  ReviewSubmitResponse,
} from '@/types/review.types';

export const reviewApi = {
  getTodayReviews: async (): Promise<CardReview[]> => {
    const { data } = await axiosClient.get<CardReview[]>('/reviews/today');
    return data;
  },

  submitReview: async (
    cardId: number,
    quality: Quality
  ): Promise<ReviewSubmitResponse> => {
    const { data } = await axiosClient.post<ReviewSubmitResponse>(
      `/reviews/${cardId}`,
      { quality }
    );
    return data;
  },
};
