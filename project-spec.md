# PROJECT SPECIFICATION: BOOK & HOBBY RETAILER
*(B2C E-commerce Model - Quản lý tồn kho tập trung)*

> Tài liệu tổng quan. Luồng chi tiết, request/response mẫu, bảng Exception Flow và quy tắc validate nằm trong từng file sprint:
> - Sprint 1 → `sprint1-auth.md` (UC01 – UC08 trong file sprint)
> - Sprint 2 → `sprint2-catalog.md` (UC10 – UC18 trong file sprint)
> - Database → `data-schema.md`
>
> Mã `UC-xx` ở đây tương ứng `UCxx` trong file sprint.

---

## 1. SYSTEM OVERVIEW

### Vai trò & Luồng nghiệp vụ
* **Roles:** Hệ thống chỉ có đúng 2 role: `ROLE_CUSTOMER` và `ROLE_ADMIN`. Tuyệt đối không có role nào khác.
* **Mô hình B2C:** Hệ thống hoạt động như nhà bán lẻ trực tiếp (giống Barnes & Noble hoặc Kinokuniya). Người dùng không thể tạo cửa hàng hay đăng sản phẩm. Toàn bộ chuỗi cung ứng (duyệt sản phẩm, giá, tồn kho) do Admin kiểm soát.
* **Luồng mua hàng:** Khách xem sản phẩm -> Thêm vào giỏ -> Đặt hàng (hệ thống giữ tồn kho tạm thời) -> Thanh toán (COD/Online) -> Admin xác nhận -> Giao hàng & hoàn tất.
* **Dữ liệu đa hình (Tech Flex):** Dùng cột kiểu JSON để lưu thuộc tính động. Nhờ đó Sách (Tác giả, NXB, Số trang, ISBN) và Đồ sưu tầm/Hobby (Hãng, Tỷ lệ, Chất liệu) nằm chung một bảng `products`, không dư thừa dữ liệu và không dùng mô hình EAV.

### Tech Stack
* **Backend:** Java, Spring Boot (Web, Data JPA, Security), JWT (JJWT), MySQL 8.0+, Flyway.
  Kiến trúc bắt buộc: Controller -> Service -> Repository. Base URL: `/api/v1`.
* **Frontend:** ReactJS (Vite), React Router DOM, Axios, Ant Design / Tailwind CSS.
* **Hỗ trợ:** springdoc-openapi (Swagger), Bucket4j (rate limit), `JavaMailSender` (SMTP), Docker Compose (MySQL), profile `dev` / `prod`.

### Quy ước kỹ thuật chung

**Response Wrapper (dùng cho MỌI endpoint):**
```
Thành công: { "success": true,  "message": "...", "data": {...} }
Lỗi:        { "success": false, "message": "...", "errors": {"field": "..."} }
```

**HTTP Status Codes:**

| Code | Ý nghĩa |
|---|---|
| 200 OK | Thành công. |
| 201 Created | Tạo mới thành công. |
| 400 Bad Request | Lỗi input / validation / vi phạm business rule. |
| 401 Unauthorized | Chưa xác thực hoặc token không hợp lệ / hết hạn. |
| 403 Forbidden | Đã xác thực nhưng không có quyền (gồm tài khoản LOCKED). |
| 404 Not Found | Resource không tồn tại. |
| 409 Conflict | Trùng dữ liệu (vd: email đã tồn tại). |
| 429 Too Many Requests | Vượt giới hạn rate limit. |

**Xác thực (Authentication):**
* Endpoint có ký hiệu [🔒] yêu cầu header `Authorization: Bearer <JWT>`.
* JWT payload gồm `{ userId, email, role, iat, exp }`, hạn 24 giờ, **KHÔNG** chứa password.
* Mỗi request [🔒] hệ thống kiểm tra lại user trong DB: đã xóa mềm → `401`; `LOCKED` → `403`; token phát hành trước lần đổi/reset mật khẩu gần nhất → `401`.
* Toàn bộ `/api/v1/admin/**` chỉ dành cho `ROLE_ADMIN`.

