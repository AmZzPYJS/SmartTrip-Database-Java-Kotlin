package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "TripsActivity";
    private static final String USER_ID = "amin";

    private TripAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    private final List<Trip> voyages = new ArrayList<>();

    private final Map<String, List<GpsPoint>> gpsByTrip = new HashMap<>();
    private final Map<String, List<Poi>> poisByTrip = new HashMap<>();

    private final Map<String, String> nameByTrip = new HashMap<>();
    private final Map<String, String> dateByTrip = new HashMap<>();
    private final Map<String, String> poiDateByTrip = new HashMap<>();
    private final Map<String, String> photoDateByTrip = new HashMap<>();

    private final Set<String> seenPoiKeys = new HashSet<>();

    // GPS + POI + Photos
    private int loadingsRemaining = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        RecyclerView recyclerTrips = findViewById(R.id.recyclerTrips);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

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
        photoDateByTrip.clear();
        seenPoiKeys.clear();

        voyages.clear();
        adapter.notifyDataSetChanged();

        loadingsRemaining = 3;

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        loadTripsFromCloud();
    }

    @Override
    public void onTripClick(Trip trip, int position) {
        Intent intent = new Intent(this, TripDetailsActivity.class);
        intent.putExtra("trip", trip);
        startActivity(intent);
    }

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
                .setPositiveButton("Supprimer", (dialog, which) -> deleteTrip(trip, position))
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

                                Toast.makeText(
                                        TripsActivity.this,
                                        "Voyage supprimé ✓",
                                        Toast.LENGTH_SHORT
                                ).show();

                                if (voyages.isEmpty()) {
                                    tvEmpty.setVisibility(View.VISIBLE);
                                }
                            } else {
                                Toast.makeText(
                                        TripsActivity.this,
                                        "Erreur suppression code " + response.code(),
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(
                                TripsActivity.this,
                                "Erreur réseau : " + t.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show());
                    }
                });
    }

    private void loadTripsFromCloud() {
        loadGpsFromCloud();
        loadPoisFromCloud();
        loadPhotosFromCloud();
    }

    private void loadGpsFromCloud() {
        ApiClient.getInstance().getApiService().getUserGps(USER_ID)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "GPS reçus : " + response.body().size());

                            for (Map<String, Object> doc : response.body()) {
                                parseGpsDoc(doc);
                            }
                        } else {
                            Log.e(TAG, "Erreur HTTP GPS : " + response.code());
                        }

                        onLoadingComplete();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Log.e(TAG, "Erreur GPS", t);

                        Toast.makeText(
                                TripsActivity.this,
                                "Erreur GPS : " + t.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();

                        onLoadingComplete();
                    }
                });
    }

    private void loadPoisFromCloud() {
        ApiClient.getInstance().getApiService().getUserPois(USER_ID)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "POI reçus : " + response.body().size());

                            for (Map<String, Object> doc : response.body()) {
                                parsePoiDoc(doc);
                            }
                        } else {
                            Log.e(TAG, "Erreur HTTP POI : " + response.code());
                        }

                        onLoadingComplete();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Log.e(TAG, "Erreur POI", t);

                        Toast.makeText(
                                TripsActivity.this,
                                "Erreur POI : " + t.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();

                        onLoadingComplete();
                    }
                });
    }

    private void loadPhotosFromCloud() {
        ApiClient.getInstance().getApiService().getUserPhotos(USER_ID)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "Photos reçues : " + response.body().size());

                            for (Map<String, Object> doc : response.body()) {
                                parsePhotoDocForTripMetadata(doc);
                            }
                        } else {
                            Log.e(TAG, "Erreur HTTP Photos : " + response.code());
                        }

                        onLoadingComplete();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Log.e(TAG, "Erreur Photos", t);

                        Toast.makeText(
                                TripsActivity.this,
                                "Erreur photos : " + t.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();

                        onLoadingComplete();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void parseGpsDoc(Map<String, Object> doc) {
        try {
            String tripId = getString(doc, "trip_id", "trip_unknown");

            Map<String, Object> loc = (Map<String, Object>) doc.get("location");
            if (loc == null) return;

            double lat = getDouble(loc, "latitude", 0.0);
            double lng = getDouble(loc, "longitude", 0.0);

            if (lat == 0.0 && lng == 0.0) return;

            String recordedAt = getString(doc, "recorded_at", null);
            long timestamp = parseTimestamp(recordedAt);

            if (!gpsByTrip.containsKey(tripId)) {
                gpsByTrip.put(tripId, new ArrayList<>());
            }

            gpsByTrip.get(tripId).add(new GpsPoint(lat, lng, timestamp));

            if (!dateByTrip.containsKey(tripId)) {
                dateByTrip.put(tripId, formatDate(recordedAt));
            }

            String tripName = getString(doc, "trip_name", "");
            if (!nameByTrip.containsKey(tripId) && !tripName.isEmpty()) {
                nameByTrip.put(tripId, tripName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur parse GPS doc : " + doc, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parsePoiDoc(Map<String, Object> doc) {
        try {
            String tripId = getString(doc, "trip_id", "trip_unknown");

            String poiId = getString(doc, "poi_id", "");
            if (poiId.isEmpty()) {
                poiId = "poi_" + Math.abs(doc.toString().hashCode());
            }

            String name = getString(doc, "name", "POI sans nom");
            String type = getString(doc, "type", "autre");
            int rating = getInt(doc, "rating", 0);
            String comment = getString(doc, "comment", "");

            Map<String, Object> loc = (Map<String, Object>) doc.get("location");
            if (loc == null) return;

            double lat = getDouble(loc, "latitude", 0.0);
            double lng = getDouble(loc, "longitude", 0.0);

            if (lat == 0.0 && lng == 0.0) return;

            String dedupeKey = tripId + "|" + poiId + "|" + name + "|"
                    + String.format(Locale.US, "%.5f", lat) + "|"
                    + String.format(Locale.US, "%.5f", lng);

            if (seenPoiKeys.contains(dedupeKey)) {
                return;
            }

            seenPoiKeys.add(dedupeKey);

            String photoBase64 = getString(doc, "photo_base64", "");

            if (!poisByTrip.containsKey(tripId)) {
                poisByTrip.put(tripId, new ArrayList<>());
            }

            Poi poi = new Poi(
                    poiId,
                    name,
                    type,
                    lat,
                    lng,
                    rating,
                    comment,
                    ""
            );

            if (!photoBase64.isEmpty()) {
                poi.setPhotoBase64(photoBase64);
            }

            poisByTrip.get(tripId).add(poi);

            String tripName = getString(doc, "trip_name", "");
            if (!nameByTrip.containsKey(tripId) && !tripName.isEmpty()) {
                nameByTrip.put(tripId, tripName);
            }

            String recordedAt = getString(doc, "recorded_at", null);
            if (!poiDateByTrip.containsKey(tripId) && recordedAt != null) {
                poiDateByTrip.put(tripId, formatDate(recordedAt));
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur parse POI doc : " + doc, e);
        }
    }

    private void parsePhotoDocForTripMetadata(Map<String, Object> doc) {
        try {
            String tripId = getString(doc, "trip_id", "trip_unknown");

            String tripName = getString(doc, "trip_name", "");
            if (!nameByTrip.containsKey(tripId) && !tripName.isEmpty()) {
                nameByTrip.put(tripId, tripName);
            }

            String recordedAt = getString(doc, "recorded_at", null);
            if (!photoDateByTrip.containsKey(tripId) && recordedAt != null) {
                photoDateByTrip.put(tripId, formatDate(recordedAt));
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur parse Photo metadata : " + doc, e);
        }
    }

    private void onLoadingComplete() {
        loadingsRemaining--;

        if (loadingsRemaining <= 0) {
            buildTripsList();
        }
    }

    private void buildTripsList() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            voyages.clear();

            Set<String> allIds = new HashSet<>();
            allIds.addAll(gpsByTrip.keySet());
            allIds.addAll(poisByTrip.keySet());
            allIds.addAll(photoDateByTrip.keySet());

            for (String tripId : allIds) {
                List<GpsPoint> points = gpsByTrip.containsKey(tripId)
                        ? gpsByTrip.get(tripId)
                        : new ArrayList<>();

                List<Poi> pois = poisByTrip.containsKey(tripId)
                        ? poisByTrip.get(tripId)
                        : new ArrayList<>();

                String date;

                if (dateByTrip.containsKey(tripId)) {
                    date = dateByTrip.get(tripId);
                } else if (poiDateByTrip.containsKey(tripId)) {
                    date = poiDateByTrip.get(tripId);
                } else {
                    date = photoDateByTrip.getOrDefault(tripId, "Date inconnue");
                }

                String name = nameByTrip.containsKey(tripId)
                        ? nameByTrip.get(tripId)
                        : "Voyage du " + date;

                voyages.add(new Trip(
                        tripId,
                        name,
                        date,
                        "Voyage cloud",
                        false,
                        points,
                        pois
                ));
            }

            Collections.sort(voyages, (a, b) -> b.getId().compareTo(a.getId()));

            tvEmpty.setVisibility(voyages.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();

            Log.d(TAG, "Voyages construits : " + voyages.size());
        });
    }

    private long parseTimestamp(String iso) {
        try {
            if (iso == null || iso.isEmpty()) {
                return System.currentTimeMillis() / 1000;
            }

            Date date = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.FRANCE
            ).parse(iso);

            if (date == null) {
                return System.currentTimeMillis() / 1000;
            }

            return date.getTime() / 1000;

        } catch (Exception e) {
            return System.currentTimeMillis() / 1000;
        }
    }

    private String formatDate(String iso) {
        try {
            if (iso == null || iso.isEmpty()) {
                return "Date inconnue";
            }

            Date date = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.FRANCE
            ).parse(iso);

            if (date == null) {
                return "Date inconnue";
            }

            return new SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.FRANCE
            ).format(date);

        } catch (Exception e) {
            return "Date inconnue";
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);

        if (value == null) {
            return defaultValue;
        }

        return String.valueOf(value);
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}