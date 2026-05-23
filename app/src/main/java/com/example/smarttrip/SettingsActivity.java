package com.example.smarttrip;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkManager;

import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.db.AppDatabase;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "smarttrip_settings";
    private static final String KEY_GPS_ENABLED  = "gps_collection_enabled";
    private static final String KEY_BATTERY_THRESHOLD = "battery_threshold";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float d = getResources().getDisplayMetrics().density;

        // ── Layout principal ───────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0F1A);

        // Header violet
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(0xFF6C63FF);
        header.setPadding(Math.round(20*d), Math.round(48*d), Math.round(20*d), Math.round(20*d));
        TextView tvHeader = new TextView(this);
        tvHeader.setText("Paramètres");
        tvHeader.setTextSize(24);
        tvHeader.setTextColor(0xFFFFFFFF);
        tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvHeader);
        root.addView(header);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Math.round(20*d), Math.round(20*d), Math.round(20*d), Math.round(20*d));
        scrollView.addView(content);

        // ── SECTION BATTERIE ─────────────────────────────────────────────────
        addSectionTitle(content, "🔋  BATTERIE", d);

        LinearLayout batteryCard = newCard(d);
        int level = BatteryHelper.getBatteryLevel(this);
        boolean charging = BatteryHelper.isCharging(this);
        boolean allowed = BatteryHelper.shouldCollectData(this);
        addInfoRow(batteryCard, "Niveau", level + "%", d);
        addInfoRow(batteryCard, "En charge", charging ? "Oui ⚡" : "Non", d);
        addInfoRow(batteryCard, "Collecte GPS", allowed ? "✅ Autorisée" : "⛔ Suspendue", d);
        content.addView(batteryCard);

        // ── SECTION COLLECTE GPS ─────────────────────────────────────────────
        addSectionTitle(content, "🛰️  COLLECTE DE DONNÉES", d);

        LinearLayout gpsCard = newCard(d);

        // Toggle GPS
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tvToggleLabel = new TextView(this);
        tvToggleLabel.setText("Collecte GPS active");
        tvToggleLabel.setTextSize(15);
        tvToggleLabel.setTextColor(0xFFFFFFFF);
        tvToggleLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch switchGps = new Switch(this);
        switchGps.setChecked(prefs.getBoolean(KEY_GPS_ENABLED, true));
        switchGps.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(KEY_GPS_ENABLED, checked).apply());
        toggleRow.addView(tvToggleLabel);
        toggleRow.addView(switchGps);
        gpsCard.addView(toggleRow);

        // Seuil batterie
        int threshold = prefs.getInt(KEY_BATTERY_THRESHOLD, 15);
        TextView tvThreshold = new TextView(this);
        tvThreshold.setText("Seuil d'arrêt : " + threshold + "%");
        tvThreshold.setTextSize(13);
        tvThreshold.setTextColor(0xFF9999BB);
        tvThreshold.setPadding(0, Math.round(12*d), 0, Math.round(4*d));
        gpsCard.addView(tvThreshold);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(45); // 5% à 50%
        seekBar.setProgress(threshold - 5);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = progress + 5;
                tvThreshold.setText("Seuil d'arrêt : " + val + "%");
                prefs.edit().putInt(KEY_BATTERY_THRESHOLD, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        gpsCard.addView(seekBar);

        TextView tvSeekInfo = new TextView(this);
        tvSeekInfo.setText("Min 5%  —  Max 50%");
        tvSeekInfo.setTextSize(11);
        tvSeekInfo.setTextColor(0xFF555570);
        gpsCard.addView(tvSeekInfo);
        content.addView(gpsCard);

        // ── SECTION SYNCHRONISATION ──────────────────────────────────────────
        addSectionTitle(content, "☁️  SYNCHRONISATION", d);

        LinearLayout syncCard = newCard(d);

        // Statut Room — points en attente de sync
        TextView tvSyncStatus = new TextView(this);
        tvSyncStatus.setText("Chargement…");
        tvSyncStatus.setTextSize(13);
        tvSyncStatus.setTextColor(0xFF9999BB);
        syncCard.addView(tvSyncStatus);

        // Charger le statut Room en arrière-plan
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            int pendingGps    = db.gpsPointDao().getUnsynced().size();
            int pendingPois   = db.poiDao().getUnsynced().size();
            int pendingPhotos = db.photoDao().getUnsynced().size();
            int total = pendingGps + pendingPois + pendingPhotos;
            runOnUiThread(() -> {
                if (total == 0) {
                    tvSyncStatus.setText("✅ Tout est synchronisé avec le cloud");
                    tvSyncStatus.setTextColor(0xFF26de81);
                } else {
                    tvSyncStatus.setText("⏳ " + total + " élément(s) en attente\n"
                            + pendingGps + " GPS · " + pendingPois + " POI · "
                            + pendingPhotos + " photo(s)");
                    tvSyncStatus.setTextColor(0xFFF7B731);
                }
            });
        });

        // Bouton forcer sync
        android.widget.Button btnSync = new android.widget.Button(this);
        btnSync.setText("↑  Forcer la synchronisation maintenant");
        btnSync.setTextColor(0xFFFFFFFF);
        btnSync.setBackgroundColor(0xFF6C63FF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, Math.round(12*d), 0, 0);
        btnSync.setLayoutParams(btnParams);
        btnSync.setOnClickListener(v -> {
            SyncWorker.schedule(this);
            Toast.makeText(this, "Synchronisation planifiée ✓", Toast.LENGTH_SHORT).show();
            tvSyncStatus.setText("⏳ Synchronisation en cours…");
            tvSyncStatus.setTextColor(0xFFF7B731);
        });
        syncCard.addView(btnSync);
        content.addView(syncCard);

        // ── SECTION STOCKAGE LOCAL ──────────────────────────────────────────
        addSectionTitle(content, "💾  STOCKAGE LOCAL (OFFLINE)", d);

        LinearLayout storageCard = newCard(d);
        TextView tvStorage = new TextView(this);
        tvStorage.setText("Chargement…");
        tvStorage.setTextSize(13);
        tvStorage.setTextColor(0xFF9999BB);
        storageCard.addView(tvStorage);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            int totalGps    = db.gpsPointDao().getUnsynced().size();
            int totalPois   = db.poiDao().getUnsynced().size();
            int totalPhotos = db.photoDao().getUnsynced().size();
            runOnUiThread(() ->
                    tvStorage.setText(
                            "Points GPS locaux (non sync) : " + totalGps + "\n" +
                                    "POI locaux (non sync) : " + totalPois + "\n" +
                                    "Photos locales (non sync) : " + totalPhotos + "\n\n" +
                                    "💡 Ces données seront envoyées au cloud dès\n" +
                                    "que la connexion sera disponible.")
            );
        });
        content.addView(storageCard);

        // ── SECTION STATS CLOUD ──────────────────────────────────────────────
        addSectionTitle(content, "📊  STATISTIQUES CLOUD", d);

        LinearLayout statsCard = newCard(d);
        TextView tvCloudStats = new TextView(this);
        tvCloudStats.setText("Chargement…");
        tvCloudStats.setTextSize(13);
        tvCloudStats.setTextColor(0xFF9999BB);
        statsCard.addView(tvCloudStats);

        ApiClient.getInstance().getApiService().getUserGps("amin")
                .enqueue(new retrofit2.Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<List<Map<String, Object>>> call,
                                           retrofit2.Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int nbGps = response.body().size();
                            java.util.Set<String> trips = new java.util.HashSet<>();
                            for (Map<String, Object> doc : response.body()) {
                                String tid = (String) doc.get("trip_id");
                                if (tid != null) trips.add(tid);
                            }
                            runOnUiThread(() -> tvCloudStats.setText(
                                    trips.size() + " voyage(s) dans le cloud\n" +
                                            nbGps + " points GPS synchronisés"));
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<List<Map<String, Object>>> call, Throwable t) {
                        runOnUiThread(() -> tvCloudStats.setText("Cloud non accessible"));
                    }
                });
        content.addView(statsCard);

        // ── SECTION INFOS APP ─────────────────────────────────────────────────
        addSectionTitle(content, "ℹ️  INFORMATIONS", d);

        LinearLayout infoCard = newCard(d);
        addInfoRow(infoCard, "Mobile UI/UX Engineer ", "Moussa NGUETTE", d);
        addInfoRow(infoCard, "Android Software Architect ", "Amîn MEZOUER", d);
        addInfoRow(infoCard, "Backend & Cloud Engineer", "Abdallah Benzoubir", d);
        addInfoRow(infoCard, "Application QA & Support Specialist ", "Papa Amath BODIAN", d);
        content.addView(infoCard);

        root.addView(scrollView);
        setContentView(root);
    }

    // =========================================================================
    // Helpers UI
    // =========================================================================

    private void addSectionTitle(LinearLayout parent, String title, float d) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTextColor(0xFF6C63FF);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Math.round(20*d), 0, Math.round(8*d));
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private LinearLayout newCard(float d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1A1A2E);
        card.setPadding(Math.round(16*d), Math.round(16*d), Math.round(16*d), Math.round(16*d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Math.round(4*d));
        card.setLayoutParams(lp);
        return card;
    }

    private void addInfoRow(LinearLayout parent, String label, String value, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Math.round(6*d));
        row.setLayoutParams(lp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(13);
        tvLabel.setTextColor(0xFF9999BB);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(13);
        tvValue.setTextColor(0xFFFFFFFF);
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tvValue);

        parent.addView(row);
    }
}