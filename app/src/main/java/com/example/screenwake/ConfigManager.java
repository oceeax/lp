package com.example.screenwake;

import android.content.Context;
import android.content.SharedPreferences;

public class ConfigManager {
    private static final String SP_NAME = "app_config";
    private static SharedPreferences sp(Context ctx){
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    private static final String KEY_TIMER_ENABLE = "timer_enable";
    private static final String KEY_TIMER_INTERVAL_MIN = "timer_interval_min";
    private static final String KEY_NEXT_TRIGGER = "next_trigger";

    // 定时器开关
    public static void setEnabled(Context ctx,boolean val){
        sp(ctx).edit().putBoolean(KEY_TIMER_ENABLE,val).apply();
    }
    public static boolean isEnabled(Context ctx){
        return sp(ctx).getBoolean(KEY_TIMER_ENABLE,false);
    }

    // 定时间隔分钟
    public static void setIntervalMinutes(Context ctx,long minute){
        sp(ctx).edit().putLong(KEY_TIMER_INTERVAL_MIN, minute).apply();
    }
    public static long getIntervalMinutes(Context ctx){
        return sp(ctx).getLong(KEY_TIMER_INTERVAL_MIN,30L); // 默认30分钟
    }

    // 记录下次触发时间戳
    public static void setNextTrigger(Context ctx,long time){
        sp(ctx).edit().putLong(KEY_NEXT_TRIGGER,time).apply();
    }
    public static long getNextTrigger(Context ctx){
        return sp(ctx).getLong(KEY_NEXT_TRIGGER,0L);
    }
}
