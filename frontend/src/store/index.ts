import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import deckReducer from './deckSlice';
import reviewReducer from './reviewSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    deck: deckReducer,
    review: reviewReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
