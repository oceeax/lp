package com.example.screenwake;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQ_PERMISSIONS = 2001;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Camera camera;

    private boolean isFrontCamera = false;
    private boolean isFlashOn = false;
    private boolean isRecording = false;
    private boolean isVideoMode = false; // ★ 新增：记录当前是否为录像模式
    private Recording activeRecording;

    private Button btnCapture, btnSwitch, btnFlash, btnRecordMode;
    private SeekBar zoomSeekBar;

    private ExecutorService cameraExecutor;
    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            buildUi();
            setupGestures();
            if (hasAllPermissions()) {
                startCamera();
            } else {
                ActivityCompat.requestPermissions(this, neededPermissions(), REQ_PERMISSIONS);
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化发生致命错误", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
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

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        return new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    }

    private boolean hasAllPermissions() {
        for (String p : neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                startCamera();
            } else {
                Toast.makeText(this, "❌未授予必要权限，无法使用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(16), dp(16), dp(16), dp(24));
        bottomBar.setBackgroundColor(0x80000000);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;

        zoomSeekBar = new SeekBar(this);
        zoomSeekBar.setMax(100);
        zoomSeekBar.setProgress(0);
        bottomBar.addView(zoomSeekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && camera != null) {
                    ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                    if (zoomState != null) {
                        float minZoom = zoomState.getMinZoomRatio();
                        float maxZoom = zoomState.getMaxZoomRatio();
                        float zoom = minZoom + (maxZoom - minZoom) * (progress / 100f);
                        camera.getCameraControl().setZoomRatio(zoom);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, dp(16), 0, 0);

        btnFlash = makeButton("闪光灯");
        btnFlash.setOnClickListener(v -> toggleFlash());

        btnRecordMode = makeButton("切换到录像");
        btnRecordMode.setOnClickListener(v -> toggleMode());

        btnCapture = makeButton("拍照");
        // ★ 修复：现在点击按钮会根据当前模式正确判断是拍照还是录像
        btnCapture.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else if (isVideoMode) {
                startRecording();
            } else {
                takePhoto();
            }
        });
        // 拍照成功时：
        LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "拍照成功，文件已保存至相册");

        // 录像开始/结束时：
        LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "开始录制视频...");
        LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "停止录像并保存成功");
        btnSwitch = makeButton("切换");
        btnSwitch.setOnClickListener(v -> switchCamera());

        buttonRow.addView(btnFlash, weightLp());
        buttonRow.addView(btnRecordMode, weightLp());
        buttonRow.addView(btnCapture, weightLp());
        buttonRow.addView(btnSwitch, weightLp());

        bottomBar.addView(buttonRow);
        root.addView(bottomBar, barLp);

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
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "获取CameraProvider失败", e);
                Toast.makeText(this, "相机启动失败，可能被占用", Toast.LENGTH_LONG).show();
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

        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                ))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        try {
            camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture);
            updateFlashButton();
            updateZoomSeekBar();
        } catch (Exception e) {
            Log.e(TAG, "绑定相机失败", e);
            Toast.makeText(this, "绑定相机失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateZoomSeekBar() {
        if (camera == null) return;
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState != null) {
            zoomSeekBar.setProgress(0);
            camera.getCameraControl().setZoomRatio(zoomState.getMinZoomRatio());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera == null) return false;
                ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    float currentZoom = zoomState.getZoomRatio();
                    float delta = detector.getScaleFactor();
                    float targetZoom = currentZoom * delta;

                    targetZoom = Math.max(zoomState.getMinZoomRatio(), Math.min(targetZoom, zoomState.getMaxZoomRatio()));
                    camera.getCameraControl().setZoomRatio(targetZoom);

                    float progress = (targetZoom - zoomState.getMinZoomRatio()) / (zoomState.getMaxZoomRatio() - zoomState.getMinZoomRatio());
                    zoomSeekBar.setProgress((int) (progress * 100));
                    return true;
                }
                return false;
            }
        });

        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    // ★ 修复：UI 与模式变量实时绑定
    private void toggleMode() {
        if (isRecording) {
            Toast.makeText(this, "请先停止录像", Toast.LENGTH_SHORT).show();
            return;
        }
        isVideoMode = !isVideoMode; // 切换模式标识

        if (isVideoMode) {
            btnRecordMode.setText("切换到拍照");
            btnCapture.setText("开始录像");
            btnCapture.setBackgroundColor(Color.RED);
            btnCapture.setTextColor(Color.WHITE);
        } else {
            btnRecordMode.setText("切换到录像");
            btnCapture.setText("拍照");
            btnCapture.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            btnCapture.setTextColor(Color.BLACK);
        }
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg";

        ImageCapture.OutputFileOptions options;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ScreenWake");
            options = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ScreenWake");
            if (!dir.exists()) dir.mkdirs();
            options = new ImageCapture.OutputFileOptions.Builder(new File(dir, name)).build();
        }

        imageCapture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "✅拍照成功", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "❌拍照失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startRecording() {
        if (videoCapture == null) return;

        String name = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".mp4";
        MediaStoreOutputOptions outputOptions = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ScreenWake");
            
            // ★ 修复：加上 new 关键字，这才是真正的实例化
            outputOptions = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values).build();
        } else {
            Toast.makeText(this, "当前系统版本过低，请使用 Android 10 及以上版本测试录像", Toast.LENGTH_SHORT).show();
            return;
        }

        activeRecording = videoCapture.getOutput()
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        isRecording = true;
                        runOnUiThread(() -> {
                            btnCapture.setText("停止录像");
                            btnCapture.setBackgroundColor(Color.GRAY);
                        });
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        isRecording = false;
                        runOnUiThread(() -> {
                            Toast.makeText(CameraActivity.this, "✅录像已保存", Toast.LENGTH_SHORT).show();
                            btnCapture.setText("开始录像");
                            btnCapture.setBackgroundColor(Color.RED);
                        });
                    }
                });
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    private void switchCamera() {
        if (cameraProvider == null || isRecording) return;
        isFrontCamera = !isFrontCamera;
        isFlashOn = false;
        bindUseCases();
    }

    private void updateFlashButton() {
        if (camera == null) return;
        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
        btnFlash.setEnabled(hasFlash);
        btnFlash.setText(hasFlash ? "闪光灯" : "无闪光灯");
    }
    // 拍照成功时：
    LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "拍照成功，文件已保存至相册");

// 录像开始/结束时：
    LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "开始录制视频...");
    LogManager.i(CameraActivity.this, LogManager.SUB_CAMERA, "停止录像并保存成功");
    private void toggleFlash() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        isFlashOn = !isFlashOn;
        camera.getCameraControl().enableTorch(isFlashOn);
        btnFlash.setText(isFlashOn ? "闪光灯开" : "闪光灯");
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}


