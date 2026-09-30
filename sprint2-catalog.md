# Sprint 2 — Danh mục & Sản phẩm đa hình (Catalog & Polymorphic Products)

> **Dự án:** Book & Hobby Retailer (B2C)
> **Stack:** Spring Boot + JPA/Hibernate + MySQL 8.0+ (cột JSON) + Flyway
> **Base URL:** `/api/v1`
> **Auth:** Endpoint có ký hiệu 🔒 yêu cầu header `Authorization: Bearer <token>`. Toàn bộ `/api/v1/admin/**` chỉ dành cho `ROLE_ADMIN`.
> **Phụ thuộc:** Sprint 1 (User, JWT, `JwtAuthFilter`, wrapper response, xử lý lỗi tập trung)

---

## ⚠️ Thay đổi Schema (Flyway `V2__catalog.sql`)

### 1. Bảng `categories`

| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | |
| `name` | `VARCHAR(160)` | UNIQUE, NOT NULL | Tên hiển thị 2–100 ký tự. Cột rộng 160 để chứa hậu tố `#deleted_<id>` khi xóa mềm |
| `description` | `VARCHAR(500)` | NULL | |
| `created_at` | `DATETIME` | NOT NULL | |
| `updated_at` | `DATETIME` | NOT NULL | |
| `deleted_at` | `DATETIME` | NULL | Soft delete |

> Danh mục là **phẳng** (không có `parent_id`). Khi xóa mềm: `name` đổi thành `<name>#deleted_<id>` để giải phóng khóa UNIQUE (tạo lại danh mục cùng tên không bị `409` oan).

### 2. Bảng `products`

| Cột | Kiểu | Ràng buộc | Mô tả |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | |
| `sku` | `VARCHAR(30)` | UNIQUE, NOT NULL | Không đổi sau khi tạo. **Vẫn giữ chỗ sau khi xóa mềm** |
| `name` | `VARCHAR(255)` | NOT NULL | |
| `description` | `TEXT` | NULL | Tối đa 5000 ký tự |
| `base_price` | `DECIMAL(12,2)` | NOT NULL | Giá bán gốc |
| `category_id` | `BIGINT` | FK → `categories.id`, NOT NULL | |
| `product_type` | `ENUM('BOOK','HOBBY')` | NOT NULL | Không đổi sau khi tạo |
| `status` | `ENUM('ACTIVE','HIDDEN')` | NOT NULL, default `ACTIVE` | `HIDDEN` = ẩn khỏi khách, Admin vẫn thấy |
| `thumbnail_url` | `VARCHAR(500)` | NULL | URL ảnh do Admin nhập (http/https) |
| `attributes` | `JSON` | NOT NULL | Thuộc tính theo `product_type` |
| `stock_quantity` | `INT` | NOT NULL, default `0`, CHECK `>= 0` | Sprint 2 luôn là `0`; quản lý qua `inventory_batches` ở Sprint sau |
| `version` | `BIGINT` | NOT NULL, default `0` | Optimistic locking (`@Version`) |
| `created_at` | `DATETIME` | NOT NULL | |
| `updated_at` | `DATETIME` | NOT NULL | |
| `deleted_at` | `DATETIME` | NULL | Soft delete |

**Index đề xuất:**

```sql
CREATE INDEX idx_products_list     ON products (deleted_at, status, category_id);
CREATE INDEX idx_products_price    ON products (deleted_at, base_price);
CREATE INDEX idx_products_created  ON products (created_at);
-- sku đã có UNIQUE
```

> - Collation `utf8mb4_unicode_ci` cho `name` (tìm kiếm không phân biệt dấu và hoa thường: gõ "nguyen" vẫn ra "Nguyễn").
> - Dùng `DECIMAL` + `BigDecimal`, **không** dùng float/double cho tiền.
> - Không tạo bảng `inventory_batches` ở Sprint này.

---

## Quy ước chung

### Request/Response format

Áp dụng wrapper của Sprint 1 (`success`, `message`, `data` / `errors`).

### HTTP Status Code

Như Sprint 1 (`200`, `201`, `400`, `401`, `403`, `404`, `409`, `429`).

### Phân quyền

| Nhóm endpoint | Quy tắc |
|---|---|
| `/api/v1/admin/**` | Chỉ `ROLE_ADMIN`. Không có token → `401`. Token của `ROLE_CUSTOMER` → `403` |
| `GET /api/v1/products/**`, `GET /api/v1/categories` | Public (`permitAll`) |

### Soft delete

