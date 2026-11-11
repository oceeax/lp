package com.example.screenwake;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SettingActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATION = 1001;
    private static final int REQ_CAMERA = 1002;

    private TextView tvCameraStatus;
    private TextView tvPermissionStatus;
    private ScrollView scrollView;
    private TextView tvNetworkStatus;
    private TextView tvBrightnessStatus;
    private TextView tvVolumeStatus;

    private AudioManager audioManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 彻底抛弃 XML，全部代码动态生成 UI
        initUI();
LogManager.log(SettingActivity.this,"[MainActivity.java][CRASH] 程序发生崩溃");
        // 检查权限并刷新状态
        refreshCameraStatus();
        refreshStatus();
        // ==========================================================
// 网络状态检测
// ==========================================================
private void refreshNetworkStatus() {
    if (tvNetworkStatus == null) return;

    try {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            tvNetworkStatus.setText("网络状态：无法获取");
            LogManager.log(this, "[SettingActivity.java][NETWORK] ConnectivityManager为空");
            return;
        }

        Network network = cm.getActiveNetwork();

        if (network == null) {
            tvNetworkStatus.setText("网络状态：❌ 未连接");
            LogManager.log(this, "[SettingActivity.java][NETWORK] 当前无活动网络");
            return;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);

        if (caps == null) {
            tvNetworkStatus.setText("网络状态：无法获取网络能力");
            LogManager.log(this, "[SettingActivity.java][NETWORK] NetworkCapabilities为空");
            return;
        }

        boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        boolean internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        String type = "其他网络";

        if (wifi) {
            type = "WiFi";
        } else if (mobile) {
            type = "移动数据";
        } else if (ethernet) {
            type = "以太网";
        }

        String result =
                "【网络状态】\n"
                + "连接状态：✅ 已连接\n"
                + "网络类型：" + type + "\n"
                + "具有互联网能力：" + (internet ? "是" : "否") + "\n"
                + "已验证互联网连接：" + (validated ? "是" : "否");

        tvNetworkStatus.setText(result);

        LogManager.log(this,
                "[SettingActivity.java][NETWORK] 网络检测成功"
                + " type=" + type
                + " internet=" + internet
                + " validated=" + validated);

    } catch (Exception e) {
        tvNetworkStatus.setText("网络检测失败：" + e.getMessage());

        LogManager.log(this,
                "[SettingActivity.java][NETWORK][ERROR] " + e.toString());
    }
}
        requestNotificationPermissionIfNeeded();
        
        
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCameraStatus();
        refreshStatus();
    }

    // ==========================================================
    // 构建整个页面的 UI
    // ==========================================================
    private void initUI() {
        scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        // 1. 标题
        TextView title = new TextView(this);
        title.setText("开机亮屏与功能设置");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // 2. 相机权限行 (横向布局)
        LinearLayout cameraRow = new LinearLayout(this);
        cameraRow.setOrientation(LinearLayout.HORIZONTAL);
        cameraRow.setGravity(Gravity.CENTER_VERTICAL);
        cameraRow.setPadding(0, dp(20), 0, dp(10));

        tvCameraStatus = new TextView(this);
        tvCameraStatus.setTextSize(16);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cameraRow.addView(tvCameraStatus, tvLp);

        Button btnCamera = new Button(this);
        btnCamera.setText("申请相机权限");
        btnCamera.setAllCaps(false);
        btnCamera.setOnClickListener(v -> onCameraButtonClick());
        cameraRow.addView(btnCamera);

        root.addView(cameraRow);

        // 3. 分隔线
        ViewGroup.LayoutParams lineLp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(0xFFDDDDDD);
        line.setLayoutParams(lineLp);
        root.addView(line);

        // 4. 权限状态显示
        tvPermissionStatus = new TextView(this);
        tvPermissionStatus.setTextSize(14);
        tvPermissionStatus.setPadding(0, dp(16), 0, dp(16));
        root.addView(tvPermissionStatus);

        // 5. 各种设置按钮
        addButton(root, "1. 开启悬浮窗权限", this::openOverlay);
        addButton(root, "2. 开启通知权限", this::openNotifications);
        addButton(root, "3. 允许后台运行 / 忽略电池优化", this::openBattery);
        addButton(root, "4. 打开应用详情（自启动等设置）", this::openAppDetails);
        addButton(root, "5. 立即测试：显示图案 5 分钟", this::testNow);
        // ================= 网络、亮度、声音功能 =================

// 网络状态
tvNetworkStatus = new TextView(this);
tvNetworkStatus.setTextSize(14);
tvNetworkStatus.setPadding(0, dp(12), 0, dp(12));
root.addView(tvNetworkStatus);

addButton(root, "6. 刷新网络状态", () -> refreshNetworkStatus());

addButton(root, "7. 打开 WiFi 设置 / 开关", () -> openWifiSettings());

addButton(root, "8. 打开蓝牙设置 / 开关", () -> openBluetoothSettings());

// 屏幕亮度
TextView brightnessTitle = new TextView(this);
brightnessTitle.setText("【屏幕亮度】");
brightnessTitle.setTextSize(17);
brightnessTitle.setPadding(0, dp(18), 0, dp(6));
root.addView(brightnessTitle);

tvBrightnessStatus = new TextView(this);
tvBrightnessStatus.setTextSize(14);
root.addView(tvBrightnessStatus);

addButton(root, "9. 刷新屏幕亮度", () -> refreshBrightness());

addButton(root, "10. 打开屏幕亮度设置", () -> openBrightnessSettings());

// 声音
TextView volumeTitle = new TextView(this);
volumeTitle.setText("【声音与音量】");
volumeTitle.setTextSize(17);
volumeTitle.setPadding(0, dp(18), 0, dp(6));
root.addView(volumeTitle);

tvVolumeStatus = new TextView(this);
tvVolumeStatus.setTextSize(14);
root.addView(tvVolumeStatus);

addButton(root, "11. 媒体音量 +", () -> changeVolume(AudioManager.STREAM_MUSIC, true));

addButton(root, "12. 媒体音量 -", () -> changeVolume(AudioManager.STREAM_MUSIC, false));

addButton(root, "13. 打开声音设置", () -> openSoundSettings());

addButton(root, "14. 打开显示设置", () -> openDisplaySettings());

// 系统设置
TextView systemTitle = new TextView(this);
systemTitle.setText("【系统设置】");
systemTitle.setTextSize(17);
systemTitle.setPadding(0, dp(18), 0, dp(6));
root.addView(systemTitle);

addButton(root, "15. 打开系统设置主页", () -> openSystemSettings());

addButton(root, "16. 打开应用详情", () -> openAppDetails());

// 初始化声音管理器
audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

// 初始化状态
refreshNetworkStatus();
refreshBrightness();
refreshVolume();
        // 6. 底部提示
        TextView note = new TextView(this);
        note.setText("OPPO/ColorOS：另外在系统设置或手机管家中搜索并开启“自启动”“允许后台活动”“后台弹出界面”。这些是厂商设置，菜单名称因版本而异，应用无法替你自动开启。");
        note.setTextSize(13);
        note.setPadding(0, dp(20), 0, 0);
        note.setTextColor(0xFF666666);
        root.addView(note);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        parent.addView(b, lp);
    }

    // ==========================================================
    // 相机权限逻辑
    // ==========================================================
    private void onCameraButtonClick() {
        int hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (hasCam == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "✅已有相机权限", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void refreshCameraStatus() {
        if (tvCameraStatus == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            tvCameraStatus.setText("✅ 相机权限：已授权");
            tvCameraStatus.setTextColor(0xFF4CAF50); // 绿色
        } else {
            tvCameraStatus.setText("❌ 相机权限：未授权");
            tvCameraStatus.setTextColor(0xFFF44336); // 红色
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
LogManager.log(SettingActivity.this,"[SettingActivity.java][INFO] 跳相机权限");
        if (requestCode == REQ_CAMERA) {
            refreshCameraStatus();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ 相机授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ 相机权限被拒绝", Toast.LENGTH_SHORT).show();
                boolean noAsk = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
                if (noAsk) openAppDetails();
            }
        } else if (requestCode == REQ_NOTIFICATION) {
            refreshStatus();
        }
        
        
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    // ==========================================================
    // 状态刷新与功能跳转
    // ==========================================================
    private void refreshStatus() {
        if (tvPermissionStatus == null) return;

        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);

        boolean battery = false;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        String notice;
        if (Build.VERSION.SDK_INT >= 33) {
            notice = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? "已允许" : "未允许";
        } else {
            notice = "无需授权";
        }

        tvPermissionStatus.setText("【系统权限状态】\n"
                + "悬浮窗：" + (overlay ? "✅ 已允许" : "❌ 未允许") + "\n"
                + "通知：" + (notice.equals("已允许") ? "✅ 已允许" : notice) + "\n"
                + "电池优化：" + (battery ? "✅ 已忽略" : "❌ 仍受限制") + "\n"
                + "自启动、后台弹出：请手动检查系统设置");
    }

    private void openOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openAppDetails();
            }
        }
    }

    private void openNotifications() {
        if (Build.VERSION.SDK_INT >= 26) {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } else {
            openAppDetails();
        }
        LogManager.log(SettingActivity.this,"[MainActivity.this][CRASH] 程序发生崩溃");
    }

    private void openBattery() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ex) {
                    openAppDetails();
                }
            }
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开应用详情页", Toast.LENGTH_SHORT).show();
        }
    }

    private void testNow() {
        try {
            Intent i = new Intent(this, ScreenWakeService.class);
            i.setAction(ScreenWakeService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        LogManager.log(SettingActivity.this,"[SettingActivity.java][INFO] 跳启动权限");
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}


