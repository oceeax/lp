package com.example.screenwake;
import com.example.screenwake.R;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import java.io.File;

public class ImageShowActivity extends AppCompatActivity {
    public static final String EXTRA_IMG_PATH = "img_path";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_show);
        ImageView iv = findViewById(R.id.iv_show);
        String path = getIntent().getStringExtra(EXTRA_IMG_PATH);
        if(path != null){
            File img = new File(path);
            Glide.with(this).load(img).into(iv);
        }
    }
}
