# Hướng dẫn demo NetworkProxySystem

Tài liệu này là kịch bản demo thực tế cho đồ án **NetworkProxySystem**:
proxy HTTP/HTTPS + kiểm soát truy cập + ghi log + dashboard quản trị.

Thời lượng gợi ý:

- Bản đầy đủ: **8–10 phút**
- Bản ngắn: **4–5 phút**
- Có thể demo thêm máy khác trong LAN: **+2 phút**

---

## 1. Mục tiêu demo

Sau buổi demo, người xem cần thấy được:

1. Hệ thống là một proxy HTTP/HTTPS thật, có thể chuyển tiếp traffic.
2. Proxy kiểm tra rule trước khi cho phép kết nối.
3. Rule thay đổi có hiệu lực ngay, không cần restart proxy.
4. Mọi kết nối được ghi log và xem được trên dashboard.
5. Có thể quản lý theo từng client IP.
6. Có audit log cho thao tác quản trị.
7. HTTPS chỉ được tunnel, không bị giải mã.

---

## 2. Chuẩn bị trước demo

### 2.1. Kiểm tra SQL Server

```powershell
Get-Service 'MSSQL$SQLEXPRESS'
```

Nếu service đang stopped:

```powershell
Start-Service 'MSSQL$SQLEXPRESS'
```

Kiểm tra database:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "SELECT COUNT(*) AS Rules FROM dbo.AccessRules; SELECT COUNT(*) AS Logs FROM dbo.ConnectionLogs;"
```

### 2.2. Khởi động Admin API

Mở PowerShell thứ nhất:

```powershell
cd "E:\MangMayTinh\NetworkProxySystem\admin-api"

$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_USERNAME = ""
$env:NETWORK_PROXY_DB_PASSWORD = ""
$env:NETWORK_PROXY_DB_ENABLED = "true"

$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_URL = "http://127.0.0.1:8082"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"

java "-Djava.library.path=C:\Users\ADMIN\AppData\Local\Temp\opencode\mssql-auth" -jar "target\admin-api-1.0-SNAPSHOT.jar"
```

### 2.3. Khởi động Proxy Server

Mở PowerShell thứ hai:

```powershell
cd "E:\MangMayTinh\NetworkProxySystem\proxy-server"

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:MAVEN_OPTS = "-Djava.library.path=C:\Users\ADMIN\AppData\Local\Temp\opencode\mssql-auth"

$env:NETWORK_PROXY_DB_ENABLED = "true"
$env:NETWORK_PROXY_DB_URL = "jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=NetworkProxyDB;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=10"
$env:NETWORK_PROXY_DB_USERNAME = ""
$env:NETWORK_PROXY_DB_PASSWORD = ""

$env:PROXY_CONTROL_ENABLED = "true"
$env:PROXY_CONTROL_TOKEN = "dev-token-change-me"

& "C:\Users\ADMIN\AppData\Local\Temp\opencode\maven\apache-maven-3.9.9\bin\mvn.cmd" exec:java
```

### 2.4. Kiểm tra các cổng

```powershell
Get-NetTCPConnection -LocalPort 8080,8081,8082 -State Listen |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

Kết quả mong đợi:

- `8080`: proxy
- `8081`: admin API/dashboard
- `8082`: control API, chỉ `127.0.0.1`

### 2.5. Kiểm tra đăng nhập và proxy

