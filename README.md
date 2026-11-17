# NetworkProxySystem

Hệ thống proxy mạng viết bằng Java, hỗ trợ xử lý HTTP, HTTPS thông qua
`CONNECT` và kết nối SSH đi ra. Thành phần proxy hiện tại là một TCP proxy
độc lập, không giải mã nội dung TLS.

## Thành phần

- `proxy-server/`: máy chủ proxy Java 17.
  - HTTP request được chuyển tiếp tới máy chủ đích.
  - HTTPS sử dụng HTTP `CONNECT` và chuyển tiếp dữ liệu hai chiều.
  - SSH sử dụng Apache MINA SSHD cho các kết nối đi ra.
  - Có kiểm soát địa chỉ riêng tư và xác thực SSH bằng `known_hosts`.
- `admin-api/`: khung thành phần API quản trị, hiện chưa có cấu hình Maven
  và mã chức năng hoàn chỉnh.
- `admin-web/`: giao diện quản trị đang được xây dựng.
- `database/`: vị trí dành cho các tập lệnh cơ sở dữ liệu.
- `docs/`: tài liệu kiến trúc, cấu hình, kiểm thử và hướng dẫn sử dụng.

## Yêu cầu

Xem đầy đủ tại [requirements.txt](requirements.txt). Tối thiểu cần:

- JDK 17 trở lên.
- Apache Maven 3.9 trở lên.
- Một tệp SSH `known_hosts` hợp lệ nếu sử dụng chức năng SSH.

Các thư viện Java được Maven tải tự động từ `pom.xml`; không cần cài thủ
công Apache MINA SSHD hoặc JUnit.

## Cài đặt và chạy proxy

```bash
cd proxy-server
mvn clean test
mvn clean package
mvn exec:java
```

Sau khi khởi động, proxy lắng nghe tại `localhost:8080` theo cấu hình mặc
định.

Ví dụ cấu hình ứng dụng khách:

```text
HTTP proxy:  localhost:8080
HTTPS proxy: localhost:8080
```

## Cấu hình

Cấu hình nằm trong
[`proxy-server/src/main/resources/application.properties`](proxy-server/src/main/resources/application.properties).

Một số tùy chọn chính:

| Tùy chọn | Mặc định | Mô tả |
|---|---:|---|
| `proxy.port` | `8080` | Cổng lắng nghe của proxy |
| `proxy.max-threads` | `50` | Số luồng xử lý tối đa |
| `proxy.client-timeout` | `60000` | Timeout phía client, tính bằng mili-giây |
| `https.enabled` | `true` | Cho phép HTTPS tunnel |
| `https.allow-private-destinations` | `false` | Cho phép đích private/loopback |
| `ssh.enabled` | `true` | Cho phép kết nối SSH đi ra |
| `ssh.known-hosts-path` | `~/.ssh/known_hosts` | Đường dẫn tệp khóa máy chủ SSH |

Không ghi mật khẩu, token hoặc nội dung private key vào tệp cấu hình hay
log. Chỉ bật `https.allow-private-destinations=true` trong môi trường LAN
được kiểm soát và được cấp quyền.

## Kiểm thử

Chạy toàn bộ kiểm thử của module proxy:

```bash
cd proxy-server
mvn test
```

Các kiểm thử hiện có nằm trong
[`proxy-server/src/test`](proxy-server/src/test).

## Tài liệu

- [Kiến trúc](docs/architecture.md)
- [Hướng dẫn sử dụng](docs/user-guide.md)
- [Các ca kiểm thử](docs/test-cases.md)

## Giấy phép

Chưa khai báo giấy phép cho dự án.