- Dùng `@SQLRestriction("deleted_at IS NULL")` (Hibernate) để mọi truy vấn mặc định loại bản ghi đã xóa.
- **Riêng** kiểm tra trùng `sku` phải dùng **native query không lọc `deleted_at`** — sku của sản phẩm đã xóa vẫn bị coi là đã dùng.
- Không bao giờ dùng lệnh `DELETE` SQL.

### Phân trang (dùng chung mọi API danh sách)

| Tham số | Mặc định | Quy tắc |
|---|---|---|
| `page` | `0` | Bắt đầu từ 0 (theo Spring). Nhỏ hơn 0 → `400` |
| `size` | `12` | Từ 1 đến 50. Ngoài khoảng → `400` |

Response dạng:

```json
{
  "success": true,
  "message": "Lấy danh sách thành công",
  "data": {
    "items": [ ],
    "page": 0,
    "size": 12,
    "totalElements": 45,
    "totalPages": 4
  }
}
```

### Sắp xếp (whitelist)

| `sort` | Ý nghĩa |
|---|---|
| `newest` *(mặc định)* | `created_at DESC` |
| `price_asc` | `base_price ASC` |
| `price_desc` | `base_price DESC` |

Luôn thêm `id DESC` làm tiêu chí phụ để phân trang ổn định. Giá trị `sort` khác → `400`.

### Chuẩn hoá dữ liệu đầu vào

| Field | Quy tắc |
|---|---|
| `sku` | `trim().toUpperCase()`, regex `^[A-Z0-9-]{3,30}$` |
| `name` (sản phẩm) | `trim()`, 2–255 ký tự |
| `name` (danh mục) | `trim()`, 2–100 ký tự |
| `description` | `trim()`, sản phẩm tối đa 5000, danh mục tối đa 500 ký tự |
| `basePrice` | Số lớn hơn `0` và không vượt quá `1_000_000_000`, tối đa 2 chữ số thập phân |
| `thumbnailUrl` | `http://` hoặc `https://`, tối đa 500 ký tự |
| `keyword` | `trim()`, tối đa 100 ký tự, **escape** ký tự `%` và `_` trước khi đưa vào `LIKE` |

### Validation `attributes` theo `product_type`

Áp dụng Strategy pattern: `BookAttributesValidator`, `HobbyAttributesValidator` chọn theo `product_type` (dễ mở rộng loại mới).

**Quy tắc chung:** chỉ chấp nhận đúng các key bắt buộc trong bảng; **key lạ → `400`**. Chuỗi được `trim()` và không được rỗng.

**`product_type = BOOK`:**

| Key | Kiểu | Quy tắc |
|---|---|---|
| `author` | string | 1–255 ký tự |
| `publisher` | string | 1–255 ký tự |
| `pages` | integer | 1–10000 |
| `isbn` | string | Bỏ dấu `-` và khoảng trắng trước khi kiểm tra; phải là ISBN-10 hoặc ISBN-13 **đúng checksum**. Lưu dạng đã chuẩn hoá (chỉ chữ số, ISBN-10 có thể kết thúc bằng `X`) |

**`product_type = HOBBY`:**

| Key | Kiểu | Quy tắc |
|---|---|---|
| `brand` | string | 1–100 ký tự |
| `scale` | string | Dạng `1/7`, `1/72`... (regex `^\d{1,3}/\d{1,4}$`, chấp nhận nhập `1:7` rồi đổi thành `1/7`), hoặc `N/A` cho sản phẩm không có tỷ lệ (ví dụ boardgame) |
| `material` | string | 1–100 ký tự |

Lỗi trả về theo từng key: `errors: { "attributes.isbn": "ISBN không hợp lệ" }`.

---

## UC10 — Xem danh sách danh mục (Public)

**Actor:** Guest, Customer, Admin
**Precondition:** Không

### API

```
GET /api/v1/categories
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Lấy danh sách danh mục thành công",
  "data": [
    { "id": 1, "name": "Literature", "description": "Sách văn học", "productCount": 24 },
    { "id": 2, "name": "Boardgames", "description": null, "productCount": 9 },
    { "id": 3, "name": "Figures", "description": null, "productCount": 12 }
  ]
}
```

### Main Flow

1. Client gọi endpoint (dùng cho sidebar / menu danh mục).
2. Hệ thống lấy các danh mục `deleted_at IS NULL`, sắp xếp theo `name`.
3. `productCount` = số sản phẩm `status = ACTIVE` và `deleted_at IS NULL` của danh mục đó.
4. Trả về `200` (không phân trang vì số lượng danh mục ít).

### Exception Flow

Không có.

> **Quyết định thiết kế:**
> - Danh mục không có sản phẩm hiển thị vẫn được trả về (`productCount = 0`); frontend tự quyết định có ẩn hay không.
> - `productCount` chỉ đếm sản phẩm `ACTIVE`, không lộ số lượng sản phẩm đang ẩn.

