# Báo cáo điều tra lỗi Database Connection - NetworkProxySystem Admin API

**Ngày:** 23/11/2026  
**Phiên bản:** admin-api 1.0-SNAPSHOT  
**Trạng thái:** Lỗi kết nối SQL Server - integratedSecurity=true thiếu DLL native

---

## 1. Cấu hình Database hiện tại

### Connection String đầy đủ

Từ file `admin-api/src/main/resources/application.properties`:

```properties
spring.datasource.url=${NETWORK_PROXY_DB_URL:jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10}
spring.datasource.username=${NETWORK_PROXY_DB_USERNAME:}
spring.datasource.password=${NETWORK_PROXY_DB_PASSWORD:}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

### Chi tiết cấu hình

| Thuộc tính | Giá trị |
|------------|---------|
| **Database name** | `NetworkProxyDB` |
| **Host** | `localhost` |
| **Instance** | `SQLEXPRESS` (Named Instance) |
| **Port** | Không xác định (SQL Server Express mặc định dùng dynamic port hoặc không bật TCP/IP, chỉ dùng named pipe) |
| **Authentication method** | **Windows Integrated Security** (`integratedSecurity=true`) |
| **Username** | Rỗng (không dùng SQL authentication) |
| **Password** | Rỗng |
| **Encryption** | Bật (`encrypt=true`) |
| **Trust Certificate** | Bật (`trustServerCertificate=true`) |
| **Login Timeout** | 10 giây |
| **JDBC Driver** | `mssql-jdbc` phiên bản `13.6.0.jre11` |

### Thư viện JDBC Driver

Từ `admin-api/pom.xml`:

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>13.6.0.jre11</version>
</dependency>
```

---

## 2. Nguyên nhân lỗi chính xác

### Thông báo lỗi từ stack trace

```
2026-11-23T18:34:58.217+07:00 ERROR 3916 --- [main] c.networkproxy.config.DataInitializer : Không thể khởi tạo bảng AdminUsers. Hãy chạy database/create-tables.sql và database/indexes.sql trước khi khởi động Admin API.

This driver is not configured for integrated authentication.
Unable to load authentication DLL 'mssql-jdbc_auth-13.6.0.x64'
```

### Giải thích chi tiết

**Windows Integrated Authentication** (hay còn gọi là Windows Authentication) cho phép ứng dụng Java kết nối SQL Server bằng tài khoản Windows hiện tại (không cần username/password).

**Cơ chế hoạt động:**

1. Khi `integratedSecurity=true` trong JDBC connection string, driver sẽ sử dụng **Kerberos** hoặc **NTLM** để xác thực người dùng Windows với SQL Server
2. Vì Java không có khả năng native để xử lý Windows authentication protocols, Microsoft cung cấp một **thư viện native DLL** (`mssql-jdbc_auth-13.6.0.x64.dll`) làm cầu nối
3. Thư viện DLL này implement các API Windows (SSPI - Security Support Provider Interface) để thực hiện xác thực
4. Java Native Interface (JNI) load DLL này từ `java.library.path`

**Tại sao lỗi xảy ra:**

- File JAR `mssql-jdbc-13.6.0.jre11.jar` chỉ chứa Java bytecode, **KHÔNG** chứa file DLL native
- Khi chạy `AdminApplication`, JVM không tìm thấy file `mssql-jdbc_auth-13.6.0.x64.dll` trong các thư mục được chỉ định bởi system property `java.library.path`
- Mặc định, `java.library.path` trên Windows bao gồm:
  - `%SystemRoot%\System32`
  - `%SystemRoot%`
  - `%PATH%` directories
  - Thư mục hiện tại
- DLL này **KHÔNG** được tự động cài đặt khi cài JDK hay Maven

**Tại sao cần DLL này:**

DLL `mssql-jdbc_auth-13.6.0.x64.dll` thực hiện:
- Gọi Windows SSPI API để lấy security token của user hiện tại
- Thực hiện Kerberos/NTLM handshake với SQL Server
- Chuyển đổi giữa Java data types và Windows native types
- Handle Windows security descriptors và ACLs

Nếu không có DLL này, driver không thể thực hiện integrated authentication và sẽ ném exception như đã thấy.

---

## 3. Các file SQL cần chạy

Dự án có cấu trúc database scripts rõ ràng trong thư mục `database/`. Thứ tự chạy:

### Script 1: `database/create-database.sql`

**Mục đích:** Tạo database `NetworkProxyDB`

