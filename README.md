# Công Ty Attendance Android

Ứng dụng Android dành cho điện thoại chấm công cố định tại Công Ty.

## Phạm vi hiện tại

- Màn hình điểm danh riêng cho thiết bị cố định.
- Xin quyền camera khi bắt đầu điểm danh.
- Camera trước bằng CameraX.
- Phát hiện khuôn mặt trực tiếp trên thiết bị bằng ML Kit.
- Kiểm tra cơ bản: đúng một khuôn mặt, đủ gần, nhìn thẳng và ổn định qua nhiều khung hình.
- Chưa xác định danh tính nhân sự và chưa ghi Attendance Event. Hai bước này chỉ nối khi contract FACE của backend Công Ty được khóa.

## Kiến trúc

APK là thiết bị đầu cuối. Nhân sự, mẫu khuôn mặt, xác minh danh tính, lịch công và Attendance Event thuộc backend/database Công Ty. APK không có database nghiệp vụ riêng.

## Chạy local

Mở repo bằng Android Studio, chờ Gradle Sync, chọn emulator hoặc thiết bị Android có camera và Run `app`.

Emulator dùng để kiểm giao diện và luồng camera. Độ chính xác nhận diện/liveness phải nghiệm thu lại trên điện thoại Android thật.