---

## UC11 — Quản lý danh mục (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`

### API

| Thao tác | Method | Endpoint |
|---|---|---|
| Tạo | `POST` | `/api/v1/admin/categories` |
| Danh sách | `GET` | `/api/v1/admin/categories` |
| Chi tiết | `GET` | `/api/v1/admin/categories/{id}` |
| Cập nhật | `PUT` | `/api/v1/admin/categories/{id}` |
| Xóa (mềm) | `DELETE` | `/api/v1/admin/categories/{id}` |

**Request body (POST, PUT):**

```json
{
  "name":        "Literature",
  "description": "Sách văn học"
}
```

**Response `201` (POST) / `200` (GET, PUT):**

```json
{
  "success": true,
  "message": "Tạo danh mục thành công",
  "data": {
    "id":           1,
    "name":         "Literature",
    "description":  "Sách văn học",
    "productCount": 0,
    "createdAt":    "2026-01-01T10:00:00Z",
    "updatedAt":    "2026-01-01T10:00:00Z"
  }
}
```

**Response `200` (DELETE):**

```json
{
  "success": true,
  "message": "Xóa danh mục thành công"
}
```

### Main Flow

**Tạo / Cập nhật:**
1. Admin gửi `name`, `description`.
2. Hệ thống chuẩn hoá và validate.
3. Kiểm tra trùng `name` (chỉ xét bản ghi `deleted_at IS NULL`; khi cập nhật thì loại trừ chính danh mục đang sửa).
4. Lưu và trả về danh mục.

**Danh sách:** trả toàn bộ danh mục `deleted_at IS NULL` kèm `productCount` (đếm **mọi** sản phẩm chưa xóa, gồm cả `HIDDEN`).

**Xóa:**
1. Kiểm tra tồn tại (`deleted_at IS NULL`).
2. Đếm sản phẩm chưa xóa (`deleted_at IS NULL`, **gồm cả `HIDDEN`**) thuộc danh mục — nếu còn thì từ chối.
3. Đặt `deleted_at = now()` và đổi `name` thành `<name>#deleted_<id>`.
4. Trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token | `401` | "Yêu cầu xác thực" |
| E2 | Token của `ROLE_CUSTOMER` | `403` | "Không có quyền thực hiện thao tác này" |
| E3 | `name` trống hoặc ngoài 2–100 ký tự | `400` | "Tên danh mục phải từ 2 đến 100 ký tự" |
| E4 | `description` quá 500 ký tự | `400` | "Mô tả tối đa 500 ký tự" |
| E5 | `name` đã tồn tại (danh mục khác chưa xóa) | `409` | "Tên danh mục đã tồn tại" |
| E6 | Danh mục không tồn tại hoặc đã xóa | `404` | "Danh mục không tồn tại" |
| E7 | Xóa danh mục còn sản phẩm chưa xóa | `400` | "Không thể xóa danh mục đang có sản phẩm" |
| E8 | `{id}` không phải số | `400` | "Mã danh mục không hợp lệ" |

> **Quyết định thiết kế:**
> - E7 tính cả sản phẩm `HIDDEN` — nếu không, xóa danh mục sẽ để lại sản phẩm ẩn trỏ tới danh mục đã xóa, sau này hiện lại sẽ lỗi.
> - Tên danh mục so sánh không phân biệt hoa thường và dấu (theo collation).
> - Danh mục phẳng, không có cây cha–con ở Sprint 2.

---

## UC12 — Tạo sản phẩm (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`; danh mục đã tồn tại

### API

