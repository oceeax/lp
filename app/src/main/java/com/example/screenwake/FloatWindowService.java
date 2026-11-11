package com.example.screenwake;
import android.content.Intent;
import android.os.IBinder;
import android.app.Service;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class FloatWindowService extends Service {
    private WindowManager wm;
    private View floatView;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        floatView = LayoutInflater.from(this).inflate(R.layout.float_panel, null);
        wm.addView(floatView, params);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
