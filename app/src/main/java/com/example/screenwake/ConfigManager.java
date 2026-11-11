package com.example.screenwake;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {
    private static final String SP_NAME = "app_config";

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ================== 你原有的部分 ==================
    private static final String KEY_TIMER_ENABLE = "timer_enable";
    private static final String KEY_TIMER_INTERVAL_MIN = "timer_interval_min";
    private static final String KEY_NEXT_TRIGGER = "next_trigger";

    // 定时器开关
    public static void setEnabled(Context ctx, boolean val) {
        sp(ctx).edit().putBoolean(KEY_TIMER_ENABLE, val).apply();
    }

    public static boolean isEnabled(Context ctx) {
        return sp(ctx).getBoolean(KEY_TIMER_ENABLE, false);
    }

    // 定时间隔分钟
    public static void setIntervalMinutes(Context ctx, long minute) {
        sp(ctx).edit().putLong(KEY_TIMER_INTERVAL_MIN, minute).apply();
    }

    public static long getIntervalMinutes(Context ctx) {
        return sp(ctx).getLong(KEY_TIMER_INTERVAL_MIN, 30L); // 默认30分钟
    }

    // 记录下次触发时间戳
    public static void setNextTrigger(Context ctx, long time) {
        sp(ctx).edit().putLong(KEY_NEXT_TRIGGER, time).apply();
    }

    public static long getNextTrigger(Context ctx) {
        return sp(ctx).getLong(KEY_NEXT_TRIGGER, 0L);
    }


    // ================== 补充缺失的部分（解决编译报错） ==================

    // 1. 睡眠时长（DeviceInfo 报错缺失）
    private static final String KEY_SLEEP_MINUTES = "sleep_minutes";

    public static void setSleepMinutes(Context ctx, int minutes) {
        sp(ctx).edit().putInt(KEY_SLEEP_MINUTES, minutes).apply();
    }

    public static int getSleepMinutes(Context ctx) {
        // 默认睡眠时间，比如 5 分钟，看你的业务逻辑需求
        return sp(ctx).getInt(KEY_SLEEP_MINUTES, 5);
    }

    // 2. 设备 ID（DeviceInfo 报错缺失）
    private static final String KEY_DEVICE_ID = "device_id";

    public static void setDeviceId(Context ctx, String deviceId) {
        sp(ctx).edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }

    public static String getDeviceId(Context ctx) {
        // 如果代码里其他地方没有生成并保存过，这里默认返回空字符串或 "unknown"
        return sp(ctx).getString(KEY_DEVICE_ID, "unknown_device_id");
    }

    // 3. 暂停状态（BootReceiver, TaskReceiver 报错缺失）
    private static final String KEY_IS_PAUSED = "is_paused";

    public static void setPaused(Context ctx, boolean isPaused) {
        sp(ctx).edit().putBoolean(KEY_IS_PAUSED, isPaused).apply();
    }

    public static boolean isPaused(Context ctx) {
        return sp(ctx).getBoolean(KEY_IS_PAUSED, false);
    }

    // 4. 最后一次触发时间（TaskReceiver 报错缺失）
    private static final String KEY_LAST_TRIGGER = "last_trigger";

    public static void setLastTrigger(Context ctx, long time) {
        sp(ctx).edit().putLong(KEY_LAST_TRIGGER, time).apply();
    }

    public static long getLastTrigger(Context ctx) {
        return sp(ctx).getLong(KEY_LAST_TRIGGER, 0L);
    }

    // 5. 飞书 Webhook（DeviceUploader 报错缺失）
    private static final String KEY_FEISHU_WEBHOOK = "feishu_webhook";

    public static void setFeishuWebhook(Context ctx, String url) {
        sp(ctx).edit().putString(KEY_FEISHU_WEBHOOK, url).apply();
    }

    public static String getFeishuWebhook(Context ctx) {
        // 默认空字符串
        return sp(ctx).getString(KEY_FEISHU_WEBHOOK, "");
    }
}