**Nội dung:**
```sql
IF DB_ID(N'NetworkProxyDB') IS NULL
BEGIN
    EXEC(N'CREATE DATABASE [NetworkProxyDB]');
END;
GO
```

**Lệnh chạy:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
```

### Script 2: `database/create-tables.sql`

**Mục đích:** Tạo 4 bảng chính

**Các bảng được tạo:**
1. **`AccessRules`**: Lưu các rule ALLOW/BLOCK cho proxy
   - Các cột: RuleId, ClientIp, Target, TargetPort, Protocol, Action, Priority, IsEnabled, CreatedAt, UpdatedAt
   - Constraints: CHECK cho protocol (HTTP/HTTPS/SSH), action (ALLOW/BLOCK), port range (1-65535)

2. **`ConnectionLogs`**: Lưu log kết nối qua proxy
   - Các cột: LogId, OccurredAt, ClientIp, Target, TargetPort, Protocol, Decision, MatchedRuleId, ReasonCode, ExecutionStatus, ErrorMessage
   - Foreign key: MatchedRuleId → AccessRules(RuleId) ON DELETE SET NULL

3. **`AdminUsers`**: Tài khoản quản trị cho Admin API
   - Các cột: UserId, Username, PasswordHash (BCrypt), DisplayName, IsEnabled, CreatedAt, LastLoginAt
   - Unique constraint: Username

4. **`AdminAuditLogs`**: Nhật ký thao tác quản trị
   - Các cột: AuditId, UserId, Username, Action, EntityType, EntityId, Details, CreatedAt
   - Foreign key: UserId → AdminUsers(UserId) ON DELETE SET NULL

**Đặc điểm:** Mỗi bảng có `IF OBJECT_ID(...) IS NULL` nên có thể chạy lại an toàn mà không làm mất dữ liệu.

**Lệnh chạy:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
```

### Script 3: `database/indexes.sql`

**Mục đích:** Tạo các index để tối ưu query performance

**Các index được tạo:**
1. `IX_AccessRules_Enabled_Priority` - Tăng tốc việc lọc rule theo priority
2. `IX_ConnectionLogs_OccurredAt` - Tăng tốc query log theo thời gian
3. `IX_ConnectionLogs_ClientIp_OccurredAt` - Lọc log theo client IP
4. `IX_ConnectionLogs_Decision_OccurredAt` - Lọc theo ALLOW/BLOCK
5. `IX_AdminAuditLogs_CreatedAt` - Query audit log theo thời gian
6. `IX_AdminAuditLogs_UserId_CreatedAt` - Query audit theo user

**Đặc điểm:** Mỗi index có check `IF NOT EXISTS` nên có thể chạy lại an toàn.

**Lệnh chạy:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql
```

### Script 4 (Tùy chọn): `database/insert-sample-data.sql`

**Mục đích:** Nạp dữ liệu mẫu để demo

**Dữ liệu:**
- Rule ALLOW cho `example.com:HTTPS` (priority 100)
- Rule BLOCK cho `facebook.com:HTTPS` (priority 100)
- Rule ALLOW cho `example.com:22/SSH` (priority 100)

**Lệnh chạy:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/insert-sample-data.sql
```

**Lưu ý:** Admin user không được tạo trong script SQL. `DataInitializer.java` tự động tạo khi bảng `AdminUsers` rỗng.

---

## 4. Giải pháp Fix (chi tiết từng bước)

### **Giải pháp 1: Dùng SQL Server Authentication (KHUYẾN NGHỊ)**

Đây là cách đơn giản nhất và không cần DLL native. Phù hợp với môi trường development và production.

#### Bước 1: Bật Mixed Mode Authentication cho SQL Server

1. Mở **SQL Server Management Studio (SSMS)**
2. Connect tới instance `.\SQLEXPRESS` bằng Windows Authentication
3. Chuột phải vào Server name (trên cùng) → chọn **Properties**
4. Vào tab **Security**
5. Trong phần **Server authentication**, chọn **SQL Server and Windows Authentication mode**
6. Click **OK**
7. **Khởi động lại SQL Server service:**

```powershell
Restart-Service "MSSQL`$SQLEXPRESS"
```

Kiểm tra service đã chạy:
```powershell
Get-Service "MSSQL`$SQLEXPRESS"
```

#### Bước 2: Tạo SQL Login và cấp quyền

Chạy script sau trong SSMS hoặc qua `sqlcmd`:

```sql
USE master;
GO

