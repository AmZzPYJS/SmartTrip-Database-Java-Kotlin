package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Écran de démarrage avec logo animé.
 *
 * Utilise Handler(Looper.getMainLooper()) au lieu de new Handler()
 * pour éviter les fuites mémoire si l'activité est détruite
 * avant la fin du délai (rotation écran, back button rapide).
 */
public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = () -> {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.imgLogo);

        // Animation zoom-in du logo
        logo.setScaleX(0f);
        logo.setScaleY(0f);
        logo.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .start();

        // Navigation différée vers MainActivity
        handler.postDelayed(navigateRunnable, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nettoyer le callback pour éviter les fuites mémoire
        handler.removeCallbacks(navigateRunnable);
    }
}
