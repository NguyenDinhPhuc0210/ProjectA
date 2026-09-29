# Sprint 1 — Nền tảng Xác thực (Core & Auth)

> **Dự án:** Book & Hobby Retailer (B2C)
> **Stack:** Spring Boot + JPA/Hibernate + JWT (JJWT) + MySQL 8.0+ + Flyway
> **Base URL:** `/api/v1`
> **Auth:** Tất cả endpoint có ký hiệu 🔒 yêu cầu header `Authorization: Bearer <token>`
> **Roles:** chỉ có `ROLE_CUSTOMER` và `ROLE_ADMIN`

---

## ⚠️ Schema bảng `users` (Flyway `V1__init_users.sql`)

| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | |
| `email` | `VARCHAR(255)` | UNIQUE, NOT NULL | Đã chuẩn hoá `trim + lowercase` |
| `password` | `VARCHAR(100)` | NULL | BCrypt hash. NULL nếu `auth_provider = GOOGLE` |
| `full_name` | `VARCHAR(100)` | NOT NULL | |
| `phone_number` | `VARCHAR(15)` | UNIQUE, **NULL** | Nullable vì tài khoản Google không có SĐT (MySQL cho phép nhiều NULL trong UNIQUE) |
| `avatar_key` | `VARCHAR(50)` | NOT NULL, default `'default'` | Key của avatar có sẵn (xem UC08) |
| `role` | `ENUM('ROLE_CUSTOMER','ROLE_ADMIN')` | NOT NULL | |
| `account_status` | `ENUM('ACTIVE','LOCKED')` | NOT NULL, default `ACTIVE` | |
| `auth_provider` | `ENUM('LOCAL','GOOGLE')` | NOT NULL, default `LOCAL` | |
| `reset_token` | `CHAR(64)` | NULL, có INDEX | **SHA-256 hash** của UUID token (không lưu token thô) |
| `reset_token_exp` | `DATETIME` | NULL | Hết hạn sau 15 phút |
| `password_changed_at` | `DATETIME` | NULL | Mọi JWT có `iat` nhỏ hơn mốc này đều bị từ chối |
| `created_at` | `DATETIME` | NOT NULL | |
| `updated_at` | `DATETIME` | NOT NULL | |
| `deleted_at` | `DATETIME` | NULL | Soft delete |

> - Không có cột `wallet_balance` (dự án B2C không có ví).
> - Không dùng `avatar_url` tự do — chỉ lưu `avatar_key` trỏ tới danh sách avatar có sẵn.
> - Không tạo thêm bảng, không dùng Redis cho reset flow.
> - Khi soft-delete user: `email` đổi thành `<email>#deleted_<id>`, `phone_number` đặt `NULL` để giải phóng khóa UNIQUE (người dùng có thể đăng ký lại bằng email/SĐT cũ).

---

## Quy ước chung

### Request/Response format

Mọi response đều bọc trong wrapper sau:

```json
// Thành công
{
  "success": true,
  "message": "Mô tả ngắn",
  "data": { }
}

// Thất bại
{
  "success": false,
  "message": "Mô tả lỗi cho user đọc",
  "errors": { "field": "chi tiết lỗi" }
}
```

### HTTP Status Code

| Status | Ý nghĩa |
|---|---|
| `200` | Thành công |
| `201` | Tạo mới thành công |
| `400` | Lỗi input / validation / business rule |
| `401` | Chưa xác thực hoặc token không hợp lệ |
| `403` | Đã xác thực nhưng không có quyền (bao gồm tài khoản `LOCKED`) |
| `404` | Không tìm thấy resource |
| `409` | Conflict — trùng dữ liệu |
| `429` | Vượt giới hạn số lần gọi (rate limit) |

### Xử lý lỗi tập trung

