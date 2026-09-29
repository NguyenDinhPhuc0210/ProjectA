# 📦 Database Schema — Book & Hobby Retailer

> Tài liệu này định nghĩa cấu trúc cơ sở dữ liệu cho phân hệ Xác thực và Sản phẩm đa hình (Sprint 1 & 2).
> Cấu trúc áp dụng Soft Delete thống nhất và dùng JSON để giải quyết bài toán Đa hình.

---

## 🏷️ Danh sách Enum

### `Role` — Vai trò người dùng
| Giá trị | Ý nghĩa |
|---|---|
| `ROLE_CUSTOMER` | Khách hàng mua sắm |
| `ROLE_ADMIN` | Quản trị viên hệ thống (Đăng sản phẩm, duyệt đơn) |

---

### `AccountStatus` — Trạng thái tài khoản
| Giá trị | Ý nghĩa |
|---|---|
| `ACTIVE` | Tài khoản hoạt động bình thường *(mặc định)* |
| `LOCKED` | Bị Admin khóa, không thể đăng nhập |

---

### `AuthProvider` — Nền tảng xác thực
| Giá trị | Ý nghĩa |
|---|---|
| `LOCAL` | Đăng ký bằng email/password truyền thống *(mặc định)* |
| `GOOGLE` | Đăng nhập qua hệ thống Google OAuth2 |

---

### `ProductType` — Phân loại sản phẩm đa hình
| Giá trị | Ý nghĩa |
|---|---|
| `BOOK` | Sách (Dùng Schema: tác giả, NXB, ISBN...) |
| `HOBBY` | Mô hình, Đồ chơi (Dùng Schema: tỷ lệ, hãng sx, chất liệu...) |

---

## 🗄️ Sơ đồ quan hệ tóm tắt

```text
users ──1:N──> orders
users ──1:N──> reviews

categories ──1:N──> products

products ──1:N──> inventory_batches
products ──1:N──> order_items
products ──1:N──> reviews

orders ──1:N──> order_items