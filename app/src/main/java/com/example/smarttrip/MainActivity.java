package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
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
            if (!BatteryHelper.shouldCollectData(this)) {
                Toast.makeText(this,
                        "Batterie trop faible (" + BatteryHelper.getBatteryLevel(this)
                                + "%). Rechargez pour démarrer un voyage.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            showNameTripDialog();
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

    private void showNameTripDialog() {
        final EditText input = new EditText(this);
        input.setHint("Ex: Week-end Paris, Visite Versailles...");
        input.setSingleLine(true);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Nommer votre voyage")
                .setMessage("Donnez un nom à ce voyage pour le retrouver facilement")
                .setView(input)
                .setPositiveButton("Démarrer", (dialog, which) -> {
                    String tripName = input.getText().toString().trim();
                    if (tripName.isEmpty()) {
                        tripName = "Voyage du " + new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
                                .format(new java.util.Date());
                    }

                    Intent intent = new Intent(MainActivity.this, ActiveTripActivity.class);
                    intent.putExtra("trip_name", tripName);
                    startActivity(intent);
                })
                .setNegativeButton("Annuler", null)
                .show();
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