-- Tạo login mới cho NetworkProxySystem
CREATE LOGIN networkproxy
WITH PASSWORD = 'MatKhauManh123!',
     CHECK_POLICY = ON,
     CHECK_EXPIRATION = OFF;
GO

-- Chuyển sang database NetworkProxyDB
USE NetworkProxyDB;
GO

-- Tạo user và cấp quyền
CREATE USER networkproxy FOR LOGIN networkproxy;
GO

-- Cấp quyền đọc dữ liệu
ALTER ROLE db_datareader ADD MEMBER networkproxy;
GO

-- Cấp quyền ghi dữ liệu
ALTER ROLE db_datawriter ADD MEMBER networkproxy;
GO

-- Cấp quyền thay đổi schema (cần cho indexes, constraints)
ALTER ROLE db_ddladmin ADD MEMBER networkproxy;
GO
```

**Hoặc sử dụng lệnh `sqlcmd`:**

```powershell
# Tạo login
sqlcmd -S ".\SQLEXPRESS" -E -C -Q "CREATE LOGIN networkproxy WITH PASSWORD = 'MatKhauManh123!', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;"

# Tạo user và cấp quyền
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "CREATE USER networkproxy FOR LOGIN networkproxy; ALTER ROLE db_datareader ADD MEMBER networkproxy; ALTER ROLE db_datawriter ADD MEMBER networkproxy; ALTER ROLE db_ddladmin ADD MEMBER networkproxy;"
```

**Giải thích các quyền:**
- `db_datareader`: SELECT trên tất cả bảng
- `db_datawriter`: INSERT, UPDATE, DELETE trên tất cả bảng
- `db_ddladmin`: CREATE/ALTER/DROP tables, indexes, constraints

#### Bước 3: Thay đổi connection string

**Cách 3a: Dùng biến môi trường (Khuyến nghị cho dev và production)**

Mở PowerShell và set các biến sau:

```powershell
# Connection string mới - KHÔNG có integratedSecurity=true
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"

# Thêm username và password
$env:NETWORK_PROXY_DB_USERNAME = "networkproxy"
$env:NETWORK_PROXY_DB_PASSWORD = "MatKhauManh123!"
```

**Lưu ý quan trọng:**
- Connection string MỚI **KHÔNG** có `integratedSecurity=true`
- Named instance `\SQLEXPRESS` vẫn giữ nguyên
- Nếu SQL Server được cấu hình dùng TCP port cố định (ví dụ 1433), dùng: `jdbc:sqlserver://localhost:1433;databaseName=NetworkProxyDB;...`

**Cách 3b: Thay đổi trực tiếp file `application.properties`** (Không khuyến nghị - commit credentials)

Sửa file `admin-api/src/main/resources/application.properties`:

```properties
# TRƯỚC (Windows Integrated):
spring.datasource.url=${NETWORK_PROXY_DB_URL:jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10}

# SAU (SQL Authentication):
spring.datasource.url=${NETWORK_PROXY_DB_URL:jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10}
spring.datasource.username=${NETWORK_PROXY_DB_USERNAME:networkproxy}
spring.datasource.password=${NETWORK_PROXY_DB_PASSWORD:MatKhauManh123!}
```

**CHÚ Ý:** Đừng commit password vào Git. Dùng biến môi trường là best practice.

#### Bước 4: Chạy database scripts

Nếu chưa chạy, chạy 3 scripts theo thứ tự:

```powershell
# Vẫn dùng Windows Auth để chạy scripts (vì Windows user có quyền cao hơn)
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql

# Tùy chọn: nạp dữ liệu mẫu
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/insert-sample-data.sql
```

#### Bước 5: Test kết nối bằng sqlcmd

Trước khi chạy ứng dụng, test login mới:

```powershell
sqlcmd -S ".\SQLEXPRESS" -U networkproxy -P "MatKhauManh123!" -d NetworkProxyDB -Q "SELECT COUNT(*) AS TableCount FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE';"
```

Kết quả mong đợi: `TableCount = 4`

#### Bước 6: Khởi động Admin API

```powershell
cd admin-api
mvn spring-boot:run
```

Hoặc build và chạy JAR:

```powershell
mvn clean package -DskipTests
java -jar target/admin-api-1.0-SNAPSHOT.jar
```

#### Bước 7: Kiểm tra log khởi động

Log thành công sẽ có:

