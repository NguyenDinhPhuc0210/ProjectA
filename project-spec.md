# PROJECT SPECIFICATION: BOOK & HOBBY RETAILER
*(B2C E-commerce Model - Centralized Inventory Management)*

> Bản đã hợp nhất: spec gốc + các bổ sung Sprint 1. Phần được thêm/sửa so với bản gốc có đánh dấu **[MỚI]** / **[SỬA]**.
> Sprint 1 vẫn ở trạng thái chờ chốt các điểm mở tại mục 4. Sprint 2 giữ nguyên như bản gốc.

---

## 1. SYSTEM OVERVIEW

### Core Roles & Business Flow
* **Roles:** The system is streamlined with exactly 2 roles: `ROLE_CUSTOMER` and `ROLE_ADMIN`. Absolutely no other roles are permitted.
* **B2C Model:** The system operates as a direct retailer (similar to Barnes & Noble or Kinokuniya). Users cannot create stores or list products. The entire supply chain (product approval, pricing, inventory) is controlled by the Admin.
* **Checkout Flow:** Customer views product -> Adds to cart -> Places order (System temporarily holds inventory) -> Payment (COD/Online) -> Admin confirms -> Order shipped & completed.
* **Polymorphic Data (Tech Flex):** The system uses a JSON data type column to store dynamic attributes. This allows combining Books (Requires: Author, ISBN, Publisher) and Hobby items (Requires: Scale, Brand, Material) into a single `products` table without data redundancy or complex EAV anti-patterns.

### Tech Stack
* **Backend:** Java, Spring Boot (Web, Data JPA, Security), JWT (JJWT), MySQL 8.0+.
  Mandatory Architecture: Controller -> Service -> Repository. Base URL: `/api/v1`.
* **Frontend:** ReactJS (Vite), React Router DOM, Axios, Ant Design / Tailwind CSS.
* **[MỚI] Tooling:** Flyway (DB migration), springdoc-openapi (Swagger), Bucket4j (rate limit), `JavaMailSender` (SMTP), Docker Compose (MySQL), profiles `dev` / `prod`.

### Global Technical Conventions

**Response Wrapper (Used for ALL endpoints):**
```
Success (with data): { "success": true,  "message": "...", "data": {...} }
Error (validation):  { "success": false, "message": "...", "errors": {"field": "..."} }
```

**HTTP Status Codes:**
| Code | Meaning |
|---|---|
| 200 OK | Request succeeded. |
| 201 Created | Resource successfully created. |
| 400 Bad Request | Input error / validation failure / business rule violation. |
| 401 Unauthorized | Not authenticated or invalid/expired token. |
| 403 Forbidden | Authenticated but lacks permission (including LOCKED accounts). |
| 404 Not Found | Resource does not exist. |
| 409 Conflict | Data duplication (e.g., email already exists). |
| **[MỚI]** 429 Too Many Requests | Rate limit exceeded (login, forgot-password, reset-password). |

**Authentication [SỬA]:**
* Endpoints marked with [🔒] require the header: `Authorization: Bearer <JWT>`.
* The JWT payload must contain `{ userId, email, role, iat, exp }` and is valid for 24 hours. It MUST NOT contain the password.
* **[MỚI]** `JwtAuthFilter` must re-query the user in the DB on every [🔒] request:
  * User does not exist or is soft-deleted -> **401**.
  * `account_status = LOCKED` -> **403**.
  * Token `iat` is earlier than `users.password_changed_at` -> **401** (token issued before a password change/reset).
* JWT secret must be at least 256 bits and read from an environment variable.

**Password Policy (System-wide) [SỬA]:**
Minimum 8 and **maximum 64 characters**, containing at least 1 uppercase letter, 1 number, and 1 special character (`@ # $ ! % ^ & *`, etc.).

**Soft Delete [SỬA]:**
Core tables (`users`, `products`, `categories`) must include a `deleted_at` column. All SELECT queries must append `WHERE deleted_at IS NULL`. A soft-deleted record is considered non-existent. NEVER use a hard DELETE SQL command.
**[MỚI]** When soft-deleting a user, release the UNIQUE keys: `email` becomes `<email>#deleted_<id>` and `phone_number` is set to `NULL`, so the person can re-register with the same email/phone without a false 409.

**[MỚI] Input Normalization:**
* `email`: `trim().toLowerCase()`, format validation, max 255 characters.
* `phoneNumber`: normalize to `0xxxxxxxxx` (convert `+84` to `0`), regex `^0(3|5|7|8|9)\d{8}$`.
* `fullName`: trim, 2–100 characters.