- `AuthenticationEntryPoint` tuỳ chỉnh → trả `401` đúng wrapper JSON.
- `AccessDeniedHandler` tuỳ chỉnh → trả `403` đúng wrapper JSON.
- `@RestControllerAdvice` xử lý: validation (`400` kèm `errors`), trùng dữ liệu (`409`), không tìm thấy (`404`), rate limit (`429`), lỗi không lường trước (`500`, **không** lộ stacktrace).

### Chuẩn hoá dữ liệu đầu vào

| Field | Quy tắc |
|---|---|
| `email` | `trim().toLowerCase()`, đúng định dạng email, tối đa 255 ký tự |
| `phoneNumber` | Chuẩn hoá về `0xxxxxxxxx` (đổi đầu `+84` thành `0`), regex `^0(3\|5\|7\|8\|9)\d{8}$` |
| `fullName` | `trim()`, 2–100 ký tự |

### Xác thực JWT trên mọi request 🔒

`JwtAuthFilter` **truy vấn lại user trong DB** ở mỗi request:

| Điều kiện | HTTP |
|---|---|
| User không tồn tại hoặc `deleted_at IS NOT NULL` | `401` |
| `account_status = LOCKED` | `403` |
| `iat` của token < `password_changed_at` (token phát hành trước khi đổi/reset mật khẩu) | `401` |

- JWT payload: `{ userId, email, role, iat, exp }`, hạn 24 giờ, **không** chứa password.
- JWT secret tối thiểu 256 bit, đọc từ biến môi trường.

### Rate limit (Bucket4j)

| Endpoint | Giới hạn | Vượt giới hạn |
|---|---|---|
| `POST /auth/login` | 5 lần sai / 15 phút theo cặp (IP + email) | `429` — "Bạn thao tác quá nhiều lần. Vui lòng thử lại sau" |
| `POST /auth/forgot-password` | 3 lần / 15 phút theo IP và theo email | `429` |
| `POST /auth/reset-password` | 10 lần / 15 phút theo IP | `429` |

### Đăng xuất (Logout)

Xử lý **hoàn toàn ở phía Client** — Frontend xóa JWT khỏi bộ nhớ. Backend **không có endpoint riêng** vì JWT là stateless. Xem UC07.

---

### Validation mật khẩu (dùng chung toàn hệ thống)

Mật khẩu hợp lệ khi **đồng thời** thỏa mãn:
- Từ 8 đến 64 ký tự (BCrypt chỉ xử lý 72 byte)
- Có ít nhất 1 chữ hoa
- Có ít nhất 1 chữ số
- Có ít nhất 1 ký tự đặc biệt (`@`, `#`, `$`, `!`, `%`, `^`, `&`, `*`, ...)

---

## UC00 — Seed Admin đầu tiên

**Actor:** System (lúc khởi động ứng dụng)
**Precondition:** Chưa có user nào `role = ROLE_ADMIN`

### API

Không có endpoint. Thực hiện bằng `ApplicationRunner`.

**Biến môi trường:**

| Biến | Mô tả |
|---|---|
| `ADMIN_EMAIL` | Email Admin |
| `ADMIN_PASSWORD` | Mật khẩu Admin (phải đạt Validation mật khẩu) |
| `ADMIN_FULL_NAME` | Họ tên Admin |

### Main Flow

1. Ứng dụng khởi động → `ApplicationRunner` chạy.
2. Kiểm tra có tồn tại user `role = ROLE_ADMIN` và `deleted_at IS NULL` hay chưa.
3. Nếu **chưa có**: đọc biến môi trường, validate, hash BCrypt, INSERT với `account_status = ACTIVE`, `auth_provider = LOCAL`, `avatar_key = default`.
4. Nếu **đã có**: bỏ qua (idempotent — chạy lại không tạo trùng).

### Exception Flow

| Mã | Điều kiện | Hành vi |
|---|---|---|
| E1 | Thiếu biến môi trường (profile `prod`) | Ứng dụng **fail-fast**, không khởi động |
| E2 | `ADMIN_PASSWORD` không đạt Validation mật khẩu (profile `prod`) | Ứng dụng **fail-fast** |
| E3 | Thiếu biến môi trường (profile `dev`) | Ghi cảnh báo, bỏ qua seed |