```powershell
curl.exe -s -o NUL -w "Dashboard: %{http_code}`n" http://localhost:8081/login.html
curl.exe -x http://localhost:8080 -s -o NUL -w "HTTP example.com: %{http_code}`n" http://example.com
```

Kết quả mong đợi:

```text
Dashboard: 200
HTTP example.com: 200
```

Nếu HTTP example.com trả `403`, kiểm tra rule `ALLOW example.com:80/HTTP`.

### 2.6. Chuẩn bị rule mẫu

Vào dashboard:

```text
http://localhost:8081
admin / admin123
```

Nên có sẵn:

| Client IP | Target | Port | Protocol | Action | Priority | Enabled |
|---|---|---|---:|---|---:|---|
| để trống | `example.com` | 80 | HTTP | ALLOW | 100 | Có |

Nếu muốn demo HTTPS mượt hơn, tạo trước rule:

| Client IP | Target | Port | Protocol | Action | Priority | Enabled |
|---|---|---|---:|---|---:|---|
| để trống | `example.com` | 443 | HTTPS | ALLOW | 100 | Có |

> Nếu muốn demo thao tác tạo rule HTTPS ngay trên sân khấu, hãy để trống rule HTTPS và tạo trong phần demo.

### 2.7. Mở sẵn các tab

Mở sẵn trên trình duyệt:

1. `http://localhost:8081` — Dashboard
2. `Access Rules`
3. `Connection Logs`
4. `Clients`
5. `Audit Logs`

Mở sẵn 1 terminal để chạy `curl.exe`.

### 2.8. Tùy chọn: reset log để demo sạch

Nếu muốn dashboard bắt đầu với log trống:

```powershell
sqlcmd -S ".\SQLEXPRESS" -E -C -d NetworkProxyDB -Q "DELETE FROM dbo.ConnectionLogs; DELETE FROM dbo.AdminAuditLogs;"
```

Sau đó đăng nhập lại dashboard để tạo audit `LOGIN`, và chạy một request để tạo log mẫu.

---

## 3. Kịch bản demo đầy đủ (8–10 phút)

### Phần 1 — Giới thiệu kiến trúc (1 phút)

Nói:

> “Đây là NetworkProxySystem, gồm ba phần chính: proxy server, admin API/dashboard và SQL Server. Client đi qua proxy ở cổng 8080. Proxy kiểm tra rule, cho phép hoặc chặn, rồi ghi log vào SQL Server. Admin quản lý qua dashboard ở cổng 8081. Control API ở cổng 8082 giúp reload rule mà không cần restart proxy.”

Có thể vẽ nhanh:

```text
Client → Proxy :8080 → Internet
              ↓
        ConnectionLogs (SQL Server)
              ↑
Admin → Admin API :8081
              ↓
        Control API :8082 → reload rule
```

Nhấn mạnh:

- HTTP được chuyển tiếp.
- HTTPS dùng `CONNECT`, không giải mã TLS.
- Mặc định deny nếu không có rule ALLOW.
- Rule có priority; BLOCK thắng khi cùng priority.

### Phần 2 — Dashboard (1 phút)

Mở `http://localhost:8081`, đăng nhập `admin / admin123`.

Chỉ vào:

- Tổng số rule, rule đang bật.
- Tổng log, ALLOW/BLOCK/ERROR.
- Số client.
- Top target.
- Log gần đây.
- Audit gần đây.
- Trạng thái **Proxy online**, số rule đã nạp.

Nói:

> “Dashboard lấy dữ liệu từ SQL Server. Trạng thái Proxy online lấy từ Control API. Số rule đã nạp là số rule proxy đang giữ trong bộ nhớ.”

### Phần 3 — Demo HTTP ALLOW (1–2 phút)

Chạy:

```powershell
curl.exe -x http://localhost:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

Kết quả: `200`.

Mở **Connection Logs**, chỉ vào bản ghi mới:

```text
example.com | 80 | HTTP | ALLOW | CONNECTED | rule=...
```

Nói:

> “Proxy đã chuyển tiếp request HTTP tới example.com. Log ghi lại client IP, target, port, protocol, quyết định, rule khớp và trạng thái thực thi. Không ghi nội dung request/response.”

### Phần 4 — Demo HTTPS ALLOW (1–2 phút)

Nếu chưa có rule HTTPS, tạo ngay:

```text
Access Rules → + Thêm rule
Client IP: để trống
Target: example.com
Port: 443
Protocol: HTTPS
Action: ALLOW
Priority: 100
Kích hoạt rule: bật
→ Tạo rule
```

Chạy:

```powershell
curl.exe -x http://localhost:8080 -s -o NUL -w "%{http_code}`n" https://example.com
```

Kết quả: `200`.

Mở **Connection Logs**, chỉ vào bản ghi:

```text
example.com | 443 | HTTPS | ALLOW | CONNECTED | rule=...
```

Nói:

> “Với HTTPS, client gửi CONNECT tới proxy. Proxy kiểm tra rule rồi mở tunnel TCP. Proxy không giải mã TLS, không thấy URL hay nội dung bên trong. Đây là lý do log chỉ có host và port.”

### Phần 5 — Demo BLOCK và priority (2 phút)

Tạo rule BLOCK:

```text
Access Rules → + Thêm rule
Client IP: để trống
Target: example.com
Port: 80
Protocol: HTTP
Action: BLOCK
Priority: 1000
Kích hoạt rule: bật
→ Tạo rule
```

Chạy lại:

```powershell
curl.exe -x http://localhost:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

Kết quả: `403`.

Mở **Connection Logs**:

```text
example.com | 80 | HTTP | BLOCK | BLOCKED | rule=...
```

Nói:

> “Rule BLOCK có priority 1000, cao hơn rule ALLOW priority 100, nên BLOCK thắng. Rule mới được reload ngay qua Control API, không cần restart proxy.”

Sau đó tắt rule BLOCK bằng công tắc trong **Access Rules**, chạy lại:

```powershell
curl.exe -x http://localhost:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

Kết quả: `200`.

Nói:

> “Tắt BLOCK là quyền truy cập được khôi phục ngay. Đây là điểm mạnh của Control API.”

### Phần 6 — Demo theo client IP (tùy chọn, +2 phút)

Trên máy khác trong LAN:

1. Cấu hình proxy `192.168.137.99:8080`.
2. Kiểm tra:

```powershell
Test-NetConnection 192.168.137.99 -Port 8080
```

3. Test:

```powershell
curl.exe -x http://192.168.137.99:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

Kết quả: `200` nếu rule ALLOW example.com đang bật.

Trên dashboard:

```text
Clients → xem IP máy khác
```

Tạo rule chặn toàn bộ máy đó:

```text
Access Rules → + Thêm rule
Client IP: 192.168.137.100   (thay bằng IP thật)
Target: để trống
Port: để trống
Protocol: để trống
Action: BLOCK
Priority: 9999
Kích hoạt rule: bật
→ Tạo rule
```

Test lại trên máy khác:

```powershell
curl.exe -x http://192.168.137.99:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

Kết quả: `403`.

Mở **Connection Logs**, chỉ vào dòng có `Client IP = 192.168.137.100` và `BLOCK / BLOCKED`.

Nói:

> “Rule này khớp mọi target, mọi port, mọi protocol của riêng máy đó. Các máy khác không bị ảnh hưởng. Priority 9999 đảm bảo BLOCK thắng mọi ALLOW hiện có.”

### Phần 7 — Clients và Audit Logs (1 phút)

Mở **Clients**:

- Số request theo IP.
- ALLOW/BLOCK/ERROR.
- Lần hoạt động cuối.
- Bấm vào client để xem log.

Mở **Audit Logs**:

- `LOGIN`
- `CREATE` rule
- `UPDATE` / `TOGGLE` rule
- `DELETE` rule

Nói:

> “Audit log giúp truy vết ai đã làm gì trên hệ thống quản trị.”

### Phần 8 — Kết luận (1 phút)

Tóm tắt:

- Proxy HTTP/HTTPS hoạt động thật.
- Kiểm soát truy cập theo IP, target, port, protocol.
- Rule reload không cần restart.
- Log và dashboard đầy đủ.
- Có audit log.
- HTTPS không bị giải mã.

Nêu giới hạn:

- Proxy 8080 chưa có authentication.
- Chưa lọc nội dung HTTPS.
- Chưa hỗ trợ wildcard domain/CIDR.
- Dashboard chạy HTTP, chưa có CSRF/rate limiting.
- Chưa có HA/clustering.

---

## 4. Kịch bản ngắn 5 phút

Nếu ít thời gian:

1. Mở dashboard, giới thiệu 30 giây.
2. `curl` HTTP example.com → 200.
3. Mở Connection Logs → chỉ log ALLOW.
4. Tạo BLOCK example.com priority 1000.
5. `curl` lại → 403.
6. Mở log → BLOCK/BLOCKED.
7. Tắt BLOCK → 200.
8. Kết luận.

Bỏ qua HTTPS và máy khác nếu không đủ thời gian.

---

## 5. Demo với máy khác trong LAN

### 5.1. Mở firewall trên máy proxy

Chạy PowerShell as Administrator:

```powershell
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

