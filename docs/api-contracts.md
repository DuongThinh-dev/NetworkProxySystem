# Hợp đồng API — Network Proxy Admin

Tài liệu mô tả các endpoint mà `admin-api` cung cấp cho dashboard và các
endpoint nội bộ của Proxy Control API. Tất cả thời gian trả về là chuỗi ISO
8601 theo UTC, ví dụ `2026-11-22T11:07:37.862Z`.

## 1. Thông tin chung

| Mục | Giá trị |
|---|---|
| Base URL | `http://localhost:8081` |
| Nội dung | `application/json; charset=UTF-8` |
| Xác thực | Session cookie `NETWORK_PROXY_ADMIN_SESSION` |
| Cookie | `HttpOnly`, `SameSite=Lax`, `Path=/` |
| Trang đăng nhập | `GET /login.html` |
| Dashboard | `GET /pages/dashboard.html` |

Các endpoint dưới `/api/**` (trừ `/api/health` và `/api/auth/login`) yêu cầu
session hợp lệ. Nếu chưa đăng nhập, API trả về `401` với JSON lỗi.

### Định dạng lỗi

```json
{
  "timestamp": "2026-11-22T11:06:52.427Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dữ liệu gửi lên không hợp lệ",
  "path": "/api/auth/login"
}
```

### Phân trang

Các endpoint danh sách trả về cùng một cấu trúc:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`page` bắt đầu từ `0`. `size` mặc định là `20`, tối đa `200`.

## 2. Xác thực

### `POST /api/auth/login`

Đăng nhập và tạo session. Endpoint công khai.

Request:

```json
{
  "username": "admin",
  "password": "admin123"
}
```

Response `200`:

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "Quản trị viên",
  "lastLoginAt": "2026-11-22T11:07:01.103Z"
}
```

Response `401`:

```json
{
  "timestamp": "2026-11-22T11:07:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Tên đăng nhập hoặc mật khẩu không đúng",
  "path": "/api/auth/login"
}
```

Sau khi đăng nhập, server gửi `Set-Cookie:
NETWORK_PROXY_ADMIN_SESSION=...`. Dùng cookie này cho các request tiếp theo.

### `GET /api/auth/me`

Trả về thông tin tài khoản đang đăng nhập.

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "Quản trị viên",
  "lastLoginAt": "2026-11-22T11:07:01.103Z"
}
```

### `POST /api/auth/logout`

Hủy session hiện tại và ghi audit log `LOGOUT`.

```json
{ "message": "Đã đăng xuất" }
```

### `POST /api/auth/change-password`

Đổi mật khẩu của tài khoản đang đăng nhập. Mật khẩu mới phải từ 6 đến 72 ký tự.

Request:

```json
{
  "currentPassword": "admin123",
  "newPassword": "MatKhauMoi123"
}
```

Response `200`:

```json
{ "message": "Đã đổi mật khẩu" }
```

Response `400` nếu mật khẩu hiện tại sai hoặc mật khẩu mới không hợp lệ.

## 3. Dashboard

### `GET /api/dashboard/summary`

Trả về số liệu tổng hợp cho dashboard.

```json
{
  "totalRules": 4,
  "enabledRules": 3,
  "totalLogs": 128,
  "allowedLogs": 100,
  "blockedLogs": 24,
  "errorLogs": 4,
  "last24hLogs": 17,
  "distinctClients": 5,
  "topTargets": [
    { "target": "example.com", "count": 40 },
    { "target": "github.com", "count": 22 }
  ],
  "recentLogs": [],
  "recentAudit": [],
  "proxy": {
    "online": true,
    "activeClients": 2,
    "loadedRules": 3,
    "uptimeSeconds": 3600,
    "version": "1.0",
    "message": null
  }
}
```

- `recentLogs`: tối đa 8 bản ghi mới nhất.
- `recentAudit`: tối đa 5 audit log mới nhất.
- `proxy.online = false` khi Proxy Control API tắt hoặc không kết nối được.
  Khi đó `proxy.message` giải thích lý do.

## 4. Access Rules

