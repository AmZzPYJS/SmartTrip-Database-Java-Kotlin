package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Écran d'accueil de SmartTrip.
 *
 * 3 actions possibles :
 * - Démarrer un voyage → ouvre ActiveTripActivity (collecte GPS)
 * - Voir les voyages   → ouvre TripsActivity (historique)
 * - Paramètres          → ouvre SettingsActivity
 *
 * Affiche aussi le statut batterie en bas de l'écran.
 */
public class MainActivity extends AppCompatActivity {

    private Button btnStartTrip;
    private Button btnViewTrips;
    private Button btnSettings;
    private TextView tvBatteryStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Liaison des vues
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnViewTrips = findViewById(R.id.btnViewTrips);
        btnSettings = findViewById(R.id.btnSettings);
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);

        // --- Navigation ---

        btnStartTrip.setOnClickListener(v -> {
            // Vérifier la batterie avant de démarrer la collecte
            if (!BatteryHelper.shouldCollectData(this)) {
                Toast.makeText(this,
                        "Batterie trop faible (" + BatteryHelper.getBatteryLevel(this)
                                + "%). Rechargez pour démarrer un voyage.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(MainActivity.this, ActiveTripActivity.class);
            startActivity(intent);
        });

        btnViewTrips.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TripsActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mettre à jour le statut batterie à chaque retour sur cet écran
        updateBatteryStatus();
    }

    /**
     * Affiche le niveau de batterie et l'état de collecte.
     */
    private void updateBatteryStatus() {
        if (tvBatteryStatus != null) {
            tvBatteryStatus.setText(BatteryHelper.getStatusMessage(this));
        }
    }
}
