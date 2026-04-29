# FlashMind 🧠

> AI-powered flashcard application với Spaced Repetition (SM-2 algorithm)

Học thông minh hơn — chỉ ôn những gì bạn sắp quên. FlashMind tự động sinh flashcard từ tài liệu PDF/text bằng AI và lên lịch ôn tập tối ưu dựa trên thuật toán SuperMemo 2.

---

## ✨ Tính năng chính

- 🔐 **Xác thực JWT** — Access token + Refresh token với Redis
- 📚 **Quản lý bộ thẻ** — Tạo, sửa, xóa decks và flashcards
- 🤖 **AI Generation** — Upload PDF/TXT, GPT-4o tự động sinh flashcard
- 🧮 **SM-2 Spaced Repetition** — Thuật toán SuperMemo 2 chuẩn
- 📊 **Analytics** — Streak, biểu đồ 30 ngày, số thẻ đã thuộc
- ⏰ **Scheduled Job** — Tự động cache due cards mỗi ngày

---

## 🏗️ Kiến trúc

```
┌─────────────────┐         ┌─────────────────┐
│  React 19 + TS  │ ─HTTP─► │  Spring Boot 3  │
│  Redux Toolkit  │         │   (Monolith)    │
│  Tailwind CSS   │         │  Spring Security│
└─────────────────┘         └────────┬────────┘
                                     │
                ┌────────────────────┼────────────────────┐
                ▼                    ▼                    ▼
         ┌──────────────┐    ┌──────────────┐     ┌──────────────┐
         │  PostgreSQL  │    │    Redis     │     │  OpenAI API  │
         │  (Main DB)   │    │   (Cache)    │     │  (gpt-4o-mini)│
         └──────────────┘    └──────────────┘     └──────────────┘
```

---

## 🛠️ Tech Stack

### Backend
- **Java 17** + **Spring Boot 3.3.5**
- **Spring Security** + **JWT** (jjwt 0.12)
- **Spring Data JPA** + **PostgreSQL 16**
- **Spring Data Redis** (cache due cards)
- **Spring WebFlux** (WebClient cho OpenAI)
- **Spring Scheduler** (cron jobs)
- **Apache PDFBox** (parse PDF)
- **Lombok**

### Frontend
- **React 19** + **TypeScript 5.6** (strict mode)
- **Vite 6** (build tool)
- **Redux Toolkit** + **React-Redux**
- **React Router 7**
- **Axios** (with refresh-token interceptor)
- **Tailwind CSS 3.4**
- **Chart.js** (analytics)
- **Lucide React** (icons)
- **react-hot-toast** (notifications)

### DevOps
- **Docker** + **Docker Compose**
- **Nginx** (frontend + reverse proxy)

---

## 🚀 Quick Start

### Yêu cầu

