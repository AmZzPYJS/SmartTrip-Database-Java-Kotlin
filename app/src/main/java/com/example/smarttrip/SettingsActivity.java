package com.example.smarttrip;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Écran de paramètres de l'application.
 *
 * Paramètres disponibles :
 * - Activer/désactiver la collecte GPS
 * - Fréquence de collecte
 * - Statut batterie détaillé
 *
 * Pour la démo, les paramètres sont en mémoire uniquement.
 * TODO : persister avec SharedPreferences ou dans MongoDB.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView tvBatteryInfo = findViewById(R.id.tvBatteryInfo);
        Switch switchGps = findViewById(R.id.switchGps);
        TextView tvThreshold = findViewById(R.id.tvThreshold);

        // Afficher les infos batterie
        int batteryLevel = BatteryHelper.getBatteryLevel(this);
        boolean charging = BatteryHelper.isCharging(this);
        boolean canCollect = BatteryHelper.shouldCollectData(this);

        String info = "Niveau : " + batteryLevel + "%\n"
                + "En charge : " + (charging ? "Oui" : "Non") + "\n"
                + "Collecte GPS : " + (canCollect ? "Autorisée" : "Suspendue") + "\n"
                + "Seuil d'arrêt : " + BatteryHelper.getThreshold() + "%";
        tvBatteryInfo.setText(info);

        // Switch GPS (visuel pour la démo)
        switchGps.setChecked(canCollect);
        switchGps.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // En production : sauvegarder la préférence
        });

        tvThreshold.setText("Seuil batterie minimum : " + BatteryHelper.getThreshold() + "%");
    }
}
