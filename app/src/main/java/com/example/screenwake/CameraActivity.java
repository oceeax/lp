package com.example.screenwake;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 完整独立的相机页面：
 * - 实时预览
 * - 拍照保存到相册（API 29+ 走 MediaStore，API < 29 保存到 Pictures/ScreenWake 并扫描）
 * - 前后摄像头切换
 * - 闪光灯开关（前置一般无闪光灯，自动禁用）
 * - 权限申请、生命周期释放
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQ_CAMERA_PERMISSION = 2001;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;

    private boolean isFrontCamera = false;
    private boolean isFlashOn = false;

    private Button btnCapture;
    private Button btnSwitch;
    private Button btnFlash;

    private ExecutorService cameraExecutor;

    // ==========================================================
    // 生命周期
    // ==========================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();

        buildUi();

        if (hasAllPermissions()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, neededPermissions(), REQ_CAMERA_PERMISSION);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    // ==========================================================
    // 动态构建 UI
    // ==========================================================
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 预览视图（铺满全屏）
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部控制栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        int pad = dp(12);
        bar.setPadding(pad, pad, pad, pad);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        barLp.bottomMargin = dp(24);

        btnFlash = makeButton("闪光灯");
        btnFlash.setOnClickListener(v -> toggleFlash());

        btnCapture = makeButton("拍照");
        btnCapture.setOnClickListener(v -> takePhoto());

        btnSwitch = makeButton("切换");
        btnSwitch.setOnClickListener(v -> switchCamera());

        bar.addView(btnFlash, weightLp());
        bar.addView(btnCapture, weightLp());
        bar.addView(btnSwitch, weightLp());

        root.addView(bar, barLp);
        setContentView(root);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = dp(6);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    // ==========================================================
    // 权限
    // ==========================================================
    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        return new String[]{Manifest.permission.CAMERA};
    }

    private boolean hasAllPermissions() {
        for (String p : neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            boolean cameraGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    break;
                }
            }
            if (cameraGranted) {
                startCamera();
            } else {
                Toast.makeText(this, "❌未授予相机权限，无法使用相机", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ==========================================================
    // 启动相机 & 绑定用例
    // ==========================================================
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "相机启动失败", e);
                Toast.makeText(this, "相机启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) return;

        cameraProvider.unbindAll();

        CameraSelector selector = isFrontCamera
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            updateFlashButton();
        } catch (Exception e) {
            Log.e(TAG, "绑定相机失败", e);
            Toast.makeText(this, "绑定相机失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==========================================================
    // 闪光灯
    // ==========================================================
    private void updateFlashButton() {
        if (camera == null) {
            btnFlash.setEnabled(false);
            btnFlash.setText("无闪光灯");
            return;
        }
        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
        if (!hasFlash) {
            // 前置一般无闪光灯，关闭状态并禁用
            isFlashOn = false;
            camera.getCameraControl().enableTorch(false);
            btnFlash.setEnabled(false);
            btnFlash.setText("无闪光灯");
        } else {
            btnFlash.setEnabled(true);
            btnFlash.setText(isFlashOn ? "闪光灯开" : "闪光灯");
        }
    }

    private void toggleFlash() {
        if (camera == null) return;
        if (!camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, "该摄像头没有闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }
        isFlashOn = !isFlashOn;
        camera.getCameraControl().enableTorch(isFlashOn);
        btnFlash.setText(isFlashOn ? "闪光灯开" : "闪光灯");
    }

    // ==========================================================
    // 切换摄像头
    // ==========================================================
    private void switchCamera() {
        if (cameraProvider == null) return;
        isFrontCamera = !isFrontCamera;
        isFlashOn = false;
        bindUseCases();
    }

    // ==========================================================
    // 拍照保存
    // ==========================================================
    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg";

        ImageCapture.OutputFileOptions options;
        File legacyFile = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ScreenWake");
            options = new ImageCapture.OutputFileOptions.Builder(
                    getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values).build();
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "ScreenWake");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "无法创建保存目录", Toast.LENGTH_LONG).show();
                return;
            }
            legacyFile = new File(dir, name);
            options = new ImageCapture.OutputFileOptions.Builder(legacyFile).build();
        }

        final File finalLegacyFile = legacyFile;
        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        Uri uri = results.getSavedUri();
                        // API < 29 保存到文件后，通知系统媒体库刷新
                        if (uri == null && finalLegacyFile != null) {
                            MediaScannerConnection.scanFile(
                                    CameraActivity.this,
                                    new String[]{finalLegacyFile.getAbsolutePath()},
                                    new String[]{"image/jpeg"}, null);
                        }
                        final String msg = uri != null
                                ? "✅拍照成功：" + uri
                                : "✅拍照成功：" + (finalLegacyFile != null
                                        ? finalLegacyFile.getAbsolutePath() : name);
                        runOnUiThread(() -> Toast.makeText(
                                CameraActivity.this, msg, Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "拍照失败", exception);
                        runOnUiThread(() -> Toast.makeText(
                                CameraActivity.this,
                                "❌拍照失败：" + exception.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    // ==========================================================
    // 工具方法
    // ==========================================================
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