**Password Policy (toàn hệ thống):**
Từ 8 đến 64 ký tự, có ít nhất 1 chữ hoa, 1 chữ số và 1 ký tự đặc biệt (`@`, `#`, `$`, `!`, ...).

**Soft Delete:**
Các bảng lõi (`users`, `products`, `categories`) có cột `deleted_at`. Mọi câu SELECT phải kèm `WHERE deleted_at IS NULL`. Bản ghi đã xóa mềm coi như không tồn tại. TUYỆT ĐỐI không dùng lệnh `DELETE` SQL.

**Quy tắc xuyên suốt khác:**
* **Timezone:** Sử dụng chuẩn UTC ở mọi tầng (Database, Backend, JSON Response thêm chữ `Z`). Frontend tự xử lý hiển thị giờ Local.
* Chuẩn hoá input (email chữ thường, SĐT về dạng `0xxxxxxxxx`, SKU chữ hoa) và xử lý lỗi tập trung, luôn trả JSON đúng wrapper.
* Rate limit cho `login`, `forgot-password`, `reset-password`.
* Avatar chỉ chọn từ danh sách có sẵn (không upload); DB lưu `avatar_key`.
* **Tiền tệ:** Dùng `DECIMAL(15,0)` / `BigDecimal` (làm tròn `HALF_UP`), không dùng số thập phân. API danh sách có phân trang (`page`, `size`).

---

## 2. QUY TRÌNH MUA HÀNG & QUẢN LÝ TỒN KHO

* **Giỏ hàng LocalStorage:** Khách (Guest/Customer) duyệt và thêm sản phẩm vào giỏ. Dữ liệu giỏ hàng lưu ở LocalStorage của trình duyệt. Khi vào trang Checkout, client gọi API `/cart/validate` để backend kiểm tra tồn kho và giá mới nhất.
* **Trừ giữ hàng (`reserved_quantity`) chống Deadlock:** Khi khách click "Đặt hàng", hệ thống gọi câu lệnh Native SQL `UPDATE products SET reserved_quantity = reserved_quantity + ? WHERE id = ? AND (stock_quantity - reserved_quantity) >= ?`. Nếu thành công, đơn hàng ở trạng thái `PENDING`. Hàng chỉ bị trừ thẳng khỏi `stock_quantity` khi hoàn thành giao hàng.
* **Hủy đơn tự động:** Có cron job chạy ngầm, quét các đơn `PENDING` không xác nhận hoặc chưa thanh toán sau 24h để chuyển sang `CANCELLED` và trả lại tồn (`UPDATE products SET reserved_quantity = reserved_quantity - ? WHERE id = ?`).
* **Snapshot địa chỉ và giá:** Giá bán và địa chỉ giao hàng tại thời điểm mua được lưu cứng (snapshot) vào bảng `order_items` và `orders`. Nếu sản phẩm đổi giá sau này, lịch sử đơn hàng cũ không bị ảnh hưởng.

---

## 3. DETAILED USE CASES — SPRINT 1 & 2

### GROUP 1: AUTH & ACCOUNT (SPRINT 1)

#### UC-00 — Seed Admin đầu tiên
**Actor:** System | **Endpoint:** *(không có — `ApplicationRunner` khi khởi động)*

* Khi ứng dụng khởi động, nếu **chưa tồn tại** user nào `ROLE_ADMIN` thì tự tạo một Admin từ biến môi trường `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME`.
* Chạy lại nhiều lần không tạo trùng (idempotent).
* `ADMIN_PASSWORD` phải đạt Password Policy và được hash BCrypt; không ghi mật khẩu ra log.
* Ở profile `prod`, thiếu biến môi trường hoặc mật khẩu không hợp lệ → ứng dụng **fail-fast**, không khởi động.
* **Không có cách tạo Admin qua API** — `/auth/register` luôn tạo `ROLE_CUSTOMER`.

