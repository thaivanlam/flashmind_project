import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { Deck, DeckRequest } from '@/types/deck.types';
import { deckApi } from '@/api/deck.api';

interface DeckState {
  decks: Deck[];
  current: Deck | null;
  loading: boolean;
  error: string | null;
}

const initialState: DeckState = {
  decks: [],
  current: null,
  loading: false,
  error: null,
};

export const fetchDecks = createAsyncThunk('deck/fetchAll', async () =>
  deckApi.list()
);

export const fetchDeckById = createAsyncThunk(
  'deck/fetchById',
  async (id: number) => deckApi.get(id)
);

export const createDeck = createAsyncThunk(
  'deck/create',
  async (req: DeckRequest) => deckApi.create(req)
);

export const updateDeck = createAsyncThunk(
  'deck/update',
  async ({ id, req }: { id: number; req: DeckRequest }) =>
    deckApi.update(id, req)
);

export const deleteDeck = createAsyncThunk(
  'deck/delete',
  async (id: number) => {
    await deckApi.delete(id);
    return id;
  }
);

const deckSlice = createSlice({
  name: 'deck',
  initialState,
  reducers: {
    clearCurrent: (state) => {
      state.current = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchDecks.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchDecks.fulfilled, (state, action: PayloadAction<Deck[]>) => {
        state.loading = false;
        state.decks = action.payload;
      })
      .addCase(fetchDecks.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message ?? 'Lỗi tải decks';
      })
      .addCase(fetchDeckById.fulfilled, (state, action: PayloadAction<Deck>) => {
        state.current = action.payload;
      })
      .addCase(createDeck.fulfilled, (state, action: PayloadAction<Deck>) => {
        state.decks.unshift(action.payload);
      })
      .addCase(updateDeck.fulfilled, (state, action: PayloadAction<Deck>) => {
        const idx = state.decks.findIndex((d) => d.id === action.payload.id);
        if (idx !== -1) state.decks[idx] = action.payload;
        if (state.current?.id === action.payload.id) state.current = action.payload;
      })
      .addCase(deleteDeck.fulfilled, (state, action: PayloadAction<number>) => {
        state.decks = state.decks.filter((d) => d.id !== action.payload);
      });
  },
});

export const { clearCurrent } = deckSlice.actions;
export default deckSlice.reducer;
