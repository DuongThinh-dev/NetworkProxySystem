# Access control và logging: hợp đồng dữ liệu

Tài liệu này chốt dữ liệu mà Proxy Core cung cấp cho Access Control và
Connection Logger. Schema SQL sẽ được thiết kế theo hợp đồng này ở bước tiếp
theo.

## 1. Ngữ cảnh của một kết nối

Mỗi lần client yêu cầu kết nối, Proxy tạo một `ConnectionContext` gồm:

| Trường | Ý nghĩa |
| --- | --- |
| `clientIp` | IP lấy từ socket client, chuẩn hóa về dạng chuỗi |
| `targetHost` | Host client yêu cầu; có thể là domain hoặc IP literal |
| `targetPort` | Port đích, số nguyên từ 1 đến 65535 |
| `protocol` | `HTTP`, `HTTPS` hoặc `SSH` |

`targetHost` được chuẩn hóa bằng cách bỏ khoảng trắng, bỏ dấu chấm cuối domain
và chuyển domain về chữ thường. Không phân giải DNS trong bước so khớp luật:
domain được so theo hostname client yêu cầu; IP literal được so như địa chỉ IP.
Việc kiểm tra địa chỉ private/loopback hiện có vẫn là lớp kiểm tra riêng.

HTTP và HTTPS có thể tạo ngữ cảnh trước khi mở kết nối upstream. SSH chỉ tạo
ngữ cảnh Access Control khi có luồng kết nối SSH đi ra được gọi trực tiếp; repo
hiện không có SSH proxy handler đầu vào.

## 2. Hợp đồng luật truy cập

Một luật gồm các điều kiện tùy chọn và hành động:

| Trường | Ý nghĩa |
| --- | --- |
| `clientIp` | IP client cụ thể; `null` nghĩa là mọi client |
| `target` | Hostname hoặc IP đích cụ thể; `null` nghĩa là mọi đích |
| `port` | Port cụ thể; `null` nghĩa là mọi port |
| `protocol` | `HTTP`, `HTTPS`, `SSH`; `null` nghĩa là mọi giao thức |
| `action` | `ALLOW` hoặc `BLOCK` |
| `priority` | Số nguyên; giá trị lớn hơn được xét trước |
| `enabled` | Luật có được áp dụng hay không |

Hostname trong luật khớp chính xác với hostname đã chuẩn hóa. Chưa hỗ trợ
wildcard domain hoặc luật theo hậu tố như `*.example.com`. Muốn bao phủ domain
con phải tạo luật riêng cho domain đó.

### Thứ tự quyết định

1. Chỉ xét luật đang bật và khớp tất cả điều kiện khác `null`.
2. Luật có `priority` cao hơn được xét trước.
3. Nếu cùng `priority`, luật cụ thể hơn được xét trước: nhiều điều kiện cụ thể
   hơn (client IP, target, port, protocol) có độ cụ thể cao hơn.
4. Nếu vẫn ngang nhau nhưng hành động xung đột, `BLOCK` thắng.
5. Nếu không có luật khớp, quyết định là `BLOCK` với lý do `DEFAULT_DENY`.

Quyết định trả về gồm `action`, `matchedRuleId` (có thể rỗng nếu dùng mặc định)
và `reason`, để handler vừa thực thi vừa ghi nhật ký nhất quán.

## 3. Hợp đồng nhật ký kết nối

Mỗi yêu cầu kết nối tạo một bản ghi với các thông tin:

- thời điểm UTC;
- IP client, host đích, port và protocol;
- quyết định `ALLOW`/`BLOCK`;
- mã luật khớp nếu có;
- lý do quyết định;
- trạng thái thực thi: `CONNECTED`, `BLOCKED` hoặc `ERROR`;
- thông báo lỗi upstream đã được làm sạch, nếu có.

Không ghi payload HTTP, dữ liệu tunnel HTTPS, mật khẩu SSH, token hoặc private
key. Nhật ký chỉ mô tả metadata kết nối và kết quả xử lý.

## 4. Điểm gọi trong Proxy Core

- HTTP: kiểm tra sau khi parse host/port, trước khi mở upstream socket.
- HTTPS: kiểm tra sau khi parse `CONNECT`, trước khi tạo tunnel.
- Cả hai handler ghi log cho quyết định bị chặn, kết nối upstream thành công
  và lỗi kết nối upstream.
- Bộ so khớp dùng danh sách luật đã nạp trong bộ nhớ; không truy vấn SQL trên
  từng byte hoặc trong vòng chuyển tiếp dữ liệu.

## 5. Ánh xạ schema SQL Server

Các script trong `database/` tạo database `NetworkProxyDB` với hai bảng:

- `AccessRules`: điều kiện luật, hành động, mức ưu tiên, trạng thái bật/tắt,
  thời điểm tạo và cập nhật.
- `ConnectionLogs`: metadata kết nối, quyết định, luật khớp, mã lý do, trạng
  thái thực thi và lỗi upstream đã làm sạch.

`ConnectionLogs.MatchedRuleId` tham chiếu `AccessRules.RuleId`; khi xóa luật,
log lịch sử vẫn được giữ và mã luật được đặt thành `NULL`. Các thời điểm được
lưu theo UTC. Index hỗ trợ nạp luật đang bật và truy vấn log mới nhất, theo
client hoặc theo quyết định.

Chạy script theo thứ tự: `create-database.sql`, `create-tables.sql`,
`indexes.sql`, rồi tùy chọn `insert-sample-data.sql`.

## 6. Java JDBC

`proxy-server` dùng Microsoft JDBC Driver cho SQL Server. Cấu hình được đọc qua
`DatabaseConfig`; thông tin kết nối có thể lấy từ `application.properties` hoặc
các biến môi trường `NETWORK_PROXY_DB_URL`, `NETWORK_PROXY_DB_USERNAME`,
`NETWORK_PROXY_DB_PASSWORD`, `NETWORK_PROXY_DB_ENABLED`. Biến môi trường được
ưu tiên. Kết nối DB tắt mặc định trong cấu hình mẫu; khi bật, đặt URL, username
và password phù hợp với SQL Server đang chạy. Mật khẩu không nên commit vào Git.

`RuleRepository` chỉ tải luật đang bật; `LogRepository` ghi metadata vào bảng
log bằng prepared statement. Mỗi thao tác hiện mở và đóng một JDBC connection.
