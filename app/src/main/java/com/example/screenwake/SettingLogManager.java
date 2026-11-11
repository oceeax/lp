package com.example.screenwake;

import android.content.Context;

/**
 * 系统设置专用日志管理器
 * 本地和远程设置操作分别标记
 */
public final class SettingLogManager {

    private SettingLogManager() {}

    /**
     * 远程设置操作日志
     */
    public static void remote(
            Context context,
            String operation,
            String status,
            String detail
    ) {
        write(context, "REMOTE", operation, status, detail);
    }

    /**
     * 本地设置操作日志
     */
    public static void local(
            Context context,
            String operation,
            String status,
            String detail
    ) {
        write(context, "LOCAL", operation, status, detail);
    }

    /**
     * 统一写入设置日志
     */
    private static void write(
            Context context,
            String source,
            String operation,
            String status,
            String detail
    ) {
        if (context == null) return;

        String message =
                "[" + source + "]"
                + "[" + safe(operation) + "]"
                + "[" + safe(status) + "] "
                + safe(detail);

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
        return value.replace("\n", " ")
                    .replace("\r", " ");
    }
}
