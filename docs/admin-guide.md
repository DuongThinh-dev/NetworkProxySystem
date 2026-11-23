# Hướng dẫn chạy Admin Backend + Dashboard

Tài liệu này hướng dẫn từng bước chạy **Phase 6: Spring Boot Admin Backend và
Dashboard** của NetworkProxySystem trên Windows.

## 1. Kiến trúc khi chạy

```text
Trình duyệt
   │  http://localhost:8081
   ▼
admin-api (Spring Boot)
   │  JDBC
   ├──────────────► SQL Server: NetworkProxyDB
   │
   │  HTTP + X-Admin-Token (tùy chọn)
   ▼
proxy-server (Java 17)
   │  HTTP/HTTPS/SSH
   ▼
Internet / máy chủ đích
```

- `admin-api` phục vụ cả REST API và dashboard tĩnh.
- `proxy-server` là proxy thật mà client sử dụng.
- Hai tiến trình dùng chung database `NetworkProxyDB`.
- Proxy Control API (mặc định `127.0.0.1:8082`) chỉ dùng để Admin reload rule.

## 2. Yêu cầu

- Windows 10/11.
- JDK 17.
- Apache Maven 3.9+.
- SQL Server 2019/2022/2025 (Express cũng được).
- `sqlcmd`.
- Trình duyệt hiện đại (Chrome, Edge, Firefox).

Kiểm tra nhanh:

```powershell
java -version
mvn -version
sqlcmd -?
```

Nếu `mvn` chưa có trong `PATH`, dùng đường dẫn đầy đủ tới `mvn.cmd`.

## 3. Chuẩn bị SQL Server

### 3.1. Kiểm tra service

```powershell
Get-Service "MSSQL`$SQLEXPRESS"
```

Service phải ở trạng thái `Running`. Nếu chưa:

```powershell
Start-Service "MSSQL`$SQLEXPRESS"
```

### 3.2. Tạo database và bảng

Chạy từ thư mục gốc dự án:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql
```

Kiểm tra:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "SELECT name FROM sys.tables ORDER BY name;"
```

Kết quả mong đợi:

```text
AccessRules
AdminAuditLogs
AdminUsers
ConnectionLogs
```

Tùy chọn nạp dữ liệu mẫu:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/insert-sample-data.sql
```

> Script có kiểm tra `IF OBJECT_ID(...) IS NULL`, nên chạy lại không làm mất
> dữ liệu đã có.

### 3.3. Chọn cách xác thực

#### Cách A — SQL Server Authentication

Phù hợp khi SQL Server đã bật **Mixed Mode**.

1. Mở SQL Server Management Studio (SSMS).
2. Chuột phải server → **Properties** → **Security**.
3. Chọn **SQL Server and Windows Authentication mode**.
4. Restart service:

```powershell
Restart-Service "MSSQL`$SQLEXPRESS"
```

5. Tạo login và cấp quyền (ví dụ dùng `sa` có sẵn, hoặc tạo login riêng):

```sql
USE master;
GO
CREATE LOGIN networkproxy
WITH PASSWORD = 'MatKhauManh123!', CHECK_POLICY = ON;
GO
USE NetworkProxyDB;
GO
CREATE USER networkproxy FOR LOGIN networkproxy;
ALTER ROLE db_datareader ADD MEMBER networkproxy;
ALTER ROLE db_datawriter ADD MEMBER networkproxy;
ALTER ROLE db_ddladmin ADD MEMBER networkproxy;
GO
```

Sau đó dùng:

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_USERNAME = "networkproxy"
$env:NETWORK_PROXY_DB_PASSWORD = "MatKhauManh123!"
$env:NETWORK_PROXY_DB_ENABLED = "true"
```

Nếu SQL Server Express dùng named instance thay vì TCP port 1433:

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"
```

#### Cách B — Windows Integrated Security

Phù hợp với máy chỉ bật Windows Authentication (như SQL Server Express mặc
định trong nhiều bản cài).

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_ENABLED = "true"
```

Cách này cần thư viện native của JDBC driver:

1. Tải file:

```text
https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.6.0.x64/mssql-jdbc_auth-13.6.0.x64.dll
```

2. Đặt vào một thư mục, ví dụ:

```text
C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll
```

3. Khi chạy Admin API bằng JAR, truyền:

```powershell
java "-Djava.library.path=C:\tools\mssql-auth" -jar admin-api-1.0-SNAPSHOT.jar
```

4. Khi chạy proxy bằng Maven, đặt:

```powershell
$env:MAVEN_OPTS = "-Djava.library.path=C:\tools\mssql-auth"
```

> Nếu dùng `mvn spring-boot:run`, có thể truyền:
> `mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Djava.library.path=C:\tools\mssql-auth"`

### 3.4. Cấu hình IntelliJ IDEA

Nếu bấm Run trực tiếp trên `com.networkproxy.AdminApplication`, cấu hình
Run Configuration phải có DLL native và biến môi trường. Mở:

```text
Run → Edit Configurations → AdminApplication
```

**VM options:**

```text
-Djava.library.path=C:\Users\ADMIN\AppData\Local\Temp\opencode\mssql-auth --enable-native-access=ALL-UNNAMED
```

**Environment variables:**

```text
NETWORK_PROXY_DB_URL=jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10
NETWORK_PROXY_DB_USERNAME=
NETWORK_PROXY_DB_PASSWORD=
PROXY_CONTROL_ENABLED=true
PROXY_CONTROL_URL=http://127.0.0.1:8082
PROXY_CONTROL_TOKEN=dev-token-change-me
```

Hoặc chọn cấu hình có sẵn `AdminApplication (SQL Express)` trong thư mục
`.run`. Nếu DLL nằm ở thư mục khác, cập nhật lại `java.library.path`.

> `application.properties` mặc định đã dùng Integrated Security với
> `localhost\SQLEXPRESS`. Biến `NETWORK_PROXY_DB_URL` chỉ cần thiết khi
> dùng SQL Authentication hoặc một instance SQL Server khác.

## 4. Chạy Admin API

Mở PowerShell tại thư mục gốc dự án:

```powershell
cd admin-api
mvn spring-boot:run
```

Hoặc build JAR rồi chạy:

```powershell
mvn -DskipTests package
java -jar target/admin-api-1.0-SNAPSHOT.jar
```

Nếu dùng Integrated Security, thay dòng `java -jar` bằng:

```powershell
java "-Djava.library.path=C:\tools\mssql-auth" -jar target/admin-api-1.0-SNAPSHOT.jar
```

Khi thành công, log có dòng tương tự:

```text
Tomcat started on port 8081 (http)
Started AdminApplication
```

Mở trình duyệt:

```text
http://localhost:8081
```

Tài khoản mặc định:

```text
username: admin
password: admin123
```

Tài khoản chỉ được tạo khi bảng `AdminUsers` đang rỗng. Muốn đổi trước lần
chạy đầu tiên:

```powershell
$env:ADMIN_DEFAULT_USERNAME = "admin"
$env:ADMIN_DEFAULT_PASSWORD = "MatKhauManh123!"
$env:ADMIN_DEFAULT_DISPLAY_NAME = "Quản trị viên"
```

Sau khi đăng nhập, đổi mật khẩu qua menu tài khoản → **Đổi mật khẩu**.

## 5. Chạy Proxy Server

Mở PowerShell thứ hai tại thư mục gốc dự án:

```powershell
cd proxy-server
mvn exec:java
```

Proxy lắng nghe tại `localhost:8080`.

Nếu dùng Integrated Security:

```powershell
$env:MAVEN_OPTS = "-Djava.library.path=C:\tools\mssql-auth"
mvn exec:java
```

Nếu dùng SQL Authentication, đặt các biến môi trường giống phần 3.3 trước khi
chạy.

Kiểm tra proxy đã lên:

```powershell
Test-NetConnection localhost -Port 8080
```

Cấu hình trình duyệt hoặc client:

```text
HTTP proxy:  localhost:8080
HTTPS proxy: localhost:8080
```

## 6. Bật Proxy Control để reload rule tự động

Mặc định control API tắt. Nếu không bật, rule vẫn được lưu vào database nhưng
proxy cần khởi động lại để nạp rule mới.

Bật ở **cả hai** tiến trình với cùng token.

### Proxy Server

```powershell
$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_HOST = "127.0.0.1"
$env:PROXY_CONTROL_PORT = "8082"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"
cd proxy-server
mvn exec:java
```

### Admin API

```powershell
$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_URL = "http://127.0.0.1:8082"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"
cd admin-api
mvn spring-boot:run
```

Kiểm tra trực tiếp:

```powershell
curl.exe -H "X-Admin-Token: dev-token-change-me" http://127.0.0.1:8082/admin/stats
```

Kết quả:

```json
{"status":"ok","activeClients":0,"loadedRules":1,"uptimeSeconds":12,"version":"1.0"}
```

Dashboard sẽ hiển thị **Proxy online** và số client/rule.

## 7. Sử dụng dashboard

### 7.1. Tổng quan

Hiển thị:

- tổng số rule và số rule đang bật;
- tổng kết nối, số kết nối 24 giờ gần nhất;
- số bị chặn và tỉ lệ;
- số client duy nhất;
- biểu đồ ALLOW / BLOCK / ERROR;
- top 5 target;
- 8 log gần nhất;
- 5 audit log gần nhất;
- trạng thái Proxy Control.

### 7.2. Tạo rule ALLOW

1. Vào **Access Rules**.
2. Bấm **+ Thêm rule**.
3. Điền:

```text
Client IP:  (để trống = mọi IP)
Target:     example.com
Port:       80
Protocol:   HTTP
Action:     ALLOW
Priority:   100
Enabled:    checked
```

4. Bấm **Tạo rule**.
5. Nếu Proxy Control đang bật, dashboard hiện toast thành công và proxy reload
   ngay. Nếu tắt, toast cảnh báo "khởi động lại proxy".

