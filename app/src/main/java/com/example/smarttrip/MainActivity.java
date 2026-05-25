package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "SmartTripCloud";
    private static final String USER_ID = "amin";

    private TextView tvBatteryStatus;
    private TextView tvStatsCloud;

    // Compteurs agrégés depuis les endpoints cloud
    private int statVoyages = 0;
    private int statGps = 0;
    private int statPois = 0;
    private int statPhotos = 0;

    // Permet de savoir si une vraie erreur réseau / serveur a eu lieu
    private boolean cloudError = false;

    // Combien d'appels ont répondu : GPS + POI + Photos = 3
    private final AtomicInteger responseCount = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout btnStartTrip = findViewById(R.id.btnStartTrip);
        LinearLayout btnViewTrips = findViewById(R.id.btnViewTrips);
        LinearLayout btnSettings = findViewById(R.id.btnSettings);

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvStatsCloud = findViewById(R.id.tvStatsCloud);

        btnStartTrip.setOnClickListener(v -> showNameTripDialog());
        btnViewTrips.setOnClickListener(v -> startActivity(new Intent(this, TripsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Réveil anticipé du serveur Render
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

                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Wake up server response code: " + responseCode);

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Erreur pendant le réveil du serveur Render", e);
            }
        }).start();
    }

    // =========================================================================
    // Stats cloud complètes : GPS + POI + Photos
    // =========================================================================

    private void loadStats() {
        tvStatsCloud.setText("☁  Connexion au cloud…");

        // Reset des valeurs
        statVoyages = 0;
        statGps = 0;
        statPois = 0;
        statPhotos = 0;
        cloudError = false;
        responseCount.set(0);

        // 1. Points GPS
        ApiClient.getInstance().getApiService().getUserGps(USER_ID)
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            retrofit2.Response<List<Map<String, Object>>> response
                    ) {
                        if (response.isSuccessful() && response.body() != null) {
                            Set<String> tripIds = new HashSet<>();

                            for (Map<String, Object> doc : response.body()) {
                                Object tripIdObj = doc.get("trip_id");

                                if (tripIdObj != null) {
                                    tripIds.add(String.valueOf(tripIdObj));
                                }
                            }

                            statVoyages = tripIds.size();
                            statGps = response.body().size();

                            Log.d(TAG, "GPS OK : " + statGps + " point(s)");
                        } else {
                            cloudError = true;
                            Log.e(TAG, "Erreur HTTP GPS : " + response.code());
                        }

                        checkAndUpdateStats();
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            Throwable t
                    ) {
                        cloudError = true;
                        Log.e(TAG, "Échec appel GPS", t);
                        checkAndUpdateStats();
                    }
                });

        // 2. POI
        ApiClient.getInstance().getApiService().getUserPois(USER_ID)
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            retrofit2.Response<List<Map<String, Object>>> response
                    ) {
                        if (response.isSuccessful() && response.body() != null) {
                            statPois = response.body().size();

                            Log.d(TAG, "POI OK : " + statPois + " POI");
                        } else {
                            cloudError = true;
                            Log.e(TAG, "Erreur HTTP POI : " + response.code());
                        }

                        checkAndUpdateStats();
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            Throwable t
                    ) {
                        cloudError = true;
                        Log.e(TAG, "Échec appel POI", t);
                        checkAndUpdateStats();
                    }
                });

        // 3. Photos
        ApiClient.getInstance().getApiService().getUserPhotos(USER_ID)
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            retrofit2.Response<List<Map<String, Object>>> response
                    ) {
                        if (response.isSuccessful() && response.body() != null) {
                            statPhotos = response.body().size();

                            Log.d(TAG, "Photos OK : " + statPhotos + " photo(s)");
                        } else {
                            cloudError = true;
                            Log.e(TAG, "Erreur HTTP Photos : " + response.code());
                        }

                        checkAndUpdateStats();
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<List<Map<String, Object>>> call,
                            Throwable t
                    ) {
                        cloudError = true;
                        Log.e(TAG, "Échec appel Photos", t);
                        checkAndUpdateStats();
                    }
                });
    }

    /**
     * Appelé après chaque réponse API.
     * Met à jour l'interface seulement quand les 3 appels ont répondu.
     */
    private void checkAndUpdateStats() {
        if (responseCount.incrementAndGet() == 3) {
            final String statsText;

            if (cloudError) {
                statsText = "☁  Cloud non connecté";
            } else if (statVoyages == 0 && statGps == 0 && statPois == 0 && statPhotos == 0) {
                statsText = "☁  Cloud connecté · aucun voyage";
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
                                "dd/MM/yyyy",
                                java.util.Locale.FRANCE
                        ).format(new java.util.Date());
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
        if (tvBatteryStatus != null) {
            tvBatteryStatus.setText(BatteryHelper.getStatusMessage(this));
        }
    }
}