---

#### UC-01 — Đăng ký tài khoản
**Actor:** Guest | **Endpoint:** `POST /api/v1/auth/register`

* Guest gửi: `email`, `password`, `fullName`, `phoneNumber`.
* Hệ thống luôn gán `role = ROLE_CUSTOMER`. Mọi giá trị `role`/`status` client gửi lên đều bị bỏ qua (chống mass assignment). `ROLE_ADMIN` **không thể đăng ký qua API**.
* Tài khoản active ngay sau đăng ký, **không cần xác thực email**.
* `email` và `phoneNumber` phải UNIQUE. Service phải check trùng trước INSERT để trả `409`, đồng thời bắt lỗi ràng buộc DB (đăng ký đồng thời) và cũng trả `409`, không để lộ `500`.
* Password được hash bằng **BCrypt** trước khi lưu.
* Tài khoản mới: `account_status = ACTIVE`, `auth_provider = LOCAL`, `avatar_key = default`.
* Thành công trả `201`, không tự động đăng nhập.

---

#### UC-02 — Đăng nhập
**Actor:** Guest | **Endpoint:** `POST /api/v1/auth/login`

* Guest gửi `email` + `password`, nhận về JWT và thông tin user cơ bản nếu hợp lệ.
* Tài khoản đăng ký qua Google (`auth_provider = GOOGLE`) → HTTP `400`, hướng dẫn dùng Đăng nhập bằng Google.
* Tài khoản `account_status = LOCKED` → HTTP `403`, chỉ kiểm tra **sau khi** mật khẩu đúng.
* Tài khoản đã soft-delete (`deleted_at IS NOT NULL`) → HTTP `401`.
* **Bảo vệ User Enumeration:** email không tồn tại, sai mật khẩu, tài khoản đã xóa mềm dùng **chung một message và status `401`**; khi không tìm thấy user vẫn chạy so sánh BCrypt giả để chống timing attack.
* **Rate limit:** tối đa 5 lần sai / 15 phút theo cặp (IP + email), vượt → `429`.

---

#### UC-02b — Đăng nhập bằng Google *(Backlog — Sprint sau)*
**Actor:** Guest | **Endpoint:** `POST /api/v1/auth/google`

* Guest gửi `idToken` nhận từ Google; Backend xác thực qua Google API và **bắt buộc `email_verified = true`**.
* Email chưa tồn tại → tự tạo user `auth_provider = GOOGLE`, `password = NULL`, `phone_number = NULL`, `role = ROLE_CUSTOMER`.
* Email đã tồn tại với `auth_provider = LOCAL` → **không tự liên kết**, trả `409` (chống account pre-hijacking).
* Email đã tồn tại với `auth_provider = GOOGLE` → cấp JWT chuẩn (áp dụng kiểm tra LOCKED / soft-delete như UC-02).

---

#### UC-03 — Quên mật khẩu & Đặt lại mật khẩu
**Actor:** Guest | **Endpoints:** `POST /api/v1/auth/forgot-password` + `POST /api/v1/auth/reset-password`

* Luồng 2 bước: **(1) Yêu cầu reset** → **(2) Đặt lại bằng token**.
* **Lưu trữ token:** sinh UUID, lưu **hash SHA-256** vào `users.reset_token` + `users.reset_token_exp`. **Không dùng Redis, không dùng JWT** cho flow này.
* Token hết hạn sau **15 phút**, chỉ dùng **1 lần** — reset thành công thì cả 2 cột về `NULL`.
* Gửi lại khi token cũ còn hạn → ghi đè token mới, token cũ tự vô hiệu.
* Bước 1 luôn trả HTTP `200` dù email có tồn tại hay không → tránh user enumeration. Tài khoản `GOOGLE` hoặc `LOCKED` vẫn trả `200` nhưng **không gửi mail**.
* Email gửi **bất đồng bộ** (`@Async`) để thời gian phản hồi không lộ email có tồn tại. Link: `https://[domain]/reset-password?token=<uuid>`. Cần cấu hình **Spring Mail**.
* Reset thành công cập nhật `password_changed_at` → mọi JWT cũ bị vô hiệu.
* Áp dụng rate limit cho cả hai bước.