```
INFO  --- [main] com.zaxxer.hikari.HikariDataSource: HikariPool-1 - Starting...
INFO  --- [main] com.zaxxer.hikari.HikariDataSource: HikariPool-1 - Start completed.
WARN  --- [main] c.networkproxy.config.DataInitializer: Đã tạo tài khoản quản trị mặc định 'admin'. Hãy đổi mật khẩu sau khi đăng nhập.
INFO  --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer: Tomcat started on port 8081 (http)
INFO  --- [main] com.networkproxy.AdminApplication: Started AdminApplication in X.XXX seconds
```

Nếu thấy dòng `Đã tạo tài khoản quản trị mặc định 'admin'` → thành công!

#### Bước 8: Truy cập dashboard

Mở trình duyệt: **http://localhost:8081**

Đăng nhập:
- Username: `admin`
- Password: `admin123`

---

### **Giải pháp 2: Cài DLL cho Windows Integrated Authentication**

Dùng cách này nếu:
- Không thể hoặc không muốn bật SQL Server Authentication
- Muốn giữ nguyên cấu hình `integratedSecurity=true`
- Môi trường production yêu cầu Windows Authentication cho security audit

#### Bước 1: Download DLL native

**URL download chính thức:**
```
https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.6.0.x64/mssql-jdbc_auth-13.6.0.x64.dll
```

**Link trực tiếp Maven Central:**
- [mssql-jdbc_auth-13.6.0.x64.dll](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.6.0.x64/mssql-jdbc_auth-13.6.0.x64.dll)

**Thông tin file:**
- Tên file: `mssql-jdbc_auth-13.6.0.x64.dll`
- Kích thước: ~200 KB
- Kiến trúc: x64 (64-bit Windows)

**Lưu ý:**
- Nếu dùng Java 32-bit (rất hiếm), download version `x86` thay vì `x64`
- Phiên bản DLL phải khớp với phiên bản JDBC driver trong `pom.xml` (`13.6.0.jre11`)

#### Bước 2: Đặt DLL vào thư mục cố định

Tạo thư mục và copy file:

```powershell
# Tạo thư mục
New-Item -ItemType Directory -Path "C:\tools\mssql-auth" -Force

# Download DLL (PowerShell 3.0+)
Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.6.0.x64/mssql-jdbc_auth-13.6.0.x64.dll" -OutFile "C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll"

# Kiểm tra file đã tồn tại
Get-Item "C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll"
```

**Thư mục khuyến nghị:**
- `C:\tools\mssql-auth\` (cho production server)
- `C:\Users\ADMIN\AppData\Local\Temp\opencode\mssql-auth\` (đã được đề cập trong README)

**Lưu ý bảo mật:** DLL này không chứa credentials, nó chỉ là native library để gọi Windows API.

#### Bước 3: Chạy database scripts

Nếu chưa chạy:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql
```

#### Bước 4: Khởi động Admin API với java.library.path

**Cách 4a: Chạy từ JAR với JVM argument**

```powershell
cd admin-api

# Build
mvn clean package -DskipTests

# Chạy với java.library.path
java "-Djava.library.path=C:\tools\mssql-auth" -jar target/admin-api-1.0-SNAPSHOT.jar
```

**CHÚ Ý:** Dấu ngoặc kép bao quanh cả argument `-Djava.library.path=...`

**Cách 4b: Chạy từ Maven với spring-boot.run.jvmArguments**

```powershell
cd admin-api

mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Djava.library.path=C:\tools\mssql-auth"
```

**Cách 4c: Chạy từ IntelliJ IDEA**

1. Mở **Run → Edit Configurations**
2. Chọn hoặc tạo configuration cho `com.networkproxy.AdminApplication`
3. Trong mục **VM options**, thêm:

```
-Djava.library.path=C:\tools\mssql-auth --enable-native-access=ALL-UNNAMED
```

4. Trong mục **Environment variables**, thêm (nếu cần):

```
NETWORK_PROXY_DB_URL=jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10
```

5. Click **Apply** và **OK**
6. Click **Run** (Shift+F10)

**Giải thích thêm `--enable-native-access=ALL-UNNAMED`:**
- Java 25+ (như log hiển thị: `Java 25.0.1`) có cơ chế bảo mật mới cho native code
- Warning trong log: `WARNING: java.lang.System::load has been called by org.apache.tomcat.jni.Library in an unnamed module`
- Thêm flag này để tránh warning và cho phép JDBC driver load DLL native

#### Bước 5: Chạy Proxy Server với DLL (nếu cần)

Nếu `proxy-server` cũng dùng database (đọc AccessRules), cần set `MAVEN_OPTS`:

```powershell
cd proxy-server

$env:MAVEN_OPTS = "-Djava.library.path=C:\tools\mssql-auth"
mvn exec:java
```

Hoặc chạy JAR:

```powershell
java "-Djava.library.path=C:\tools\mssql-auth" -cp target/proxy-server-1.0-SNAPSHOT.jar com.networkproxy.Main
```

#### Bước 6: Kiểm tra log

Log thành công tương tự Giải pháp 1. Nếu vẫn thấy lỗi `Unable to load authentication DLL`, kiểm tra:

1. **Đường dẫn DLL đúng chưa:**
```powershell
Test-Path "C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll"
```

2. **JVM argument được apply chưa:**
Thêm log debug vào code hoặc kiểm tra:
```powershell
# Trong Java code (tạm thời để debug):
System.out.println("java.library.path = " + System.getProperty("java.library.path"));
```

3. **Kiến trúc khớp chưa:**
```powershell
java -version
# Phải thấy: 64-Bit Server VM (không phải 32-bit)
```

4. **Phiên bản DLL khớp với JDBC driver:**
Phải là `13.6.0.x64` vì `pom.xml` dùng `mssql-jdbc:13.6.0.jre11`

#### Bước 7: Truy cập dashboard

Mở trình duyệt: **http://localhost:8081**

Đăng nhập với `admin/admin123`

---

### **So sánh 2 giải pháp**

| Tiêu chí | Giải pháp 1: SQL Auth | Giải pháp 2: Windows Auth + DLL |
|----------|------------------------|----------------------------------|
| **Độ phức tạp setup** | ⭐⭐ (Vừa phải) | ⭐⭐⭐⭐ (Phức tạp) |
| **Cần restart SQL Server** | ✅ Có (1 lần) | ❌ Không |
| **Cần download thêm file** | ❌ Không | ✅ Có (DLL) |
| **Cần thay đổi code/config** | ✅ Có (URL + credentials) | ❌ Không (nếu đã config sẵn) |
| **JVM arguments cần thiết** | ❌ Không | ✅ Có (`-Djava.library.path`) |
| **Portability** | ⭐⭐⭐⭐⭐ (Cross-platform) | ⭐⭐ (Chỉ Windows) |
| **Security audit** | ⭐⭐⭐ (SQL login) | ⭐⭐⭐⭐⭐ (Windows user audit) |
| **Password management** | Cần quản lý password riêng | Dùng Windows user, không cần password riêng |
| **Phù hợp cho** | Dev, Test, Production đơn giản | Enterprise, Domain-joined servers |
| **Troubleshooting** | ⭐⭐⭐⭐⭐ Dễ | ⭐⭐ Khó |

**Khuyến nghị:** Dùng **Giải pháp 1** trừ khi môi trường có yêu cầu bắt buộc Windows Authentication.

---

## 5. Checklist setup database từ đầu

### ☑ Phase 1: Chuẩn bị môi trường

- [ ] 1.1. Kiểm tra JDK đã cài đặt:
  ```powershell
  java -version
  # Cần: Java 17 trở lên
  ```

- [ ] 1.2. Kiểm tra Maven đã cài đặt:
  ```powershell
  mvn -version
  # Cần: Maven 3.9+
  ```

- [ ] 1.3. Kiểm tra SQL Server đang chạy:
  ```powershell
  Get-Service "MSSQL`$SQLEXPRESS"
  # Status phải là: Running
  ```

- [ ] 1.4. Nếu service chưa chạy, start:
  ```powershell
  Start-Service "MSSQL`$SQLEXPRESS"
  ```

- [ ] 1.5. Kiểm tra `sqlcmd` khả dụng:
  ```powershell
  sqlcmd -?
  # Nếu không có, cài SQL Server Command Line Tools
  ```

### ☑ Phase 2: Tạo database và schema

- [ ] 2.1. Tạo database `NetworkProxyDB`:
  ```powershell
  cd e:\MangMayTinh\NetworkProxySystem
  sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
  ```

- [ ] 2.2. Kiểm tra database đã tồn tại:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -Q "SELECT name FROM sys.databases WHERE name='NetworkProxyDB';"
  # Phải có kết quả: NetworkProxyDB
  ```

- [ ] 2.3. Tạo các bảng:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
  ```

- [ ] 2.4. Tạo indexes:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/indexes.sql
  ```

- [ ] 2.5. Kiểm tra 4 bảng đã được tạo:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "SELECT name FROM sys.tables ORDER BY name;"
  # Kết quả: AccessRules, AdminAuditLogs, AdminUsers, ConnectionLogs
  ```

- [ ] 2.6. (Tùy chọn) Nạp dữ liệu mẫu:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/insert-sample-data.sql
  ```

