package com.example.screenwake;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
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
    private TextView tvNetworkStatus;
    private TextView tvBrightnessStatus;
    private TextView tvVolumeStatus;

    private ScrollView scrollView;
    private AudioManager audioManager;

    // ==========================================================
    // Activity 生命周期
    // ==========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initUI();

        LogManager.log(this,
                "[SettingActivity.java][INFO] 页面创建");

        // 保留原来的日志
        LogManager.log(this,
                "[MainActivity.java][CRASH] 程序发生崩溃");

        refreshCameraStatus();
        refreshStatus();
        refreshNetworkStatus();
        refreshBrightness();
        refreshVolume();

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshCameraStatus();
        refreshStatus();
        refreshNetworkStatus();
        refreshBrightness();
        refreshVolume();

        LogManager.log(this,
                "[SettingActivity.java][LIFECYCLE] 页面恢复，状态刷新完成");
    }

    // ==========================================================
    // UI
    // ==========================================================

    private void initUI() {

        scrollView = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("开机亮屏与功能设置");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // 相机权限
        LinearLayout cameraRow = new LinearLayout(this);
        cameraRow.setOrientation(LinearLayout.HORIZONTAL);
        cameraRow.setGravity(Gravity.CENTER_VERTICAL);
        cameraRow.setPadding(0, dp(20), 0, dp(10));

        tvCameraStatus = new TextView(this);
        tvCameraStatus.setTextSize(16);

        LinearLayout.LayoutParams tvLp =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        cameraRow.addView(tvCameraStatus, tvLp);

        Button btnCamera = new Button(this);
        btnCamera.setText("申请相机权限");
        btnCamera.setAllCaps(false);
        btnCamera.setOnClickListener(v -> onCameraButtonClick());

        cameraRow.addView(btnCamera);
        root.addView(cameraRow);

        // 分隔线
        android.view.View line = new android.view.View(this);
        line.setBackgroundColor(0xFFDDDDDD);

        root.addView(line, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        // 原有权限状态
        tvPermissionStatus = new TextView(this);
        tvPermissionStatus.setTextSize(14);
        tvPermissionStatus.setPadding(0, dp(16), 0, dp(16));
        root.addView(tvPermissionStatus);

        // 原有功能
        addButton(root, "1. 开启悬浮窗权限", this::openOverlay);
        addButton(root, "2. 开启通知权限", this::openNotifications);
        addButton(root, "3. 允许后台运行 / 忽略电池优化", this::openBattery);
        addButton(root, "4. 打开应用详情（自启动等设置）", this::openAppDetails);
        addButton(root, "5. 立即测试：显示图案 5 分钟", this::testNow);

        // ======================================================
        // 网络
        // ======================================================

        addSection(root, "【网络状态】");

        tvNetworkStatus = new TextView(this);
        tvNetworkStatus.setTextSize(14);
        tvNetworkStatus.setPadding(0, dp(10), 0, dp(10));
        root.addView(tvNetworkStatus);

        addButton(root, "6. 刷新网络状态", this::refreshNetworkStatus);
        addButton(root, "7. 打开 WiFi 设置 / 开关", this::openWifiSettings);
        addButton(root, "8. 打开蓝牙设置 / 开关", this::openBluetoothSettings);

        // ======================================================
        // 屏幕亮度
        // ======================================================

        addSection(root, "【屏幕亮度】");

        tvBrightnessStatus = new TextView(this);
        tvBrightnessStatus.setTextSize(14);
        root.addView(tvBrightnessStatus);

        addButton(root, "9. 刷新屏幕亮度", this::refreshBrightness);
        addButton(root, "10. 打开屏幕亮度设置", this::openBrightnessSettings);
        addButton(root, "11. 修改系统亮度权限", this::openWriteSettings);

        // ======================================================
        // 音量
        // ======================================================

        addSection(root, "【声音与音量】");

        tvVolumeStatus = new TextView(this);
        tvVolumeStatus.setTextSize(14);
        root.addView(tvVolumeStatus);

        audioManager = (AudioManager)
                getSystemService(Context.AUDIO_SERVICE);

        addButton(root, "12. 媒体音量 +", () ->
                changeVolume(AudioManager.STREAM_MUSIC, true));

        addButton(root, "13. 媒体音量 -", () ->
                changeVolume(AudioManager.STREAM_MUSIC, false));

        addButton(root, "14. 铃声音量 +", () ->
                changeVolume(AudioManager.STREAM_RING, true));

        addButton(root, "15. 铃声音量 -", () ->
                changeVolume(AudioManager.STREAM_RING, false));

        addButton(root, "16. 闹钟音量 +", () ->
                changeVolume(AudioManager.STREAM_ALARM, true));

        addButton(root, "17. 闹钟音量 -", () ->
                changeVolume(AudioManager.STREAM_ALARM, false));

        addButton(root, "18. 打开声音设置", this::openSoundSettings);

        // ======================================================
        // 系统设置
        // ======================================================

        addSection(root, "【系统设置】");

        addButton(root, "19. 打开显示设置", this::openDisplaySettings);
        addButton(root, "20. 打开系统设置主页", this::openSystemSettings);
        addButton(root, "21. 打开应用详情", this::openAppDetails);

        // 原有提示
        TextView note = new TextView(this);

        note.setText(
                "OPPO/ColorOS：另外在系统设置或手机管家中搜索并开启"
                + "“自启动”“允许后台活动”“后台弹出界面”。"
                + "这些是厂商设置，菜单名称因版本而异，应用无法替你自动开启。"
        );

        note.setTextSize(13);
        note.setPadding(0, dp(20), 0, 0);
        note.setTextColor(0xFF666666);

        root.addView(note);

        scrollView.addView(root);
        setContentView(scrollView);

        LogManager.log(this,
                "[SettingActivity.java][UI] 所有设置界面创建完成");
    }

    private void addSection(LinearLayout root, String title) {

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(18);
        tv.setPadding(0, dp(20), 0, dp(8));

        root.addView(tv);

        LogManager.log(this,
                "[SettingActivity.java][UI] 创建分区：" + title);
    }

    private void addButton(
            LinearLayout parent,
            String label,
            Runnable action
    ) {

        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
b.setOnClickListener(v -> {

    // 独立设置日志：记录本地操作请求
    SettingLogManager.local(
            this,
            label,
            "REQUEST",
            "用户点击设置按钮"
    );

    LogManager.log(this,
            "[SettingActivity.java][BUTTON] 点击：" + label);

    try {
        action.run();

    } catch (Exception e) {

        // 独立设置日志：记录失败
        SettingLogManager.local(
                this,
                label,
                "FAILED",
                "执行异常：" + e.toString()
        );
                LogManager.log(this,
                        "[SettingActivity.java][BUTTON][ERROR] "
                                + label + " " + e.toString());

                Toast.makeText(this,
                        "操作失败：" + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        lp.setMargins(0, dp(6), 0, dp(6));

        parent.addView(b, lp);
    }

    // ==========================================================
    // 相机权限
    // ==========================================================

    private void onCameraButtonClick() {

        LogManager.log(this,
                "[SettingActivity.java][CAMERA] 请求相机权限");

        int hasCam = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        );

        if (hasCam == PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this,
                    "已有相机权限",
                    Toast.LENGTH_SHORT).show();

            LogManager.log(this,
                    "[SettingActivity.java][CAMERA] 已有授权");

        } else {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQ_CAMERA
            );
        }
    }

    private void refreshCameraStatus() {

        if (tvCameraStatus == null) return;

        boolean granted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED;

        if (granted) {

            tvCameraStatus.setText("相机权限：已授权");
            tvCameraStatus.setTextColor(0xFF4CAF50);

        } else {

            tvCameraStatus.setText("相机权限：未授权");
            tvCameraStatus.setTextColor(0xFFF44336);
        }

        LogManager.log(this,
                "[SettingActivity.java][CAMERA] 状态：" + granted);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        LogManager.log(this,
                "[SettingActivity.java][PERMISSION] 回调 requestCode="
                        + requestCode);

        if (requestCode == REQ_CAMERA) {

            refreshCameraStatus();

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this,
                        "相机授权成功",
                        Toast.LENGTH_SHORT).show();

                LogManager.log(this,
                        "[SettingActivity.java][CAMERA] 授权成功");

            } else {

                Toast.makeText(this,
                        "相机权限被拒绝",
                        Toast.LENGTH_SHORT).show();

                LogManager.log(this,
                        "[SettingActivity.java][CAMERA] 授权被拒绝");

                boolean noAsk =
                        !shouldShowRequestPermissionRationale(
                                Manifest.permission.CAMERA
                        );

                if (noAsk) {
                    openAppDetails();
                }
            }

        } else if (requestCode == REQ_NOTIFICATION) {

            refreshStatus();

            LogManager.log(this,
                    "[SettingActivity.java][NOTIFICATION] 通知权限回调");
        }
    }

    private void requestNotificationPermissionIfNeeded() {

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            LogManager.log(this,
                    "[SettingActivity.java][NOTIFICATION] 请求通知权限");

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    REQ_NOTIFICATION
            );
        }
    }

    // ==========================================================
    // 原有权限状态
    // ==========================================================

    private void refreshStatus() {

        if (tvPermissionStatus == null) return;

        boolean overlay =
                Build.VERSION.SDK_INT < 23
                        || Settings.canDrawOverlays(this);

        boolean battery = false;

        if (Build.VERSION.SDK_INT >= 23) {

            PowerManager pm =
                    (PowerManager) getSystemService(POWER_SERVICE);

            battery = pm != null
                    && pm.isIgnoringBatteryOptimizations(
                            getPackageName()
                    );
        }

        String notice;

        if (Build.VERSION.SDK_INT >= 33) {

            notice = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
                    ? "已允许"
                    : "未允许";

        } else {
            notice = "无需授权";
        }

        tvPermissionStatus.setText(
                "【系统权限状态】\n"
                        + "悬浮窗："
                        + (overlay ? "已允许" : "未允许") + "\n"
                        + "通知：" + notice + "\n"
                        + "电池优化："
                        + (battery ? "已忽略" : "仍受限制") + "\n"
                        + "自启动、后台弹出：请手动检查系统设置"
        );

        LogManager.log(this,
                "[SettingActivity.java][PERMISSION] "
                        + "overlay=" + overlay
                        + " notification=" + notice
                        + " battery=" + battery);
    }

    // ==========================================================
    // 网络状态
    // ==========================================================

    private void refreshNetworkStatus() {

        if (tvNetworkStatus == null) return;

        try {

            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            if (cm == null) {

                tvNetworkStatus.setText("无法获取网络管理器");

                LogManager.log(this,
                        "[SettingActivity.java][NETWORK][ERROR] CM为空");

                return;
            }

            Network network = cm.getActiveNetwork();

            if (network == null) {

                tvNetworkStatus.setText(
                        "网络状态：未连接"
                );

                LogManager.log(this,
                        "[SettingActivity.java][NETWORK] 无活动网络");

                return;
            }

            NetworkCapabilities caps =
                    cm.getNetworkCapabilities(network);

            if (caps == null) {

                tvNetworkStatus.setText(
                        "网络状态：无法获取网络能力"
                );

                LogManager.log(this,
                        "[SettingActivity.java][NETWORK] caps为空");

                return;
            }

            boolean wifi = caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
            );

            boolean mobile = caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
            );

            boolean ethernet = caps.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET
            );

            boolean internet = caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
            );

            boolean validated = caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
            );

            String type = "其他网络";

            if (wifi) {
                type = "WiFi";
            } else if (mobile) {
                type = "移动数据";
            } else if (ethernet) {
                type = "以太网";
            }

            tvNetworkStatus.setText(
                    "连接状态：已连接\n"
                            + "网络类型：" + type + "\n"
                            + "具有互联网能力：" + (internet ? "是" : "否") + "\n"
                            + "已验证互联网连接：" + (validated ? "是" : "否")
            );

            LogManager.log(this,
                    "[SettingActivity.java][NETWORK] "
                            + "type=" + type
                            + " internet=" + internet
                            + " validated=" + validated);

        } catch (Exception e) {

            tvNetworkStatus.setText(
                    "网络检测失败：" + e.getMessage()
            );

            LogManager.log(this,
                    "[SettingActivity.java][NETWORK][ERROR] "
                            + e.toString());
        }
    }

    // ==========================================================
    // WiFi
    // ==========================================================

    private void openWifiSettings() {

        LogManager.log(this,
                "[SettingActivity.java][WIFI] 请求打开WiFi控制");

        try {

            Intent intent;

            if (Build.VERSION.SDK_INT >= 29) {

                intent = new Intent(Settings.Panel.ACTION_WIFI);

            } else {

                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            }

            startActivity(intent);

            LogManager.log(this,
                    "[SettingActivity.java][WIFI] 已打开WiFi页面");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][WIFI][ERROR] "
                            + e.toString());

            try {

                startActivity(
                        new Intent(Settings.ACTION_WIFI_SETTINGS)
                );

                LogManager.log(this,
                        "[SettingActivity.java][WIFI] 备用页面打开成功");

            } catch (Exception ex) {

                LogManager.log(this,
                        "[SettingActivity.java][WIFI][ERROR] 备用入口失败 "
                                + ex.toString());

                Toast.makeText(this,
                        "无法打开WiFi设置",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==========================================================
    // 蓝牙
    // ==========================================================

    private void openBluetoothSettings() {

        LogManager.log(this,
                "[SettingActivity.java][BLUETOOTH] 请求打开蓝牙设置");

        try {

            startActivity(
                    new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            );

            LogManager.log(this,
                    "[SettingActivity.java][BLUETOOTH] 已打开蓝牙设置");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][BLUETOOTH][ERROR] "
                            + e.toString());

            try {

                startActivity(
                        new Intent(Settings.ACTION_SETTINGS)
                );

            } catch (Exception ex) {

                LogManager.log(this,
                        "[SettingActivity.java][BLUETOOTH][ERROR] 备用入口失败 "
                                + ex.toString());

                Toast.makeText(this,
                        "无法打开蓝牙设置",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==========================================================
    // 屏幕亮度
    // ==========================================================

    private void refreshBrightness() {

        if (tvBrightnessStatus == null) return;

        try {

            int brightness = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );

            int percent = brightness * 100 / 255;

            int mode = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE
            );

            String modeText =
                    mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                            ? "自动亮度"
                            : "手动亮度";

            boolean canWrite =
                    Build.VERSION.SDK_INT < 23
                            || Settings.System.canWrite(this);

            tvBrightnessStatus.setText(
                    "当前亮度：" + percent + "%\n"
                            + "亮度数值：" + brightness + "/255\n"
                            + "亮度模式：" + modeText + "\n"
                            + "修改系统亮度权限："
                            + (canWrite ? "已允许" : "未允许")
            );

            LogManager.log(this,
                    "[SettingActivity.java][BRIGHTNESS] "
                            + "value=" + brightness
                            + " percent=" + percent
                            + " mode=" + modeText
                            + " canWrite=" + canWrite);

        } catch (Exception e) {

            tvBrightnessStatus.setText(
                    "亮度读取失败：" + e.getMessage()
            );

            LogManager.log(this,
                    "[SettingActivity.java][BRIGHTNESS][ERROR] "
                            + e.toString());
        }
    }

    private void openBrightnessSettings() {

        LogManager.log(this,
                "[SettingActivity.java][BRIGHTNESS] 打开显示设置");

        try {

            startActivity(
                    new Intent(Settings.ACTION_DISPLAY_SETTINGS)
            );

            LogManager.log(this,
                    "[SettingActivity.java][BRIGHTNESS] 显示设置已打开");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][BRIGHTNESS][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "无法打开亮度设置",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openWriteSettings() {

        LogManager.log(this,
                "[SettingActivity.java][BRIGHTNESS] 请求修改系统设置权限");

        try {

            if (Build.VERSION.SDK_INT >= 23) {

                if (Settings.System.canWrite(this)) {

                    Toast.makeText(this,
                            "已经拥有修改系统设置权限",
                            Toast.LENGTH_SHORT).show();

                    LogManager.log(this,
                            "[SettingActivity.java][BRIGHTNESS] 已有修改权限");

                } else {

                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + getPackageName())
                    );

                    startActivity(intent);

                    LogManager.log(this,
                            "[SettingActivity.java][BRIGHTNESS] 已打开授权页面");
                }
            }

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][BRIGHTNESS][ERROR] "
                            + e.toString());
        }
    }

    // ==========================================================
    // 音量
    // ==========================================================

    private void refreshVolume() {

        if (tvVolumeStatus == null) return;

        try {

            if (audioManager == null) {

                audioManager = (AudioManager)
                        getSystemService(Context.AUDIO_SERVICE);
            }

            if (audioManager == null) return;

            int music = audioManager.getStreamVolume(
                    AudioManager.STREAM_MUSIC
            );

            int musicMax = audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_MUSIC
            );

            int ring = audioManager.getStreamVolume(
                    AudioManager.STREAM_RING
            );

            int ringMax = audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_RING
            );

            int alarm = audioManager.getStreamVolume(
                    AudioManager.STREAM_ALARM
            );

            int alarmMax = audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_ALARM
            );

            tvVolumeStatus.setText(
                    "媒体音量：" + music + "/" + musicMax + "\n"
                            + "铃声音量：" + ring + "/" + ringMax + "\n"
                            + "闹钟音量：" + alarm + "/" + alarmMax
            );

            LogManager.log(this,
                    "[SettingActivity.java][VOLUME] "
                            + "music=" + music + "/" + musicMax
                            + " ring=" + ring + "/" + ringMax
                            + " alarm=" + alarm + "/" + alarmMax);

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][VOLUME][ERROR] "
                            + e.toString());
        }
    }

    private void changeVolume(int streamType, boolean increase) {

        try {

            if (audioManager == null) {

                audioManager = (AudioManager)
                        getSystemService(Context.AUDIO_SERVICE);
            }

            if (audioManager == null) {

                LogManager.log(this,
                        "[SettingActivity.java][VOLUME][ERROR] AudioManager为空");

                return;
            }

            int before = audioManager.getStreamVolume(streamType);

            audioManager.adjustStreamVolume(
                    streamType,
                    increase
                            ? AudioManager.ADJUST_RAISE
                            : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
            );

            int after = audioManager.getStreamVolume(streamType);

            LogManager.log(this,
                    "[SettingActivity.java][VOLUME] "
                            + "stream=" + streamType
                            + " direction=" + (increase ? "UP" : "DOWN")
                            + " before=" + before
                            + " after=" + after);
SettingLogManager.local(
        this,
        "音量调整",
        "SUCCESS",
        "音量类型=" + streamType
                + "，调整前=" + before
                + "，调整后=" + after
                    + "，调整前数值，"
                    + "，调整后数值"
);
            refreshVolume();

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][VOLUME][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "音量调节失败",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openSoundSettings() {

        LogManager.log(this,
                "[SettingActivity.java][SOUND] 请求打开声音设置");

        try {

            startActivity(
                    new Intent(Settings.ACTION_SOUND_SETTINGS)
            );

            LogManager.log(this,
                    "[SettingActivity.java][SOUND] 声音设置已打开");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][SOUND][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "无法打开声音设置",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================================
    // 系统设置
    // ==========================================================

    private void openDisplaySettings() {

        LogManager.log(this,
                "[SettingActivity.java][DISPLAY] 请求打开显示设置");

        try {

            startActivity(
                    new Intent(Settings.ACTION_DISPLAY_SETTINGS)
            );

            LogManager.log(this,
                    "[SettingActivity.java][DISPLAY] 已打开显示设置");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][DISPLAY][ERROR] "
                            + e.toString());
        }
    }

    private void openSystemSettings() {

        LogManager.log(this,
                "[SettingActivity.java][SYSTEM] 请求打开系统设置");

        try {

            startActivity(
                    new Intent(Settings.ACTION_SETTINGS)
            );

            LogManager.log(this,
                    "[SettingActivity.java][SYSTEM] 已打开系统设置");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][SYSTEM][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "无法打开系统设置",
                    Toast.LENGTH_SHORT).show();
        }
    }
// ==========================================================
    // 原有权限跳转
    // ==========================================================

    private void openOverlay() {

        LogManager.log(this,
                "[SettingActivity.java][OVERLAY] 请求悬浮窗权限");

        if (Build.VERSION.SDK_INT >= 23) {
            try {

                startActivity(
                        new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())
                        )
                );

                LogManager.log(this,
                        "[SettingActivity.java][OVERLAY] 已打开授权页面");

            } catch (Exception e) {

                LogManager.log(this,
                        "[SettingActivity.java][OVERLAY][ERROR] "
                                + e.toString());

                openAppDetails();
            }
        }
    }

    private void openNotifications() {

        LogManager.log(this,
                "[SettingActivity.java][NOTIFICATION] 请求打开通知设置");

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                Intent i = new Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                );

                i.putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        getPackageName()
                );

                startActivity(i);

            } else {

                openAppDetails();
            }

            LogManager.log(this,
                    "[SettingActivity.java][NOTIFICATION] 通知设置已打开");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][NOTIFICATION][ERROR] "
                            + e.toString());

            openAppDetails();
        }

        // 保留原来的日志
        LogManager.log(this,
                "[MainActivity.this][CRASH] 程序发生崩溃");
    }

    private void openBattery() {

        LogManager.log(this,
                "[SettingActivity.java][BATTERY] 请求电池优化设置");

        if (Build.VERSION.SDK_INT >= 23) {

            try {

                startActivity(
                        new Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())
                        )
                );

                LogManager.log(this,
                        "[SettingActivity.java][BATTERY] 已打开电池授权页面");

            } catch (Exception e) {

                LogManager.log(this,
                        "[SettingActivity.java][BATTERY] 主入口失败 "
                                + e.toString());

                try {

                    startActivity(
                            new Intent(
                                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                            )
                    );

                    LogManager.log(this,
                            "[SettingActivity.java][BATTERY] 备用页面已打开");

                } catch (Exception ex) {

                    LogManager.log(this,
                            "[SettingActivity.java][BATTERY][ERROR] "
                                    + ex.toString());

                    openAppDetails();
                }
            }
        }
    }

    private void openAppDetails() {

        LogManager.log(this,
                "[SettingActivity.java][APP] 请求打开应用详情");

        try {

            startActivity(
                    new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())
                    )
            );

            LogManager.log(this,
                    "[SettingActivity.java][APP] 应用详情已打开");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][APP][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "无法打开应用详情页",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================================
    // 原有测试服务
    // ==========================================================

    private void testNow() {

        LogManager.log(this,
                "[SettingActivity.java][SERVICE] 请求启动测试服务");

        try {

            Intent i = new Intent(
                    this,
                    ScreenWakeService.class
            );

            i.setAction(ScreenWakeService.ACTION_START);

            if (Build.VERSION.SDK_INT >= 26) {

                startForegroundService(i);

            } else {

                startService(i);
            }

            LogManager.log(this,
                    "[SettingActivity.java][SERVICE] 已提交服务启动请求");

        } catch (Exception e) {

            LogManager.log(this,
                    "[SettingActivity.java][SERVICE][ERROR] "
                            + e.toString());

            Toast.makeText(this,
                    "启动失败：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        LogManager.log(this,
                "[SettingActivity.java][INFO] 跳启动权限");
    }

    // ==========================================================
    // DP
    // ==========================================================

    private int dp(int value) {

        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