---

#### UC-04 — Xem thông tin cá nhân
**Actor:** Customer 🔒, Admin 🔒 | **Endpoint:** `GET /api/v1/users/me`

* Trả thông tin user hiện tại dựa trên `userId` trong JWT: `email`, `fullName`, `phoneNumber`, `role`, `avatarKey` + `avatarUrl`, `authProvider`, `accountStatus`, `createdAt`.
* **KHÔNG** bao giờ trả `password`, `reset_token`, `reset_token_exp`, `deleted_at`, `password_changed_at`.
* User trong token đã bị xóa mềm hoặc token phát hành trước lần đổi mật khẩu → `401`; tài khoản `LOCKED` → `403`.

---

#### UC-05 — Cập nhật thông tin cá nhân
**Actor:** Customer 🔒, Admin 🔒 | **Endpoint:** `PATCH /api/v1/users/me`

* Dùng **PATCH** (partial update) — chỉ xử lý các field có mặt trong body: `fullName`, `phoneNumber`, `avatarKey`.
* Không gửi field → giữ nguyên. `fullName`/`phoneNumber` gửi `null` → `400`. `avatarKey = null` → đặt lại `default`.
* `avatarKey` phải nằm trong danh sách avatar có sẵn (UC-08), sai → `400`.
* **Không cho phép** đổi `email`, `role`, `auth_provider`, `account_status` qua endpoint này.
* Khi check trùng `phoneNumber` phải loại trừ chính user hiện tại; trùng với user khác → `409`.

---

#### UC-06 — Đổi mật khẩu
**Actor:** Customer 🔒, Admin 🔒 | **Endpoint:** `PATCH /api/v1/users/me/password`

* Gửi `currentPassword` + `newPassword`. Chỉ dành cho tài khoản `LOCAL`; tài khoản Google → `400`.
* `currentPassword` sai → `400` (không dùng `401` để Frontend không tự đăng xuất nhầm).
* `newPassword` phải đạt Password Policy và **khác** mật khẩu hiện tại.
* Thành công: lưu hash mới, cập nhật `password_changed_at`, trả `200` kèm **JWT mới** để phiên hiện tại tiếp tục; các token cũ bị vô hiệu.

---

#### UC-07 — Đăng xuất
**Actor:** Customer, Admin | **Endpoint:** *(không có — xử lý ở Frontend)*

* JWT là stateless: đăng xuất = Frontend xóa token khỏi bộ nhớ, xóa state trong `AuthContext` và chuyển về trang Đăng nhập.
* Backend không lưu blacklist (không dùng Redis). Token tự vô hiệu khi đổi/reset mật khẩu, khóa hoặc xóa tài khoản.
* Thu hồi token phía server (blacklist / refresh token) nằm trong backlog.

---

#### UC-08 — Xem danh sách avatar có sẵn
**Actor:** Public | **Endpoint:** `GET /api/v1/avatars`

* Trả danh sách avatar cố định (`key`, `url`) từ cấu hình backend, **không truy vấn DB**.
* Hệ thống **không hỗ trợ upload avatar**; người dùng chọn một avatar rồi lưu qua UC-05 bằng `avatarKey`.
* DB chỉ lưu `avatar_key` (không lưu URL tự do) để tránh URL độc hại.

---

### GROUP 2: CATALOG & POLYMORPHIC PRODUCTS (SPRINT 2)

#### UC-10 — Xem danh sách danh mục (Public)
**Actor:** Public | **Endpoint:** `GET /api/v1/categories`

