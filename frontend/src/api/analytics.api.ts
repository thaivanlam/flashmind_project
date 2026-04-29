import axiosClient from './axiosClient';
import { Analytics } from '@/types/review.types';

export const analyticsApi = {
  get: async (): Promise<Analytics> => {
    const { data } = await axiosClient.get<Analytics>('/analytics');
    return data;
  },
};
