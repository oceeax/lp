package com.example.screenwake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null ||
                !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Context c = context.getApplicationContext();

        // 【日志】记录接收到开机广播
        LogManager.i(c, LogManager.SUB_APP, "接收到系统开机广播 (ACTION_BOOT_COMPLETED)，开始恢复所有任务...");

        // ====================================================
        // 1. 保留原有开机亮屏功能
        // ====================================================
        try {
            Intent serviceIntent = new Intent(c, ScreenWakeService.class);

            if (Build.VERSION.SDK_INT >= 26) {
                // 【日志】记录系统版本判断原因
                LogManager.i(c, LogManager.SUB_APP, "当前系统版本 >= Android 8.0 (API 26)，使用 startForegroundService 启动亮屏服务");
                c.startForegroundService(serviceIntent);
            } else {
                LogManager.i(c, LogManager.SUB_APP, "当前系统版本 < Android 8.0 (API 26)，使用 startService 启动亮屏服务");
                c.startService(serviceIntent);
            }

            LogManager.i(c, LogManager.SUB_APP, "开机亮屏服务启动请求已成功发送");

        } catch (Exception e) {
            // 【日志】记录系统原因导致的服务启动失败（比如被系统限制后台启动）
            LogManager.e(c, LogManager.SUB_CRASH, "开机亮屏服务启动请求发送失败！系统可能限制了后台启动或缺少权限", e);
        }

        // ====================================================
        // 2. 恢复主定时器
        // ====================================================
        try {
            if (ConfigManager.isEnabled(c)
                    && !ConfigManager.isPaused(c)) {
                
                AlarmScheduler.scheduleNext(c);
                // 【日志】记录定时器恢复成功
                LogManager.i(c, LogManager.SUB_TIMER, "开机检测通过：定时器已启用且未暂停。已重新安排下一次定时任务 (scheduleNext)");
                
            } else {
                // 【日志】记录不恢复定时器的具体原因
                String reason = !ConfigManager.isEnabled(c) ? "定时器开关未启用" : "定时器处于暂停状态";
                LogManager.w(c, LogManager.SUB_TIMER, "开机未恢复定时器，原因：" + reason);
            }
        } catch (Exception e) {
            // 【日志】记录定时器调度引发的系统异常
            LogManager.e(c, LogManager.SUB_TIMER, "开机恢复主定时器时发生异常", e);
        }

        // ====================================================
        // 3. 开机就安排一小时保活基线（跟定时器设置无关）
        // ====================================================
        try {
            AlarmScheduler.scheduleBaseline(c);
            // 【日志】记录基线任务
            LogManager.i(c, LogManager.SUB_TIMER, "开机保活基线任务已安排 (scheduleBaseline)，确保系统不被杀");
        } catch (Exception e) {
            // 【日志】记录保活基线安排失败原因
            LogManager.e(c, LogManager.SUB_TIMER, "开机安排保活基线任务失败", e);
        }

        // ====================================================
        // 4. 开机直接把指令轮询拉起来
        // ====================================================
        try {
            CommandPoller.ensureRunning(c);
            // 【日志】记录轮询拉起成功
            LogManager.i(c, LogManager.SUB_COMMAND, "开机后指令轮询服务已成功恢复，准备接收云端指令");
        } catch (Exception e) {
            // 【日志】记录轮询拉起失败原因
            LogManager.e(c, LogManager.SUB_NETWORK, "开机后恢复指令轮询失败，网络状态或后台服务可能受限", e);
        }
        
        // 【日志】全部流程结束
        LogManager.i(c, LogManager.SUB_APP, "开机自启恢复流程全部执行完毕");
    }
}