* Trả toàn bộ danh mục chưa xóa để Frontend dựng menu/sidebar, sắp xếp theo tên, không phân trang.
* Mỗi danh mục kèm `productCount` — số sản phẩm **đang hiển thị** (`ACTIVE`, chưa xóa).
* Danh mục không có sản phẩm hiển thị vẫn được trả (`productCount = 0`).

---

#### UC-11 — Quản lý danh mục
**Actor:** Admin 🔒 | **Endpoints:** CRUD tại `/api/v1/admin/categories`

* Admin có thể Tạo, Xem (danh sách + chi tiết), Cập nhật và Xóa danh mục (vd: Literature, Boardgames, Figures). Danh mục là **phẳng**, không có cây cha–con.
* `name` phải UNIQUE (so khớp không phân biệt hoa thường và dấu); trùng → `409`.
* Xóa là **Soft Delete**: đặt `deleted_at` và đổi `name` thành `<name>#deleted_<id>` để giải phóng khóa UNIQUE.
* **Business Rule:** không được xóa danh mục còn sản phẩm chưa xóa (**kể cả sản phẩm `HIDDEN`**) → `400`.
* Không có token → `401`; token của Customer → `403`.

---

#### UC-12 — Tạo sản phẩm (Đa hình)
**Actor:** Admin 🔒 | **Endpoint:** `POST /api/v1/admin/products`

* Admin gửi các field lõi: `sku` (UNIQUE), `name`, `description`, `basePrice`, `categoryId`, `productType` (`BOOK` hoặc `HOBBY`), `thumbnailUrl` và JSON `attributes`.
* **Xử lý JSON Attributes (Critical):**
  - `BOOK`: `attributes` phải có đúng `{author, publisher, pages, isbn}` (`isbn` phải đúng ISBN-10/13, kiểm tra checksum).
  - `HOBBY`: `attributes` phải có đúng `{brand, scale, material}` (`scale` dạng `1/7`, hoặc `N/A`).
  - Kiểm tra kiểu và giá trị từng key; **key lạ → `400`**.
* `stock_quantity` khởi tạo bằng `0` (tồn kho quản lý riêng qua `inventory_batches` ở Sprint sau); `status` mặc định `ACTIVE`.
* `categoryId` phải tồn tại và chưa xóa, nếu không → `400`.
* Trả `409` nếu `sku` đã tồn tại — **kể cả khi sản phẩm mang sku đó đã bị xóa mềm**.

---

#### UC-13 — Xem danh sách & chi tiết sản phẩm (Admin)
**Actor:** Admin 🔒 | **Endpoints:** `GET /api/v1/admin/products` + `GET /api/v1/admin/products/{id}`

* Danh sách có phân trang, lọc theo `keyword` (tên hoặc sku), `categoryId`, `productType`, `status`, khoảng giá; sắp xếp mới nhất / giá tăng / giá giảm.
* Admin thấy **cả sản phẩm `HIDDEN`** và `stockQuantity`, `version`; sản phẩm đã xóa mềm coi như không tồn tại (`404`).
* Tham số sai (`minPrice > maxPrice`, `sort` lạ, `size` ngoài 1–50...) → `400`.

---

#### UC-14 — Cập nhật sản phẩm
**Actor:** Admin 🔒 | **Endpoint:** `PUT /api/v1/admin/products/{id}`

* Admin được sửa: `name`, `description`, `basePrice`, `categoryId`, `thumbnailUrl`, `attributes`.
* **Không được sửa qua endpoint này:** `sku`, `productType`, `stockQuantity`, `status`. `attributes` được validate theo `productType` **hiện có** của sản phẩm.
* Bắt buộc gửi kèm `version`; không khớp (người khác vừa sửa) → `409` (optimistic locking).
* Đổi `base_price` **không** ảnh hưởng đơn cũ — đơn hàng sẽ lưu snapshot giá ở Sprint sau.

