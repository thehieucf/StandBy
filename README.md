# StandBy

Biến điện thoại Android thành đồng hồ để bàn khi máy đang khóa và được đặt nằm ngang, tương tự chế độ StandBy trên iPhone. App được làm cho máy Xiaomi chạy HyperOS, giao diện hoàn toàn bằng tiếng Việt.

## Tính năng

- **Tự bật khi khóa máy và đặt ngang.** Một dịch vụ chạy nền theo dõi trạng thái sạc, màn hình khóa và cảm biến gia tốc. Khi máy nằm ngang ổn định, StandBy tự sáng màn qua full-screen intent (giống app báo thức). Có thể chọn chỉ bật khi đang sạc.
- **15 mặt đồng hồ:** Số lớn, Lật số, Chữ viền, Đèn LED, Cuộn số, Kim & lịch, Kim lớn, Vòng tròn, Lịch âm dương, Mặt trời & trăng, Dòng thời gian, Bằng chữ, Giờ thế giới (Tokyo, Paris, New York), Pin & báo thức, Khung ảnh.
- **Lịch âm Việt Nam:** ngày/tháng can chi và giờ hoàng đạo, tính theo thuật toán của Hồ Ngọc Đức (múi giờ +7).
- **8 hiệu ứng nền:** tuyết rơi, mưa, đom đóm, trời sao, hoa mai, bong bóng, cực quang, pháo hoa. Có 3 mức mật độ.
- **Tùy biến:** 8 màu nhấn, 9 kiểu chữ số lấy từ font có sẵn của HyperOS (không có font thì dùng font hệ thống), đồng hồ 12/24 giờ.
- **Khung ảnh:** chọn ảnh từ máy để trình chiếu, ảnh được chép vào bộ nhớ riêng của app.
- **Chống lưu ảnh màn hình (burn-in):** dịch điểm ảnh mỗi phút, làm mờ sau 5 phút không chạm, tự đổi giao diện mỗi 20 phút, chế độ ban đêm chữ đỏ khi phòng tối, tắt hẳn màn khi phòng tối lâu.
- **Tiết kiệm pin:** khi không sạc thì tự thoát sau một khoảng không chạm, và thoát khi pin dưới 15%. Có thể đặt giới hạn thời gian tối đa cho mỗi lần chạy.
- **Tự chạy lại** sau khi khởi động máy hoặc cập nhật app.

## Thao tác trên màn StandBy

| Cử chỉ | Tác dụng |
|---|---|
| Vuốt ngang | Đổi giao diện |
| Vuốt xuống | Đổi kiểu chữ số |
| Chạm giữ | Đổi màu nhấn |
| Chạm 2 lần | Đổi hiệu ứng nền |
| Vuốt lên | Mở khóa bằng vân tay hoặc mật khẩu |
| Nút nguồn | Tắt màn, StandBy không tự bật lại |
| Dựng dọc máy | Thoát StandBy |

## Yêu cầu

- Android 12 (API 31) trở lên, target API 35.
- Dành cho Xiaomi/HyperOS. Trên máy hãng khác app vẫn chạy được, nhưng các font riêng và lối tắt tới trang cấp quyền của MIUI sẽ không có tác dụng.

## Cài đặt

1. Cài file `StandBy.apk` lên máy.
2. Mở app và cấp đủ các quyền trong mục **Quyền**:
   - Thông báo
   - Thông báo toàn màn hình (để StandBy tự sáng màn khi máy đang tắt)
   - Tiết kiệm pin → **Không hạn chế** (để HyperOS không đóng băng app khi tắt màn)
   - Hiện trên màn hình khóa
   - Mở cửa sổ khi chạy nền
   - Tự khởi chạy
3. Khóa máy rồi đặt nằm ngang.

Thiếu quyền nào thì app hiện cảnh báo ngay ở đầu trang cài đặt.

## Tự build

Dự án không dùng Gradle. Script `build.sh` gọi trực tiếp các công cụ của Android SDK:

```
aapt2 → javac → d8 → zipalign → apksigner
```

Cần có:

- JDK (có `javac` và `keytool`)
- Android SDK với `build-tools/35.0.0` và `platforms/android-35`
- `zip`

Build:

```bash
./build.sh
```

Script tìm SDK ở `$ANDROID_HOME`, nếu không có thì dùng `~/Android/Sdk`. Có thể đặt số phiên bản bằng biến môi trường:

```bash
VER_CODE=2 VER_NAME=2.0 ./build.sh
```

File kết quả là `StandBy.apk` ở thư mục gốc. Nếu chưa có `keystore.jks`, script sẽ tự tạo một keystore mới để ký.

Cài lên máy qua adb:

```bash
adb install -r StandBy.apk
```

## Cấu trúc mã nguồn

```
AndroidManifest.xml
build.sh                 Script build không cần Gradle
res/                     Icon, màu, theme
src/com/thehieu/standby/
  StandbyService.java    Dịch vụ nền: theo dõi sạc, khóa màn, hướng máy
  StandbyActivity.java   Màn hình StandBy hiện trên màn khóa
  StandbyView.java       View vẽ đồng hồ, xử lý cử chỉ
  Faces.java             Vẽ 15 mặt đồng hồ
  Effects.java           Hiệu ứng nền
  Lunar.java             Đổi dương lịch sang âm lịch
  Fonts.java             Nạp font của HyperOS
  Photos.java            Quản lý ảnh cho mặt Khung ảnh
  PhotosActivity.java    Màn chọn và xem ảnh
  PhotoTile.java         Ô ảnh trong lưới
  MainActivity.java      Màn cài đặt
  FacePreview.java       Ảnh xem trước mặt đồng hồ trong cài đặt
  Ui.java, MiSwitch.java Thành phần giao diện cài đặt
  Prefs.java             Lưu cài đặt (SharedPreferences)
  BootReceiver.java      Khởi động lại dịch vụ sau khi bật máy
```