**[MỚI] Centralized Error Handling (JSON in the response wrapper):**
* Custom `AuthenticationEntryPoint` -> **401**; custom `AccessDeniedHandler` -> **403**.
* `@RestControllerAdvice` handles: validation (400, with `errors`), duplicate (409), not found (404), rate limit (429), and unexpected errors (500, never leak stack traces).

**[MỚI] Rate Limit (Bucket4j):**
| Endpoint | Limit |
|---|---|
| `POST /auth/login` | 5 failed attempts / 15 min per (IP + email) |
| `POST /auth/forgot-password` | 3 requests / 15 min per IP and per email |
| `POST /auth/reset-password` | 10 requests / 15 min per IP |

**[MỚI] Email Service:** SMTP via `JavaMailSender`, sent asynchronously (`@Async`) so response time does not reveal whether an email exists.

**[MỚI] Preset Avatars:**
* Avatar upload is **not supported**. Users pick one avatar from a fixed preset list.
* The preset list is fixed backend configuration (enum or config file); each avatar has a `key` (e.g. `avatar_01`) and a `url`. Image files are hosted statically.
* The DB stores only `avatar_key`, never a free-form URL.
* A `default` avatar applies to new users and when a user resets their choice.

### Database Schema — `users` **[MỚI]**
| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK AI | |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(100) | NULL if GOOGLE |
| full_name | VARCHAR(100) | NOT NULL |
| phone_number | VARCHAR(15) | UNIQUE, **nullable** |
| avatar_key | VARCHAR(50) | NOT NULL, default `'default'` |
| role | ENUM('ROLE_CUSTOMER','ROLE_ADMIN') | NOT NULL |
| account_status | ENUM('ACTIVE','LOCKED') | default ACTIVE |
| auth_provider | ENUM('LOCAL','GOOGLE') | default LOCAL |
| reset_token | CHAR(64) | stores **SHA-256 hash**, indexed |
| reset_token_exp | DATETIME | nullable |
| password_changed_at | DATETIME | nullable |
| created_at / updated_at | DATETIME | |
| deleted_at | DATETIME | nullable |

Schema is managed by **Flyway** (`V1__init_users.sql`).

---

## 2. DETAILED USE CASES — SPRINT 1 & 2

### GROUP 1: AUTH & ACCOUNT (SPRINT 1)

#### UC-00 — Seed First Admin **[MỚI]**
* Actor: System (on application startup) | No endpoint.
* Uses an `ApplicationRunner`, reading `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` from environment variables.
* Creates the admin only when **no user with `role = ROLE_ADMIN` exists** (idempotent).
* `ADMIN_PASSWORD` must pass the Password Policy and is hashed with BCrypt. If variables are missing or the password is invalid, the app must **fail fast** in the `prod` profile.
* Admin is created with `account_status = ACTIVE`, `auth_provider = LOCAL`. The password is never written to logs.
* There is no way to create an Admin through the API: `/auth/register` always creates `ROLE_CUSTOMER`.

#### UC-01 — Register Customer Account **[SỬA]**
* Actor: Guest | Endpoint: `POST /api/v1/auth/register`
* Guest sends: `email`, `password`, `fullName`, `phoneNumber` (with the normalization rules above).
* System defaults: `role = ROLE_CUSTOMER`, `account_status = ACTIVE`, `auth_provider = LOCAL`, `avatar_key = default`. Ignore any `role`/`status` sent by the client (anti mass-assignment).
* `email` and `phoneNumber` must be UNIQUE. The Service must check duplicates (only records with `deleted_at IS NULL`) before INSERT and return **409 Conflict** with `errors` naming the field. It must also catch `DataIntegrityViolationException` (race condition) and return 409 (never a 500).
* Password must be hashed using BCrypt before saving.
* Account is active immediately; no email verification required.
* Success: **201**, `data` contains user info (no password). No auto-login.

#### UC-02 — Login **[SỬA]**
* Actor: Guest | Endpoint: `POST /api/v1/auth/login`
* Guest sends: `email`, `password`. Returns a JWT and basic user info if valid.
* Processing order:
  1. Check rate limit; exceeded -> **429**.
  2. Find user by email with `deleted_at IS NULL`. Not found -> still run a dummy BCrypt comparison (timing-attack protection), then return **401** with the generic message.
  3. If `auth_provider == GOOGLE`: **400** "This account was registered via Google. Please use Google Login."
  4. Compare password. Wrong -> **401** with the same generic message.
  5. **Only after the password is correct**, if `account_status == LOCKED` -> **403**.