```
POST /api/v1/admin/products
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body (BOOK):**

```json
{
  "sku":          "BK-0001",
  "name":         "Nhà giả kim",
  "description":  "Tiểu thuyết của Paulo Coelho",
  "basePrice":    79000,
  "categoryId":   1,
  "productType":  "BOOK",
  "thumbnailUrl": "https://cdn.example.com/p/bk-0001.jpg",
  "attributes": {
    "author":    "Paulo Coelho",
    "publisher": "NXB Văn Học",
    "pages":     228,
    "isbn":      "9786045888292"
  }
}
```

**Request body (HOBBY):**

```json
{
  "sku":         "FG-0001",
  "name":        "Mô hình Saber 1/7",
  "basePrice":   1850000,
  "categoryId":  3,
  "productType": "HOBBY",
  "attributes": {
    "brand":    "Good Smile Company",
    "scale":    "1/7",
    "material": "PVC"
  }
}
```

**Response `201`:**

```json
{
  "success": true,
  "message": "Tạo sản phẩm thành công",
  "data": {
    "id":            1,
    "sku":           "BK-0001",
    "name":          "Nhà giả kim",
    "description":   "Tiểu thuyết của Paulo Coelho",
    "basePrice":     79000,
    "category":      { "id": 1, "name": "Literature" },
    "productType":   "BOOK",
    "status":        "ACTIVE",
    "thumbnailUrl":  "https://cdn.example.com/p/bk-0001.jpg",
    "attributes":    { "author": "Paulo Coelho", "publisher": "NXB Văn Học", "pages": 228, "isbn": "9786045888292" },
    "stockQuantity": 0,
    "version":       0,
    "createdAt":     "2026-01-01T10:00:00Z",
    "updatedAt":     "2026-01-01T10:00:00Z"
  }
}
```

### Main Flow

1. Admin gửi thông tin sản phẩm.
2. Hệ thống chuẩn hoá và validate các field lõi.
3. Kiểm tra `categoryId` tồn tại và chưa bị xóa.
4. Chọn validator theo `productType` → validate `attributes`.
5. Kiểm tra trùng `sku` bằng native query (**gồm cả sản phẩm đã xóa**).
6. Lưu với `stock_quantity = 0`, `status = ACTIVE`.
7. Trả về `201` kèm chi tiết sản phẩm.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token / token của Customer | `401` / `403` | (như UC11 — E1, E2) |
| E2 | `sku` sai format | `400` | "SKU chỉ gồm chữ hoa, số, dấu gạch ngang, dài 3–30 ký tự" |
| E3 | `sku` đã tồn tại (kể cả sản phẩm đã xóa) | `409` | "SKU đã tồn tại" |
| E4 | `name` trống hoặc ngoài 2–255 ký tự | `400` | "Tên sản phẩm phải từ 2 đến 255 ký tự" |
| E5 | `basePrice` thiếu, không dương hoặc vượt giới hạn | `400` | "Giá không hợp lệ" |
| E6 | `productType` thiếu hoặc không thuộc `[BOOK, HOBBY]` | `400` | "Loại sản phẩm không hợp lệ" |
| E7 | `categoryId` không tồn tại hoặc đã xóa | `400` | "Danh mục không tồn tại" |
| E8 | `attributes` thiếu key bắt buộc / sai kiểu / giá trị sai / có key lạ | `400` | "Thuộc tính sản phẩm không hợp lệ" + `errors` theo từng key |
| E9 | `thumbnailUrl` không phải URL http/https hợp lệ | `400` | "URL ảnh sản phẩm không hợp lệ" |
| E10 | `description` quá 5000 ký tự | `400` | "Mô tả tối đa 5000 ký tự" |

> **Quyết định thiết kế:**
> - `stock_quantity` luôn là `0` khi tạo. Nếu client gửi `stockQuantity` / `status` lên thì bỏ qua.
> - Sản phẩm đã xóa **vẫn giữ `sku`** (E3) để đơn hàng cũ luôn tham chiếu đúng mã; muốn dùng lại thì phải đặt SKU mới.
> - Category có thể chứa cả BOOK lẫn HOBBY (không ràng buộc category theo type).
> - Ảnh sản phẩm là URL do Admin nhập; upload ảnh nằm trong backlog.

---

## UC13 — Xem danh sách & chi tiết sản phẩm (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`

### API

```
GET /api/v1/admin/products
GET /api/v1/admin/products/{id}
Authorization: Bearer <token>
```

**Query parameters của danh sách** (tất cả tùy chọn):

| Tham số | Mô tả |
|---|---|
| `keyword` | Khớp `name` hoặc `sku` (chứa chuỗi, không phân biệt hoa thường và dấu) |
| `categoryId` | Lọc theo danh mục |
| `productType` | `BOOK` hoặc `HOBBY` |
| `status` | `ACTIVE` hoặc `HIDDEN` (không gửi = cả hai) |
| `minPrice`, `maxPrice` | Khoảng giá |
| `sort` | `newest` (mặc định), `price_asc`, `price_desc` |
| `page`, `size` | Phân trang |

**Response `200` (danh sách):** dạng phân trang (xem *Quy ước chung*), mỗi phần tử trong `items`:

```json
{
  "id": 1,
  "sku": "BK-0001",
  "name": "Nhà giả kim",
  "basePrice": 79000,
  "category": { "id": 1, "name": "Literature" },
  "productType": "BOOK",
  "status": "ACTIVE",
  "thumbnailUrl": "https://cdn.example.com/p/bk-0001.jpg",
  "stockQuantity": 0,
  "createdAt": "2026-01-01T10:00:00Z"
}
```

**Response `200` (chi tiết):** cùng format `data` của UC12 (đủ `description`, `attributes`, `version`).

### Main Flow