### `GET /api/rules`

Tham số:

| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `q` | string | — | Tìm theo ID, client IP hoặc target |
| `enabled` | boolean | — | `true`/`false` để lọc trạng thái |
| `page` | int | `0` | Trang hiện tại |
| `size` | int | `20` | Số bản ghi mỗi trang |

Ví dụ:

```http
GET /api/rules?q=example&enabled=true&page=0&size=20
```

Response:

```json
{
  "content": [
    {
      "id": 1,
      "clientIp": null,
      "target": "example.com",
      "targetPort": 80,
      "protocol": "HTTP",
      "action": "ALLOW",
      "priority": 100,
      "enabled": true,
      "createdAt": "2026-11-22T11:07:37.862Z",
      "updatedAt": "2026-11-22T11:07:37.862Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `GET /api/rules/{id}`

Trả về một rule hoặc `404`.

### `POST /api/rules`

Tạo rule mới. Các trường `clientIp`, `target`, `targetPort`, `protocol` có thể
để `null` để áp dụng cho mọi giá trị.

Request:

```json
{
  "clientIp": null,
  "target": "Example.COM.",
  "targetPort": 80,
  "protocol": "http",
  "action": "ALLOW",
  "priority": 100,
  "enabled": true
}
```

Quy tắc chuẩn hóa:

- `target` được trim, bỏ dấu chấm cuối và chuyển về chữ thường.
- `protocol` được trim và chuyển thành `HTTP`, `HTTPS` hoặc `SSH`.
- `clientIp` được trim; không kiểm tra định dạng IP ở tầng API.
- `priority` mặc định `0` nếu bỏ trống.
- `enabled` mặc định `true` nếu bỏ trống.

Response `200`:

```json
{
  "rule": {
    "id": 2,
    "clientIp": null,
    "target": "example.com",
    "targetPort": 80,
    "protocol": "HTTP",
    "action": "ALLOW",
    "priority": 100,
    "enabled": true,
    "createdAt": "2026-11-22T11:20:00Z",
    "updatedAt": "2026-11-22T11:20:00Z"
  },
  "warning": null
}
```

`warning` khác `null` khi rule đã lưu nhưng không reload được proxy, ví dụ
Proxy Control API đang tắt.

### `PUT /api/rules/{id}`

Cập nhật toàn bộ rule. Request giống `POST /api/rules`. Response giống
`POST`, với `rule.id` là ID hiện có.

### `PATCH /api/rules/{id}/toggle`

Bật/tắt rule mà không cần gửi toàn bộ dữ liệu.

Request:

```json
{ "enabled": false }
```

Response giống `PUT`.

### `DELETE /api/rules/{id}`

Xóa rule. Response:

```json
{
  "rule": null,
  "warning": "Chưa bật proxy control; khởi động lại proxy để áp dụng thay đổi."
}
```

## 5. Connection Logs

### `GET /api/logs`

Tham số:

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `clientIp` | string | Lọc theo IP client (khớp chính xác) |
| `target` | string | Lọc tương đối theo target |
| `protocol` | string | `HTTP`, `HTTPS`, `SSH` |
| `decision` | string | `ALLOW`, `BLOCK` |
| `executionStatus` | string | `CONNECTED`, `BLOCKED`, `ERROR` |
| `from` | date | Từ ngày, định dạng `YYYY-MM-DD` (UTC) |
| `to` | date | Đến ngày, định dạng `YYYY-MM-DD` (UTC) |
| `page` | int | Trang, mặc định `0` |
| `size` | int | Kích thước trang, mặc định `20` |

Ví dụ:

```http
GET /api/logs?clientIp=127.0.0.1&decision=BLOCK&from=2026-11-01&to=2026-11-22
```

Response:

```json
{
  "content": [
    {
      "id": 10,
      "occurredAt": "2026-11-22T11:30:00Z",
      "clientIp": "127.0.0.1",
      "target": "example.com",
      "targetPort": 80,
      "protocol": "HTTP",
      "decision": "ALLOW",
      "matchedRuleId": 1,
      "reasonCode": "RULE_ALLOW",
      "executionStatus": "CONNECTED",
      "errorMessage": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `GET /api/logs/{id}`

Trả về một log hoặc `404`.

## 6. Clients

### `GET /api/clients`

Tham số:

| Tham số | Kiểu | Mô tả |
|---|---|---|
| `q` | string | Tìm tương đối theo IP |
| `page` | int | Trang, mặc định `0` |
| `size` | int | Kích thước trang, mặc định `20` |

Response:

```json
{
  "content": [
    {
      "clientIp": "127.0.0.1",
      "totalRequests": 20,
      "allowed": 15,
      "blocked": 4,
      "errors": 1,
      "lastSeen": "2026-11-22T11:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 7. Audit Logs

### `GET /api/audit-logs`

Tham số `page`, `size` như các endpoint danh sách khác.

Response:

```json
{
  "content": [
    {
      "id": 2,
      "createdAt": "2026-11-22T11:07:37.906Z",
      "userId": 1,
      "username": "admin",
      "action": "CREATE",
      "entityType": "ACCESS_RULE",
      "entityId": 1,
      "details": "Rule #1 | client=any | target=example.com | port=80 | protocol=HTTP | action=ALLOW | priority=100 | enabled=true"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Các giá trị `action` hiện dùng: `LOGIN`, `LOGOUT`, `CHANGE_PASSWORD`, `CREATE`,
`UPDATE`, `DELETE`, `ENABLE`, `DISABLE`.

## 8. Health check

### `GET /api/health`

Endpoint công khai, không cần đăng nhập.

```json
{
  "status": "ok",
  "service": "network-proxy-admin-api"
}
```

## 9. Proxy Control API (nội bộ)

Proxy Control API chạy trong `proxy-server`, mặc định
`http://127.0.0.1:8082`, và chỉ dành cho Admin API gọi nội bộ. Mọi request
phải gửi header:

```http
X-Admin-Token: dev-token-change-me
```

### `GET /admin/health`

```json
{ "status": "ok" }
```

### `GET /admin/stats`

```json
{
  "status": "ok",
  "activeClients": 2,
  "loadedRules": 3,
  "uptimeSeconds": 3600,
  "version": "1.0"
}
```

### `POST /admin/reload-rules`

Reload danh sách rule đang bật từ database vào bộ nhớ proxy.

Response thành công:

```json
{ "status": "ok", "rules": 3 }
```

Response lỗi:

```json
{
  "status": "error",
  "message": "Cannot reload rules from database"
}
```

Mã lỗi: `401` khi thiếu/sai token, `405` khi sai method, `500` khi reload thất
bại.

## 10. Ví dụ curl

Đăng nhập và lưu cookie:

```bash
curl -i -c cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  http://localhost:8081/api/auth/login
```

Lấy dashboard:

```bash
curl -b cookies.txt http://localhost:8081/api/dashboard/summary
```

Tạo rule:

```bash
curl -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"target":"example.com","targetPort":80,"protocol":"HTTP","action":"ALLOW","priority":100,"enabled":true}' \
  http://localhost:8081/api/rules
```

Kiểm tra Proxy Control API trực tiếp:

```bash
curl -H "X-Admin-Token: dev-token-change-me" \
  http://127.0.0.1:8082/admin/stats
```

## 11. Ghi chú nghiệp vụ

- Proxy mặc định **deny**: nếu không có rule ALLOW khớp, kết nối bị chặn.
- Rule có `priority` cao hơn được xét trước. Nếu cùng priority, rule cụ thể
  hơn (có client IP/target/port/protocol) được xét trước; BLOCK được ưu tiên
  hơn ALLOW khi cùng độ cụ thể.
- `target` trong rule là hostname client yêu cầu, không phải IP sau khi phân
  giải DNS.
- `clientIp` hiện được so khớp chính xác, chưa hỗ trợ CIDR.
- Khi Proxy Control API tắt, rule vẫn được lưu vào database nhưng proxy cần
  khởi động lại để nạp rule mới.
