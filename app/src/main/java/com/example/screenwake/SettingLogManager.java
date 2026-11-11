package com.example.screenwake;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 系统设置日志管理器
 * 包含：亮度远程/本地控制、音量远程/本地控制
 * 远程日志带 [Y]，本地无标记；时间精确到毫秒，日志全中文
 */
public final class SettingLogManager {

    private SettingLogManager() {}

    // ====================== 日志底层 ======================
    public static void remote(
            Context context,
            String operation,
            String status,
            String detail
    ) {
        write(context, "Y", operation, status, detail);
    }

    public static void local(
            Context context,
            String operation,
            String status,
            String detail
    ) {
        write(context, "", operation, status, detail);
    }

    private static void write(
            Context context,
            String sourceTag,
            String operation,
            String status,
            String detail
    ) {
        if (context == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);
        String timeStr = sdf.format(new Date(System.currentTimeMillis()));

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timeStr).append("]");
        if (!sourceTag.isEmpty()) {
            sb.append("[").append(sourceTag).append("]");
        }
        sb.append("[").append(safe(operation)).append("]");
        sb.append("[").append(safe(status)).append("] ");
        sb.append(safe(detail));

        String message = sb.toString();

        LogManager.log(
                context,
                LogManager.SUB_SETTING,
                LogManager.LV_INFO,
                message,
                null
        );
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace("\n", " ").replace("\r", " ");
    }


    // ====================== 亮度控制 ======================
    /**
     * 远程下发调整亮度（带Y标记）
     * @param brightness 0‑255
     */
    public static void applyRemoteBrightness(Context context, int brightness) {
        remote(context, "亮度调整", "请求", "请求亮度：" + brightness);

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.System.canWrite(context)) {
            remote(context, "亮度调整", "拒绝", "系统拒绝：未获得修改系统设置权限");
            return;
        }

        try {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
            );
            remote(context, "亮度调整", "成功", "亮度修改请求已执行，目标值：" + brightness);
        } catch (Exception e) {
            remote(context, "亮度调整", "失败", "执行异常：" + e.toString());
        }
    }

    /**
     * 本地手动调整亮度（无Y标记）
     */
    public static void applyLocalBrightness(Context context, int brightness) {
        local(context, "亮度调整", "请求", "本地设置亮度：" + brightness);

        if (Build.VERSION.SDK_INT >= 23
                && !Settings.System.canWrite(context)) {
            local(context, "亮度调整", "拒绝", "未获得修改系统设置权限");
            return;
        }

        try {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
            );
            local(context, "亮度调整", "成功", "本地亮度已修改，目标值：" + brightness);
        } catch (Exception e) {
            local(context, "亮度调整", "失败", "执行异常：" + e.toString());
        }
    }


    // ====================== 音量控制 ======================
    /**
     * 远程调整音量
     * @param streamType 音量类型，例如 AudioManager.STREAM_MUSIC
     * @param increase true增大音量，false减小音量
     */
    public static void applyRemoteVolume(Context context, int streamType, boolean increase) {
        remote(context, "音量调整", "请求",
                "音量类型：" + streamType + "，方向：" + (increase ? "增大" : "减小"));

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            remote(context, "音量调整", "失败", "获取音频服务失败");
            return;
        }

        try {
            int before = audioManager.getStreamVolume(streamType);
            audioManager.adjustStreamVolume(
                    streamType,
                    increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
            );
            int after = audioManager.getStreamVolume(streamType);
            remote(context, "音量调整", "成功", "调整前：" + before + "，调整后：" + after);
        } catch (Exception e) {
            remote(context, "音量调整", "失败", "执行异常：" + e.toString());
        }
    }

    /**
     * 本地手动调整音量（无Y标记）
     */
    public static void applyLocalVolume(Context context, int streamType, boolean increase) {
        local(context, "音量调整", "请求",
                "音量类型：" + streamType + "，方向：" + (increase ? "增大" : "减小"));

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            local(context, "音量调整", "失败", "获取音频服务失败");
            return;
        }

        try {
            int before = audioManager.getStreamVolume(streamType);
            audioManager.adjustStreamVolume(
                    streamType,
                    increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
            );
            int after = audioManager.getStreamVolume(streamType);
            local(context, "音量调整", "成功", "调整前：" + before + "，调整后：" + after);
        } catch (Exception e) {
            local(context, "音量调整", "失败", "执行异常：" + e.toString());
        }
    }

}