* Anti-Enumeration Protection: non-existent email, wrong password, or soft-deleted account MUST return the exact same message and status **401**: "Invalid email or password."
* Note (accepted trade-off): the dedicated Google message (step 3) reveals that the email exists. This is a deliberate UX/security trade-off.

#### UC-02b — Google Login (Placeholder for Future Sprint)
* Actor: Guest | Endpoint: `POST /api/v1/auth/google`
* Guest sends: `idToken` (received from Google).
* Backend verifies the token via Google API and **requires `email_verified = true`**, otherwise 400.
* Email does not exist: auto-create user with `auth_provider = GOOGLE`, `password = NULL`, `phone_number = NULL`, `role = ROLE_CUSTOMER`, `avatar_key = default`.
* Email exists with `auth_provider = LOCAL`: **do not auto-link**. Return 409 telling the user to log in with a password (prevents account pre-hijacking).
* Email exists with `auth_provider = GOOGLE`: issue a standard JWT (apply the LOCKED and soft-delete checks as in UC-02).

#### UC-03 — Forgot & Reset Password **[SỬA]**
* Actor: Guest | Endpoints: `POST /api/v1/auth/forgot-password` & `POST /api/v1/auth/reset-password`
* 2-Step Flow: (1) Request Reset -> (2) Reset with Token.

**Step 1 — Forgot (body: `email`)**
* MUST always return **HTTP 200** with a generic message, regardless of whether the email exists (Anti-enumeration).
* Send mail only if the user exists, is not soft-deleted, has `auth_provider = LOCAL` and `account_status = ACTIVE`. GOOGLE/LOCKED accounts still get 200 but **no mail is sent**.
* Generate a random UUID, **store its SHA-256 hash** in `users.reset_token`, and set `reset_token_exp = now + 15 minutes`. The mail link contains the raw UUID. An existing token is overwritten (old token invalidated).
* Mail is sent asynchronously (`@Async`); rate limit applies.

**Step 2 — Reset (body: `token`, `newPassword`)**
* Hash the received token and look up the user by `reset_token`. Not found, expired, or user soft-deleted -> **400** "Invalid or expired token."
* `newPassword` must pass the Password Policy and is hashed with BCrypt.
* On success: set `reset_token = NULL`, `reset_token_exp = NULL` (single use) and `password_changed_at = now` (invalidates all old JWTs). Return 200.

#### UC-04 — View & Update Profile **[SỬA]**
* Actor: Customer [🔒], Admin [🔒] | Endpoints: `GET` & `PATCH /api/v1/users/me`
* GET returns current user info based on `userId` in the JWT, including `avatarKey` and `avatarUrl` (derived from the key). NEVER return `password`, `reset_token`, `reset_token_exp`, `deleted_at`, or `password_changed_at`.
* PATCH is a partial update — only fields present in the body are processed: `fullName`, `phoneNumber`, `avatarKey`.
  * Omitted fields are left unchanged. `fullName`/`phoneNumber` = `null` -> **400**.
  * `avatarKey` must be in the preset list, otherwise **400**. `avatarKey = null` resets to `default`.
  * `email`, `role`, `auth_provider`, `account_status`, `avatarUrl` are rejected/ignored.
  * `phoneNumber` uniqueness check excludes the current user's ID; a conflict with another user returns **409**.
* Success: 200 with the updated user data.

#### UC-04a — List Avatar Presets **[MỚI]**
* Actor: Public | Endpoint: `GET /api/v1/avatars`
* Returns `[{ "key": "avatar_01", "url": "/static/avatars/avatar_01.png" }, ...]`, including `default`.
* Data comes from fixed configuration; no DB query.

#### UC-04b — Change Password **[MỚI]**
* Actor: Customer [🔒], Admin [🔒] | Endpoint: `PATCH /api/v1/users/me/password`
* Body: `currentPassword`, `newPassword`.
* Only for `auth_provider = LOCAL`; GOOGLE accounts get **400**.
* Wrong `currentPassword` -> **400** (not 401, so the frontend does not auto-logout). `newPassword` must pass the Password Policy and **differ** from the current password.
* On success: store the new BCrypt hash, set `password_changed_at = now`, return 200 with a **new JWT** so the current session continues; all older tokens become invalid.

