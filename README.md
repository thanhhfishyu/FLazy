# FLazy

Quét sách đang mở trên màn hình Android thành file văn bản, để Claude (hoặc bất kỳ ai) đọc được.

FLazy tự chụp trang đang hiển thị, nhận dạng chữ Trung ngay trên máy (không cần mạng), rồi tự lật sang trang tiếp theo, lặp đến hết sách. Kết quả là một file `.txt`, mỗi trang có dòng `--- Trang N ---` để dễ tham chiếu.

## Cài đặt

1. Trên điện thoại, mở **https://github.com/thanhhfishyu/FLazy/releases**.
2. Ở bản mới nhất, tải file `FLazy-v1.0.x.apk`.
3. Mở file vừa tải. Nếu Android hỏi, cho phép trình duyệt/trình quản lý file "cài ứng dụng không rõ nguồn gốc".
4. Bản mới cài đè lên bản cũ, không cần gỡ.

Yêu cầu: Android 11 trở lên.

## Bật FLazy

Mở FLazy › **Mở cài đặt Trợ năng** › chọn **FLazy – quét sách** › Bật.

- Trên Android 13 trở lên, nếu mục này bị mờ: vào **Cài đặt › Ứng dụng › FLazy › ⋮ (góc trên) › Cho phép cài đặt bị hạn chế**, rồi bật lại.
- Khi bật xong, một nút nổi nhỏ `⠿ ▶ FLazy` hiện trên màn hình.

## Quét một cuốn sách

1. Trong FLazy, nhập tên sách và chọn cách app đọc sách lật trang (chạm cạnh phải, vuốt, hay cuộn dọc).
2. Mở app đọc sách, tới trang muốn bắt đầu.
3. Bấm **▶** trên nút nổi. FLazy làm phần còn lại.
4. Bấm **■** để dừng bất cứ lúc nào. FLazy tự dừng khi trang không đổi nữa (thường là hết sách).

Mẹo:
- Kéo nút nổi bằng biểu tượng `⠿` để nó không che chỗ lật trang.
- Nếu chữ bị lẫn tiêu đề app hay số trang, tăng **Bỏ phần trên / Bỏ phần dưới (%)**.
- Nếu app đọc sách lật chậm, tăng **Chờ mỗi trang (giây)**.

## Đưa cho Claude đọc

1. Trong FLazy, bấm **Gửi** bên cạnh file › chọn **Drive** › lưu vào một thư mục, ví dụ `FLazy`.
2. Trong Claude (đã kết nối Google Drive), nói: *"Đọc file <tên sách> trong thư mục FLazy trên Drive và tóm tắt chương 3."*

Hoặc tải file `.txt` vào phần kiến thức của một **Project** trong Claude để mọi cuộc trò chuyện trong Project đó đều dùng được.

File cũng được sao một bản vào thư mục **Download/FLazy** trên máy.

## Quyền riêng tư

- FLazy **không có quyền Internet**. Không có gì được gửi đi trừ khi bạn tự bấm **Gửi**.
- Ảnh chụp chỉ tồn tại trong bộ nhớ vài giây để đọc chữ, rồi bị xoá. Chỉ chữ được lưu.
- FLazy chỉ chụp và lật trang khi bạn bấm ▶. Nó không đọc nội dung các app khác theo cách nào khác (`canRetrieveWindowContent=false`).

## Giới hạn

- App đọc sách nào **chặn chụp màn hình** thì FLazy không quét được và sẽ báo lỗi. FLazy không tìm cách vượt qua việc chặn này.
- Chữ quá nhỏ, chữ dọc (sách cổ) hoặc trang toàn hình ảnh có thể đọc sai hoặc không đọc được.
- Chỉ dùng cho sách bạn đã mua, để học cá nhân. Không chia sẻ lại nội dung đã quét.

## Dành cho người phát triển

- Kotlin, Android Views, ML Kit Text Recognition (Chinese, bản đóng gói sẵn trong APK).
- Mỗi lần đẩy lên `main`, GitHub Actions build APK và tạo Release mới (`.github/workflows/build.yml`).
- Khoá ký `app/flazy-debug.jks` được để trong repo để các bản build cài đè lên nhau được. Đây là khoá riêng cho bản tự cài, không dùng để phát hành lên Google Play.

| File | Vai trò |
|---|---|
| `ScanService.kt` | Nút nổi, vòng lặp chụp → đọc chữ → lật trang |
| `OcrEngine.kt` | Nhận dạng chữ Trung bằng ML Kit |
| `PageText.kt` | Ghép dòng thành đoạn, so sánh trang, bỏ phần trùng khi cuộn |
| `BookStore.kt` | Lưu file `.txt`, sao ra Download/FLazy |
| `MainActivity.kt` | Màn hình cài đặt và danh sách sách đã quét |