### 7.3. Tạo rule BLOCK

Làm tương tự, chọn `Action = BLOCK`. Rule BLOCK có priority cao hơn sẽ chặn
target tương ứng.

### 7.4. Kiểm tra traffic

Với trình duyệt đã cấu hình proxy `localhost:8080`, mở:

```text
http://example.com
```

Sau đó vào **Connection Logs** để xem bản ghi mới. Nếu bị chặn, `Decision =
BLOCK`, `Execution status = BLOCKED`.

Có thể test nhanh bằng curl:

```powershell
curl.exe -x http://localhost:8080 http://example.com
```

### 7.5. Xem client và audit

- **Clients**: tổng hợp theo IP, số request, ALLOW/BLOCK/ERROR, lần cuối.
- **Audit Logs**: đăng nhập, đăng xuất, đổi mật khẩu, thêm/sửa/xóa/bật/tắt rule.

## 8. Kịch bản kiểm thử end-to-end

1. Chạy SQL Server, tạo database/bảng.
2. Chạy Admin API, đăng nhập `admin/admin123`.
3. Chạy proxy với DB bật và control bật.
4. Trên dashboard, xác nhận **Proxy online**.
5. Tạo rule ALLOW `example.com:80/HTTP`, priority 100.
6. Chạy:

```powershell
curl.exe -x http://localhost:8080 http://example.com
```

7. Vào **Connection Logs**, thấy log `ALLOW / CONNECTED`, `matchedRuleId = 1`.
8. Tạo rule BLOCK `example.com:80/HTTP`, priority 200.
9. Chạy lại curl, thấy log `BLOCK / BLOCKED`.
10. Vào **Clients**, thấy IP `127.0.0.1` (hoặc IP máy) với số request tương ứng.
11. Vào **Audit Logs**, thấy `CREATE` cho cả hai rule.
12. Tắt proxy, dashboard chuyển **Proxy offline**; bật lại, dashboard online.

## 9. Xử lý sự cố

### Không kết nối được SQL Server

Triệu chứng:

```text
The TCP/IP connection to the host localhost, port 1433 has failed
```

Kiểm tra:

```powershell
Get-Service "MSSQL`$SQLEXPRESS"
Test-NetConnection localhost -Port 1433
```

- Nếu dùng named instance, dùng `localhost\SQLEXPRESS` trong JDBC URL.
- Bật TCP/IP trong SQL Server Configuration Manager nếu cần.
- Thêm `trustServerCertificate=true` khi dùng chứng chỉ tự ký.
- Kiểm tra `NETWORK_PROXY_DB_URL`, username, password.

### Lỗi `AdminUsers` không tồn tại

Chạy lại:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
```

### Đăng nhập trả 401

- Kiểm tra đúng `admin` / `admin123`.
- Nếu đã đổi mật khẩu, dùng mật khẩu mới.
- Xóa cookie trình duyệt và đăng nhập lại.
- Kiểm tra bảng `AdminUsers` có dòng `admin` chưa.

### Dashboard báo Proxy offline

- Kiểm tra `PROXY_CONTROL_ENABLED=true` ở **cả** admin-api và proxy-server.
- Kiểm tra token hai bên giống nhau.
- Kiểm tra port `8082` không bị chiếm:

```powershell
Get-NetTCPConnection -LocalPort 8082
```

- Gọi trực tiếp `/admin/stats` bằng curl.

### Rule mới không có hiệu lực ngay

- Bật Proxy Control ở cả hai phía, hoặc restart proxy-server.
- Kiểm tra toast cảnh báo trong dashboard.
- Xem log proxy-server có dòng reload rule không.

### Tất cả request bị BLOCK

Proxy mặc định **deny**. Cần tạo rule ALLOW khớp target/port/protocol. Kiểm tra:

- rule có `Enabled = true`;
- priority đủ cao;
- target đúng hostname (không phải IP sau DNS);
- protocol/port khớp.

### Port 8081 hoặc 8080 đã bị chiếm

```powershell
Get-NetTCPConnection -LocalPort 8081,8080,8082 -State Listen
```

Dừng tiến trình đang chiếm hoặc đổi `server.port` / `proxy.port`.

## 10. Lưu ý bảo mật

- Đổi mật khẩu `admin` mặc định ngay.
- Dùng token mạnh cho `PROXY_CONTROL_TOKEN`; không dùng
  `dev-token-change-me` ngoài môi trường local.
- Không mở `8081` và `8082` ra Internet.
- Control API chỉ bind `127.0.0.1`.
- Không commit mật khẩu SQL Server, token hay private key vào Git.
- Dashboard hiện dùng HTTP cho môi trường học tập. Khi triển khai thật, cần
  HTTPS, CSRF protection, rate limiting và phân quyền chi tiết.
- Session dùng cookie `HttpOnly`, `SameSite=Lax`; không lưu token trong
  localStorage.