New-NetFirewallRule -DisplayName "NetworkProxy 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
New-NetFirewallRule -DisplayName "NetworkProxy Admin 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow -Profile Private
```

Nếu mạng vẫn để Public, dùng `-Profile Public` (chỉ lab tin cậy).

### 5.2. Cấu hình máy khác

Windows:

```text
Settings → Network & Internet → Proxy
→ Manual proxy setup → Edit
→ On
→ Proxy IP: 192.168.137.99
→ Port: 8080
→ Save
```

Firefox:

```text
Settings → Network Settings → Manual proxy configuration
HTTP Proxy: 192.168.137.99   Port: 8080
Also use this proxy for HTTPS: bật
```

### 5.3. Test

Trên máy khác:

```powershell
Test-NetConnection 192.168.137.99 -Port 8080
curl.exe -x http://192.168.137.99:8080 -s -o NUL -w "%{http_code}`n" http://example.com
```

### 5.4. Lưu ý

- Hai máy phải cùng LAN/Wi-Fi.
- Không bật client isolation trên router/AP.
- IP máy proxy có thể đổi; kiểm tra bằng `ipconfig`.
- Nếu máy khác dùng IPv6, log có thể hiện IPv6; dùng đúng IP đó hoặc để trống Client IP.
- Proxy 8080 không có authentication; chỉ demo trong LAN tin cậy.

---

## 6. Câu hỏi thường gặp khi demo

### “Vì sao HTTPS không thấy URL?”

Vì proxy chỉ tunnel TLS, không giải mã. Đây là thiết kế bảo mật, tránh MITM. Muốn lọc nội dung HTTPS phải làm TLS interception với CA riêng, phức tạp và cần cân nhắc pháp lý.

### “Làm sao chặn cả một máy?”

Tạo rule:

- `Client IP` = IP máy đó
- Target/Port/Protocol để trống
- Action `BLOCK`
- Priority cao hơn mọi ALLOW, ví dụ `9999`

### “Làm sao chỉ cho một máy vào một số web?”

Dùng whitelist: chỉ tạo các rule ALLOW cho máy đó với các target/port cần thiết. Không có rule khớp → mặc định BLOCK.

### “Làm sao chặn vài web nhưng cho phép phần còn lại?”

Dùng blacklist:

1. Tạo rule ALLOW catch-all priority 0.
2. Tạo các rule BLOCK cho domain cần chặn, priority cao hơn.

### “Rule thay đổi có cần restart proxy không?”

Không, nếu bật Control API ở cả hai phía. Admin gọi `/admin/reload-rules`, proxy reload `RuleManager`.

### “Vì sao dùng SQL Server?”

Theo yêu cầu đồ án, dùng Spring JDBC + SQL Server. Có thể thay bằng PostgreSQL/MySQL nếu muốn.

### “Có production-ready không?”

Chưa. Cần thêm:

- Authentication cho proxy client.
- HTTPS cho dashboard.
- CSRF, rate limiting, phân quyền.
- HA, clustering, monitoring.
- Chính sách backup/retention cho log.

---

## 7. Xử lý sự cố khi demo

| Hiện tượng | Kiểm tra |
|---|---|
| Dashboard không mở | Admin API có chạy? Cổng 8081? Firewall? |
| Login sai | `AdminUsers` có dữ liệu? Xem log admin-api. |
| Proxy online = false | Control API bật chưa? Token hai bên khớp? |
| `curl` HTTP example.com = 403 | Rule ALLOW có bật? Đúng port 80/HTTP? Client IP? Priority? |
| `curl` HTTPS example.com = 403 | Có rule HTTPS 443 chưa? |
| `502 Bad Gateway` | Máy proxy ra Internet được không? DNS? |
| Rule mới không có hiệu lực | Control API tắt? Khởi động lại proxy. |
| Rule không khớp | `Target` chỉ nhập hostname/IP, không nhập `https://...`; `Port` là port đích (80/443), không phải port proxy 8080. |
| Log không xuất hiện | DB bật chưa? Đúng URL? Xem log proxy. |
| Máy khác không kết nối được | `Test-NetConnection`; firewall; cùng LAN; client isolation. |
| `curl` không nhận `-x` | Dùng `curl.exe`, không dùng alias `curl` của PowerShell. |
| Browser tự chuyển HTTPS | Dùng `curl.exe` hoặc thêm rule HTTPS. |

