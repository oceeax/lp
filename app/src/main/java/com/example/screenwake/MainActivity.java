package com.example.screenwake;
import com.example.screenwake.VoiceReader;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements CommandPoller.ServerBindNotifyCallback {

    private static final int COLOR_BG     = Color.rgb(243, 246, 250);
    private static final int COLOR_WHITE  = Color.rgb(255, 255, 255);
    private static final int COLOR_TEXT   = Color.rgb(34, 34, 34);
    private static final int COLOR_SUB    = Color.rgb(130, 140, 150);
    private static final int COLOR_BLUE   = Color.rgb(45, 134, 255);

    private static final int[] ICON_COLORS = {
            Color.rgb(82, 155, 255),  Color.rgb(72, 207, 173),  Color.rgb(255, 159, 67),
            Color.rgb(255, 107, 129), Color.rgb(159, 122, 234), Color.rgb(84, 190, 255),
            Color.rgb(255, 206, 84),  Color.rgb(50, 205, 205),  Color.rgb(184, 142, 255)
    };

    private TextView globalStatusView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            refreshGlobalStatus();
            ui.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BLUE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        addHeader(root);

        LinearLayout statusWrapper = new LinearLayout(this);
        statusWrapper.setPadding(dp(20), 0, dp(20), dp(16));
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wrapLp.topMargin = -dp(24);
        statusWrapper.setLayoutParams(wrapLp);
        addStatusCard(statusWrapper);
        root.addView(statusWrapper);

        addGridSection(root);
        addFooter(root);

        scroll.addView(root);
        setContentView(scroll);
        ui.post(statusTicker);
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    // ★ 放在最顶部，这样后面只要闪退就能抓到
        LogManager.initCrashHandler(getApplicationContext());
    
    }

    private void addHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(55, 145, 255), Color.rgb(30, 90, 255)});
        bg.setCornerRadii(new float[]{0, 0, 0, 0, dp(32), dp(32), dp(32), dp(32)});
        header.setBackground(bg);
        header.setPadding(dp(24), dp(56), dp(24), dp(64));

        TextView title = new TextView(this);
        title.setText("ScreenWake");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);

        TextView sub = new TextView(this);
        sub.setText("设备管理与自动化中枢");
        sub.setTextColor(Color.parseColor("#D0FFFFFF"));
        sub.setTextSize(15);
        sub.setPadding(0, dp(8), 0, 0);
        header.addView(sub);
        root.addView(header);
    }

    private void addStatusCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(COLOR_WHITE, dp(16)));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setElevation(dp(6));

        TextView tv = new TextView(this);
        tv.setText("📊 系统运行状态");
        tv.setTextColor(COLOR_TEXT);
        tv.setTextSize(16);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tv);

        globalStatusView = new TextView(this);
        globalStatusView.setTextColor(COLOR_SUB);
        globalStatusView.setTextSize(14);
        globalStatusView.setPadding(0, dp(12), 0, 0);
        globalStatusView.setLineSpacing(dp(4), 1.2f);
        card.addView(globalStatusView);

        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addGridSection(LinearLayout root) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(roundedBg(COLOR_WHITE, dp(16)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(20), 0, dp(20), dp(20));
        section.setLayoutParams(lp);
        section.setPadding(dp(12), dp(24), dp(12), dp(24));
        section.setElevation(dp(2));

        // 纯页面跳转
        addGridRow(section,
                new GridItem("文件接收", "💬", 0, MultiWsActivity.class),
                new GridItem("亮屏测试", "📱", 1, BootReceiver.class),
                new GridItem("内置浏览器", "🌐", 2, BrowserActivity.class)
        );
        addGridRow(section,
                new GridItem("相机功能", "⚡", 3, CameraActivity.class),
                new GridItem("语音播报", "🍦", 4, VoiceReader.class),
                new GridItem("定时任务", "⏰", 5, AlarmScheduler.class)
        );
        addGridRow(section,
                new GridItem("日志查看", "📝", 6, LogManagerActivity.class),
                new GridItem("数据上传", "☁️", 7, DeviceUploader.class),
                new GridItem("设置权限", "⚙️", 8, SettingActivity.class)
        );
         
        root.addView(section);
    }

    private void addGridRow(LinearLayout parent, GridItem... items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(12));

        for (GridItem item : items) {
            LinearLayout itemView = new LinearLayout(this);
            itemView.setOrientation(LinearLayout.VERTICAL);
            itemView.setGravity(Gravity.CENTER);
            itemView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView icon = new TextView(this);
            icon.setText(item.emoji);
            icon.setTextSize(26);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(dp(16));
            shape.setColor(ICON_COLORS[item.colorIndex % ICON_COLORS.length]);
            icon.setBackground(shape);
            icon.setElevation(dp(2));
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
            itemView.addView(icon);

            TextView text = new TextView(this);
            text.setText(item.title);
            text.setTextColor(COLOR_TEXT);
            text.setTextSize(13);
            text.setTypeface(null, android.graphics.Typeface.BOLD);
            text.setPadding(0, dp(10), 0, 0);
            itemView.addView(text);

            itemView.setOnClickListener(v -> {
                if (item.targetActivity == ScreenWakeService.class) {
                    Intent intent = new Intent(this, ScreenWakeService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
                    else startService(intent);
                    Toast.makeText(this, "唤醒服务已启动", Toast.LENGTH_SHORT).show();
                } else if (item.targetActivity != null) {
                    try {
                        startActivity(new Intent(this, item.targetActivity));
                    } catch (Exception e) {
                        Toast.makeText(this, "未在 Manifest 注册！", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            row.addView(itemView);
        }
        parent.addView(row);
    }

    private void addFooter(LinearLayout root) {
        TextView footer = new TextView(this);
        footer.setText("ScreenWake v1.0\n设备状态稳定运行中");
        footer.setTextColor(COLOR_SUB);
        footer.setTextSize(12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(16), 0, dp(40));
        root.addView(footer);
    }

    private static class GridItem {
        String title; String emoji; int colorIndex; Class<?> targetActivity;
        GridItem(String title, String emoji, int colorIndex, Class<?> targetActivity) {
            this.title = title; this.emoji = emoji; this.colorIndex = colorIndex; this.targetActivity = targetActivity;
        }
    }

    @Override protected void onResume() { super.onResume(); refreshGlobalStatus(); CommandPoller.setServerBindNotifyCallback(this); }
    @Override protected void onPause() { super.onPause(); CommandPoller.setServerBindNotifyCallback(null); }
    @Override protected void onDestroy() { super.onDestroy(); ui.removeCallbacks(statusTicker); }

    private void refreshGlobalStatus() {
        if (globalStatusView == null) return;
        String webStatusStr = WebMonitor.isStarted() ? "运行中" : "已停止";
        String timerStr = ConfigManager.isEnabled(this) ? "已开启" : "已关闭";
        String trustStr = (LogManager.getTrustedServerBindId(this) != null) ? "已绑定" : "未绑定";
        globalStatusView.setText("检测状态：" + webStatusStr + "  |  定时任务：" + timerStr + "\n受信服务器：" + trustStr);
    }

    @Override
    public void needShowBindDialog(String bindId, boolean isChanged) {
        new AlertDialog.Builder(this)
                .setTitle(isChanged ? "服务器标识码已变更" : "收到新服务器绑定请求")
                .setMessage((isChanged ? "是否信任新服务器？\nID: " : "是否信任此服务器？\nID: ") + bindId)
                .setPositiveButton("接收", (d, w) -> {
                    if (LogManager.setTrustedServerBindId(this, bindId)) {
                        CommandPoller.start(this);
                        Toast.makeText(this, "已信任", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("拒绝", null)
                .setCancelable(false)
                .show();
    }

    private GradientDrawable roundedBg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}