- Docker và Docker Compose
- OpenAI API Key — đăng ký tại [platform.openai.com](https://platform.openai.com)

### Chạy với Docker (khuyến nghị)

```bash
# 1. Clone project và vào thư mục
cd flashmind

# 2. Tạo file .env từ template
cp .env.example .env

# 3. Mở file .env và điền OPENAI_API_KEY
# OPENAI_API_KEY=sk-...your-key...

# 4. Build và chạy
docker-compose up -d

# 5. Xem logs
docker-compose logs -f backend
```

Truy cập:
- 🌐 **Frontend:** http://localhost:5173
- 🔌 **Backend API:** http://localhost:8080
- 🐘 **PostgreSQL:** localhost:5432
- 🔴 **Redis:** localhost:6379

### Chạy local (development)

#### Backend

```bash
cd backend

# Cần PostgreSQL + Redis chạy sẵn
# (Có thể dùng: docker-compose up postgres redis -d)

export OPENAI_API_KEY=sk-...

mvn spring-boot:run
```

Backend chạy tại `http://localhost:8080`

#### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend chạy tại `http://localhost:5173` với proxy `/api` → backend.

---

## 📡 API Endpoints

### Authentication
```
POST   /api/auth/register      Đăng ký user mới
POST   /api/auth/login         Đăng nhập
POST   /api/auth/refresh       Refresh access token
```

### Decks
```
GET    /api/decks              Danh sách deck của user
POST   /api/decks              Tạo deck mới
GET    /api/decks/{id}         Chi tiết deck
PUT    /api/decks/{id}         Cập nhật deck
DELETE /api/decks/{id}         Xóa deck (cascade cards)
```

### Flashcards
```
GET    /api/decks/{deckId}/cards          Danh sách thẻ của deck
POST   /api/decks/{deckId}/cards          Tạo thẻ thủ công
POST   /api/decks/{deckId}/generate-ai    Sinh thẻ bằng AI (multipart/form-data)
PUT    /api/cards/{cardId}                Cập nhật thẻ
DELETE /api/cards/{cardId}                Xóa thẻ
```

### Reviews (Spaced Repetition)
```
GET    /api/reviews/today      Danh sách thẻ cần ôn hôm nay
POST   /api/reviews/{cardId}   Submit kết quả ôn (quality 0-5)
```

### Analytics
```
GET    /api/analytics          Streak, mastered, biểu đồ 30 ngày
```

---

## 🧮 SM-2 Algorithm

Hệ thống dùng [SuperMemo 2](https://en.wikipedia.org/wiki/SuperMemo#Description_of_SM-2_algorithm) để tính lịch ôn tập tối ưu.

### Đánh giá chất lượng (0-5)

| Quality | Ý nghĩa                  | Ảnh hưởng |
|---------|--------------------------|-----------|
| 0       | Quên hoàn toàn           | Reset interval về 1 ngày |
| 1       | Sai, mơ hồ               | Reset interval về 1 ngày |
| 2       | Sai, gần đúng            | Reset interval về 1 ngày |
| 3       | Đúng nhưng khó           | Tăng interval, EF giảm nhẹ |
| 4       | Đúng, hơi nghĩ           | Tăng interval, EF giữ nguyên |
| 5       | Đúng dễ dàng             | Tăng interval, EF tăng |

### Công thức

```java
// Tính easiness factor mới (tối thiểu 1.3)
EF = max(1.3, EF + 0.1 - (5-q) * (0.08 + (5-q) * 0.02))

// Tính interval (số ngày tới lần ôn tiếp theo)
if (q < 3)            interval = 1            // sai → reset
else if (rep == 0)    interval = 1
else if (rep == 1)    interval = 6
else                  interval = round(prevInterval * EF)
```

Ví dụ: nếu trả lời đúng 3 lần liên tiếp với quality=4, lịch ôn sẽ là: ngày 1 → 6 → 15 → 38 → 95 → ...

---

## 📂 Project Structure

```
flashmind/
├── docker-compose.yml
├── .env.example
├── README.md
│
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/flashmind/
│       │   ├── FlashmindApplication.java
│       │   ├── config/         # SecurityConfig, RedisConfig, WebClientConfig
│       │   ├── controller/     # Auth, Deck, Flashcard, Review, Analytics
│       │   ├── service/        # Business logic + SM-2 algorithm
│       │   ├── repository/     # JPA repositories
│       │   ├── entity/         # User, Deck, Flashcard, CardReview, StudySession
│       │   ├── dto/            # Request/Response DTOs
│       │   ├── exception/      # Custom exceptions + global handler
│       │   └── security/       # JWT utilities, filter
│       └── resources/
│           └── application.properties
│
└── frontend/
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── tailwind.config.js
    └── src/
        ├── main.tsx, App.tsx
        ├── api/                # axios + endpoint clients
        ├── types/              # TypeScript interfaces
        ├── store/              # Redux Toolkit slices
        ├── hooks/              # Custom hooks (useAppDispatch)
        ├── components/         # Reusable components
        ├── pages/              # Route-level pages
        └── utils/              # tokenStorage, formatDate
```

---

## 🧪 Test Account

Sau khi chạy, đăng ký tài khoản mới qua giao diện hoặc test bằng curl:

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"123456"}'
```

---

## 💡 Demo Flow

1. Đăng ký tài khoản tại `/register`
2. Tạo deck mới (VD: "TOEIC Vocabulary")
3. Vào chi tiết deck → upload file PDF/TXT → AI tự động sinh 10 flashcard
4. Vào trang **Ôn tập** → flip card → đánh giá độ nhớ (0-5)
5. Sau vài ngày, vào lại để ôn các thẻ đến hạn
6. Xem **Thống kê** để theo dõi streak và tiến độ

---

## ⚙️ Cấu hình

Xem [`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties) để biết các biến môi trường:

| Biến                | Mặc định                              | Mô tả |
|---------------------|---------------------------------------|-------|
| `DB_URL`            | jdbc:postgresql://localhost:5432/flashmind | PostgreSQL URL |
| `DB_USER`           | postgres                              | DB username |
| `DB_PASSWORD`       | postgres                              | DB password |
| `REDIS_HOST`        | localhost                             | Redis host |
| `REDIS_PORT`        | 6379                                  | Redis port |
| `JWT_SECRET`        | (Base64-encoded)                      | JWT signing key |
| `OPENAI_API_KEY`    | -                                     | **Bắt buộc** |

---

## 🔒 Security Notes

- Mật khẩu được hash bằng **BCrypt**
- JWT access token hết hạn sau **1 giờ**
- Refresh token hết hạn sau **7 ngày**
- File upload giới hạn **5MB**
- CORS chỉ allow `http://localhost:5173` và `http://localhost:3000` (production cần đổi)

---

## 📝 License

MIT — Free for educational and commercial use.

---

## 🙋 Author

Built by **Lâm** as a portfolio project demonstrating:
- Full-stack architecture (Spring Boot + React TypeScript)
- Spaced repetition algorithm implementation
- AI integration (OpenAI GPT-4o)
- Production-ready features (JWT auth, scheduled jobs, Redis caching, Docker deployment)