> **Quyết định thiết kế:**
> - Không ghi mật khẩu ra log.
> - Không có cách tạo Admin qua API — `/auth/register` luôn tạo `ROLE_CUSTOMER`.

---

## UC01 — Đăng ký tài khoản

**Actor:** Guest
**Precondition:** Chưa có tài khoản

### API

```
POST /api/v1/auth/register
Content-Type: application/json
```

**Request body:**

```json
{
  "email":       "user@example.com",
  "password":    "Abc@1234",
  "fullName":    "Nguyễn Văn A",
  "phoneNumber": "0901234567"
}
```

**Response `201`:**

```json
{
  "success": true,
  "message": "Đăng ký thành công",
  "data": {
    "userId":   1,
    "email":    "user@example.com",
    "fullName": "Nguyễn Văn A",
    "role":     "ROLE_CUSTOMER"
  }
}
```

### Main Flow

1. Guest gửi request với đầy đủ thông tin.
2. Hệ thống chuẩn hoá và validate toàn bộ input.
3. Kiểm tra trùng `email` và `phoneNumber` (chỉ xét bản ghi `deleted_at IS NULL`).
4. Hash password bằng BCrypt.
5. Lưu vào bảng `users` với `role = ROLE_CUSTOMER`, `account_status = ACTIVE`, `auth_provider = LOCAL`, `avatar_key = default`.
6. Trả về `201` với thông tin cơ bản (không tự động đăng nhập).

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Email đã tồn tại trong DB | `409` | "Email này đã được sử dụng" |
| E2 | Email sai format | `400` | "Email không đúng định dạng" |
| E3 | Password không đủ độ mạnh | `400` | "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt" |
| E4 | `fullName` để trống hoặc ngoài 2–100 ký tự | `400` | "Họ tên phải từ 2 đến 100 ký tự" |
| E5 | `phoneNumber` sai format | `400` | "Số điện thoại không hợp lệ" |
| E6 | `phoneNumber` đã tồn tại trong DB | `409` | "Số điện thoại này đã được sử dụng" |
| E7 | Race condition: DB quăng `DataIntegrityViolationException` | `409` | Message theo field bị trùng (không để lộ `500`) |

> **Quyết định thiết kế:**
> - Không yêu cầu xác thực email — tài khoản active ngay sau đăng ký.
> - Request **không có field `role`**. Nếu client gửi `role`/`accountStatus` lên thì bỏ qua (chống mass assignment). Role luôn là `ROLE_CUSTOMER`.
> - `email` và `phoneNumber` UNIQUE trong DB — Service check trước khi INSERT để trả `409`, đồng thời bắt `DataIntegrityViolationException` cho trường hợp đăng ký đồng thời.
> - Xác thực email nằm trong backlog.

---

## UC02 — Đăng nhập hệ thống

**Actor:** Guest
**Precondition:** Đã có tài khoản

### API

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Request body:**

```json
{
  "email":    "user@example.com",
  "password": "Abc@1234"
}
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGci...",
    "tokenType":   "Bearer",
    "expiresIn":   86400,
    "user": {
      "userId":    1,
      "email":     "user@example.com",
      "role":      "ROLE_CUSTOMER",
      "fullName":  "Nguyễn Văn A",
      "avatarKey": "default",
      "avatarUrl": "/static/avatars/default.png"
    }
  }
}
```

### Main Flow

