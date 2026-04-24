package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smarttrip.api.ApiClient;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvBatteryStatus;
    private TextView tvStatsCloud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStartTrip = findViewById(R.id.btnStartTrip);
        Button btnViewTrips = findViewById(R.id.btnViewTrips);
        Button btnSettings  = findViewById(R.id.btnSettings);
        tvBatteryStatus     = findViewById(R.id.tvBatteryStatus);
        tvStatsCloud        = findViewById(R.id.tvStatsCloud);

        btnStartTrip.setOnClickListener(v -> showNameTripDialog());

        btnViewTrips.setOnClickListener(v ->
                startActivity(new Intent(this, TripsActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBatteryStatus();
        loadStats();
    }

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

    private void loadStats() {
        tvStatsCloud.setText("Chargement...");
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
                            int nbVoyages = tripIds.size();
                            int nbPoints  = response.body().size();
                            runOnUiThread(() -> tvStatsCloud.setText(
                                    nbVoyages + " voyage(s) · " + nbPoints + " points GPS dans le cloud"));
                        } else {
                            runOnUiThread(() -> tvStatsCloud.setText("0 voyage(s) · 0 points GPS dans le cloud"));
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        runOnUiThread(() -> tvStatsCloud.setText("Cloud non connecté"));
                    }
                });
    }

    private void updateBatteryStatus() {
        if (tvBatteryStatus != null)
            tvBatteryStatus.setText(BatteryHelper.getStatusMessage(this));
    }
}