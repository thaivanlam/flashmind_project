# Frontend

React 19 + TypeScript 5.6 + Vite 6, source in [frontend/src/](../frontend/src/).
The alias `@/*` points at `src/*`, declared in both `tsconfig.json` and `vite.config.ts`.

## Application startup

[main.tsx](../frontend/src/main.tsx) wraps `App` in `Provider` (the Redux store),
`BrowserRouter` and `Toaster` (react-hot-toast, top right corner).

## Routing

Declared in [App.tsx](../frontend/src/App.tsx):

| Path | Page | Protection |
|------|------|------------|
| `/` | redirects to `/dashboard` | — |
| `/login` | `LoginPage` | public |
| `/register` | `RegisterPage` | public |
| `/dashboard` | `DashboardPage` | `ProtectedRoute` |
| `/decks` | `DecksPage` | `ProtectedRoute` |
| `/decks/:id` | `DeckDetailPage` | `ProtectedRoute` |
| `/review` | `ReviewPage` | `ProtectedRoute` |
| `/analytics` | `AnalyticsPage` | `ProtectedRoute` |

[ProtectedRoute](../frontend/src/components/common/ProtectedRoute.tsx) only reads
`state.auth.isAuthenticated` and redirects to `/login` when not logged in.

## Redux Toolkit

The store is configured in [store/index.ts](../frontend/src/store/index.ts), one slice per
domain. Each slice owns its own `createAsyncThunk`s, and those thunks call down into a thin
`*.api.ts` client. **Components dispatch thunks, they never call axios directly.**

| Slice | State | Thunks / actions |
|-------|-------|------------------|
| [authSlice](../frontend/src/store/authSlice.ts) | `user`, `isAuthenticated`, `loading`, `error` | `login`, `register`, action `logout` |
| [deckSlice](../frontend/src/store/deckSlice.ts) | `decks`, `current`, `loading`, `error` | `fetchDecks`, `fetchDeckById`, `createDeck`, `updateDeck`, `deleteDeck`, action `clearCurrent` |
| [reviewSlice](../frontend/src/store/reviewSlice.ts) | `todayCards`, `currentIndex`, `loading`, `submitting`, `error` | `fetchTodayReviews`, `submitCardReview`, actions `nextCard`, `resetSession` |

Details worth knowing:

- `authSlice` initializes its state from `getStoredUser()`, so the session survives a refresh.
  `login`/`register` use `rejectWithValue` to surface the `message` returned by the backend.
- `deckSlice` updates local state after each mutation instead of refetching the list.
- `reviewSlice` filters out reviews with `card == null` when data arrives — the last line of
  defence against orphaned reviews (see [data-model.md](data-model.md)).

**Exception:** analytics has no slice; `DashboardPage` and `AnalyticsPage` call
`analyticsApi.get()` directly and keep the result in local `useState`.

## HTTP layer

[axiosClient.ts](../frontend/src/api/axiosClient.ts) is the **only HTTP entry point**.

- `baseURL = import.meta.env.VITE_API_URL || '/api'` — dev goes through the Vite proxy,
  production through the nginx reverse proxy.
- The request interceptor attaches `Authorization: Bearer <accessToken>`.
- The response interceptor: on a 401 it tries to refresh **exactly once** (the `_retry` flag,
  skipping URLs containing `/auth/`), stores the new token pair and replays the original
  request. If the refresh fails it clears the tokens and hard-redirects to `/login`.

The per-domain clients: [auth.api.ts](../frontend/src/api/auth.api.ts),
[deck.api.ts](../frontend/src/api/deck.api.ts) (which also covers flashcards and `generateAi`),
[review.api.ts](../frontend/src/api/review.api.ts),
[analytics.api.ts](../frontend/src/api/analytics.api.ts).

## Token storage

[tokenStorage.ts](../frontend/src/utils/tokenStorage.ts) wraps `localStorage` behind three keys:
`flashmind_access_token`, `flashmind_refresh_token`, `flashmind_user`.
**Never touch these keys directly anywhere else** — use
`getAccessToken`/`getRefreshToken`/`setTokens`/`clearTokens`/`setStoredUser`/`getStoredUser`.

## Components

| Folder | Components |
|--------|------------|
| `components/common/` | `Button`, `Modal`, `ProtectedRoute` |
| `components/layout/` | `Layout`, `Navbar` |
| `components/deck/` | `DeckCard`, `DeckForm`, `AiGenerateForm` |
| `components/flashcard/` | `FlashcardForm`, `FlashcardItem`, `ReviewCard` |

`ReviewPage` drives a review session: load the due cards, flip a card, submit a 0–5 grade, show
the next review date for 1.2 seconds, then move on to the next card. It does not render
`ReviewCard` when the card is `null`.

## Types

`types/auth.types.ts`, `types/deck.types.ts` and `types/review.types.ts` mirror the backend DTOs
directly. Changing a DTO on the backend means changing these interfaces and updating
[api-reference.md](api-reference.md).

`CardReview.card` is deliberately declared as `Flashcard | null`: the backend already filters
orphaned records, but the type still reflects the API contract accurately.

## TypeScript constraints

`strict` is on together with `noUnusedLocals` and `noUnusedParameters`, and `npm run build` runs
`tsc -b` before `vite build` — **an unused import will break the build**. To type-check on its
own, run `npx tsc -b --noEmit`. The `npm run lint` script is declared but does not work: the
repo has neither eslint nor an eslint config file.
