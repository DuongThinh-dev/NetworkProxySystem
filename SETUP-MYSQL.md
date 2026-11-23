# Hướng dẫn Setup MySQL cho NetworkProxySystem

## Bước 1: Khởi động MySQL bằng Docker

```powershell
# Chạy MySQL container
docker-compose up -d

# Kiểm tra container đang chạy
docker ps

# Xem logs
docker-compose logs -f mysql
```

Container sẽ:
- Chạy MySQL 8.0 trên port **3307** (tránh conflict với MySQL local nếu có)
- Tự động tạo database `NetworkProxyDB`
- Tự động chạy scripts tạo bảng và indexes
- Root password: `123456`

## Bước 2: Kiểm tra Database đã sẵn sàng

```powershell
# Kết nối vào MySQL
docker exec -it networkproxy-mysql mysql -uroot -p123456

# Trong MySQL shell, kiểm tra database
SHOW DATABASES;
USE NetworkProxyDB;
SHOW TABLES;

# Phải thấy 4 bảng:
# - AccessRules
# - AdminAuditLogs
# - AdminUsers
# - ConnectionLogs

# Thoát
exit
```

## Bước 3: Chạy Admin API từ IntelliJ IDEA

1. Mở IntelliJ IDEA
2. Reload Maven project (nếu cần): Right-click `pom.xml` → Maven → Reload Project
3. Chạy `AdminApplication.java`

Application sẽ:
- Kết nối MySQL tại `localhost:3307`
- Tự động tạo admin user mặc định: `admin/admin123`
- Chạy trên port **8081**

## Bước 4: Truy cập Dashboard

Mở trình duyệt: **http://localhost:8081**

Đăng nhập:
- Username: `admin`
- Password: `admin123`

## Bước 5: Đổi mật khẩu admin (Khuyến nghị)

Sau khi đăng nhập, click menu góc phải → **Đổi mật khẩu**

---

## Troubleshooting

### Lỗi: Port 3307 đã được sử dụng

```powershell
# Dừng container
docker-compose down

# Đổi port trong docker-compose.yml
# Thay "3307:3306" thành "3308:3306"

# Cập nhật application.properties
# Thay localhost:3307 thành localhost:3308

# Chạy lại
docker-compose up -d
```

### Lỗi: Cannot connect to database

```powershell
# Kiểm tra container đang chạy
docker ps

# Nếu không có, start lại
docker-compose start

# Xem logs để debug
docker-compose logs mysql
```

### Reset Database

```powershell
# Dừng và xóa toàn bộ
docker-compose down -v

# Chạy lại (sẽ tạo database mới)
docker-compose up -d
```

---

## Environment Variables (Tùy chọn)

Nếu muốn override cấu hình, set các biến môi trường:

```powershell
$env:NETWORK_PROXY_DB_URL = "jdbc:mysql://localhost:3307/NetworkProxyDB?useSSL=false&serverTimezone=UTC"
$env:NETWORK_PROXY_DB_USERNAME = "root"
$env:NETWORK_PROXY_DB_PASSWORD = "123456"
```

---

## Production Deployment

Cho môi trường production:

1. **Đổi root password mạnh hơn** trong `docker-compose.yml`
2. **Tạo user riêng** thay vì dùng root:

```sql
CREATE USER 'networkproxy'@'%' IDENTIFIED BY 'StrongPassword123!';
GRANT ALL PRIVILEGES ON NetworkProxyDB.* TO 'networkproxy'@'%';
FLUSH PRIVILEGES;
```

3. **Bật SSL** cho MySQL connection
4. **Backup thường xuyên**:

```powershell
docker exec networkproxy-mysql mysqldump -uroot -p123456 NetworkProxyDB > backup.sql
```