1. Guest gửi email + password.
2. Kiểm tra rate limit.
3. Hệ thống tìm user theo email (đã chuẩn hoá) **VÀ `deleted_at IS NULL`** — tài khoản đã xóa mềm coi như không tồn tại.
4. Nếu `auth_provider = GOOGLE` → từ chối (E4).
5. So sánh password với BCrypt hash trong DB.
6. **Chỉ sau khi password đúng**, kiểm tra `account_status`.
7. Tạo JWT chứa `{ userId, email, role, iat, exp }`, thời hạn 24 giờ.
8. Trả về `200` kèm token.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Email không tồn tại trong DB | `401` | "Thông tin đăng nhập không chính xác" |
| E2 | Sai mật khẩu | `401` | "Thông tin đăng nhập không chính xác" |
| E3 | `deleted_at IS NOT NULL` — tài khoản đã bị xóa mềm | `401` | "Thông tin đăng nhập không chính xác" |
| E4 | `auth_provider = GOOGLE` | `400` | "Tài khoản này đăng ký qua Google. Vui lòng dùng Đăng nhập bằng Google" |
| E5 | `account_status = LOCKED` (sau khi password đúng) | `403` | "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ" |
| E6 | Vượt rate limit | `429` | "Bạn thao tác quá nhiều lần. Vui lòng thử lại sau" |

> **Quyết định thiết kế:**
> - E1, E2, E3 dùng **chung một message và status `401`** để chống user enumeration.
> - Khi không tìm thấy user, vẫn chạy một phép so sánh BCrypt giả để thời gian phản hồi không lộ email có tồn tại hay không (chống timing attack).
> - Kiểm tra `LOCKED` **sau** khi xác thực đúng password, để người lạ không dò được tài khoản nào đang bị khóa.
> - **Đánh đổi có chủ đích:** E4 và E5 tiết lộ email tồn tại (đổi lấy trải nghiệm người dùng). E4 chỉ xảy ra khi có Google Login (backlog).
> - JWT payload không chứa thông tin nhạy cảm.

---

## UC02b — Đăng nhập bằng Google *(Backlog — Sprint sau)*

**Actor:** Guest

```
POST /api/v1/auth/google
Content-Type: application/json
```

**Request body:** `{ "idToken": "<token từ Google>" }`

### Quy tắc đã chốt (để triển khai sau)

1. Backend xác thực `idToken` qua Google API và **bắt buộc `email_verified = true`**, nếu không → `400`.
2. Email **chưa tồn tại**: tự tạo user `auth_provider = GOOGLE`, `password = NULL`, `phone_number = NULL`, `role = ROLE_CUSTOMER`, `avatar_key = default`.
3. Email **đã tồn tại với `auth_provider = LOCAL`**: **không tự liên kết** — trả `409`, hướng dẫn đăng nhập bằng mật khẩu (chống account pre-hijacking, vì đăng ký LOCAL không xác thực email).
4. Email **đã tồn tại với `auth_provider = GOOGLE`**: cấp JWT chuẩn (áp dụng kiểm tra `LOCKED` và soft-delete như UC02).

---

## UC03 — Quên mật khẩu & Đặt lại mật khẩu

**Actor:** Guest
**Precondition:** Có email trong hệ thống

### API — Bước 1: Yêu cầu reset

```
POST /api/v1/auth/forgot-password
Content-Type: application/json
```

**Request body:**

```json
{
  "email": "user@example.com"
}
```

**Response `200` (luôn trả về thành công dù email có tồn tại hay không):**

```json
{
  "success": true,
  "message": "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu"
}
```

### API — Bước 2: Đặt lại mật khẩu

```
POST /api/v1/auth/reset-password
Content-Type: application/json
```

**Request body:**

```json
{
  "token":       "uuid-from-email",
  "newPassword": "NewPass@5678"
}
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại"
}
```

### Main Flow

**Bước 1:**
1. Guest gửi email.
2. Kiểm tra rate limit.
3. Hệ thống tìm user theo email và `deleted_at IS NULL` — nếu không tồn tại, vẫn trả `200`.
4. Chỉ khi user tồn tại, `auth_provider = LOCAL` và `account_status = ACTIVE`: tạo UUID token → lưu **SHA-256 hash** vào `users.reset_token` và `users.reset_token_exp = now() + 15 phút`.
5. Gửi email **bất đồng bộ (`@Async`)** chứa link: `https://yourapp.com/reset-password?token=<uuid>` (UUID thô).

