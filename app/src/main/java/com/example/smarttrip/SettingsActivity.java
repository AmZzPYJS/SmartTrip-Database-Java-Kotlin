package com.example.smarttrip;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvBatteryInfo = findViewById(R.id.tvBatteryInfo);
        Switch switchGps = findViewById(R.id.switchGps);
        TextView tvThreshold = findViewById(R.id.tvThreshold);
        SeekBar seekBarThreshold = findViewById(R.id.seekBarThreshold);

        // Infos batterie
        int batteryLevel = BatteryHelper.getBatteryLevel(this);
        boolean charging = BatteryHelper.isCharging(this);
        boolean canCollect = BatteryHelper.shouldCollectData(this);
        int currentThreshold = BatteryHelper.getThreshold(this);

        String info = "Niveau : " + batteryLevel + "%\n"
                + "En charge : " + (charging ? "Oui" : "Non") + "\n"
                + "Collecte GPS : " + (canCollect ? "Autorisée" : "Suspendue");
        tvBatteryInfo.setText(info);

        switchGps.setChecked(canCollect);

        // SeekBar pour le seuil
        tvThreshold.setText("Seuil d'arrêt : " + currentThreshold + "%");
        seekBarThreshold.setMax(50);
        seekBarThreshold.setProgress(currentThreshold);

        seekBarThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int threshold = Math.max(5, progress); // minimum 5%
                tvThreshold.setText("Seuil d'arrêt : " + threshold + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int threshold = Math.max(5, seekBar.getProgress());
                BatteryHelper.setThreshold(SettingsActivity.this, threshold);
                Toast.makeText(SettingsActivity.this,
                        "Seuil sauvegardé : " + threshold + "%",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}