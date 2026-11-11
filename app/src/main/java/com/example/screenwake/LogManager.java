package com.example.screenwake;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LogManager {
    // ========= 日志分类目录 =========
    public static final String SUB_APP = "app/";           // 常规运行
    public static final String SUB_UPLOAD = "upload/";     // 数据上传
    public static final String SUB_COMMAND = "command/";   // 远程指令
    public static final String SUB_CAMERA = "camera/";     // 相机拍照/录像
    public static final String SUB_NETWORK = "network/";   // 网络请求/WebSocket
    public static final String SUB_TIMER = "timer/";       // 定时器/闹钟
    public static final String SUB_CRASH = "crash/";       // 崩溃闪退日志
    public static final String SUB_SETTING = "setting/"; // 系统设置操作
    public static final String SUB_SETTING = "setting/";
    
    // ========= 日志级别标签 =========
    public static final String LV_DEBUG = "DEBUG";
    public static final String LV_INFO  = "INFO";
    public static final String LV_WARN  = "WARN";
    public static final String LV_ERROR = "ERROR";

    private static final String WHITELIST_SUB = "whitelist/";
    private static final String WHITELIST_FILENAME = "action_whitelist.txt";
    private static final String SERVER_ALLOW_FILENAME = "server_allow.txt";
    private static final String SERVER_TRUST_FILENAME = "server_trust.txt";

    private static final int MAX_SINGLE_FILE_LINES = 2000;

    private LogManager() {}

    /**
     * 获取日志根目录
     */
    private static File getRootDir(Context context) {
        File publicRoot = new File(Environment.getExternalStorageDirectory(), "ScreenWake/log");
        if (publicRoot.exists() || publicRoot.mkdirs()) {
            return publicRoot;
        }
        File base = context.getExternalFilesDir(null);
        if (base == null) return null;
        File privateRoot = new File(base, "ScreenWake/log");
        if (!privateRoot.exists()) privateRoot.mkdirs();
        return privateRoot;
    }

    private static File getSubDir(Context context, String subDir) {
        File root = getRootDir(context);
        if(root == null) return null;
        File sub = new File(root, subDir);
        if (!sub.exists()) sub.mkdirs();
        return sub;
    }

    private static String getTodayFileName() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".log";
    }

    private static File getLogFile(Context context, String subDir) {
        File sub = getSubDir(context, subDir);
        if(sub == null) return null;
        return new File(sub, getTodayFileName());
    }

    private static File getWhitelistFile(Context context, String fileName) {
        File sub = getSubDir(context, WHITELIST_SUB);
        if(sub == null) return null;
        return new File(sub, fileName);
    }

    // ========= 核心日志写入 =========

    public static void d(Context context, String subDir, String message) { log(context, subDir, LV_DEBUG, message, null); }
    public static void i(Context context, String subDir, String message) { log(context, subDir, LV_INFO, message, null); }
    public static void w(Context context, String subDir, String message) { log(context, subDir, LV_WARN, message, null); }
    public static void e(Context context, String subDir, String message, Throwable t) { log(context, subDir, LV_ERROR, message, t); }

    public static synchronized void log(Context context, String subDir, String level, String message, Throwable throwable) {
        if (context == null || message == null) return;
        try {
            File file = getLogFile(context, subDir);
            if (file == null) return;

            String threadName = Thread.currentThread().getName();
            long now = System.currentTimeMillis();
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(now));

            // 拼接格式：[时间] [级别] [线程] | 内容
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("[").append(time).append("] ")
                      .append("[").append(level).append("] ")
                      .append("[").append(threadName).append("] | ")
                      .append(message.replace("\n", " "));

            // 如果包含异常堆栈，追加打印
            if (throwable != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                logBuilder.append("\n").append(sw.toString());
            }

            try (OutputStream out = new FileOutputStream(file, true);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                writer.write(logBuilder.toString());
                writer.newLine();
            }

            // 裁剪超长日志
            if (file.length() > 500 * 1024) {
                List<String> lines = readLines(context, subDir);
                if (lines.size() > MAX_SINGLE_FILE_LINES) {
                    List<String> newLines = lines.subList(lines.size() - MAX_SINGLE_FILE_LINES, lines.size());
                    writeAll(context, subDir, newLines);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ScreenWake", "写日志失败", e);
        }
    }

    // 兼容旧调用
    public static void log(Context context, String message) { i(context, SUB_APP, message); }
    public static void log(Context context, String subDir, String message) { i(context, subDir, message); }

    // ========= 全局崩溃捕获 =========
    public static void initCrashHandler(final Context context) {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 1. 收集设备信息
            String deviceInfo = "设备: " + Build.MANUFACTURER + " " + Build.MODEL + " | 系统: Android " + Build.VERSION.RELEASE;
            // 2. 写入崩溃日志
            e(context, SUB_CRASH, "【致命崩溃】" + deviceInfo + " | 异常: " + throwable.getMessage(), throwable);
            
            // 3. 交还给系统处理（闪退）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    // ========= 日志读取与清空 =========
    private static List<String> readLines(Context context, String subDir) throws Exception {
        List<String> lines = new ArrayList<>();
        File file = getLogFile(context, subDir);
        if (file == null || !file.exists()) return lines;
        try (InputStream input = new FileInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private static void writeAll(Context context, String subDir, List<String> lines) throws Exception {
        File file = getLogFile(context, subDir);
        if(file == null) return;
        try (OutputStream out = new FileOutputStream(file, false);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public static synchronized String read(Context context, String subDir) {
        try {
            List<String> all = readLines(context, subDir);
            StringBuilder sb = new StringBuilder();
            for(String s : all) sb.append(s).append('\n');
            return sb.toString();
        }catch (Exception e){
            return "读取异常："+e.getClass().getSimpleName()+" | "+e.getMessage();
        }
    }

    public static synchronized void clearLog(Context context, String subDir) {
        try {
            File file = getLogFile(context, subDir);
            if (file != null && file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            android.util.Log.e("LogManager", "清空日志失败", e);
        }
    }

    // ========= 白名单相关逻辑（保留原样） =========
    public static Set<String> readActionWhitelist(Context context) {
        Set<String> set = new HashSet<>();
        if (context == null) return set;
        File f = getWhitelistFile(context, WHITELIST_FILENAME);
        if(f == null || !f.exists()) return set;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trim = line.trim();
                if (!trim.isEmpty() && !trim.startsWith("#")) set.add(trim);
            }
        } catch (Exception ignored) {}
        return set;
    }

    public static Set<String> readServerAllowList(Context context) {
        Set<String> set = new HashSet<>();
        if (context == null) return set;
        File f = getWhitelistFile(context, SERVER_ALLOW_FILENAME);
        if(f == null || !f.exists()) return set;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trim = line.trim();
                if (!trim.isEmpty() && !trim.startsWith("#")) set.add(trim);
            }
        } catch (Exception ignored) {}
        return set;
    }

    public static String getTrustedServerBindId(Context context) {
        if (context == null) return null;
        File f = getWhitelistFile(context, SERVER_TRUST_FILENAME);
        if(f == null || !f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = br.readLine();
            if (line != null && !line.trim().isEmpty()) return line.trim();
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean setTrustedServerBindId(Context context, String bindId) {
        if (context == null) return false;
        File f = getWhitelistFile(context, SERVER_TRUST_FILENAME);
        if(f == null) return false;
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8))) {
            if (bindId != null && !bindId.trim().isEmpty()) bw.write(bindId.trim());
            else bw.write("");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

