# Cấu hình

Toàn bộ cấu hình backend nằm ở
[application.properties](../backend/src/main/resources/application.properties),
mọi giá trị đều ghi đè được bằng biến môi trường.

## Biến môi trường backend

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/flashmind` | Chuỗi kết nối PostgreSQL |
| `DB_USER` | `postgres` | Tài khoản DB |
| `DB_PASSWORD` | `postgres` | Mật khẩu DB |
| `REDIS_HOST` | `localhost` | Host Redis |
| `REDIS_PORT` | `6379` | Cổng Redis |
| `JWT_SECRET` | secret dev nhúng sẵn | Khóa ký JWT, **mã hóa Base64** |
| `OPENAI_API_KEY` | `your-api-key-here` | **Bắt buộc** để dùng chức năng sinh thẻ AI |

## Thuộc tính cố định

| Thuộc tính | Giá trị | Ý nghĩa |
|------------|---------|---------|
| `server.port` | `8080` | |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema sinh từ entity, không có migration |
| `app.jwt.access-expiration` | `3600000` | Access token sống 1 giờ |
| `app.jwt.refresh-expiration` | `604800000` | Refresh token sống 7 ngày |
| `openai.api-url` | `https://api.openai.com/v1/chat/completions` | |
| `openai.model` | `gpt-4o-mini` | |
| `spring.servlet.multipart.max-file-size` | `5MB` | Giới hạn file upload |
| `spring.servlet.multipart.max-request-size` | `5MB` | |
| `app.cors.allowed-origins` | `http://localhost:5173,http://localhost:3000` | Danh sách origin, phân tách bằng dấu phẩy |
| `logging.level.com.flashmind` | `DEBUG` | |

## Biến môi trường frontend

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `VITE_API_URL` | `/api` | Base URL của axios. Bỏ trống để đi qua proxy (Vite ở dev, nginx ở production) |

## File `.env` cho Docker Compose

`docker-compose.yml` đọc `.env` ở thư mục gốc. Tạo từ mẫu:

```bash
cp .env.example .env
```

```
OPENAI_API_KEY=sk-your-openai-api-key-here
JWT_SECRET=<chuỗi Base64>
```

Compose tự đặt `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` trỏ vào các
service nội bộ; chỉ hai biến trên là cần bạn cung cấp. `JWT_SECRET` có giá trị dự phòng
nhúng sẵn trong compose file nếu không đặt.

## Về `JWT_SECRET`

`JwtUtil` **giải Base64** giá trị này rồi mới dùng làm khóa HMAC. Vì vậy secret phải là
chuỗi Base64 hợp lệ, và sau khi giải phải đủ dài cho HS256 (tối thiểu 32 byte).

Sinh secret mới:

```bash
openssl rand -base64 48
```

## Bắt buộc kiểm tra trước khi lên production

1. **Đổi `JWT_SECRET`.** Giá trị mặc định được commit trong repo — công khai với bất kỳ ai
   đọc được mã nguồn.
2. **Đổi mật khẩu PostgreSQL.** `postgres/postgres` chỉ dành cho môi trường dev.
3. **Đặt lại `app.cors.allowed-origins`** thành domain thật; mặc định chỉ có localhost.
4. **Hạ mức log** `logging.level.com.flashmind` từ `DEBUG` xuống `INFO`.
5. Cân nhắc giới hạn `/actuator/**` — hiện endpoint này là public.
6. Nhớ rằng **không có cơ chế thu hồi token**: refresh token bị lộ vẫn dùng được đủ 7 ngày.

## Cấu hình proxy

| Môi trường | Cơ chế |
|------------|--------|
| Dev | `vite.config.ts`: `/api` → `http://localhost:8080` |
| Production | [nginx.conf](../frontend/nginx.conf): `location /api/` → `http://backend:8080`, còn lại `try_files … /index.html` cho SPA routing |

## Docker

- [backend/Dockerfile](../backend/Dockerfile): build nhiều tầng, `maven:3.9-eclipse-temurin-25`
  → `eclipse-temurin:25-jre-alpine`. Test bị bỏ qua (`-DskipTests`) trong bước build image.
- [frontend/Dockerfile](../frontend/Dockerfile): `node:20-alpine` build → `nginx:alpine`
  phục vụ thư mục `dist`.
