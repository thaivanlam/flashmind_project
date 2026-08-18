# FlashMind 🧠

> AI-powered flashcard application với Spaced Repetition (SM-2 algorithm)

Học thông minh hơn — chỉ ôn những gì bạn sắp quên. FlashMind tự động sinh flashcard từ tài liệu
PDF/TXT bằng AI và lên lịch ôn tập tối ưu dựa trên thuật toán SuperMemo 2.

## 📚 Tài liệu

Toàn bộ tài liệu của dự án nằm trong **[`docs/`](docs/README.md)**.

| | |
|---|---|
| [Kiến trúc](docs/architecture.md) | Tổng thể hệ thống, luồng dữ liệu, quyết định thiết kế |
| [Backend](docs/backend.md) | Phân tầng, service, bảo mật, scheduler, lỗi |
| [Frontend](docs/frontend.md) | Routing, Redux, tầng API, token |
| [API Reference](docs/api-reference.md) | Đặc tả đầy đủ mọi endpoint |
| [Mô hình dữ liệu](docs/data-model.md) | Entity, bảng, quy tắc xóa |
| [Thuật toán SM-2](docs/spaced-repetition.md) | Công thức và cách cài đặt |
| [Phát triển](docs/development.md) | Chạy, build, test |
| [Cấu hình](docs/configuration.md) | Biến môi trường, triển khai |

## ✨ Tính năng chính

- 🔐 **Xác thực JWT** — access token 1 giờ + refresh token 7 ngày (stateless)
- 📚 **Quản lý bộ thẻ** — tạo, sửa, xóa decks và flashcards
- 🤖 **AI Generation** — upload PDF/TXT, `gpt-4o-mini` tự động sinh flashcard
- 🧮 **SM-2 Spaced Repetition** — thuật toán SuperMemo 2 chuẩn
- 📊 **Analytics** — streak, biểu đồ 30 ngày, số thẻ đã thuộc
- ⏰ **Scheduled Job** — cache due cards và dọn dữ liệu mồ côi hằng ngày

## 🛠️ Tech Stack

**Backend** — Java 25, Spring Boot 3.5.16, Spring Security + jjwt 0.12, Spring Data JPA,
PostgreSQL 16, Spring Data Redis, Spring WebFlux (WebClient), Spring Scheduler,
Apache PDFBox, Lombok.

**Frontend** — React 19, TypeScript 5.6 (strict), Vite 6, Redux Toolkit, React Router 7,
Axios, Tailwind CSS 3.4, Chart.js, Lucide React, react-hot-toast.

**DevOps** — Docker, Docker Compose, Nginx.

## 🚀 Quick Start

```bash
cp .env.example .env          # rồi điền OPENAI_API_KEY
docker-compose up -d
docker-compose logs -f backend
```

- 🌐 Frontend: http://localhost:5173
- 🔌 Backend API: http://localhost:8080
- 🐘 PostgreSQL: localhost:5432
- 🔴 Redis: localhost:6379

Chạy local không dùng Docker, chi tiết test và build: xem
[docs/development.md](docs/development.md).

## 💡 Demo Flow

1. Đăng ký tài khoản tại `/register`
2. Tạo deck mới (VD: "TOEIC Vocabulary")
3. Vào chi tiết deck → upload file PDF/TXT → AI tự động sinh flashcard
4. Vào trang **Ôn tập** → lật thẻ → đánh giá độ nhớ (0–5)
5. Sau vài ngày, quay lại ôn các thẻ đến hạn
6. Xem **Thống kê** để theo dõi streak và tiến độ

## 🔒 Security Notes

- Mật khẩu băm bằng **BCrypt**
- JWT access token hết hạn sau **1 giờ**, refresh token sau **7 ngày**
- Refresh token là **stateless** — chưa có cơ chế logout/thu hồi
- File upload giới hạn **5MB**
- CORS mặc định chỉ cho localhost; **production bắt buộc đổi** `app.cors.allowed-origins`
  và `JWT_SECRET` (xem [docs/configuration.md](docs/configuration.md))

## 📝 License

MIT — Free for educational and commercial use.

## 🙋 Author

Built by **Lâm** as a portfolio project demonstrating full-stack architecture
(Spring Boot + React TypeScript), spaced repetition, AI integration, và các tính năng
production-ready (JWT auth, scheduled jobs, Docker deployment).