**Bước 2:**
1. Guest gửi token + newPassword.
2. Hệ thống hash token nhận được (SHA-256) → query `users WHERE reset_token = ? AND deleted_at IS NULL`.
3. Kiểm tra `reset_token_exp > now()`.
4. Validate `newPassword`, hash BCrypt → cập nhật `users.password`.
5. Set `reset_token = NULL`, `reset_token_exp = NULL`, `password_changed_at = now()`.
6. Trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Email không tồn tại (Bước 1) | `200` | Vẫn trả message thành công — tránh user enumeration |
| E2 | Tài khoản `GOOGLE` hoặc `LOCKED` (Bước 1) | `200` | Vẫn trả message thành công, **không gửi mail** |
| E3 | Gửi lại forgot khi token cũ còn hạn (Bước 1) | `200` | Ghi đè token mới + gia hạn `exp` — token cũ tự động vô hiệu |
| E4 | Token không tồn tại / đã dùng / user đã bị xóa (Bước 2) | `400` | "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn" |
| E5 | Token đã hết hạn — `reset_token_exp < now()` (Bước 2) | `400` | "Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn" |
| E6 | `newPassword` không đủ độ mạnh (Bước 2) | `400` | "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt" |
| E7 | `newPassword` để trống (Bước 2) | `400` | "Mật khẩu mới không được để trống" |
| E8 | Vượt rate limit | `429` | "Bạn thao tác quá nhiều lần. Vui lòng thử lại sau" |

> **Quyết định thiết kế:**
> - Token dùng **1 lần**, hết hạn sau **15 phút**.
> - E4 và E5 dùng **chung message** — không lộ lý do cụ thể.
> - DB chỉ lưu **hash** của token — lộ DB cũng không dùng được token.
> - Gửi mail bất đồng bộ để thời gian phản hồi không tiết lộ email có tồn tại hay không.
> - Reset thành công cập nhật `password_changed_at` → mọi JWT cũ bị vô hiệu.
> - Không dùng JWT hay Redis cho reset flow.

---

## UC04 — Xem thông tin cá nhân

**Actor:** Customer 🔒, Admin 🔒
**Precondition:** Đã đăng nhập

### API

```
GET /api/v1/users/me
Authorization: Bearer <token>
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Lấy thông tin thành công",
  "data": {
    "userId":        1,
    "email":         "user@example.com",
    "fullName":      "Nguyễn Văn A",
    "phoneNumber":   "0901234567",
    "role":          "ROLE_CUSTOMER",
    "avatarKey":     "default",
    "avatarUrl":     "/static/avatars/default.png",
    "authProvider":  "LOCAL",
    "accountStatus": "ACTIVE",
    "createdAt":     "2026-01-01T10:00:00"
  }
}
```

### Main Flow

1. Client gửi request kèm JWT.
2. `JwtAuthFilter` validate token → extract `userId` → truy vấn lại user (xem mục *Xác thực JWT*).
3. Trả về thông tin user.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có header `Authorization` | `401` | "Yêu cầu xác thực" |
| E2 | JWT sai chữ ký hoặc sai format | `401` | "Token không hợp lệ" |
| E3 | JWT đã hết hạn | `401` | "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại" |
| E4 | User trong token không tồn tại hoặc đã soft-delete | `401` | "Token không hợp lệ" |
| E5 | `iat` < `password_changed_at` | `401` | "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại" |
| E6 | `account_status = LOCKED` | `403` | "Tài khoản đã bị khóa. Vui lòng liên hệ hỗ trợ" |

