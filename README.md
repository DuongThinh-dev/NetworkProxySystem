# NetworkProxySystem

Hệ thống proxy mạng viết bằng Java, hỗ trợ xử lý HTTP, HTTPS thông qua
`CONNECT` và kết nối SSH đi ra. Thành phần proxy là một TCP proxy độc lập,
không giải mã nội dung TLS.

Dự án đã hoàn thiện thêm **Phase 6: Spring Boot Admin Backend + Dashboard**
để quản lý Access Rules, Connection Logs, Clients và Audit Logs qua trình duyệt.

## Thành phần

- `proxy-server/`: máy chủ proxy Java 17.
  - HTTP request được chuyển tiếp tới máy chủ đích.
  - HTTPS sử dụng HTTP `CONNECT` và chuyển tiếp dữ liệu hai chiều.
  - SSH sử dụng Apache MINA SSHD cho các kết nối đi ra.
  - Có kiểm soát địa chỉ riêng tư và xác thực SSH bằng `known_hosts`.
  - Có **Proxy Control API** tùy chọn để Admin reload rule mà không cần
    khởi động lại proxy.
- `admin-api/`: Spring Boot 3.3.5 + Spring JDBC + SQL Server.
  - REST API cho đăng nhập, dashboard, Access Rules, Connection Logs,
    Clients và Audit Logs.
  - Xác thực bằng `HttpSession` + BCrypt, không dùng filter chain đầy đủ
    của Spring Security.
  - Tự tạo tài khoản quản trị mặc định khi bảng `AdminUsers` đang rỗng.
- `admin-web/`: dashboard HTML/CSS/JavaScript thuần, không cần Node.js hay
  bước build frontend. Maven copy thư mục này vào static resources của
  `admin-api`.
- `database/`: script tạo database, bảng, index và dữ liệu mẫu.
- `docs/`: tài liệu kiến trúc, API, database và hướng dẫn sử dụng.

## Yêu cầu

- JDK 17 trở lên.
- Apache Maven 3.9 trở lên.
- SQL Server 2019/2022/2025 (Express, Developer hoặc Enterprise).
- `sqlcmd` (đi kèm SQL Server Command Line Tools) để chạy script.
- Một tệp SSH `known_hosts` hợp lệ nếu sử dụng chức năng SSH.

Các thư viện Java được Maven tải tự động từ `pom.xml`; không cần cài thủ
công Apache MINA SSHD, Spring Boot hay JUnit.

## Chạy nhanh

### 1. Tạo database và bảng

Chạy từ thư mục gốc dự án:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql
```

Nếu SQL Server dùng TCP port cố định, thay `.\SQLEXPRESS` bằng
`localhost,1433` hoặc `localhost`. Script có `IF OBJECT_ID(...) IS NULL`
nên có thể chạy lại an toàn.

Tùy chọn, nạp dữ liệu mẫu:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/insert-sample-data.sql
```

### 2. Cấu hình kết nối database

Với SQL Server Express cài mặc định trên máy local, dùng **Cách B — Windows
Integrated Security**. `admin-api` đã cấu hình sẵn cách này; chỉ cần thêm
`java.library.path` khi chạy.

Đặt các biến môi trường dùng chung cho cả `admin-api` và `proxy-server`
(nếu dùng cách khác mặc định).

**Cách A — SQL Server Authentication (khi đã bật mixed mode):**

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_USERNAME = "sa"
$env:NETWORK_PROXY_DB_PASSWORD = "YourStrongPassword"
$env:NETWORK_PROXY_DB_ENABLED = "true"
```

Cần bật **SQL Server and Windows Authentication mode** trong SQL Server
Properties → Security, sau đó restart service `MSSQL$SQLEXPRESS`.

**Cách B — Windows Integrated Security (khuyến nghị cho SQL Server Express local):**

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_ENABLED = "true"
```

Cách B cần file `mssql-jdbc_auth-13.6.0.x64.dll` nằm trong
`java.library.path` của tiến trình Java. Khi chạy `AdminApplication` từ
IntelliJ, thêm VM option
`-Djava.library.path=C:\Users\ADMIN\AppData\Local\Temp\opencode\mssql-auth`
hoặc chọn run configuration `AdminApplication (SQL Express)` trong thư mục
`.run`. Xem [hướng dẫn Admin](docs/admin-guide.md) để biết chi tiết.

### 3. Chạy Admin API

```powershell
cd admin-api
mvn spring-boot:run
```

Hoặc build JAR và chạy:

```powershell
mvn -DskipTests package
java -jar target/admin-api-1.0-SNAPSHOT.jar
```

Mở trình duyệt tại **http://localhost:8081**.

Tài khoản mặc định:

```text
username: admin
password: admin123
```

Tài khoản chỉ được tạo khi bảng `AdminUsers` rỗng. Có thể đổi trước lần
khởi động đầu tiên bằng:

```powershell
$env:ADMIN_DEFAULT_USERNAME = "admin"
$env:ADMIN_DEFAULT_PASSWORD = "MatKhauManh123"
$env:ADMIN_DEFAULT_DISPLAY_NAME = "Quản trị viên"
```

Sau khi đăng nhập, dùng menu tài khoản → **Đổi mật khẩu**.

### 4. Chạy Proxy Server

Mở terminal khác:

```powershell
cd proxy-server
mvn exec:java
```

Proxy lắng nghe tại `localhost:8080`. Cấu hình client:

```text
HTTP proxy:  localhost:8080
HTTPS proxy: localhost:8080
```

Nếu dùng Windows Integrated Security với `mvn exec:java`, đặt
`MAVEN_OPTS` để Maven JVM tìm thấy DLL:

```powershell
$env:MAVEN_OPTS = "-Djava.library.path=C:\path\to\mssql-auth"
```

### 5. Bật reload rule tự động (tùy chọn)

Mặc định Proxy Control API tắt. Để Admin có thể reload rule ngay sau khi
lưu, bật ở cả hai phía với cùng một token.

**Proxy Server:**

```powershell
$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"
cd proxy-server
mvn exec:java
```

**Admin API:**

```powershell
$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"
cd admin-api
mvn spring-boot:run
```

Control API chỉ bind `127.0.0.1:8082` theo mặc định. Dashboard sẽ hiển thị
trạng thái **Proxy online** và số client/rule đang hoạt động.

## Cấu hình

### Proxy Server

Cấu hình nằm trong
[`proxy-server/src/main/resources/application.properties`](proxy-server/src/main/resources/application.properties).

| Tùy chọn | Mặc định | Mô tả |
|---|---:|---|
| `proxy.port` | `8080` | Cổng lắng nghe của proxy |
| `proxy.max-threads` | `50` | Số luồng xử lý tối đa |
| `proxy.client-timeout` | `60000` | Timeout phía client, mili-giây |
| `https.enabled` | `true` | Cho phép HTTPS tunnel |
| `https.allow-private-destinations` | `false` | Cho phép đích private/loopback |
| `ssh.enabled` | `true` | Cho phép kết nối SSH đi ra |
| `ssh.known-hosts-path` | `~/.ssh/known_hosts` | Đường dẫn tệp khóa máy chủ SSH |
| `proxy.control.enabled` | `false` | Bật control API cho Admin |
| `proxy.control.host` | `127.0.0.1` | Địa chỉ bind của control API |
| `proxy.control.port` | `8082` | Cổng control API |
| `proxy.control.token` | `dev-token-change-me` | Token header `X-Admin-Token` |

Các biến môi trường tương ứng: `PROXY_CONTROL_ENABLED`,
`PROXY_CONTROL_HOST`, `PROXY_CONTROL_PORT`, `PROXY_CONTROL_TOKEN`.

### Admin API

Cấu hình nằm trong
[`admin-api/src/main/resources/application.properties`](admin-api/src/main/resources/application.properties).

| Tùy chọn | Mặc định | Mô tả |
|---|---:|---|
| `server.port` | `8081` | Cổng Admin API/dashboard |
| `spring.datasource.url` | SQL Server local | URL JDBC, ưu tiên `NETWORK_PROXY_DB_URL` |
| `admin.default-username` | `admin` | Tài khoản seed lần đầu |
| `admin.default-password` | `admin123` | Mật khẩu seed lần đầu |
| `proxy.control.enabled` | `false` | Gọi control API khi rule thay đổi |
| `proxy.control.url` | `http://127.0.0.1:8082` | Địa chỉ control API |
| `proxy.control.token` | `dev-token-change-me` | Token phải khớp proxy |

## Tính năng Admin

- **Dashboard**: tổng rule, tổng kết nối, số bị chặn, số client, biểu đồ
  ALLOW/BLOCK/ERROR, top target, log gần đây, audit gần đây và trạng thái
  proxy control.
- **Access Rules**: thêm/sửa/xóa, bật/tắt, lọc theo từ khóa và trạng thái.
  Target được chuẩn hóa lowercase, bỏ dấu chấm cuối; protocol được chuẩn hóa
  `HTTP`/`HTTPS`/`SSH`.
- **Connection Logs**: lọc theo client IP, target, protocol, decision,
  execution status và khoảng ngày; xem chi tiết từng bản ghi.
- **Clients**: tổng hợp request/ALLOW/BLOCK/ERROR theo IP, lần cuối hoạt động,
  liên kết nhanh sang log của client.
- **Audit Logs**: ghi nhận đăng nhập, đăng xuất, đổi mật khẩu và thao tác
  thêm/sửa/xóa/bật/tắt rule.
- **Đổi mật khẩu** ngay trong dashboard.

## Kiểm thử

```powershell
cd proxy-server
mvn test

cd ..\admin-api
mvn test
```

`proxy-server` có bộ test hiện có. `admin-api` được build và kiểm tra khởi
động với SQL Server; nếu chưa cấu hình database, ứng dụng sẽ báo lỗi rõ
ràng và yêu cầu chạy script schema trước.

## Tài liệu

- [Hướng dẫn Admin chi tiết](docs/admin-guide.md)
- [Hợp đồng API Admin](docs/api-contracts.md)
- [Thiết kế database](docs/database-design.md)
- [Kiến trúc](docs/architecture.md)
- [Hướng dẫn sử dụng proxy](docs/user-guide.md)
- [Các ca kiểm thử](docs/test-cases.md)

## Lưu ý bảo mật

- Đổi mật khẩu `admin` mặc định ngay sau lần đăng nhập đầu tiên.
- Dùng token mạnh cho `proxy.control.token`; không dùng giá trị mặc định
  khi triển khai ngoài máy local.
- Không mở cổng `8081`/`8082` ra Internet. Control API chỉ nên bind
  `127.0.0.1`.
- Không commit mật khẩu SQL Server, token hoặc private key vào Git.
- Dashboard phục vụ qua HTTP cho môi trường học tập/local. Khi triển khai
  thật, cần thêm HTTPS, CSRF protection, rate limiting và phân quyền chi tiết.
- Proxy mặc định **deny** nếu không có rule ALLOW khớp. Khi bật database,
  hãy tạo rule ALLOW phù hợp trước khi kiểm thử traffic.

## Giấy phép

Chưa khai báo giấy phép cho dự án.