#### UC-04c — Logout **[MỚI]**
* Actor: Customer, Admin | **No backend endpoint.**
* JWT is stateless: logout means the frontend deletes the token and redirects to Login.
* Server-side revocation (blacklist/refresh token) is in the backlog. Meanwhile, tokens are automatically invalidated on password change/reset, account lock, or account deletion (via `JwtAuthFilter`).

### Frontend — Sprint 1 **[MỚI]**
* **Pages:** Register, Login, Forgot Password, Reset Password (reads `token` from the query string), Profile (view/edit, change password, **choose avatar**).
* **Avatar picker:** call `GET /avatars`, show a grid; the user selects one and saves via `PATCH /users/me` with `avatarKey`.
* **AuthContext:** holds user + token; provides `login`, `logout`, `updateUser`.
* **Route guards:** `ProtectedRoute` (requires login) and `AdminRoute` (requires `ROLE_ADMIN`).
* **Axios:** interceptor attaches `Authorization`. **401** -> clear token, go to Login. **403** -> show "account locked" message. **429** -> show "try again later" message.
* Client-side form validation follows the Password Policy and the phone regex.

### Infrastructure — Sprint 1 **[MỚI]**
Flyway, Swagger/OpenAPI, Docker Compose for MySQL, `dev`/`prod` profiles, CORS configured via environment variables, unit tests for `AuthService` (register, all login branches, expired/reused reset token).

---

### GROUP 2: CATALOG & POLYMORPHIC PRODUCTS (SPRINT 2)

#### UC-05 — Manage Categories
* Actor: Admin [🔒] | Endpoints: CRUD at `/api/v1/admin/categories`
* Admin can Create, Read, Update, and Delete categories (e.g., Literature, Boardgames, Figures).
* Deletion is a Soft Delete.
* Business Rule: Cannot delete a category if there are active products linked to it (Return HTTP 400).

#### UC-06 — Create Product (Polymorphic)
* Actor: Admin [🔒] | Endpoint: `POST /api/v1/admin/products`
* Admin sends core fields: `sku` (UNIQUE), `name`, `base_price`, `category_id`, `product_type` (Enum: BOOK or HOBBY).
* JSON Attributes Handling (Critical):
  * If `product_type == BOOK`: the `attributes` JSON payload MUST contain: `{"author", "publisher", "pages", "isbn"}`.
  * If `product_type == HOBBY`: the `attributes` JSON payload MUST contain: `{"brand", "scale", "material"}`.
* Initial `stock_quantity` is explicitly set to 0. (Inventory is managed separately via `inventory_batches` in future sprints).
* Return 409 if `sku` already exists.

#### UC-07 — Update & Delete Product
* Actor: Admin [🔒] | Endpoints: `PUT` & `DELETE /api/v1/admin/products/{id}`
* Update (PUT): Admin can modify `base_price`, `name`, `thumbnail_url`, or the JSON attributes.
* Delete (DELETE): Perform a Soft Delete (`deleted_at = NOW()`).
* Products must be soft-deleted so historical orders linking to this `product_id` are not broken.

#### UC-08 — Search & Filter Products
* Actor: Public | Endpoint: `GET /api/v1/products`
* Allow dynamic filtering via Query Parameters: `categoryId`, `minPrice`, `maxPrice`, `keyword` (matches product name).
* Sorting capabilities: Newest first, Price Asc/Desc.
* Mandatory Filter: the query MUST strictly filter out any products where `deleted_at IS NOT NULL`.

#### UC-09 — View Product Details
* Actor: Public | Endpoint: `GET /api/v1/products/{id}`
* Returns full product details, including the dynamic JSON `attributes` object.
* If the product is soft-deleted, return HTTP 404 (Not Found).
* Frontend Logic (Note): the UI will check the `product_type` field to conditionally render the attributes (e.g., a Book Spec Table vs. a Hobby Spec Table).

---

## 3. BACKLOG
* Admin lock/unlock users (`LOCKED`) and list users (currently no UC can set LOCKED; edit the DB directly if needed).
* Refresh token / blacklist for real token revocation.
* Email verification at registration.
* Google Login (UC-02b).

## 4. OPEN POINTS (to finalize with Sprint 1)
1. Frontend token storage: `localStorage` vs httpOnly cookie (affects CORS/CSRF).
2. The concrete preset avatar list (count, file names, hosted by backend or frontend).
3. Final rate-limit thresholds (the table above is a proposal).
4. Email provider (SendGrid, Gmail SMTP, ...).
5. Whether a refresh token is needed in Sprint 1 or stays in the backlog.
