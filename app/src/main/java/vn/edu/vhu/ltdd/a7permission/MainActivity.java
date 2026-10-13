package vn.edu.vhu.ltdd.a7permission;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // TODO: thay 2201234567 bằng MSSV của bạn
    private static final String TAG = "A7_2201234567";

    private static final String CHANNEL_ID = "a7_channel";
    private static final int NOTI_ID = 1001;

    private ImageView imgAnh;
    private TextView tvTrangThai;

    // ---------------------------------------------------------------
    // 1) Bộ xin quyền CAMERA.
    //    registerForActivityResult phải được gọi TRƯỚC khi Activity ở trạng thái
    //    STARTED, nên ta khai báo ở cấp field (chạy trong constructor).
    // ---------------------------------------------------------------
    private final ActivityResultLauncher<String> xinQuyenCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền CAMERA: " + duocCap);
                if (duocCap) {
                    moCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Trạng thái 2: từ chối nhưng còn hỏi lại được
                    hienGiaiThich();
                } else {
                    // Trạng thái 3: đã chọn "Không cho phép" nhiều lần → hệ thống không
                    // hiện hộp thoại nữa, chỉ còn cách vào Cài đặt
                    hienMoCaiDat();
                }
            });

    // 2) Bộ xin quyền POST_NOTIFICATIONS (chỉ cần từ Android 13 / API 33)
    private final ActivityResultLauncher<String> xinQuyenThongBao = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    guiThongBao();
                } else {
                    Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
                }
            });

    // 2.1) Bộ xin quyền READ_CONTACTS (đọc danh bạ)
    private final ActivityResultLauncher<String> xinQuyenDanhBa = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền READ_CONTACTS: " + duocCap);
                if (duocCap) {
                    doc5LienHeDauTien();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                    // Người dùng từ chối lần đầu, hiển thị giải thích
                    hienGiaiThichDanhBa();
                } else {
                    // Người dùng chọn "Không hỏi lại" hoặc bị chặn vĩnh viễn
                    hienMoCaiDatDanhBa();
                }
            });

    // 4) Bộ xin nhiều quyền cùng lúc (RequestMultiplePermissions)
    /**
     * Sử dụng ActivityResultContracts.RequestMultiplePermissions để yêu cầu cấp nhiều quyền một lúc.
     * Callback nhận về một Map<String, Boolean> trong đó key là tên quyền (Manifest.permission...) 
     * và value là trạng thái cấp phép (true nếu được cấp, false nếu từ chối).
     */
    private final ActivityResultLauncher<String[]> xinNhieuQuyen = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> ketQua) -> {
                Log.d(TAG, "Kết quả xin nhiều quyền: " + ketQua);
                
                // Trích xuất trạng thái của từng quyền riêng biệt từ Map kết quả
                Boolean cameraGranted = ketQua.get(Manifest.permission.CAMERA);
                Boolean contactsGranted = ketQua.get(Manifest.permission.READ_CONTACTS);

                // Hiển thị kết quả của từng quyền lên TextView trạng thái
                StringBuilder sb = new StringBuilder("Kết quả xin 2 quyền cùng lúc:\n");
                if (cameraGranted != null) {
                    sb.append("- CAMERA: ").append(cameraGranted ? "ĐÃ CẤP" : "TỪ CHỐI").append("\n");
                }
                if (contactsGranted != null) {
                    sb.append("- READ_CONTACTS: ").append(contactsGranted ? "ĐÃ CẤP" : "TỪ CHỐI").append("\n");
                }
                tvTrangThai.setText(sb.toString());

                // Xử lý logic riêng cho từng quyền nếu người dùng đã đồng ý cấp
                if (Boolean.TRUE.equals(cameraGranted)) {
                    // Xử lý khi camera được cấp (ví dụ: sẵn sàng chụp ảnh)
                    Log.d(TAG, "Quyền CAMERA đã được cấp qua RequestMultiplePermissions");
                }
                if (Boolean.TRUE.equals(contactsGranted)) {
                    // Xử lý khi quyền danh bạ được cấp (tiến hành đọc danh bạ)
                    doc5LienHeDauTien();
                }
            });

    // 3) Bộ mở Camera lấy ảnh xem trước (bitmap thu nhỏ, không cần lưu file)
    private final ActivityResultLauncher<Void> chupAnh = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            (Bitmap bitmap) -> {
                if (bitmap != null) {
                    imgAnh.setImageBitmap(bitmap);
                    tvTrangThai.setText(getString(R.string.status_photo,
                            bitmap.getWidth(), bitmap.getHeight()));
                } else {
                    Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        imgAnh = findViewById(R.id.imgAnh);
        tvTrangThai = findViewById(R.id.tvTrangThai);
        Button btnChupAnh = findViewById(R.id.btnChupAnh);
        Button btnThongBao = findViewById(R.id.btnThongBao);
        Button btnDanhBa = findViewById(R.id.btnDanhBa);
        Button btnXinHaiQuyen = findViewById(R.id.btnXinHaiQuyen);
        Button btnCaiDat = findViewById(R.id.btnCaiDat);

        taoKenhThongBao();

        btnChupAnh.setOnClickListener(v -> kiemTraRoiChup());
        btnThongBao.setOnClickListener(v -> kiemTraRoiGuiThongBao());
        btnDanhBa.setOnClickListener(v -> kiemTraRoiDocDanhBa());
        btnXinHaiQuyen.setOnClickListener(v -> {
            xinNhieuQuyen.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_CONTACTS
            });
        });
        btnCaiDat.setOnClickListener(v -> moCaiDatUngDung());
    }

    @Override
    protected void onResume() {
        super.onResume();
        capNhatTrangThai();   // người dùng có thể vừa đổi quyền trong Cài đặt rồi quay lại
    }

    // =====================  LUỒNG XIN QUYỀN CAMERA  =====================

    /** Mẫu chuẩn 3 nhánh mà Google khuyến nghị. */
    private void kiemTraRoiChup() {
        int trangThai = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (trangThai == PackageManager.PERMISSION_GRANTED) {
            // Nhánh 1: đã có quyền → dùng ngay
            moCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Nhánh 2: đã từng từ chối → giải thích lý do trước khi hỏi lại
            hienGiaiThich();
        } else {
            // Nhánh 3: lần đầu (hoặc đã bị chặn vĩnh viễn) → gọi thẳng hộp thoại hệ thống
            xinQuyenCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void hienGiaiThich() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_camera)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenCamera.launch(Manifest.permission.CAMERA))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void hienMoCaiDat() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(R.string.blocked_camera)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moCamera() {
        chupAnh.launch(null);
    }

    /** Mở đúng trang thông tin ứng dụng của app này trong Cài đặt. */
    private void moCaiDatUngDung() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    // =====================  LUỒNG THÔNG BÁO  =====================

    private void kiemTraRoiGuiThongBao() {
        // Trước Android 13 không có quyền POST_NOTIFICATIONS → gửi được ngay
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            guiThongBao();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            guiThongBao();
        } else {
            xinQuyenThongBao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Từ Android 8 (API 26) mọi thông báo đều phải thuộc về một kênh. */
    private void taoKenhThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kenh = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            kenh.setDescription(getString(R.string.channel_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(kenh);
        }
    }

    private void guiThongBao() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.noti_title))
                .setContentText(getString(R.string.noti_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(this).notify(NOTI_ID, b.build());
        } catch (SecurityException e) {
            // Người dùng vừa tắt quyền ở Cài đặt trong lúc app đang chạy
            Log.w(TAG, "Thiếu quyền gửi thông báo", e);
        }
    }

    // =====================  LUỒNG ĐỌC DANH BẠ  =====================

    /**
     * Kiểm tra quyền READ_CONTACTS trước khi đọc danh bạ theo mô hình 3 nhánh:
     * 1. Đã cấp quyền -> Tiến hành đọc 5 liên hệ đầu tiên.
     * 2. Cần giải thích -> Hiển thị hộp thoại giải thích lý do cần quyền.
     * 3. Chưa cấp / Bị chặn -> Yêu cầu hệ thống cấp quyền hoặc hướng dẫn mở Cài đặt.
     */
    private void kiemTraRoiDocDanhBa() {
        int trangThai = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS);
        if (trangThai == PackageManager.PERMISSION_GRANTED) {
            doc5LienHeDauTien();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            hienGiaiThichDanhBa();
        } else {
            xinQuyenDanhBa.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    /** Hiển thị hộp thoại giải thích lý do ứng dụng cần quyền đọc danh bạ. */
    private void hienGiaiThichDanhBa() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_contacts)
                .setPositiveButton(R.string.agree,
                        (d, w) -> xinQuyenDanhBa.launch(Manifest.permission.READ_CONTACTS))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Hiển thị hộp thoại hướng dẫn người dùng mở Cài đặt khi quyền bị chặn vĩnh viễn. */
    private void hienMoCaiDatDanhBa() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(R.string.blocked_contacts)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Truy vấn ContactsContract.Contacts.CONTENT_URI bằng ContentResolver
     * để lấy danh sách liên hệ và hiển thị 5 liên hệ đầu tiên lên giao diện.
     */
    private void doc5LienHeDauTien() {
        try {
            // Sử dụng ContentResolver để truy vấn Content Provider của Contacts
            Cursor cursor = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null,
                    null,
                    null,
                    null
            );

            StringBuilder sb = new StringBuilder("5 liên hệ đầu tiên:\n");
            if (cursor != null) {
                int count = 0;
                // Duyệt qua Cursor để lấy tối đa 5 liên hệ đầu tiên
                while (cursor.moveToNext() && count < 5) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        sb.append("- ").append(name != null ? name : "Không tên").append("\n");
                        count++;
                    }
                }
                cursor.close();
            }
            if (sb.toString().equals("5 liên hệ đầu tiên:\n")) {
                sb.append("Không tìm thấy liên hệ nào trong danh bạ.");
            }
            tvTrangThai.setText(sb.toString());
        } catch (SecurityException e) {
            Toast.makeText(this, "Thiếu quyền đọc danh bạ", Toast.LENGTH_SHORT).show();
        }
    }

    // =====================  HIỂN THỊ TRẠNG THÁI  =====================

    private void capNhatTrangThai() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean thongBao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean danhBa = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        String s = getString(R.string.status_format,
                camera ? getString(R.string.granted) : getString(R.string.denied),
                thongBao ? getString(R.string.granted) : getString(R.string.denied),
                danhBa ? getString(R.string.granted) : getString(R.string.denied));
        tvTrangThai.setText(s);
        Log.d(TAG, s.replace("\n", " | "));
    }
}