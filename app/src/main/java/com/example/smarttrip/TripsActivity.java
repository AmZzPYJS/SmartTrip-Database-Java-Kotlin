package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smarttrip.api.ApiClient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TripsActivity extends AppCompatActivity implements TripAdapter.OnTripClickListener {

    private TripAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private List<Trip> voyages = new ArrayList<>();

    private Map<String, List<GpsPoint>> gpsByTrip    = new HashMap<>();
    private Map<String, List<Poi>>      poisByTrip   = new HashMap<>();
    private Map<String, String>         nameByTrip   = new HashMap<>();
    private Map<String, String>         dateByTrip   = new HashMap<>();
    private Map<String, String>         poiDateByTrip = new HashMap<>();

    private int loadingsRemaining = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        RecyclerView recyclerTrips = findViewById(R.id.recyclerTrips);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);

        recyclerTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(voyages, this);
        recyclerTrips.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gpsByTrip.clear();
        poisByTrip.clear();
        nameByTrip.clear();
        dateByTrip.clear();
        poiDateByTrip.clear();
        voyages.clear();
        adapter.notifyDataSetChanged();
        loadingsRemaining = 2;
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        loadTripsFromCloud();
    }

    @Override
    public void onTripClick(Trip trip, int position) {
        // Clic simple → détail
        Intent intent = new Intent(this, TripDetailsActivity.class);
        intent.putExtra("trip", trip);
        startActivity(intent);
    }

    // Appel depuis TripAdapter pour appui long
    public void onTripLongClick(Trip trip, int position) {
        String[] options = {"Voir le détail", "Partager", "Supprimer"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(trip.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        onTripClick(trip, position);
                    } else if (which == 1) {
                        shareTrip(trip);
                    } else {
                        confirmDeleteTrip(trip, position);
                    }
                })
                .show();
    }

    private void shareTrip(Trip trip) {
        String text = "🗺 Voyage SmartTrip : " + trip.getTitle() + "\n"
                + "📅 Date : " + trip.getDate() + "\n"
                + "📍 " + trip.getGpsPoints().size() + " points GPS\n"
                + "⭐ " + trip.getPois().size() + " points d'intérêt\n"
                + "Enregistré avec SmartTrip — Carnet de voyage intelligent";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Partager le voyage"));
    }

    private void confirmDeleteTrip(Trip trip, int position) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Supprimer ce voyage ?")
                .setMessage("\"" + trip.getTitle() + "\" sera supprimé définitivement du cloud.")
                .setPositiveButton("Supprimer", (d, w) -> deleteTrip(trip, position))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void deleteTrip(Trip trip, int position) {
        ApiClient.getInstance().getApiService().deleteTrip(trip.getId())
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                voyages.remove(position);
                                adapter.notifyItemRemoved(position);
                                adapter.notifyItemRangeChanged(position, voyages.size());
                                Toast.makeText(TripsActivity.this,
                                        "Voyage supprimé ✓", Toast.LENGTH_SHORT).show();
                                if (voyages.isEmpty())
                                    tvEmpty.setVisibility(View.VISIBLE);
                            } else {
                                Toast.makeText(TripsActivity.this,
                                        "Erreur suppression (code " + response.code() + ")",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(TripsActivity.this,
                                "Erreur réseau : " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void loadTripsFromCloud() {
        loadGpsFromCloud();
        loadPoisFromCloud();
    }

    private void loadGpsFromCloud() {
        ApiClient.getInstance().getApiService().getUserGps("amin")
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null)
                            for (Map<String, Object> doc : response.body()) parseGpsDoc(doc);
                        onLoadingComplete();
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(TripsActivity.this,
                                "Erreur réseau GPS : " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        onLoadingComplete();
                    }
                });
    }

    private void loadPoisFromCloud() {
        ApiClient.getInstance().getApiService().getUserPois("amin")
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null)
                            for (Map<String, Object> doc : response.body()) parsePoiDoc(doc);
                        onLoadingComplete();
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        onLoadingComplete();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void parseGpsDoc(Map<String, Object> doc) {
        try {
            String tripId = (String) doc.get("trip_id");
            if (tripId == null) tripId = "trip_unknown";

            Map<String, Object> loc = (Map<String, Object>) doc.get("location");
            double lat = ((Number) loc.get("latitude")).doubleValue();
            double lng = ((Number) loc.get("longitude")).doubleValue();
            String recordedAt = (String) doc.get("recorded_at");
            long ts = parseTimestamp(recordedAt);

            if (!gpsByTrip.containsKey(tripId)) gpsByTrip.put(tripId, new ArrayList<>());
            gpsByTrip.get(tripId).add(new GpsPoint(lat, lng, ts));

            if (!dateByTrip.containsKey(tripId)) dateByTrip.put(tripId, formatDate(recordedAt));

            String name = (String) doc.get("trip_name");
            if (!nameByTrip.containsKey(tripId) && name != null && !name.isEmpty())
                nameByTrip.put(tripId, name);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void parsePoiDoc(Map<String, Object> doc) {
        try {
            String tripId = (String) doc.get("trip_id");
            if (tripId == null) tripId = "trip_unknown";

            String name    = (String) doc.get("name");
            String type    = (String) doc.get("type");
            int    rating  = ((Number) doc.get("rating")).intValue();
            String comment = (String) doc.get("comment");

            Map<String, Object> loc = (Map<String, Object>) doc.get("location");
            double lat = ((Number) loc.get("latitude")).doubleValue();
            double lng = ((Number) loc.get("longitude")).doubleValue();

            if (!poisByTrip.containsKey(tripId)) poisByTrip.put(tripId, new ArrayList<>());
            poisByTrip.get(tripId).add(new Poi(name, type, lat, lng, rating, comment, ""));

            String tripName = (String) doc.get("trip_name");
            if (!nameByTrip.containsKey(tripId) && tripName != null && !tripName.isEmpty())
                nameByTrip.put(tripId, tripName);

            String recordedAt = (String) doc.get("recorded_at");
            if (!poiDateByTrip.containsKey(tripId) && recordedAt != null)
                poiDateByTrip.put(tripId, formatDate(recordedAt));
        } catch (Exception ignored) {}
    }

    private void onLoadingComplete() {
        loadingsRemaining--;
        if (loadingsRemaining <= 0) buildTripsList();
    }

    private void buildTripsList() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            voyages.clear();

            Set<String> allIds = new HashSet<>();
            allIds.addAll(gpsByTrip.keySet());
            allIds.addAll(poisByTrip.keySet());

            for (String tripId : allIds) {
                List<GpsPoint> points = gpsByTrip.containsKey(tripId)
                        ? gpsByTrip.get(tripId) : new ArrayList<>();
                List<Poi> pois = poisByTrip.containsKey(tripId)
                        ? poisByTrip.get(tripId) : new ArrayList<>();

                if (points.isEmpty() && pois.isEmpty()) continue;

                String date = dateByTrip.containsKey(tripId)
                        ? dateByTrip.get(tripId)
                        : poiDateByTrip.getOrDefault(tripId, "Date inconnue");

                String name = nameByTrip.containsKey(tripId)
                        ? nameByTrip.get(tripId)
                        : "Voyage du " + date;

                voyages.add(new Trip(tripId, name, date, "Voyage cloud", false, points, pois));
            }

            Collections.sort(voyages, (a, b) -> b.getDate().compareTo(a.getDate()));
            tvEmpty.setVisibility(voyages.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        });
    }

    private long parseTimestamp(String iso) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                    .parse(iso).getTime() / 1000;
        } catch (Exception e) { return System.currentTimeMillis() / 1000; }
    }

    private String formatDate(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(d);
        } catch (Exception e) { return "Date inconnue"; }
    }
}