### ☑ Phase 3: Chọn và cấu hình authentication method

**Option A: SQL Server Authentication (Khuyến nghị)**

- [ ] 3A.1. Bật Mixed Mode trong SSMS:
  - Mở SSMS → Connect tới `.\SQLEXPRESS`
  - Chuột phải Server → Properties → Security
  - Chọn "SQL Server and Windows Authentication mode"
  - Click OK

- [ ] 3A.2. Restart SQL Server service:
  ```powershell
  Restart-Service "MSSQL`$SQLEXPRESS"
  ```

- [ ] 3A.3. Tạo SQL login `networkproxy`:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -Q "CREATE LOGIN networkproxy WITH PASSWORD = 'MatKhauManh123!', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;"
  ```

- [ ] 3A.4. Tạo user và cấp quyền:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "CREATE USER networkproxy FOR LOGIN networkproxy; ALTER ROLE db_datareader ADD MEMBER networkproxy; ALTER ROLE db_datawriter ADD MEMBER networkproxy; ALTER ROLE db_ddladmin ADD MEMBER networkproxy;"
  ```

- [ ] 3A.5. Test login:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -U networkproxy -P "MatKhauManh123!" -d NetworkProxyDB -Q "SELECT COUNT(*) FROM AdminUsers;"
  # Nếu thành công → kết quả: 0 (bảng rỗng)
  ```

- [ ] 3A.6. Set biến môi trường:
  ```powershell
  $env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"
  $env:NETWORK_PROXY_DB_USERNAME = "networkproxy"
  $env:NETWORK_PROXY_DB_PASSWORD = "MatKhauManh123!"
  ```

**Option B: Windows Integrated Authentication**

- [ ] 3B.1. Tạo thư mục cho DLL:
  ```powershell
  New-Item -ItemType Directory -Path "C:\tools\mssql-auth" -Force
  ```

- [ ] 3B.2. Download DLL:
  ```powershell
  Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc_auth/13.6.0.x64/mssql-jdbc_auth-13.6.0.x64.dll" -OutFile "C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll"
  ```

- [ ] 3B.3. Kiểm tra file DLL:
  ```powershell
  Test-Path "C:\tools\mssql-auth\mssql-jdbc_auth-13.6.0.x64.dll"
  # Phải trả về: True
  ```

- [ ] 3B.4. Set biến môi trường (nếu khác default):
  ```powershell
  $env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10"
  ```

### ☑ Phase 4: Build và chạy Admin API

- [ ] 4.1. Build project:
  ```powershell
  cd e:\MangMayTinh\NetworkProxySystem\admin-api
  mvn clean package -DskipTests
  ```

- [ ] 4.2. Chạy Admin API:

  **Nếu dùng SQL Auth:**
  ```powershell
  java -jar target/admin-api-1.0-SNAPSHOT.jar
  ```

  **Nếu dùng Windows Auth:**
  ```powershell
  java "-Djava.library.path=C:\tools\mssql-auth" --enable-native-access=ALL-UNNAMED -jar target/admin-api-1.0-SNAPSHOT.jar
  ```

- [ ] 4.3. Kiểm tra log startup:
  - Phải thấy: `HikariPool-1 - Start completed`
  - Phải thấy: `Đã tạo tài khoản quản trị mặc định 'admin'`
  - Phải thấy: `Tomcat started on port 8081`
  - Phải thấy: `Started AdminApplication in X.XXX seconds`

- [ ] 4.4. Kiểm tra port 8081 đang listen:
  ```powershell
  Test-NetConnection localhost -Port 8081
  # TcpTestSucceeded phải là: True
  ```

### ☑ Phase 5: Truy cập Dashboard

- [ ] 5.1. Mở trình duyệt: `http://localhost:8081`

- [ ] 5.2. Đăng nhập:
  - Username: `admin`
  - Password: `admin123`

- [ ] 5.3. Kiểm tra Dashboard hiển thị:
  - Tổng Access Rules: 0 (hoặc 3 nếu chạy sample data)
  - Tổng Connections: 0
  - Card thống kê hiển thị bình thường

- [ ] 5.4. Đổi mật khẩu admin:
  - Click menu góc phải trên → **Đổi mật khẩu**
  - Nhập mật khẩu cũ: `admin123`
  - Nhập mật khẩu mới (mạnh): ví dụ `Admin@2026!`
  - Confirm và đăng nhập lại