1. Admin gọi endpoint kèm bộ lọc.
2. Hệ thống validate tham số (`minPrice`, `maxPrice`, `sort`, `page`, `size`).
3. Truy vấn sản phẩm `deleted_at IS NULL`, **gồm cả `HIDDEN`**.
4. Trả về danh sách phân trang hoặc chi tiết.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token / token của Customer | `401` / `403` | (như UC11 — E1, E2) |
| E2 | `minPrice`/`maxPrice` âm, hoặc `minPrice > maxPrice` | `400` | "Khoảng giá không hợp lệ" |
| E3 | `sort`, `productType`, `status`, `page`, `size` sai giá trị | `400` | "Tham số truy vấn không hợp lệ" |
| E4 | `{id}` không phải số | `400` | "Mã sản phẩm không hợp lệ" |
| E5 | Sản phẩm không tồn tại hoặc đã xóa (chi tiết) | `404` | "Sản phẩm không tồn tại" |

> **Quyết định thiết kế:**
> - Đây là endpoint để Admin thấy cả sản phẩm `HIDDEN` và `stockQuantity`; endpoint public thì không.
> - `categoryId` không tồn tại → trả danh sách rỗng (không lỗi).

---

## UC14 — Cập nhật sản phẩm (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`; sản phẩm tồn tại

### API

```
PUT /api/v1/admin/products/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**

```json
{
  "name":         "Nhà giả kim (Bản đặc biệt)",
  "description":  "Mô tả mới",
  "basePrice":    89000,
  "categoryId":   1,
  "thumbnailUrl": "https://cdn.example.com/p/bk-0001-v2.jpg",
  "attributes": {
    "author":    "Paulo Coelho",
    "publisher": "NXB Văn Học",
    "pages":     240,
    "isbn":      "9786045888292"
  },
  "version": 0
}
```

**Response `200`:** chi tiết sản phẩm sau cập nhật (format UC12, `version` tăng lên).

### Main Flow

1. Admin gửi toàn bộ các field được phép sửa kèm `version` hiện tại.
2. Hệ thống tìm sản phẩm `deleted_at IS NULL`.
3. Validate field lõi; validate `attributes` theo **`product_type` hiện có của sản phẩm**.
4. Kiểm tra `categoryId` tồn tại và chưa xóa.
5. So sánh `version` — lưu bằng optimistic locking.
6. Cập nhật `updated_at`, trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token / token của Customer | `401` / `403` | (như UC11 — E1, E2) |
| E2 | Sản phẩm không tồn tại hoặc đã xóa | `404` | "Sản phẩm không tồn tại" |
| E3 | Sai `name`, `basePrice`, `thumbnailUrl`, `description` | `400` | (như UC12 — E4, E5, E9, E10) |
| E4 | `categoryId` không tồn tại hoặc đã xóa | `400` | "Danh mục không tồn tại" |
| E5 | `attributes` không hợp lệ theo type của sản phẩm | `400` | "Thuộc tính sản phẩm không hợp lệ" + `errors` theo từng key |
| E6 | Thiếu `version` | `400` | "Thiếu thông tin phiên bản dữ liệu" |
| E7 | `version` không khớp (người khác vừa cập nhật) | `409` | "Sản phẩm đã được cập nhật bởi người khác. Vui lòng tải lại" |
| E8 | `{id}` không phải số | `400` | "Mã sản phẩm không hợp lệ" |

> **Quyết định thiết kế:**
> - Các field **được sửa:** `name`, `description`, `basePrice`, `categoryId`, `thumbnailUrl`, `attributes`.
> - Các field **không được sửa qua PUT** (bị bỏ qua nếu client gửi): `sku`, `productType`, `stockQuantity`, `status`. Đổi `productType` sẽ làm `attributes` cũ sai schema; `stockQuantity` sẽ do `inventory_batches` quản lý; `status` có endpoint riêng (UC15).
> - `thumbnailUrl = null` hoặc `description = null` nghĩa là xóa giá trị.
> - **Ghi chú cho Sprint sau:** đổi `base_price` **không** được ảnh hưởng đơn cũ. Bảng `order_items` phải lưu snapshot `unit_price`, `product_name`, `sku` tại thời điểm đặt hàng.

---

## UC15 — Ẩn / hiện sản phẩm (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`; sản phẩm tồn tại

### API

```
PATCH /api/v1/admin/products/{id}/status
Authorization: Bearer <token>
Content-Type: application/json
```

**Request body:**

```json
{
  "status": "HIDDEN"
}
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Cập nhật trạng thái sản phẩm thành công",
  "data": { "id": 1, "status": "HIDDEN" }
}
```

### Main Flow