> **Quyết định thiết kế:**
> - **Không** trả về `password`, `reset_token`, `reset_token_exp`, `deleted_at`, `password_changed_at` trong bất kỳ trường hợp nào.
> - `avatarUrl` được **suy ra** từ `avatarKey` (không lưu trong DB).
> - E4 dùng `401` (không dùng `404`) để Frontend tự chuyển về trang đăng nhập khi tài khoản đã bị xóa.

---

## UC05 — Cập nhật thông tin cá nhân

**Actor:** Customer 🔒, Admin 🔒
**Precondition:** Đã đăng nhập

### API

```
PATCH /api/v1/users/me
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body** (chỉ gửi field muốn thay đổi — partial update):

```json
{
  "fullName":    "Nguyễn Văn B",
  "phoneNumber": "0909999999",
  "avatarKey":   "avatar_03"
}
```

**Response `200`:** Trả về toàn bộ thông tin user sau khi cập nhật (cùng format UC04).

### Main Flow

1. Client gửi request với các field cần cập nhật.
2. Hệ thống chuẩn hoá và validate từng field **có mặt** trong request.
3. Chỉ update các field có trong request — field không gửi giữ nguyên giá trị cũ.
4. Cập nhật `updated_at = now()`.
5. Trả về `200` với data đã cập nhật.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | JWT không hợp lệ / hết hạn / user bị khóa | `401` / `403` | (như UC04 — E1 đến E6) |
| E2 | `fullName` gửi lên là `null` hoặc chuỗi rỗng | `400` | "Họ tên phải từ 2 đến 100 ký tự" |
| E3 | `phoneNumber` sai format hoặc gửi `null` | `400` | "Số điện thoại không hợp lệ" |
| E4 | `avatarKey` không nằm trong danh sách avatar có sẵn | `400` | "Ảnh đại diện không hợp lệ" |
| E5 | `phoneNumber` đã tồn tại ở user khác | `409` | "Số điện thoại này đã được sử dụng" |

> **Quyết định thiết kế:**
> - Dùng `PATCH` (partial update) thay vì `PUT`.
> - Phân biệt "không gửi field" (giữ nguyên) và "gửi `null`": `fullName`/`phoneNumber` = `null` → `400`; `avatarKey = null` → đặt lại `default`.
> - Các field `email`, `role`, `authProvider`, `accountStatus`, `avatarUrl` **bị bỏ qua** nếu client gửi lên.
> - Khi check trùng `phoneNumber` (E5), loại trừ chính user hiện tại — `WHERE phone_number = ? AND id != currentUserId AND deleted_at IS NULL`.
> - **Không hỗ trợ upload avatar** — người dùng chỉ chọn từ danh sách có sẵn (UC08).

---

## UC06 — Đổi mật khẩu

**Actor:** Customer 🔒, Admin 🔒
**Precondition:** Đã đăng nhập, biết mật khẩu hiện tại, `auth_provider = LOCAL`

### API

```
PATCH /api/v1/users/me/password
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**

```json
{
  "currentPassword": "Abc@1234",
  "newPassword":     "NewPass@5678"
}
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Đổi mật khẩu thành công",
  "data": {
    "accessToken": "eyJhbGci...",
    "tokenType":   "Bearer",
    "expiresIn":   86400
  }
}
```

### Main Flow

