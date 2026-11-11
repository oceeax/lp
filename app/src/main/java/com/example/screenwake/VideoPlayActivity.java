package com.example.screenwake;
import com.example.screenwake.R;
import android.net.Uri;
import android.os.Bundle;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;

public class VideoPlayActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_PATH = "video_path";
    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_play);
        videoView = findViewById(R.id.video_view);
        String path = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if(path != null){
            File videoFile = new File(path);
            Uri uri = Uri.fromFile(videoFile);
            videoView.setVideoURI(uri);
            videoView.start();
        }
    }
}
