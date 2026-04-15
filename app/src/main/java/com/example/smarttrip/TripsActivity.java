package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Écran d'historique des voyages — données récupérées depuis MongoDB.
 *
 * Logique :
 * 1. Au démarrage, fait 3 appels parallèles : GET /gps/amin, /pois/amin, /photos/amin
 * 2. Groupe les éléments par trip_id
 * 3. Construit une liste de Trip avec leurs points/POI/photos
 * 4. Affiche dans le RecyclerView
 */
public class TripsActivity extends AppCompatActivity implements TripAdapter.OnTripClickListener {

    private RecyclerView recyclerTrips;
    private TripAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private List<Trip> voyages = new ArrayList<>();

    // Maps temporaires pour grouper par trip_id
    private Map<String, List<GpsPoint>> gpsByTrip = new HashMap<>();
    private Map<String, List<Poi>> poisByTrip = new HashMap<>();
    private Map<String, String> dateByTrip = new HashMap<>();

    private int loadingsRemaining = 2; // GPS + POI

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        recyclerTrips = findViewById(R.id.recyclerTrips);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        recyclerTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(voyages, this);
        recyclerTrips.setAdapter(adapter);

        loadTripsFromCloud();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recharger à chaque fois qu'on revient sur cet écran
        // (ex: après avoir terminé un voyage)
        gpsByTrip.clear();
        poisByTrip.clear();
        dateByTrip.clear();
        voyages.clear();
        adapter.notifyDataSetChanged();
        loadingsRemaining = 2;
        progressBar.setVisibility(android.view.View.VISIBLE);
        tvEmpty.setVisibility(android.view.View.GONE);
        loadTripsFromCloud();
    }

    @Override
    public void onTripClick(Trip trip, int position) {
        Intent intent = new Intent(TripsActivity.this, TripDetailsActivity.class);
        intent.putExtra("trip", trip);
        startActivity(intent);
    }

    /**
     * Lance les 2 appels parallèles GPS + POI.
     */
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
                        if (response.isSuccessful() && response.body() != null) {
                            for (Map<String, Object> doc : response.body()) {
                                parseGpsDoc(doc);
                            }
                        }
                        onLoadingComplete();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(TripsActivity.this,
                                "Erreur réseau GPS : " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
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
                        if (response.isSuccessful() && response.body() != null) {
                            for (Map<String, Object> doc : response.body()) {
                                parsePoiDoc(doc);
                            }
                        }
                        onLoadingComplete();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        onLoadingComplete();
                    }
                });
    }

    /**
     * Parse un document GPS de MongoDB et l'ajoute dans la map groupée.
     */
    @SuppressWarnings("unchecked")
    private void parseGpsDoc(Map<String, Object> doc) {
        try {
            String tripId = (String) doc.get("trip_id");
            if (tripId == null) tripId = "trip_unknown"; // fallback pour anciens points sans trip_id

            Map<String, Object> location = (Map<String, Object>) doc.get("location");
            double lat = ((Number) location.get("latitude")).doubleValue();
            double lng = ((Number) location.get("longitude")).doubleValue();

            String recordedAt = (String) doc.get("recorded_at");
            long timestamp = parseTimestamp(recordedAt);

            GpsPoint point = new GpsPoint(lat, lng, timestamp);

            if (!gpsByTrip.containsKey(tripId)) {
                gpsByTrip.put(tripId, new ArrayList<>());
            }
            gpsByTrip.get(tripId).add(point);

            // Stocker la date du voyage (date du premier point)
            if (!dateByTrip.containsKey(tripId)) {
                dateByTrip.put(tripId, formatDate(recordedAt));
            }
        } catch (Exception e) {
            // Ignorer les documents malformés
        }
    }

    /**
     * Parse un document POI de MongoDB.
     */
    @SuppressWarnings("unchecked")
    private void parsePoiDoc(Map<String, Object> doc) {
        try {
            String tripId = (String) doc.get("trip_id");
            if (tripId == null) tripId = "trip_unknown";

            String name = (String) doc.get("name");
            String type = (String) doc.get("type");
            int rating = ((Number) doc.get("rating")).intValue();
            String comment = (String) doc.get("comment");

            Map<String, Object> location = (Map<String, Object>) doc.get("location");
            double lat = ((Number) location.get("latitude")).doubleValue();
            double lng = ((Number) location.get("longitude")).doubleValue();

            Poi poi = new Poi(name, type, lat, lng, rating, comment, "");

            if (!poisByTrip.containsKey(tripId)) {
                poisByTrip.put(tripId, new ArrayList<>());
            }
            poisByTrip.get(tripId).add(poi);
        } catch (Exception e) {
            // Ignorer les documents malformés
        }
    }

    /**
     * Appelé après chaque chargement (GPS ou POI).
     * Quand les 2 sont terminés, on construit la liste finale.
     */
    private void onLoadingComplete() {
        loadingsRemaining--;
        if (loadingsRemaining <= 0) {
            buildTripsList();
        }
    }

    /**
     * Construit la liste finale de Trip à partir des maps groupées.
     */
    private void buildTripsList() {
        progressBar.setVisibility(android.view.View.GONE);
        voyages.clear();

        // Tous les trip_ids (GPS + POI)
        java.util.Set<String> allTripIds = new java.util.HashSet<>();
        allTripIds.addAll(gpsByTrip.keySet());
        allTripIds.addAll(poisByTrip.keySet());

        for (String tripId : allTripIds) {
            List<GpsPoint> points = gpsByTrip.getOrDefault(tripId, new ArrayList<>());
            List<Poi> pois = poisByTrip.getOrDefault(tripId, new ArrayList<>());
            String date = dateByTrip.getOrDefault(tripId, "Date inconnue");

            // Nom du voyage : "Voyage du JJ/MM/AAAA"
            String name = "Voyage du " + date;

            Trip trip = new Trip(tripId, name, date, "Voyage cloud", false, points, pois);
            voyages.add(trip);
        }

        // Tri : voyages les plus récents en premier
        Collections.sort(voyages, (a, b) -> b.getDate().compareTo(a.getDate()));

        if (voyages.isEmpty()) {
            tvEmpty.setVisibility(android.view.View.VISIBLE);
        } else {
            tvEmpty.setVisibility(android.view.View.GONE);
        }

        adapter.notifyDataSetChanged();
    }

    /**
     * Convertit un timestamp ISO 8601 en long (secondes Unix).
     */
    private long parseTimestamp(String iso) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE);
            return sdf.parse(iso).getTime() / 1000;
        } catch (Exception e) {
            return System.currentTimeMillis() / 1000;
        }
    }

    /**
     * Convertit un timestamp ISO en date lisible JJ/MM/AAAA.
     */
    private String formatDate(String iso) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE);
            SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);
            Date date = input.parse(iso);
            return output.format(date);
        } catch (Exception e) {
            return "Date inconnue";
        }
    }
}