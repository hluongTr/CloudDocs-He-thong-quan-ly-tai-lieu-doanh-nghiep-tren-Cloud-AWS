# Giao việc và nộp báo cáo — phiên bản đầu

## Cài đặt bản mới

1. Sao lưu schema hoặc dùng bản sao để thử migration trước.
2. Trong MySQL Workbench, chọn đúng kết nối RDS và database CloudDocs đang dùng.
3. Mở Back_end/db/migration/06_APPLY_work_tasks.sql. Chạy riêng phần preflight và xác nhận
   đúng database, bảng work_tasks chưa tồn tại, notifications chưa có recipient_id/work_task_id.
4. Chạy CREATE TABLE và ALTER TABLE một lần. Migration không xóa dữ liệu cũ; thông báo cũ
   vẫn là thông báo chung. MySQL DDL tự commit, không có rollback tự động cho migration.
5. Khởi động lại backend bằng JAR mới, rồi Ctrl+F5 frontend.

Backend vẫn dùng ddl-auto=validate. Nếu chưa áp dụng migration, bản mới không thể khởi động.
Không chạy lại các migration 01-05 trên schema đã được cập nhật và không chạy migration 04
cùng share-cognito-identities.sql vì chúng tạo cùng một bảng.

## Quy trình

ADMIN vào Công việc -> Giao việc, nhập tiêu đề, yêu cầu, chọn một USER đang hoạt động đã có
liên kết Cognito, đặt hạn nộp trong tương lai. Nhân viên nhận thông báo riêng.

| Trạng thái | Người thao tác | Thao tác tiếp theo |
| --- | --- | --- |
| Chưa làm (TODO) | Người nhận việc | Bắt đầu làm hoặc nộp báo cáo |
| Đang làm (IN_PROGRESS) | Người nhận việc | Chọn một tài liệu đã upload xong của mình, kèm ghi chú để nộp |
| Chờ duyệt (SUBMITTED) | ADMIN đã giao việc | Duyệt hoàn thành hoặc yêu cầu chỉnh sửa có lý do |
| Hoàn thành (COMPLETED) | Chỉ đọc | Xem lại việc và tài liệu báo cáo |

Yêu cầu chỉnh sửa đưa công việc về IN_PROGRESS. Người nhận thấy phản hồi, sửa/tải báo cáo,
rồi nộp lại. Lần nộp mới xóa phản hồi duyệt cũ khỏi phần phản hồi hiện tại; các hành động vẫn
được ghi trong audit. Bản đầu lưu một tài liệu báo cáo hiện tại cho mỗi công việc, chưa có
bảng lịch sử các phiên bản báo cáo riêng.

Quá hạn là nhãn tự tính khi TODO/IN_PROGRESS đã vượt hạn nộp, không phải một trạng thái riêng.
SUBMITTED không bị đánh quá hạn chỉ vì người giao duyệt chậm. Nộp sau hạn vẫn được chấp nhận
và có nhãn Nộp muộn. Không có nhắc hạn định kỳ tự động ở bản đầu.

## Phân quyền và tính nhất quán

- USER chỉ xem việc mình nhận hoặc từng giao; ADMIN được xem toàn bộ để theo dõi hệ thống.
- Chỉ người được giao mới được bắt đầu/nộp. Chỉ ADMIN đã giao việc đó mới được duyệt/yêu cầu sửa.
- Mỗi thay đổi gửi version đang hiển thị. Dữ liệu đã thay đổi sẽ trả 409, không duyệt nhầm
  một lần nộp mới dựa trên màn hình cũ. Backend khóa dòng và dùng optimistic version.
- Báo cáo phải là tài liệu đang hoạt động thuộc người nộp và có bản ghi xác nhận UPLOAD.
  Tài liệu còn giữ chỗ, trong thùng rác hoặc của người khác không được nộp. Nếu tài liệu cũ
  thực sự tồn tại nhưng thiếu xác nhận, hãy tải lại/xác nhận upload trước khi chọn làm báo cáo.
