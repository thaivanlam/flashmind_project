import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { CardReview, Quality } from '@/types/review.types';
import { reviewApi } from '@/api/review.api';

interface ReviewState {
  todayCards: CardReview[];
  currentIndex: number;
  loading: boolean;
  submitting: boolean;
  error: string | null;
}

const initialState: ReviewState = {
  todayCards: [],
  currentIndex: 0,
  loading: false,
  submitting: false,
  error: null,
};

export const fetchTodayReviews = createAsyncThunk('review/fetchToday', async () =>
  reviewApi.getTodayReviews()
);

export const submitCardReview = createAsyncThunk(
  'review/submit',
  async ({ cardId, quality }: { cardId: number; quality: Quality }) =>
    reviewApi.submitReview(cardId, quality)
);

const reviewSlice = createSlice({
  name: 'review',
  initialState,
  reducers: {
    nextCard: (state) => {
      state.currentIndex += 1;
    },
    resetSession: (state) => {
      state.currentIndex = 0;
      state.todayCards = [];
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchTodayReviews.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(
        fetchTodayReviews.fulfilled,
        (state, action: PayloadAction<CardReview[]>) => {
          state.loading = false;
          // Bỏ review mồ côi (thẻ đã bị xóa) để UI không render card null
          state.todayCards = action.payload.filter((r) => r.card != null);
          state.currentIndex = 0;
        }
      )
      .addCase(fetchTodayReviews.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message ?? 'Lỗi tải bài ôn';
      })
      .addCase(submitCardReview.pending, (state) => {
        state.submitting = true;
      })
      .addCase(submitCardReview.fulfilled, (state) => {
        state.submitting = false;
      })
      .addCase(submitCardReview.rejected, (state) => {
        state.submitting = false;
      });
  },
});

export const { nextCard, resetSession } = reviewSlice.actions;
export default reviewSlice.reducer;