1. Admin gửi `status` mới.
2. Hệ thống tìm sản phẩm `deleted_at IS NULL`.
3. Cập nhật `status`, `updated_at`.
4. Trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token / token của Customer | `401` / `403` | (như UC11 — E1, E2) |
| E2 | Sản phẩm không tồn tại hoặc đã xóa | `404` | "Sản phẩm không tồn tại" |
| E3 | `status` thiếu hoặc không thuộc `[ACTIVE, HIDDEN]` | `400` | "Trạng thái không hợp lệ" |

> **Quyết định thiết kế:**
> - Ẩn sản phẩm để tạm ngừng bán mà không xóa; đặt lại `ACTIVE` để bán lại.
> - Đặt trạng thái trùng trạng thái hiện tại vẫn trả `200` (idempotent).
> - Sản phẩm `HIDDEN` biến mất khỏi API public (UC17, UC18) nhưng Admin vẫn thấy ở UC13.

---

## UC16 — Xóa sản phẩm (Admin)

**Actor:** Admin 🔒
**Precondition:** Đã đăng nhập với `ROLE_ADMIN`; sản phẩm tồn tại

### API

```
DELETE /api/v1/admin/products/{id}
Authorization: Bearer <token>
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Xóa sản phẩm thành công"
}
```

### Main Flow

1. Admin gửi yêu cầu xóa.
2. Hệ thống tìm sản phẩm `deleted_at IS NULL`.
3. Đặt `deleted_at = now()` (**xóa mềm**, không đổi `sku`).
4. Trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Không có token / token của Customer | `401` / `403` | (như UC11 — E1, E2) |
| E2 | Sản phẩm không tồn tại hoặc đã xóa | `404` | "Sản phẩm không tồn tại" |
| E3 | `{id}` không phải số | `400` | "Mã sản phẩm không hợp lệ" |

> **Quyết định thiết kế:**
> - Chỉ xóa mềm để các đơn hàng lịch sử tham chiếu `product_id` không bị vỡ.
> - Sản phẩm đã xóa coi như không tồn tại với mọi API (kể cả Admin); khôi phục bản ghi đã xóa nằm trong backlog.
> - Xóa sản phẩm đã xóa → `404` (không idempotent).

---

## UC17 — Tìm kiếm & lọc sản phẩm (Public)

**Actor:** Guest, Customer, Admin
**Precondition:** Không

### API

```
GET /api/v1/products
```

**Query parameters** (tất cả tùy chọn):

| Tham số | Mô tả |
|---|---|
| `keyword` | Khớp `name` (chứa chuỗi, không phân biệt hoa thường và dấu) |
| `categoryId` | Lọc theo danh mục |
| `productType` | `BOOK` hoặc `HOBBY` |
| `minPrice`, `maxPrice` | Khoảng giá |
| `sort` | `newest` (mặc định), `price_asc`, `price_desc` |
| `page`, `size` | Phân trang (mặc định `0`, `12`) |

**Response `200`:** dạng phân trang, mỗi phần tử trong `items` (DTO nhẹ, không có `attributes`):

```json
{
  "id": 1,
  "name": "Nhà giả kim",
  "basePrice": 79000,
  "category": { "id": 1, "name": "Literature" },
  "productType": "BOOK",
  "thumbnailUrl": "https://cdn.example.com/p/bk-0001.jpg",
  "inStock": false
}
```

### Main Flow

1. Client gọi endpoint kèm bộ lọc.
2. Hệ thống validate tham số.
3. Truy vấn với điều kiện **bắt buộc**: `deleted_at IS NULL` **và** `status = ACTIVE`.
4. Áp dụng các bộ lọc (kết hợp AND), sắp xếp theo whitelist, phân trang.
5. Trả về `200`.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | `minPrice`/`maxPrice` âm, hoặc `minPrice > maxPrice` | `400` | "Khoảng giá không hợp lệ" |
| E2 | `sort`, `productType`, `page`, `size` sai giá trị | `400` | "Tham số truy vấn không hợp lệ" |
| E3 | `keyword` quá 100 ký tự | `400` | "Từ khóa tối đa 100 ký tự" |

> **Quyết định thiết kế:**
> - Không có kết quả → `200` với `items` rỗng (không phải `404`).
> - `categoryId` không tồn tại → danh sách rỗng.
> - Public **không** trả `sku`, `stockQuantity`, `status`, `version`; chỉ trả `inStock` (`stock_quantity > 0`).
> - `keyword` được escape `%` và `_`; dùng `LIKE` với collation không phân biệt dấu. Nếu dữ liệu lớn, cân nhắc FULLTEXT index (backlog).

---

## UC18 — Xem chi tiết sản phẩm (Public)