---

#### UC-15 — Ẩn / hiện sản phẩm
**Actor:** Admin 🔒 | **Endpoint:** `PATCH /api/v1/admin/products/{id}/status`

* Admin đổi `status` giữa `ACTIVE` và `HIDDEN` để tạm ngừng/bán lại mà không xóa.
* Sản phẩm `HIDDEN` biến mất khỏi API public (UC-17, UC-18) nhưng Admin vẫn thấy ở UC-13.
* Đặt lại đúng trạng thái hiện tại vẫn trả `200`.

---

#### UC-16 — Xóa sản phẩm
**Actor:** Admin 🔒 | **Endpoint:** `DELETE /api/v1/admin/products/{id}`

* Thực hiện **Soft Delete** (`deleted_at = NOW()`); **không** đổi `sku`.
* Sản phẩm phải được xóa mềm để các đơn hàng lịch sử tham chiếu `product_id` không bị vỡ.
* Sản phẩm đã xóa coi như không tồn tại với mọi API (kể cả Admin); xóa lại → `404`.

---

#### UC-17 — Tìm kiếm & Lọc sản phẩm
**Actor:** Public | **Endpoint:** `GET /api/v1/products`

* Lọc động qua query params: `categoryId`, `productType`, `minPrice`, `maxPrice`, `keyword` (khớp tên sản phẩm, không phân biệt hoa thường và dấu).
* Sắp xếp: mới nhất (mặc định), giá tăng dần, giá giảm dần. Có phân trang (`page`, `size`).
* **Filter bắt buộc:** chỉ trả sản phẩm `deleted_at IS NULL` **và** `status = ACTIVE`.
* Danh sách trả DTO nhẹ (không có `attributes`) kèm `inStock`; không trả `sku`, `stockQuantity`, `status`, `version`.
* Không có kết quả → `200` với danh sách rỗng.

---

#### UC-18 — Xem chi tiết sản phẩm
**Actor:** Public | **Endpoint:** `GET /api/v1/products/{id}`

* Trả đầy đủ chi tiết sản phẩm, gồm object JSON `attributes` và thông tin danh mục.
* Sản phẩm không tồn tại, đã xóa mềm hoặc đang `HIDDEN` → `404` **cùng một message**.
* **Logic Frontend (Ghi chú):** UI đọc `productType` để hiển thị có điều kiện (Bảng thông số Sách hoặc Bảng thông số Hobby).

---

## 3. BẢNG ĐỐI CHIẾU MÃ UC

| Spec gốc | Mã trong tài liệu này |
|---|---|
| UC-01 Register | UC-01 |
| UC-02 Login | UC-02 |
| UC-02b Google Login | UC-02b |
| UC-03 Forgot & Reset Password | UC-03 |
| UC-04 View & Update Profile | UC-04 + UC-05 |
| *(mới)* Change Password / Logout / Seed Admin / Avatars | UC-06 / UC-07 / UC-00 / UC-08 |
| UC-05 Manage Categories | UC-11 *(+ UC-10 danh sách public)* |
| UC-06 Create Product | UC-12 |
| UC-07 Update & Delete Product | UC-14 + UC-16 *(+ UC-15 ẩn/hiện)* |
| UC-08 Search & Filter Products | UC-17 |
| UC-09 View Product Details | UC-18 *(+ UC-13 Admin xem)* |

---

## 4. BACKLOG (ngoài Sprint 1 & 2)

* Admin khóa/mở khóa user và xem danh sách user.
* Refresh token / blacklist để thu hồi token thật sự.
* Xác thực email khi đăng ký.
* Đăng nhập bằng Google (UC-02b).
* Upload ảnh / gallery nhiều ảnh cho sản phẩm.
* Tồn kho (`inventory_batches`), giỏ hàng, đơn hàng, thanh toán, đánh giá (các Sprint sau).
