# Rà soát CloudDocs — 06/11/2026

## Phạm vi

Đã đọc các tài liệu Markdown, cấu hình, migration SQL và script chạy/thiết lập hạ tầng;
đã rà soát các module tài khoản/Cognito, tài liệu, phân quyền, yêu thích, thùng rác,
thông báo, audit log, dung lượng và JavaScript giao diện. Đây là rà soát workspace hiện tại,
không phải chứng nhận hệ thống triển khai không còn lỗi.

## Đã dọn phần cũ

- Bỏ hàm Preview local bị hàm S3 phía sau ghi đè; hiện chỉ còn một handler Preview.
- Bỏ return true không bao giờ chạy, import trùng và các query repository không còn caller.
- Bỏ JpaSpecificationExecutor không được sử dụng.
- Xóa test.txt và downloaded-test.txt ở gốc: hai file mẫu CloudDocs AWS Test, không có tham chiếu.
- Viết lại hướng dẫn backend/frontend/S3/dung lượng: bỏ hướng dẫn frontend demo, lộ trình
  các tính năng đã có, tên migration không tồn tại và đường dẫn Java cũ.
- Thay script npx lệch callback bằng server Node không cần tải dependency; chỉ phục vụ FrontEnd.

Giữ local storage APIs, dữ liệu upload, migration lịch sử, cột password/owner và các bản ghi
PURGED vì vẫn liên quan đến tương thích/schema/audit. Không xóa file người dùng hay sửa dữ liệu DB.

## Lỗi đã sửa

| Vấn đề | Cách xử lý |
| --- | --- |
| File 0 B bị coi là tải/xem thất bại | Cho phép file rỗng hợp lệ |
| Hai tài khoản upload cùng tên cùng thời điểm có thể trùng key | Dùng UUID; tên gốc ở metadata |
| Tên Unicode dài làm key vượt độ dài cột | Key không còn chứa tên file đã encode |
| Thiếu giới hạn cột, fileType có thể chèn markup | Validation độ dài/kiểu/size và escape nhãn định dạng |
| Tên file chứa ký tự header không hợp lệ | Chặn CR/LF/NUL; Content-Disposition dùng UTF-8 builder |
| Kiểm tra owner theo trường tên cũ | Dùng owner_id |
| Upload chờ quota có thể giữ entity cũ sau khi bị trash/purge | Khóa và refresh trạng thái trước thao tác ghi/ký URL |
| Audit thay đổi dữ liệu ở transaction độc lập | Ghi cùng transaction cho sửa/chia sẻ/thông báo |
| Response file về sau khi đổi tài khoản | Kiểm tra phiên trước khi tải/hiển thị và giữa các bước upload |
| URL blob Preview giữ trong bộ nhớ | Thu hồi khi đóng/thay preview; xử lý MIME PDF từ phần mở rộng |
| VIEW-only thấy nút tải ở định dạng không hỗ trợ preview | Chỉ hiện nút nếu quyền cho tải |
| Xóa tài khoản vướng khóa ngoại và có thể tạo lại danh tính | Xóa logic DELETED; giữ identity/audit, từ chối token và ẩn khỏi danh sách |
| Script startup không khớp origin/path của callback | Đọc cùng cấu hình public; CORS theo origin đó |
| Server local có nguy cơ phục vụ cả project | Chỉ phục vụ FrontEnd; chặn traversal/hidden files/symlink ra ngoài |
| Xung đột khóa trả lỗi chung | Trả 409 để người dùng có thể thử lại |
| Script AWS có thể bỏ qua lỗi của lệnh cấu hình | Dừng ngay khi lệnh native báo lỗi |

## Nhóm kiểm thử tự động

