# Frontend

React 19 + TypeScript 5.6 + Vite 6, mã nguồn trong [frontend/src/](../frontend/src/).
Alias `@/*` trỏ tới `src/*`, khai báo ở cả `tsconfig.json` lẫn `vite.config.ts`.

## Khởi động ứng dụng

[main.tsx](../frontend/src/main.tsx) bọc `App` trong `Provider` (Redux store),
`BrowserRouter` và `Toaster` (react-hot-toast, góc trên bên phải).

## Routing

Khai báo trong [App.tsx](../frontend/src/App.tsx):

| Đường dẫn | Trang | Bảo vệ |
|-----------|-------|--------|
| `/` | chuyển hướng sang `/dashboard` | — |
| `/login` | `LoginPage` | công khai |
| `/register` | `RegisterPage` | công khai |
| `/dashboard` | `DashboardPage` | `ProtectedRoute` |
| `/decks` | `DecksPage` | `ProtectedRoute` |
| `/decks/:id` | `DeckDetailPage` | `ProtectedRoute` |
| `/review` | `ReviewPage` | `ProtectedRoute` |
| `/analytics` | `AnalyticsPage` | `ProtectedRoute` |

[ProtectedRoute](../frontend/src/components/common/ProtectedRoute.tsx) chỉ đọc
`state.auth.isAuthenticated` và điều hướng về `/login` nếu chưa đăng nhập.

## Redux Toolkit

Store cấu hình ở [store/index.ts](../frontend/src/store/index.ts), mỗi domain một slice.
Mỗi slice tự sở hữu các `createAsyncThunk` của nó và các thunk này gọi xuống client
`*.api.ts` mỏng. **Component dispatch thunk, không gọi axios trực tiếp.**

| Slice | State | Thunk / action |
|-------|-------|----------------|
| [authSlice](../frontend/src/store/authSlice.ts) | `user`, `isAuthenticated`, `loading`, `error` | `login`, `register`, action `logout` |
| [deckSlice](../frontend/src/store/deckSlice.ts) | `decks`, `current`, `loading`, `error` | `fetchDecks`, `fetchDeckById`, `createDeck`, `updateDeck`, `deleteDeck`, action `clearCurrent` |
| [reviewSlice](../frontend/src/store/reviewSlice.ts) | `todayCards`, `currentIndex`, `loading`, `submitting`, `error` | `fetchTodayReviews`, `submitCardReview`, action `nextCard`, `resetSession` |

Chi tiết đáng lưu ý:

- `authSlice` khởi tạo state từ `getStoredUser()`, nên phiên đăng nhập sống sót qua F5.
  `login`/`register` dùng `rejectWithValue` để lấy `message` do backend trả về.
- `deckSlice` cập nhật state cục bộ sau mỗi mutation thay vì fetch lại danh sách.
- `reviewSlice` lọc bỏ review có `card == null` khi nhận dữ liệu — lớp phòng thủ cuối
  chống review mồ côi (xem [data-model.md](data-model.md)).

**Ngoại lệ:** analytics không có slice; `DashboardPage` và `AnalyticsPage` gọi thẳng
`analyticsApi.get()` rồi giữ trong `useState` cục bộ.

## Tầng HTTP

[axiosClient.ts](../frontend/src/api/axiosClient.ts) là **cổng HTTP duy nhất**.

- `baseURL = import.meta.env.VITE_API_URL || '/api'` — dev đi qua Vite proxy,
  production đi qua nginx reverse proxy.
- Request interceptor gắn `Authorization: Bearer <accessToken>`.
- Response interceptor: gặp 401 thì thử refresh **đúng một lần** (cờ `_retry`, bỏ qua các
  URL chứa `/auth/`), lưu cặp token mới rồi phát lại request gốc. Refresh thất bại thì xóa
  token và chuyển hướng cứng về `/login`.

Các client theo domain: [auth.api.ts](../frontend/src/api/auth.api.ts),
[deck.api.ts](../frontend/src/api/deck.api.ts) (bao gồm cả flashcard và `generateAi`),
[review.api.ts](../frontend/src/api/review.api.ts),
[analytics.api.ts](../frontend/src/api/analytics.api.ts).

## Lưu trữ token

[tokenStorage.ts](../frontend/src/utils/tokenStorage.ts) bọc `localStorage` với ba khóa:
`flashmind_access_token`, `flashmind_refresh_token`, `flashmind_user`.
**Không bao giờ chạm trực tiếp vào các khóa này ở nơi khác** — dùng
`getAccessToken`/`getRefreshToken`/`setTokens`/`clearTokens`/`setStoredUser`/`getStoredUser`.

## Component

| Thư mục | Component |
|---------|-----------|
| `components/common/` | `Button`, `Modal`, `ProtectedRoute` |
| `components/layout/` | `Layout`, `Navbar` |
| `components/deck/` | `DeckCard`, `DeckForm`, `AiGenerateForm` |
| `components/flashcard/` | `FlashcardForm`, `FlashcardItem`, `ReviewCard` |

`ReviewPage` chạy phiên ôn tập: nạp thẻ đến hạn, lật thẻ, gửi điểm 0–5, hiện lịch ôn kế tiếp
trong 1,2 giây rồi tự chuyển sang thẻ sau. Nó không render `ReviewCard` khi thẻ là `null`.

## Kiểu dữ liệu

`types/auth.types.ts`, `types/deck.types.ts`, `types/review.types.ts` phản ánh trực tiếp
DTO của backend. Khi sửa DTO ở backend phải sửa các interface này và cập nhật
[api-reference.md](api-reference.md).

`CardReview.card` được khai báo là `Flashcard | null` một cách có chủ đích: backend đã lọc
các bản ghi mồ côi, nhưng kiểu dữ liệu vẫn phản ánh đúng hợp đồng API.

## Ràng buộc TypeScript

`strict` bật cùng `noUnusedLocals` và `noUnusedParameters`, và `npm run build` chạy `tsc -b`
trước `vite build` — **import thừa sẽ làm hỏng build**. Kiểm tra kiểu riêng lẻ bằng
`npx tsc -b --noEmit`. Script `npm run lint` được khai báo nhưng không chạy được:
repo không có eslint lẫn file cấu hình eslint.