---

## 8. Checklist trước khi lên demo

- [ ] SQL Server đang chạy.
- [ ] Admin API đang chạy ở 8081.
- [ ] Proxy đang chạy ở 8080.
- [ ] Control API đang chạy ở 8082.
- [ ] Đăng nhập dashboard thành công.
- [ ] Có rule ALLOW example.com HTTP.
- [ ] Có rule ALLOW example.com HTTPS (hoặc tạo live).
- [ ] Không có rule BLOCK đang bật ngoài ý muốn.
- [ ] `curl` HTTP example.com trả 200.
- [ ] `curl` HTTPS example.com trả 200.
- [ ] Đã mở sẵn các tab dashboard.
- [ ] Đã mở terminal curl.
- [ ] Firewall mở 8080/8081 nếu demo máy khác.
- [ ] Máy khác đã test `Test-NetConnection`.
- [ ] Có ảnh chụp màn hình/video dự phòng.
- [ ] Đã chuẩn bị câu trả lời cho phần giới hạn.

---

## 9. Gợi ý slide thuyết trình

1. **Tên đề tài & thành viên**
2. **Mục tiêu**: proxy HTTP/HTTPS có kiểm soát truy cập, logging, dashboard.
3. **Kiến trúc**: proxy-server, admin-api, admin-web, SQL Server.
4. **Luồng xử lý**: client → rule → upstream → log.
5. **Tính năng chính**: rule theo IP/target/port/protocol, priority, default deny.
6. **Demo**: ảnh dashboard, log, rule.
7. **Bảo mật**: HTTPS tunnel, không giải mã; cảnh báo proxy 8080 chưa auth.
8. **Giới hạn & hướng phát triển**: proxy auth, SOCKS5, TLS inspection, HA.
9. **Kết luận & Q&A**.

---

## 10. Mẹo demo an toàn

- Dùng `curl.exe` thay vì trình duyệt để kết quả ổn định.
- Không phụ thuộc vào website bên ngoài nếu mạng yếu; có thể dùng một web server nội bộ.
- Chuẩn bị sẵn ảnh chụp dashboard/log phòng khi mạng lỗi.
- Không mở proxy ra Internet.
- Đổi mật khẩu admin và token nếu demo ngoài máy cá nhân.
- Nếu demo máy khác, khóa màn hình máy đó hoặc chuẩn bị sẵn terminal để tránh lộ dữ liệu.