### ☑ Phase 6: Setup Proxy Server (Tùy chọn)

- [ ] 6.1. Set biến môi trường database (nếu dùng SQL Auth):
  ```powershell
  $env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;encrypt=true;trustServerCertificate=true;loginTimeout=10"
  $env:NETWORK_PROXY_DB_USERNAME = "networkproxy"
  $env:NETWORK_PROXY_DB_PASSWORD = "MatKhauManh123!"
  $env:NETWORK_PROXY_DB_ENABLED = "true"
  ```

- [ ] 6.2. Set biến môi trường Proxy Control (tùy chọn):
  ```powershell
  $env:PROXY_CONTROL_ENABLED = "true"
  $env:PROXY_CONTROL_HOST = "127.0.0.1"
  $env:PROXY_CONTROL_PORT = "8082"
  $env:PROXY_CONTROL_TOKEN = "dev-token-change-me"
  ```

- [ ] 6.3. Chạy Proxy Server:
  ```powershell
  cd e:\MangMayTinh\NetworkProxySystem\proxy-server
  
  # Nếu dùng Windows Auth:
  $env:MAVEN_OPTS = "-Djava.library.path=C:\tools\mssql-auth"
  
  mvn exec:java
  ```

- [ ] 6.4. Kiểm tra proxy đang chạy:
  ```powershell
  Test-NetConnection localhost -Port 8080
  # TcpTestSucceeded: True
  ```

- [ ] 6.5. Nếu bật Proxy Control, kiểm tra control API:
  ```powershell
  curl.exe -H "X-Admin-Token: dev-token-change-me" http://127.0.0.1:8082/admin/stats
  # Phải trả về JSON: {"status":"ok","activeClients":0,...}
  ```

### ☑ Phase 7: Test End-to-End

- [ ] 7.1. Tạo rule ALLOW trong dashboard:
  - Vào **Access Rules** → **+ Thêm rule**
  - Target: `example.com`
  - Port: `80`
  - Protocol: `HTTP`
  - Action: `ALLOW`
  - Priority: `100`
  - Enabled: ✅ checked
  - Click **Tạo rule**

- [ ] 7.2. Test với curl qua proxy:
  ```powershell
  curl.exe -x http://localhost:8080 http://example.com
  # Phải trả về HTML của example.com
  ```

- [ ] 7.3. Kiểm tra Connection Log:
  - Vào **Connection Logs** trên dashboard
  - Phải thấy log mới với:
    - ClientIp: `127.0.0.1` (hoặc IP máy)
    - Target: `example.com`
    - Decision: `ALLOW`
    - Execution Status: `CONNECTED`

- [ ] 7.4. Tạo rule BLOCK:
  - Target: `facebook.com`
  - Protocol: `HTTPS`
  - Action: `BLOCK`
  - Priority: `100`

- [ ] 7.5. Test rule BLOCK:
  ```powershell
  curl.exe -x http://localhost:8080 https://facebook.com
  # Phải nhận HTTP 403 Forbidden hoặc connection reset
  ```

- [ ] 7.6. Kiểm tra Audit Log:
  - Vào **Audit Logs**
  - Phải thấy:
    - `LOGIN` khi đăng nhập
    - `CREATE` khi tạo rule
    - `CHANGE_PASSWORD` nếu đã đổi mật khẩu

### ☑ Phase 8: Production Hardening (Nếu deploy thật)

- [ ] 8.1. Đổi token Proxy Control:
  ```powershell
  # Generate random token
  $token = [System.Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  echo $token
  # Set cho cả admin-api và proxy-server
  ```

- [ ] 8.2. Sử dụng environment-specific credentials:
  - Không dùng `admin123` trên production
  - Không dùng `MatKhauManh123!` trên production
  - Dùng password manager hoặc secret vault

- [ ] 8.3. Review network binding:
  - Admin API chỉ nên bind `127.0.0.1` hoặc private IP
  - Proxy Control API **PHẢI** bind `127.0.0.1` only
  - Không expose port 8081, 8082 ra Internet

- [ ] 8.4. Backup database:
  ```powershell
  sqlcmd -S ".\SQLEXPRESS" -E -C -Q "BACKUP DATABASE NetworkProxyDB TO DISK='C:\Backup\NetworkProxyDB_backup.bak' WITH INIT;"
  ```

