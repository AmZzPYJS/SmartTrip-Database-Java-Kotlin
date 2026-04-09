package com.example.smarttrip;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Écran de voyage en cours — collecte GPS active.
 *
 * Cet écran simule la collecte de données GPS en temps réel.
 * En production, il utiliserait LocationManager ou FusedLocationProviderClient
 * pour obtenir les vraies coordonnées du téléphone.
 *
 * Pour la démo en soutenance, il génère des points GPS simulés
 * autour de la position de l'université (UVSQ / Paris-Saclay).
 *
 * La gestion batterie est intégrée :
 * → Vérification toutes les 30 secondes
 * → Arrêt automatique de la collecte si batterie < 15%
 */
public class ActiveTripActivity extends AppCompatActivity {

    private TextView tvTripStatus;
    private TextView tvGpsCount;
    private TextView tvPoiCount;
    private TextView tvBatteryLive;
    private Button btnAddPoi;
    private Button btnStopTrip;

    private List<GpsPoint> collectedPoints = new ArrayList<>();
    private List<Poi> collectedPois = new ArrayList<>();
    private boolean isCollecting = true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Simulation : position de base (Versailles / UVSQ)
    private static final double BASE_LAT = 48.8014;
    private static final double BASE_LNG = 2.1301;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tvTripStatus = findViewById(R.id.tvTripStatus);
        tvGpsCount = findViewById(R.id.tvGpsCount);
        tvPoiCount = findViewById(R.id.tvPoiCount);
        tvBatteryLive = findViewById(R.id.tvBatteryLive);
        btnAddPoi = findViewById(R.id.btnAddPoi);
        btnStopTrip = findViewById(R.id.btnStopTrip);

        tvTripStatus.setText("● Voyage en cours");

        // Démarrer la collecte GPS simulée
        startGpsCollection();

        // Bouton : ajouter un POI
        btnAddPoi.setOnClickListener(v -> showAddPoiDialog());

        // Bouton : arrêter le voyage
        btnStopTrip.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Terminer le voyage ?")
                    .setMessage("Le voyage sera sauvegardé avec "
                            + collectedPoints.size() + " points GPS et "
                            + collectedPois.size() + " POI.")
                    .setPositiveButton("Terminer", (dialog, which) -> stopTrip())
                    .setNegativeButton("Continuer", null)
                    .show();
        });
    }

    /**
     * Simule la collecte GPS : un nouveau point toutes les 5 secondes.
     * En production : remplacer par FusedLocationProviderClient.
     */
    private void startGpsCollection() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isCollecting) return;

                // Vérifier la batterie
                if (!BatteryHelper.shouldCollectData(ActiveTripActivity.this)) {
                    isCollecting = false;
                    tvTripStatus.setText("⚠ Collecte suspendue — batterie faible");
                    Toast.makeText(ActiveTripActivity.this,
                            "Batterie en dessous de " + BatteryHelper.getThreshold()
                                    + "% — collecte GPS arrêtée automatiquement",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Simuler un déplacement aléatoire autour de la position de base
                double lat = BASE_LAT + (Math.random() - 0.5) * 0.01;
                double lng = BASE_LNG + (Math.random() - 0.5) * 0.01;
                long timestamp = System.currentTimeMillis() / 1000;

                GpsPoint point = new GpsPoint(lat, lng, timestamp);
                collectedPoints.add(point);

                // Mettre à jour l'UI
                tvGpsCount.setText(collectedPoints.size() + " points GPS");
                tvBatteryLive.setText(BatteryHelper.getStatusMessage(
                        ActiveTripActivity.this));

                // Prochain point dans 5 secondes
                handler.postDelayed(this, 5000);
            }
        }, 1000); // Premier point après 1 seconde
    }

    /**
     * Dialogue pour ajouter un POI manuellement.
     * En production : pré-remplir avec les coordonnées GPS actuelles
     * et permettre de prendre une photo avec l'appareil.
     */
    private void showAddPoiDialog() {
        // POI simulé pour la démo
        double lat = BASE_LAT + (Math.random() - 0.5) * 0.005;
        double lng = BASE_LNG + (Math.random() - 0.5) * 0.005;

        Poi demoPoi = new Poi(
                "POI #" + (collectedPois.size() + 1),
                "autre",
                lat, lng,
                3, "Ajouté pendant le voyage",
                ""
        );
        collectedPois.add(demoPoi);
        tvPoiCount.setText(collectedPois.size() + " POI");

        Toast.makeText(this,
                "POI ajouté à (" + String.format("%.4f", lat) + ", "
                        + String.format("%.4f", lng) + ")",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Arrête la collecte et sauvegarde le voyage.
     * TODO : envoyer les données au backend FastAPI via POST /api/trips
     */
    private void stopTrip() {
        isCollecting = false;
        handler.removeCallbacksAndMessages(null);

        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                .format(new Date());

        Trip trip = new Trip(
                "trip_" + System.currentTimeMillis(),
                "Voyage du " + date,
                date,
                "Voyage enregistré avec SmartTrip",
                false,
                collectedPoints,
                collectedPois
        );

        // TODO : sauvegarder dans MongoDB via l'API
        // ApiClient.saveTrip(trip, response -> { ... });

        Toast.makeText(this,
                "Voyage sauvegardé : " + trip.getSummary(),
                Toast.LENGTH_LONG).show();

        finish(); // Retour à l'accueil
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