1. Client gửi `currentPassword` + `newPassword`.
2. Hệ thống lấy user từ JWT → query DB lấy `password` hash.
3. Kiểm tra `auth_provider = LOCAL`.
4. Kiểm tra `currentPassword` khớp với hash bằng BCrypt.
5. Kiểm tra `newPassword` đạt độ mạnh và khác `currentPassword`.
6. Hash `newPassword` → lưu DB, cập nhật `password_changed_at = now()` và `updated_at`.
7. Cấp **JWT mới** và trả về `200`. Frontend thay token cũ bằng token mới, phiên hiện tại tiếp tục; mọi token cũ bị vô hiệu.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | JWT không hợp lệ / hết hạn / user bị khóa | `401` / `403` | (như UC04 — E1 đến E6) |
| E2 | `auth_provider = GOOGLE` | `400` | "Tài khoản đăng nhập bằng Google không có mật khẩu để đổi" |
| E3 | `currentPassword` không đúng | `400` | "Mật khẩu hiện tại không chính xác" |
| E4 | `newPassword` trùng với mật khẩu cũ | `400` | "Mật khẩu mới không được trùng mật khẩu cũ" |
| E5 | `newPassword` không đủ độ mạnh | `400` | "Mật khẩu phải từ 8 đến 64 ký tự, gồm chữ hoa, số và ký tự đặc biệt" |
| E6 | `currentPassword` hoặc `newPassword` để trống | `400` | "Vui lòng nhập đầy đủ thông tin" |

> **Quyết định thiết kế:**
> - Dùng HTTP `400` (không phải `401`) cho E3 vì user đã authenticated — đây là lỗi input; dùng `401` sẽ khiến Frontend tự đăng xuất nhầm (Axios interceptor).
> - Cấp JWT mới ngay trong response để người dùng không bị đăng xuất sau khi đổi mật khẩu, trong khi các token cũ (ví dụ trên thiết bị khác) bị vô hiệu nhờ `password_changed_at`.

---

## UC07 — Đăng xuất

**Actor:** Customer, Admin
**Precondition:** Đã đăng nhập

### API

**Không có endpoint backend.**

### Main Flow

1. User bấm "Đăng xuất" trên Frontend.
2. **Frontend** xóa JWT khỏi bộ nhớ (LocalStorage / Cookie / Memory) và xóa state trong `AuthContext`.
3. Frontend redirect user về trang đăng nhập.

### Exception Flow

Không có.

> **Quyết định thiết kế:**
> - Backend **stateless** — không lưu danh sách token đã logout (không blacklist, không Redis).
> - Token vẫn hợp lệ về mặt kỹ thuật cho đến khi hết hạn (24h), **trừ khi** xảy ra một trong các sự kiện sau, khi đó `JwtAuthFilter` sẽ từ chối: đổi mật khẩu, reset mật khẩu, khóa tài khoản, xóa tài khoản.
> - Thu hồi token thật sự (blacklist / refresh token) nằm trong backlog.

---

## UC08 — Xem danh sách avatar có sẵn

**Actor:** Guest, Customer, Admin (public)
**Precondition:** Không

### API

```
GET /api/v1/avatars
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Lấy danh sách avatar thành công",
  "data": [
    { "key": "default",   "url": "/static/avatars/default.png" },
    { "key": "avatar_01", "url": "/static/avatars/avatar_01.png" },
    { "key": "avatar_02", "url": "/static/avatars/avatar_02.png" }
  ]
}
```

### Main Flow

1. Client gọi endpoint.
2. Backend trả danh sách avatar từ cấu hình cố định (enum hoặc file cấu hình) — **không truy vấn DB**.
3. Frontend hiển thị lưới avatar; user chọn một avatar và lưu bằng `PATCH /users/me` với `avatarKey` (UC05).

### Exception Flow

Không có.

> **Quyết định thiết kế:**
> - Ảnh avatar được host tĩnh; DB chỉ lưu `avatar_key`.
> - Người dùng không thể đưa URL tự do vào hệ thống (tránh URL độc hại / tracking).
> - Danh sách cụ thể (số lượng, tên file, nơi host) sẽ chốt sau — xem *Điểm còn mở*.

---

## Tổng hợp API — Sprint 1