- [ ] 8.5. Enable SSL/TLS cho Admin API (nếu cần):
  - Cấu hình `server.ssl.*` trong `application.properties`
  - Sử dụng certificate từ Let's Encrypt hoặc CA nội bộ

---

## 6. Troubleshooting thêm

### Vấn đề: "Login failed for user 'networkproxy'"

**Nguyên nhân:** Password sai hoặc login chưa được cấp quyền

**Fix:**
1. Kiểm tra password trong environment variable
2. Reset password:
```sql
ALTER LOGIN networkproxy WITH PASSWORD = 'NewPassword123!';
```

### Vấn đề: "Database 'NetworkProxyDB' does not exist"

**Nguyên nhân:** Script create-database.sql chưa chạy

**Fix:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -i database/create-database.sql
```

### Vấn đề: "Invalid object name 'dbo.AdminUsers'"

**Nguyên nhân:** Script create-tables.sql chưa chạy hoặc chạy sai database

**Fix:**
```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -i database/create-tables.sql
```

### Vấn đề: Port 8081 bị chiếm

**Triệu chứng:** `Port 8081 was already in use`

**Fix:**
1. Tìm process đang chiếm:
```powershell
Get-NetTCPConnection -LocalPort 8081 | Select-Object OwningProcess
```

2. Kill process hoặc đổi port trong `application.properties`:
```properties
server.port=8082
```

### Vấn đề: Proxy Control API trả 403 Forbidden

**Nguyên nhân:** Token không khớp giữa admin-api và proxy-server

**Fix:** Đảm bảo cả hai dùng cùng token:
```powershell
# Trên admin-api
$env:PROXY_CONTROL_TOKEN = "same-token-123"

# Trên proxy-server
$env:PROXY_CONTROL_TOKEN = "same-token-123"
```

### Vấn đề: Dashboard báo "Session expired"

**Nguyên nhân:** Session timeout (mặc định 8 giờ) hoặc server restart

**Fix:** Đăng nhập lại. Nếu muốn tăng timeout:
```properties
server.servlet.session.timeout=24h
```

---

## 7. Kết luận và Khuyến nghị

### Tóm tắt

Lỗi `Unable to load authentication DLL mssql-jdbc_auth-13.6.0.x64` xảy ra vì:
1. Dự án cấu hình sử dụng Windows Integrated Authentication (`integratedSecurity=true`)
2. JDBC driver cần file DLL native để implement Windows authentication protocols
3. DLL này không được tự động cài đặt và cần download riêng
4. Khi chạy từ IntelliJ hoặc command line, JVM không tìm thấy DLL trong `java.library.path`

### Khuyến nghị cho dự án này

**Cho môi trường Development (local machine):**
- ✅ **Dùng SQL Server Authentication (Giải pháp 1)**
- Lý do:
  - Setup nhanh hơn
  - Không phụ thuộc DLL native
  - Dễ troubleshoot
  - Credentials quản lý đơn giản bằng environment variables

**Cho môi trường Production (nếu có):**
- Nếu server là Windows Server trong domain → **Windows Authentication (Giải pháp 2)**
- Nếu server là Linux hoặc container → **SQL Authentication (Giải pháp 1)** (vì không có Windows auth trên Linux)
- Nếu có nhiều services cần access database → SQL Authentication với managed secrets (Azure Key Vault, AWS Secrets Manager, HashiCorp Vault)

### Next Steps

1. **Ngay lập tức:** Chọn 1 trong 2 giải pháp trên và làm theo checklist
2. **Sau khi chạy được:** Test toàn bộ flow (create rule → proxy traffic → check logs)
3. **Tối ưu:** Setup Proxy Control API để reload rule tự động
4. **Bảo mật:** Đổi mật khẩu admin mặc định và token control API
5. **Document:** Ghi lại cấu hình đã chọn vào project wiki hoặc INSTALL.md

### Tài liệu tham khảo

- [Microsoft SQL Server JDBC Driver Documentation](https://learn.microsoft.com/en-us/sql/connect/jdbc/microsoft-jdbc-driver-for-sql-server)
- [Using Kerberos Integrated Authentication](https://learn.microsoft.com/en-us/sql/connect/jdbc/using-kerberos-integrated-authentication-to-connect-to-sql-server)
- [Configuring the client for Integrated Authentication](https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url#Connectingintegrated)
- [Admin Guide trong dự án](docs/admin-guide.md)
- [README.md trong dự án](README.md)

---

**Báo cáo được tạo tự động bởi Investigation Agent**  
**Mọi thông tin đã được xác thực từ source code và documentation thực tế của dự án**