- Khi nộp, người giao được thêm VIEW nếu chưa có quyền; quyền mạnh hơn đang có không bị hạ.
- Không cho trash/purge hoặc yêu cầu ghi đè file đang là báo cáo SUBMITTED/COMPLETED. Sửa
  tiêu đề/mô tả tài liệu vẫn được phép theo quyền hiện có. Không xóa file để thử guard này.
- Không xóa tài khoản còn công việc chưa hoàn thành, dù là người giao hay người nhận.
- Thông báo công việc chỉ xuất hiện với người nhận liên quan; người nhận có thể xóa thông báo
  của mình. ADMIN vẫn quản lý thông báo chung nhưng không được xóa thông báo riêng của người khác.
- Task, quyền xem báo cáo, thông báo và audit được ghi trong cùng transaction dữ liệu.

Signed URL đã cấp từ trước vẫn có hiệu lực tới hết hạn như thiết kế S3 hiện tại; guard API
không thể thu hồi ngay những URL cũ. Nếu cần tính bất biến cấp object/version cho báo cáo,
cần bổ sung bản chụp hoặc S3 version ID ở phiên bản sau.

## API

| Method/path | Chức năng |
| --- | --- |
| GET /api/work-tasks | Danh sách đã lọc theo tài khoản |
| GET /api/work-tasks/assignees | ADMIN lấy nhân viên hợp lệ |
| GET /api/work-tasks/report-documents | Tài liệu của người hiện tại đủ điều kiện nộp |
| GET /api/work-tasks/{id} | Chi tiết có kiểm tra quyền |
| POST /api/work-tasks | Giao một người, gồm title/description/assigneeId/dueAt |
| POST /api/work-tasks/{id}/start | Bắt đầu, gồm version |
| POST /api/work-tasks/{id}/submit | Nộp, gồm version/documentId/note |
| POST /api/work-tasks/{id}/approve | Duyệt, gồm version/feedback |
| POST /api/work-tasks/{id}/request-changes | Yêu cầu sửa, gồm version/feedback bắt buộc |

Audit: TASK_DELETED, TASK_ASSIGNED, TASK_STARTED, TASK_SUBMITTED, TASK_APPROVED, TASK_CHANGES_REQUESTED.
Thông báo có workTaskId để mở công việc tương ứng; canDelete do backend quyết định.

## Kiểm tra sau migration

1. ADMIN giao việc cho USER, đăng nhập USER và kiểm tra thông báo riêng; USER khác không thấy.
2. USER bắt đầu làm, upload một file nhỏ, chọn file và nộp báo cáo.
3. ADMIN mở báo cáo, yêu cầu sửa có lý do; USER thấy phản hồi và nộp lại.
4. ADMIN duyệt hoàn thành; USER không thể tiếp tục nộp và file báo cáo không được trash/ghi đè.
5. Dùng hai tab: tab cũ thử duyệt sau khi báo cáo đã được nộp lại, phải bị 409 và yêu cầu mở lại.
6. Kiểm tra hạn nộp, nộp muộn, lọc theo người giao/người nhận, tên hồ sơ và audit.
7. Kiểm tra thông báo chung cũ vẫn đọc/gửi/xóa được theo quyền ADMIN như trước.

Kiểm thử tự động dùng mock repository/S3; migration và quy trình mới chưa tự chạy trên RDS thật.

## Xóa công việc

`DELETE /api/work-tasks/{id}?version={version}`: chỉ ADMIN đã giao việc được xóa, kiểm tra phiên bản để tránh xóa nhầm dữ liệu vừa thay đổi. Xóa công việc và thông báo liên quan trong cùng giao dịch, giữ tài liệu báo cáo, quyền chia sẻ và nhật ký TASK_DELETED. Không thể khôi phục công việc sau khi xác nhận xóa. Không cần migration mới. Giao diện hỗ trợ chọn một/nhiều, chọn tất cả trong kết quả hiện tại; lỗi một việc không ngăn xóa những việc còn lại.

Nút nộp bị khóa khi không có tài liệu sẽ không hiện vòng xoay. Chỉ yêu cầu đang gửi mới hiện trạng thái bận. Công việc quá hạn vẫn nộp được và được đánh dấu nộp muộn.
