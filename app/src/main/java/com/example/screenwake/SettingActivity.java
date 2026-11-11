package com.example.screenwake;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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
    private Button btnCamera;

    private LinearLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        // ========== 相机权限控件 ==========
        tvCameraStatus = findViewById(R.id.tv_camera_status);
        btnCamera = findViewById(R.id.btn_camera);
        refreshCameraStatus();
        btnCamera.setOnClickListener(v -> onCameraButtonClick());

        // ========== 动态构建悬浮窗/电池/通知等 UI ==========
        buildPage();

        // ========== Android 13+ 主动申请通知权限 ==========
        requestNotificationPermissionIfNeeded();

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCameraStatus();
        if (status != null) {
            refreshStatus();
        }
    }

    // ==========================================================
    // 相机权限
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            tvCameraStatus.setText("✅相机权限：已授权");
        } else {
            tvCameraStatus.setText("❌相机权限：未授权");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA) {
            refreshCameraStatus();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅相机授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌相机权限被拒绝", Toast.LENGTH_SHORT).show();
                // 用户勾选“不再询问”后，引导到应用详情页手动开启
                boolean noAsk = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
                if (noAsk) {
                    openAppDetails();
                }
            }
        } else if (requestCode == REQ_NOTIFICATION) {
            refreshStatus();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    // ==========================================================
    // 动态构建页面（悬浮窗 / 电池 / 通知 / 测试）
    // ==========================================================
    private void buildPage() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("开机亮屏 5 分钟");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("请逐项设置权限。完成后先手动测试，再重启手机验证。\n注意：不会绕过锁屏密码。");
        intro.setTextSize(15);
        intro.setPadding(0, dp(16), 0, dp(12));
        root.addView(intro);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);

        addButton("1. 开启悬浮窗权限", this::openOverlay);
        addButton("2. 开启通知权限", this::openNotifications);
        addButton("3. 允许后台运行 / 忽略电池优化", this::openBattery);
        addButton("4. 打开应用详情（自启动等设置）", this::openAppDetails);
        addButton("5. 立即测试：显示图案 5 分钟", this::testNow);

        TextView note = new TextView(this);
        note.setText("OPPO/ColorOS：另外在系统设置或手机管家中搜索并开启“自启动”“允许后台活动”“后台弹出界面”。这些是厂商设置，菜单名称因版本而异，应用无法替你自动开启。");
        note.setTextSize(13);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        attachToPage(root);
    }

    /**
     * 把动态构建的控件添加到 XML 页面下方：
     * 1. 根布局是垂直 LinearLayout —— 直接添加
     * 2. 根布局是 ScrollView 且其唯一子布局是垂直 LinearLayout —— 添加到其子布局
     * 3. 其它情况 —— 兜底添加到 android.R.id.content
     */
    private void attachToPage(LinearLayout dynamicRoot) {
        ViewGroup content = findViewById(android.R.id.content);
        ViewGroup target = null;

        View first = content.getChildAt(0);
        if (first instanceof LinearLayout
                && ((LinearLayout) first).getOrientation() == LinearLayout.VERTICAL) {
            target = (LinearLayout) first;
        } else if (first instanceof ScrollView
                && ((ScrollView) first).getChildCount() == 1
                && ((ScrollView) first).getChildAt(0) instanceof LinearLayout) {
            LinearLayout inner = (LinearLayout) ((ScrollView) first).getChildAt(0);
            if (inner.getOrientation() == LinearLayout.VERTICAL) {
                target = inner;
            }
        }

        if (target == null) {
            target = content;
        }

        target.addView(dynamicRoot,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addButton(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        root.addView(b,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ==========================================================
    // 权限状态刷新
    // ==========================================================
    private void refreshStatus() {
        if (status == null) return;

        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);

        boolean battery = false;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        String notice;
        if (Build.VERSION.SDK_INT >= 33) {
            notice = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED ? "已允许" : "未允许";
        } else {
            notice = "系统版本无需运行时通知授权";
        }

        status.setText("权限状态：\n悬浮窗：" + (overlay ? "已允许" : "未允许")
                + "\n通知：" + notice
                + "\n电池优化：" + (battery ? "已忽略" : "仍受限制/未确认")
                + "\n自启动、后台弹出：请检查 ColorOS 设置");
    }

    // ==========================================================
    // 各系统设置跳转
    // ==========================================================
    private void openOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
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
    }

    private void openBattery() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ex) {
                    openAppDetails();
                }
            }
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开应用详情页", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================================
    // 立即测试
    // ==========================================================
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
    }

    // ==========================================================
    // 工具方法
    // ==========================================================
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}




