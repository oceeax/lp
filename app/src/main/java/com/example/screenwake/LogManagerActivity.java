package com.example.screenwake;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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
    private static final int COLOR_ERR    = Color.rgb(255, 100, 100); // 红色
    private static final int COLOR_WARN   = Color.rgb(255, 200, 100); // 黄色

    private TextView logTextView;
    private ScrollView logScroll;
    private String currentSubDir = LogManager.SUB_APP; // 默认显示运行日志

    private Button btnApp, btnUpload, btnCommand, btnCamera, btnNetwork, btnCrash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        // 1. 顶部标题与清空按钮
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("📝 系统运行日志");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(22);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(title, titleParams);

        Button btnClear = createActionButton("🗑️ 清空日志");
        btnClear.setOnClickListener(v -> {
            LogManager.clearLog(this, currentSubDir);
            toast("已清空当前分类日志");
            loadLogs();
        });
        titleRow.addView(btnClear, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));
        
        root.addView(titleRow);

        // 2. 分类切换按钮区 (使用 HorizontalScrollView 防止按钮过多挤压)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.setPadding(0, dp(12), 0, dp(12));

        LinearLayout tabLayout = new LinearLayout(this);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);

        btnApp     = createTabButton("运行");
        btnUpload  = createTabButton("上传");
        btnCommand = createTabButton("指令");
        btnCamera  = createTabButton("相机");
        btnNetwork = createTabButton("网络");
        btnCrash   = createTabButton("崩溃");

        addTabToLayout(tabLayout, btnApp);
        addTabToLayout(tabLayout, btnUpload);
        addTabToLayout(tabLayout, btnCommand);
        addTabToLayout(tabLayout, btnCamera);
        addTabToLayout(tabLayout, btnNetwork);
        addTabToLayout(tabLayout, btnCrash);

        hScroll.addView(tabLayout);
        root.addView(hScroll);

        // 3. 操作栏（刷新 / 打开文件夹）
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setPadding(0, 0, 0, dp(12));

        Button btnRefresh = createActionButton("🔄 刷新日志");
        btnRefresh.setOnClickListener(v -> loadLogs());
        
        Button btnOpenFolder = createActionButton("📁 打开存储目录");
        btnOpenFolder.setOnClickListener(v -> openLogFolder());

        LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        actParams.setMargins(dp(4), 0, dp(4), 0);
        actionLayout.addView(btnRefresh, actParams);
        actionLayout.addView(btnOpenFolder, actParams);
        root.addView(actionLayout);

        // 4. 日志显示区域
        logScroll = new ScrollView(this);
        logScroll.setBackground(roundedBg(COLOR_CARD, dp(10)));
        logScroll.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        logTextView = new TextView(this);
        logTextView.setTextColor(COLOR_TEXT);
        logTextView.setTextSize(12);
        logTextView.setLineSpacing(0, 1.2f);
        logTextView.setTextIsSelectable(true); // 允许长按复制
        logScroll.addView(logTextView);
        
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        logScroll.setLayoutParams(scrollParams);
        root.addView(logScroll);

        setContentView(root);

        // 5. 初始化点击事件
        btnApp.setOnClickListener(v -> switchTab(LogManager.SUB_APP, btnApp));
        btnUpload.setOnClickListener(v -> switchTab(LogManager.SUB_UPLOAD, btnUpload));
        btnCommand.setOnClickListener(v -> switchTab(LogManager.SUB_COMMAND, btnCommand));
        btnCamera.setOnClickListener(v -> switchTab(LogManager.SUB_CAMERA, btnCamera));
        btnNetwork.setOnClickListener(v -> switchTab(LogManager.SUB_NETWORK, btnNetwork));
        btnCrash.setOnClickListener(v -> switchTab(LogManager.SUB_CRASH, btnCrash));

        switchTab(LogManager.SUB_APP, btnApp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLogs();
    }

    // ================================================================
    // 业务逻辑
    // ================================================================

    private void switchTab(String subDir, Button selectedBtn) {
        currentSubDir = subDir;
        
        resetTabButton(btnApp);
        resetTabButton(btnUpload);
        resetTabButton(btnCommand);
        resetTabButton(btnCamera);
        resetTabButton(btnNetwork);
        resetTabButton(btnCrash);

        selectedBtn.setTextColor(Color.WHITE);
        selectedBtn.setBackground(roundedBg(COLOR_ACCENT, dp(8)));

        loadLogs();
    }

    private void loadLogs() {
        String logs = LogManager.read(this, currentSubDir);
        
        if (logs == null || logs.trim().isEmpty()) {
            logTextView.setText("（当前分类暂时没有日志记录）");
            logTextView.setTextColor(COLOR_SUB);
        } else {
            // ★ 使用 Spannable 进行关键字高亮
            SpannableString spannable = new SpannableString(logs);
            highlightKeyWords(spannable, logs, "[ERROR]", COLOR_ERR, true);
            highlightKeyWords(spannable, logs, "[WARN]", COLOR_WARN, true);
            highlightKeyWords(spannable, logs, "[DEBUG]", COLOR_SUB, false);
            
            logTextView.setText(spannable);
            logTextView.setTextColor(COLOR_TEXT);

            // 自动滚动到底部
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void highlightKeyWords(SpannableString spannable, String text, String keyword, int color, boolean bold) {
        int index = text.indexOf(keyword);
        while (index >= 0) {
            int end = index + keyword.length();
            spannable.setSpan(new ForegroundColorSpan(color), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bold) {
                spannable.setSpan(new StyleSpan(Typeface.BOLD), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            index = text.indexOf(keyword, end);
        }
    }

    private void openLogFolder() {
        try {
            File logDir = new File(Environment.getExternalStorageDirectory(), "ScreenWake/log");
            if (!logDir.exists()) logDir.mkdirs();
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(logDir), "*/*");
            startActivity(intent);
        } catch (Exception e) {
            toast("请手动前往：内部存储/ScreenWake/log");
        }
    }

    // ================================================================
    // UI 辅助
    // ================================================================

    private Button createTabButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(COLOR_SUB);
        b.setBackground(roundedBg(COLOR_CARD, dp(8)));
        return b;
    }

    private void addTabToLayout(LinearLayout layout, Button btn) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(dp(4), 0, dp(4), 0);
        layout.addView(btn, params);
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