| Nhóm | Các trường hợp chính |
| --- | --- |
| Đăng nhập | PKCE S256/state, callback thiếu/trùng/sai/hết hạn, token ký sai, issuer/client sai, token cũ, 401/403 |
| Tài khoản | ADMIN/USER, khóa/chờ duyệt/DELETED, admin cuối, không tự khóa/hạ quyền/xóa, email trùng, họ tên |
| Tài liệu | Unicode, dữ liệu thiếu/quá dài, file rỗng, key duy nhất, tìm theo họ tên, 400/403/404/409 |
| Phân quyền | OWNER/ADMIN/EDIT/DOWNLOAD/VIEW, owner text cũ không cấp nhầm quyền, chia sẻ nhiều file |
| Upload/dung lượng | Hết quota, file quá lớn, actual S3 size, pending allocation, unknown/0 B, owner quota, trạng thái bị đổi khi chờ khóa |
| Thùng rác | Xóa mềm/restore/purge, owner/admin, không restore PURGED, S3 lỗi không ghi thành công, route literal trash |
| Nhật ký/thông báo | User chỉ thấy sự kiện của mình, admin thấy tất cả, họ tên hồ sơ, admin xóa thông báo |
| Frontend | Đổi phiên giữa request, partial failure, chọn nhiều, file rỗng, escape HTML, thu hồi blob, chart đầy đủ định dạng |
| Script | Parse PowerShell; resolve callback/mount và kiểm tra đường dẫn static server |

Kết quả cuối: **108 kiểm thử backend và 53 kiểm thử frontend đều đạt; không có failure/error.**
Backend đã được build lại thành JAR. Tất cả script PowerShell qua kiểm tra cú pháp; JavaScript qua node --check.
Các kiểm thử bổ sung tập trung vào hồi quy đã tìm thấy, không kiểm thử bằng tài khoản/file thật của người dùng.

## Phần phải kiểm tra trên môi trường thật

1. Đăng nhập ở đúng callback hiện tại; đăng xuất; token của tài khoản bị khóa/DELETED bị từ chối.
2. Upload TXT/PDF/PNG/DOCX và file rỗng; đối chiếu metadata, biểu đồ, tên người sở hữu;
   thử preview PDF/image/text và tải xuống. File Office chưa có bộ render riêng.
3. Dùng hai tài khoản test để thử đủ mức VIEW/DOWNLOAD/EDIT; thu hồi quyền khi đang mở trang.
4. Chọn nhiều tài liệu, chia sẻ một lần; kiểm tra trường hợp chỉ một file thất bại và retry.
5. Trash -> restore -> purge bằng file test; kiểm tra file S3 và quota. Kiểm tra cả S3 Versioning
   nếu môi trường có bật, vì DeleteObject không đồng nghĩa xóa mọi phiên bản.
6. Hai upload đồng thời khi quota chỉ đủ một; upload chạy song song với trash/purge;
   đo lock timeout và retry trên MySQL/RDS thật.
7. Thử SQL trên bản sao schema trước khi dùng cho môi trường khác. Repository hiện chỉ có
   migration tăng dần, chưa có installer hoàn chỉnh cho DB trắng.

## Giới hạn còn lại

- Chưa chạy end-to-end với database/AWS/browser của người dùng; kiểm thử Java dùng mock DB/S3.
- MySQL và S3 không có distributed transaction. File có thể đã xóa trên S3 trước khi một bước
  local/DB phía sau thất bại. Cần kiểm tra/quy trình retry và đối soát metadata trên môi trường thật.
- Signed URL đã cấp có thể còn hiệu lực tới hết hạn; đổi quyền không thu hồi ngay URL đã phát.
- Upload dở có thể để lại metadata giữ dung lượng. Người dùng cần xóa vĩnh viễn bản không cần;
  không tự chạy dọn bucket vì không đủ bằng chứng rằng object không thuộc người dùng.
- Khôi phục đưa tài liệu về ACTIVE; không lưu riêng trạng thái ARCHIVED/SHARED trước khi trash.
- Đăng nhập dùng localStorage access token như thiết kế hiện tại; chưa triển khai BFF/httpOnly session.