**Actor:** Guest, Customer, Admin
**Precondition:** Không

### API

```
GET /api/v1/products/{id}
```

**Response `200`:**

```json
{
  "success": true,
  "message": "Lấy chi tiết sản phẩm thành công",
  "data": {
    "id":           1,
    "name":         "Nhà giả kim",
    "description":  "Tiểu thuyết của Paulo Coelho",
    "basePrice":    79000,
    "category":     { "id": 1, "name": "Literature" },
    "productType":  "BOOK",
    "thumbnailUrl": "https://cdn.example.com/p/bk-0001.jpg",
    "attributes": {
      "author":    "Paulo Coelho",
      "publisher": "NXB Văn Học",
      "pages":     228,
      "isbn":      "9786045888292"
    },
    "inStock": true,
    "availableQuantity": 15,
    "createdAt":    "2026-01-01T10:00:00Z"
  }
}
```

### Main Flow

1. Client gọi endpoint với `id`.
2. Hệ thống tìm sản phẩm `deleted_at IS NULL` **và** `status = ACTIVE`.
3. Trả về chi tiết đầy đủ, gồm `attributes`.
4. Frontend đọc `productType` để hiển thị **Book Spec Table** hoặc **Hobby Spec Table**.

### Exception Flow

| Mã | Điều kiện | HTTP | Message |
|---|---|---|---|
| E1 | Sản phẩm không tồn tại | `404` | "Sản phẩm không tồn tại" |
| E2 | Sản phẩm đã xóa mềm | `404` | "Sản phẩm không tồn tại" |
| E3 | Sản phẩm `status = HIDDEN` | `404` | "Sản phẩm không tồn tại" |
| E4 | `{id}` không phải số | `400` | "Mã sản phẩm không hợp lệ" |

> **Quyết định thiết kế:**
> - E1, E2, E3 dùng **chung một message** để khách không phân biệt được sản phẩm bị xóa hay đang ẩn.
> - Không trả `sku`, `stockQuantity`, `status`, `version`.
> - "Sản phẩm liên quan" nằm trong backlog.

---

## Tổng hợp API — Sprint 2

| UC | Method | Endpoint | Auth | Roles |
|---|---|---|---|---|
| UC10 | `GET` | `/api/v1/categories` | ❌ | Public |
| UC11 | `POST` | `/api/v1/admin/categories` | 🔒 JWT | Admin |
| UC11 | `GET` | `/api/v1/admin/categories` | 🔒 JWT | Admin |
| UC11 | `GET` | `/api/v1/admin/categories/{id}` | 🔒 JWT | Admin |
| UC11 | `PUT` | `/api/v1/admin/categories/{id}` | 🔒 JWT | Admin |
| UC11 | `DELETE` | `/api/v1/admin/categories/{id}` | 🔒 JWT | Admin |
| UC12 | `POST` | `/api/v1/admin/products` | 🔒 JWT | Admin |
| UC13 | `GET` | `/api/v1/admin/products` | 🔒 JWT | Admin |
| UC13 | `GET` | `/api/v1/admin/products/{id}` | 🔒 JWT | Admin |
| UC14 | `PUT` | `/api/v1/admin/products/{id}` | 🔒 JWT | Admin |
| UC15 | `PATCH` | `/api/v1/admin/products/{id}/status` | 🔒 JWT | Admin |
| UC16 | `DELETE` | `/api/v1/admin/products/{id}` | 🔒 JWT | Admin |
| UC17 | `GET` | `/api/v1/products` | ❌ | Public |
| UC18 | `GET` | `/api/v1/products/{id}` | ❌ | Public |

> **Đối chiếu với spec tổng:** UC11 = UC-05 (Manage Categories); UC12 = UC-06 (Create Product); UC14 + UC16 = UC-07 (Update & Delete Product); UC17 = UC-08 (Search & Filter); UC18 = UC-09 (View Product Details). UC10, UC13, UC15 là các UC **bổ sung** (danh mục public, Admin xem danh sách/chi tiết, ẩn/hiện sản phẩm).

---

## Frontend — Sprint 2

**Admin:**
- Trang quản lý **Danh mục**: bảng + form thêm/sửa, hộp thoại xác nhận khi xóa, hiển thị lỗi `400` khi danh mục còn sản phẩm.
- Trang **Sản phẩm**: bảng phân trang, bộ lọc (từ khóa, danh mục, loại, trạng thái, khoảng giá), công tắc ẩn/hiện, nút xóa có xác nhận.
- **Form tạo/sửa sản phẩm:** chọn `productType` → hiển thị ô nhập `attributes` tương ứng (BOOK: author, publisher, pages, isbn; HOBBY: brand, scale, material). Khi sửa, khóa `sku` và `productType`, giữ `version` để gửi lại; gặp `409` thì báo "đã bị người khác cập nhật" và tải lại dữ liệu.
- Route dưới `/admin/**` dùng `AdminRoute` (Sprint 1).

