package com.example.screenwake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class LogManagerActivity extends Activity {

    // -------- 颜色配置 --------
    private static final int COLOR_BG     = Color.rgb(20, 27, 36);
    private static final int COLOR_CARD   = Color.rgb(48, 59, 79);
    private static final int COLOR_TEXT   = Color.rgb(220, 227, 234);
    private static final int COLOR_SUB    = Color.rgb(150, 165, 180);
    private static final int COLOR_ACCENT = Color.rgb(100, 180, 255);

    private TextView logTextView;
    private String currentSubDir = LogManager.SUB_APP; // 默认显示运行日志

    private Button btnApp, btnUpload, btnCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        // 1. 顶部标题
        TextView title = new TextView(this);
        title.setText("📝 系统运行日志");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // 2. 分类切换按钮区
        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setPadding(0, 0, 0, dp(12));

        btnApp = createTabButton("运行日志");
        btnUpload = createTabButton("上传日志");
        btnCommand = createTabButton("指令日志");

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        tabParams.setMargins(dp(4), 0, dp(4), 0);

        tabLayout.addView(btnApp, tabParams);
        tabLayout.addView(btnUpload, tabParams);
        tabLayout.addView(btnCommand, tabParams);
        root.addView(tabLayout);

        // 3. 操作栏（刷新 / 打开文件夹）
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setPadding(0, 0, 0, dp(12));

        Button btnRefresh = createActionButton("🔄 刷新日志");
        btnRefresh.setOnClickListener(v -> loadLogs());
        
        Button btnOpenFolder = createActionButton("📁 在文件中查看");
        btnOpenFolder.setOnClickListener(v -> openLogFolder());

        actionLayout.addView(btnRefresh, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actionLayout.addView(btnOpenFolder, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(actionLayout);

        // 4. 日志显示区域 (用 ScrollView 包裹)
        ScrollView logScroll = new ScrollView(this);
        logScroll.setBackground(roundedBg(COLOR_CARD, dp(10)));
        logScroll.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        logTextView = new TextView(this);
        logTextView.setTextColor(COLOR_TEXT);
        logTextView.setTextSize(12);
        logTextView.setLineSpacing(0, 1.2f);
        logTextView.setTextIsSelectable(true); // 允许长按复制日志
        logScroll.addView(logTextView);
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        logScroll.setLayoutParams(scrollParams);
        root.addView(logScroll);

        setContentView(root);

        // 初始化点击事件
        btnApp.setOnClickListener(v -> switchTab(LogManager.SUB_APP, btnApp));
        btnUpload.setOnClickListener(v -> switchTab(LogManager.SUB_UPLOAD, btnUpload));
        btnCommand.setOnClickListener(v -> switchTab(LogManager.SUB_COMMAND, btnCommand));

        // 初始化默认选中
        switchTab(LogManager.SUB_APP, btnApp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面时刷新日志
        loadLogs();
    }

    // ================================================================
    // 业务逻辑
    // ================================================================

    private void switchTab(String subDir, Button selectedBtn) {
        currentSubDir = subDir;
        
        // 重置所有按钮样式
        resetTabButton(btnApp);
        resetTabButton(btnUpload);
        resetTabButton(btnCommand);

        // 高亮当前按钮
        selectedBtn.setTextColor(Color.WHITE);
        selectedBtn.setBackground(roundedBg(COLOR_ACCENT, dp(8)));

        loadLogs();
    }

    private void loadLogs() {
        // 调用你刚才写的 LogManager 读取日志
        String logs = LogManager.read(this, currentSubDir);
        
        if (logs == null || logs.trim().isEmpty()) {
            logTextView.setText("（当前分类暂时没有日志记录）");
            logTextView.setTextColor(COLOR_SUB);
        } else {
            logTextView.setText(logs);
            logTextView.setTextColor(COLOR_TEXT);
        }
    }

    private void openLogFolder() {
        try {
            // 定位到 /storage/emulated/0/ScreenWake/log/
            File logDir = new File(Environment.getExternalStorageDirectory(), "ScreenWake/log");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 唤起系统文件管理器
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(logDir), "*/*");
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开文件管理器，请手动前往屏幕存储/ScreenWake/log");
        }
    }

    // ================================================================
    // UI 辅助
    // ================================================================

    private Button createTabButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(COLOR_SUB);
        b.setBackground(roundedBg(COLOR_CARD, dp(8)));
        return b;
    }

    private void resetTabButton(Button btn) {
        btn.setTextColor(COLOR_SUB);
        btn.setBackground(roundedBg(COLOR_CARD, dp(8)));
    }

    private Button createActionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(COLOR_TEXT);
        b.setBackground(roundedBg(COLOR_CARD, dp(8)));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private GradientDrawable roundedBg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

