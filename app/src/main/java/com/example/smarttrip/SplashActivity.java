package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.imgLogo);

        // :fire: Animation (zoom)
        logo.setScaleX(0f);
        logo.setScaleY(0f);

        logo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .start();

        // :stopwatch: Redirection
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 1500);
    }
}