**Public:**
- Trang danh sách: sidebar danh mục (UC10), lọc loại/khoảng giá, sắp xếp, phân trang; **đồng bộ bộ lọc lên URL query**; debounce ô tìm kiếm.
- Trang chi tiết: render `Book Spec Table` hoặc `Hobby Spec Table` theo `productType`; hiển thị `inStock`.
- Định dạng tiền theo `vi-VN`; có trạng thái loading, danh sách rỗng và lỗi; ảnh lỗi thì dùng ảnh mặc định.

---

## Dữ liệu mẫu & công cụ — Sprint 2

- Flyway `V2__catalog.sql` (bảng, index) và `V3__seed_catalog_dev.sql` (chỉ chạy ở profile `dev`): vài danh mục và khoảng 20 sản phẩm gồm cả BOOK lẫn HOBBY.
- Swagger/OpenAPI mô tả đủ tham số lọc, phân trang và các mã lỗi.
- Unit test: validator `attributes` theo từng type (kể cả checksum ISBN), luồng xóa danh mục (`400` khi còn sản phẩm, gồm cả sản phẩm `HIDDEN`), trùng `sku` với sản phẩm đã xóa, optimistic locking `409`.

---

## Gợi ý thứ tự implement

1. Flyway `V2__catalog.sql` + entity `Category`, `Product` (`@SQLRestriction`, `@Version`, mapping cột JSON) + repository
2. UC11 (quản lý danh mục) + UC10 (danh mục public) — đơn giản, làm trước để có dữ liệu
3. `AttributesValidator` (Strategy: Book/Hobby) + unit test
4. UC12 (tạo sản phẩm) + UC13 (Admin xem danh sách/chi tiết)
5. UC14 (cập nhật) + UC15 (ẩn/hiện) + UC16 (xóa mềm)
6. UC17 (tìm kiếm & lọc) — dùng `Specification` hoặc query động, kiểm tra index
7. UC18 (chi tiết public)
8. Seed dữ liệu mẫu, sau đó làm Frontend Admin rồi Frontend Public

---

## Điểm còn mở (chốt cùng Sprint 2)

1. Danh mục **phẳng** (mặc định) hay cần cây cha–con (`parent_id`)?
2. Ảnh sản phẩm: giữ **URL do Admin nhập** (mặc định) hay làm upload (local / Cloudinary)? Có cần nhiều ảnh (gallery)?
3. Có giữ cột `status` (`ACTIVE`/`HIDDEN`) như đề xuất không?
4. Giữ chỗ `sku` sau khi xóa (mặc định) hay giải phóng như danh mục?
5. Giới hạn giá tối đa (đề xuất 1 tỷ) và đơn vị tiền (VND, không có phần lẻ?).
6. `scale = N/A` cho hobby không có tỷ lệ (boardgame) — chấp nhận hay tách thêm loại sản phẩm?
7. Public có hiển thị số lượng tồn chính xác hay chỉ `inStock`? (mặc định: chỉ `inStock`).

## Backlog

- Upload ảnh sản phẩm / gallery nhiều ảnh.
- Category lồng nhau (`parent_id`).
- Khôi phục bản ghi đã xóa mềm (sản phẩm, danh mục).
- Generated column + index trên JSON (`isbn`, `author`, `brand`); ràng buộc `isbn` UNIQUE cho BOOK.
- FULLTEXT index cho tìm kiếm khi dữ liệu lớn.
- Sản phẩm liên quan (cùng danh mục) ở trang chi tiết.
- Snapshot giá/tên/sku trong `order_items` (Sprint đơn hàng).
- Quản lý tồn kho qua `inventory_batches` (cập nhật `stock_quantity`).

---

## UC19 — Validate Giỏ hàng (Mới)

**Actor:** Guest, Customer 
**Precondition:** Không

### API

```
POST /api/v1/cart/validate
Content-Type: application/json
```

**Request body:**
```json
{
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

**Response `200`:**
```json
{
  "success": true,
  "message": "Kiểm tra giỏ hàng thành công",
  "data": {
    "items": [
      {
        "productId": 1,
        "productName": "Nhà giả kim",
        "unitPrice": 79000,
        "quantity": 2,
        "lineTotal": 158000,
        "inStock": true,
        "availableQuantity": 15
      }
    ],
    "cartTotal": 158000,
    "isValid": true
  }
}
```
*(Nếu có 1 item hết hàng hoặc vượt số lượng, `isValid` sẽ trả `false`)*