| UC | Method | Endpoint | Auth | Roles |
|---|---|---|---|---|
| UC00 | — | *(ApplicationRunner, không có endpoint)* | — | System |
| UC01 | `POST` | `/api/v1/auth/register` | ❌ | Guest |
| UC02 | `POST` | `/api/v1/auth/login` | ❌ | Guest |
| UC02b | `POST` | `/api/v1/auth/google` | ❌ | Guest *(Backlog)* |
| UC03a | `POST` | `/api/v1/auth/forgot-password` | ❌ | Guest |
| UC03b | `POST` | `/api/v1/auth/reset-password` | ❌ | Guest |
| UC04 | `GET` | `/api/v1/users/me` | 🔒 JWT | Customer, Admin |
| UC05 | `PATCH` | `/api/v1/users/me` | 🔒 JWT | Customer, Admin |
| UC06 | `PATCH` | `/api/v1/users/me/password` | 🔒 JWT | Customer, Admin |
| UC07 | — | *(chỉ Frontend, không có endpoint)* | — | Customer, Admin |
| UC08 | `GET` | `/api/v1/avatars` | ❌ | Public |

> **Đối chiếu với spec tổng:** UC04 + UC05 trong file này tương ứng UC-04 (View & Update Profile); UC06 = UC-04b; UC07 = UC-04c; UC08 = UC-04a.

---

## Frontend — Sprint 1

- **Trang:** Register, Login, Forgot Password, Reset Password (đọc `token` từ query string), Profile (xem/sửa thông tin, chọn avatar, đổi mật khẩu).
- **`AuthContext`:** giữ user + token; cung cấp `login`, `logout`, `updateUser`.
- **Route guard:** `ProtectedRoute` (cần đăng nhập) và `AdminRoute` (cần `ROLE_ADMIN`).
- **Axios interceptor:** gắn header `Authorization`. `401` → xóa token, về Login. `403` → thông báo tài khoản bị khóa. `429` → thông báo thử lại sau.
- **Validate form phía client** theo đúng Validation mật khẩu và regex số điện thoại để báo lỗi sớm.

---

## Hạ tầng & công cụ — Sprint 1

Flyway (migration), Swagger/OpenAPI (springdoc), Docker Compose cho MySQL, profile `dev` / `prod`, CORS cấu hình theo biến môi trường, unit test cho `AuthService` (register, các nhánh login, reset token hết hạn / dùng lại).

---

## Gợi ý thứ tự implement

1. Flyway `V1__init_users.sql` + `User` entity + `UserRepository` — nền tảng của mọi thứ
2. `GlobalExceptionHandler` + `AuthenticationEntryPoint` + `AccessDeniedHandler` — để mọi lỗi đúng wrapper ngay từ đầu
3. `JwtUtil` + `JwtAuthFilter` + `SecurityConfig` — unblock toàn bộ endpoint có 🔒
4. UC00 (seed Admin) + UC01 + UC02 — test được end-to-end bằng Postman
5. UC04 + UC05 + UC06 + UC08 — đơn giản, làm nhanh sau khi có JWT filter
6. Rate limit (Bucket4j) cho login / forgot / reset
7. UC03 — làm cuối vì cần config Spring Mail (`spring.mail.*`)
8. Frontend: `AuthContext` → Register/Login → Profile → Forgot/Reset

---

## Điểm còn mở (chốt cùng Sprint 1)

1. Cách lưu token phía Frontend: `localStorage` (đơn giản, rủi ro XSS) hay httpOnly cookie (an toàn hơn, cần cấu hình CORS/CSRF).
2. Danh sách avatar cụ thể: số lượng, tên file, host ở Backend hay Frontend.
3. Ngưỡng rate limit cuối cùng (bảng trên là đề xuất).
4. Nhà cung cấp email (Gmail SMTP, SendGrid, ...).
5. Có cần refresh token ngay Sprint 1 hay để backlog.

## Backlog

- Admin khóa / mở khóa user (`LOCKED`) và xem danh sách user *(hiện chưa có UC nào đặt được trạng thái LOCKED — tạm sửa trực tiếp trong DB khi cần)*.
- Refresh token / blacklist để thu hồi token thật sự.
- Xác thực email khi đăng ký.
- Đăng nhập bằng Google (UC02b).
