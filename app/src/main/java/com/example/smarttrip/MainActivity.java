package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smarttrip.api.ApiClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private TextView tvBatteryStatus;
    private TextView tvStatsCloud;

    // Compteurs agrégés depuis les 3 endpoints
    private int statVoyages = 0;
    private int statGps     = 0;
    private int statPois    = 0;
    private int statPhotos  = 0;

    // Combien d'appels ont répondu (sur 3 attendus)
    private final AtomicInteger responseCount = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout btnStartTrip = findViewById(R.id.btnStartTrip);
        LinearLayout btnViewTrips = findViewById(R.id.btnViewTrips);
        LinearLayout btnSettings  = findViewById(R.id.btnSettings);

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvStatsCloud    = findViewById(R.id.tvStatsCloud);

        btnStartTrip.setOnClickListener(v -> showNameTripDialog());
        btnViewTrips.setOnClickListener(v -> startActivity(new Intent(this, TripsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Réveil anticipé de Render.com (cold start ~15-30s sans ça)
        wakeUpServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryStatus();
        loadStats();
    }

    // =========================================================================
    // Réveil serveur Render
    // =========================================================================

    private void wakeUpServer() {
        new Thread(() -> {
            try {
                String baseUrl = "https://travel-tracker-backend-j5q0.onrender.com/";
                java.net.URL url = new java.net.URL(baseUrl);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    // =========================================================================
    // Stats cloud complètes (GPS + POI + photos)
    // =========================================================================

    private void loadStats() {
        tvStatsCloud.setText("☁  Connexion au cloud…");

        // Reset
        statVoyages = 0;
        statGps     = 0;
        statPois    = 0;
        statPhotos  = 0;
        responseCount.set(0);

        // 1. Points GPS
        ApiClient.getInstance().getApiService().getUserGps("amin")
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                           retrofit2.Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Set<String> tripIds = new HashSet<>();
                            for (Map<String, Object> doc : response.body()) {
                                String tripId = (String) doc.get("trip_id");
                                if (tripId != null) tripIds.add(tripId);
                            }
                            statVoyages = tripIds.size();
                            statGps     = response.body().size();
                        }
                        checkAndUpdateStats();
                    }
                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        checkAndUpdateStats();
                    }
                });

        // 2. POI
        ApiClient.getInstance().getApiService().getUserPois("amin")
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                           retrofit2.Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            statPois = response.body().size();
                        }
                        checkAndUpdateStats();
                    }
                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        checkAndUpdateStats();
                    }
                });

        // 3. Photos
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                           retrofit2.Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            statPhotos = response.body().size();
                        }
                        checkAndUpdateStats();
                    }
                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        checkAndUpdateStats();
                    }
                });
    }

    /**
     * Appelé après chaque réponse API.
     * Met à jour l'UI seulement quand les 3 appels ont répondu.
     */
    private void checkAndUpdateStats() {
        if (responseCount.incrementAndGet() == 3) {
            final String statsText;
            if (statVoyages == 0 && statGps == 0) {
                statsText = "☁  Cloud non connecté";
            } else {
                statsText = statVoyages + " voyage(s)  ·  "
                        + statGps + " pts GPS\n"
                        + statPois + " POI  ·  "
                        + statPhotos + " photo(s)";
            }
            runOnUiThread(() -> tvStatsCloud.setText(statsText));
        }
    }

    // =========================================================================
    // Dialog nommage voyage
    // =========================================================================

    private void showNameTripDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Ex: Week-end Paris, Visite Versailles...");
        input.setSingleLine(true);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Nommer votre voyage")
                .setMessage("Donnez un nom à ce voyage pour le retrouver facilement")
                .setView(input)
                .setPositiveButton("Démarrer", (dialog, which) -> {
                    String tripName = input.getText().toString().trim();
                    if (tripName.isEmpty()) {
                        tripName = "Voyage du " + new java.text.SimpleDateFormat(
                                "dd/MM/yyyy", java.util.Locale.FRANCE).format(new java.util.Date());
                    }
                    Intent intent = new Intent(this, ActiveTripActivity.class);
                    intent.putExtra("trip_name", tripName);
                    startActivity(intent);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // =========================================================================
    // Batterie
    // =========================================================================

    private void updateBatteryStatus() {
        if (tvBatteryStatus != null)
            tvBatteryStatus.setText(BatteryHelper.getStatusMessage(this));
    }
}