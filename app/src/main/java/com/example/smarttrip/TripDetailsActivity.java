package com.example.smarttrip;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.example.smarttrip.api.ApiClient;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Écran de détail d'un voyage.
 *
 * Affiche les informations complètes :
 * - Titre, date, description
 * - Statistiques (nb points GPS, nb POI, distance totale)
 * - Liste des POI avec note et commentaire
 * - Carte OSMDroid : tracé bleu GPS + marqueurs rouges POI
 * - Statut batterie au moment de la consultation
 */
public class TripDetailsActivity extends AppCompatActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuration OSMDroid — à faire avant setContentView
        Configuration.getInstance().setUserAgentValue(getPackageName());
        // Stockage interne (pas besoin de WRITE_EXTERNAL_STORAGE)
        Configuration.getInstance().setOsmdroidBasePath(getFilesDir());
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid"));

        setContentView(R.layout.activity_trip_details);

        Trip trip = (Trip) getIntent().getSerializableExtra("trip");
        if (trip == null) {
            finish();
            return;
        }

        // --- Liaison des vues ---
        TextView tvTripName        = findViewById(R.id.tvTripName);
        TextView tvTripDate        = findViewById(R.id.tvTripDate);
        TextView tvTripDescription = findViewById(R.id.tvTripDescription);
        TextView tvStats           = findViewById(R.id.tvStats);
        LinearLayout layoutPois    = findViewById(R.id.layoutPois);
        TextView tvBattery         = findViewById(R.id.tvBatteryDetail);

        // --- Remplir les données ---
        tvTripName.setText(trip.getTitle());
        tvTripDate.setText(trip.getDate());
        tvTripDescription.setText(trip.getDescription());

        int nbGps = trip.getGpsPoints().size();
        int nbPoi = trip.getPois().size();
        double distanceKm = calculateTotalDistance(trip) / 1000.0;
        tvStats.setText(nbGps + " points GPS • " + nbPoi + " POI • "
                + String.format("%.1f", distanceKm) + " km parcourus");

        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }

        // --- Marqueurs violets pour les photos souvenirs ---
        loadPhotosForMap(trip);

        tvBattery.setText(BatteryHelper.getStatusMessage(this));

        // --- Carte ---
        setupMap(trip);
        loadAndDisplayPhotos(trip);
    }

    // -------------------------------------------------------------------------
    // Carte OSMDroid
    // -------------------------------------------------------------------------

    private void setupMap(Trip trip) {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);

        // Déléguer les événements tactiles à la carte même dans un ScrollView
        mapView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        List<GpsPoint> gpsPoints = trip.getGpsPoints();
        List<Poi> pois = trip.getPois();

        // --- Tracé GPS (ligne bleue) ---
        if (gpsPoints.size() >= 2) {
            List<GeoPoint> routePoints = new ArrayList<>();
            for (GpsPoint p : gpsPoints) {
                routePoints.add(new GeoPoint(p.getLat(), p.getLng()));
            }
            Polyline polyline = new Polyline(mapView);
            polyline.setPoints(routePoints);
            polyline.getOutlinePaint().setColor(0xFF1D4ED8); // bleu
            polyline.getOutlinePaint().setStrokeWidth(8f);
            mapView.getOverlays().add(polyline);
        }

        // --- Marqueurs rouges pour les POI ---
        BitmapDrawable redIcon = createRedMarker();
        for (Poi poi : pois) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(poi.getLat(), poi.getLng()));
            marker.setTitle(poi.getName());
            marker.setSnippet(poi.getType() + " • " + poi.getRatingStars());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setIcon(redIcon);
            mapView.getOverlays().add(marker);
        }

        // --- Zoom automatique pour englober tous les points ---
        List<GeoPoint> allPoints = new ArrayList<>();
        for (GpsPoint p : gpsPoints) {
            allPoints.add(new GeoPoint(p.getLat(), p.getLng()));
        }
        for (Poi poi : pois) {
            allPoints.add(new GeoPoint(poi.getLat(), poi.getLng()));
        }

        if (allPoints.isEmpty()) {
            // Aucun point : vue par défaut sur Paris
            mapView.getController().setZoom(10.0);
            mapView.getController().setCenter(new GeoPoint(48.8566, 2.3522));
        } else if (allPoints.size() == 1) {
            mapView.getController().setZoom(15.0);
            mapView.getController().setCenter(allPoints.get(0));
        } else {
            // Centrer sur le premier point GPS, puis zoomer pour tout voir
            if (!gpsPoints.isEmpty()) {
                mapView.getController().setCenter(
                        new GeoPoint(gpsPoints.get(0).getLat(), gpsPoints.get(0).getLng()));
            }
            BoundingBox bbox = BoundingBox.fromGeoPoints(allPoints);
            mapView.post(() -> mapView.zoomToBoundingBox(bbox, true, 80));
        }
    }

    /**
     * Crée un marqueur violet pour les photos souvenirs.
     * Différent du rouge des POI pour les distinguer sur la carte.
     */
    private BitmapDrawable createPurpleMarker() {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(40, 40,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        paint.setColor(0xFF6C3AED); // violet SmartTrip
        canvas.drawCircle(20, 20, 18, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(20f);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText("📸", 20, 28, paint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    /**
     * Charge les photos du voyage et les affiche comme marqueurs sur la carte.
     */
    private void loadPhotosForMap(Trip trip) {
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                           retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BitmapDrawable purpleIcon = createPurpleMarker();
                            for (java.util.Map<String, Object> doc : response.body()) {
                                String docTripId = (String) doc.get("trip_id");
                                if (!trip.getId().equals(docTripId)) continue;

                                // Récupérer les coordonnées GPS de la photo
                                try {
                                    java.util.Map<String, Object> location =
                                            (java.util.Map<String, Object>) doc.get("location");
                                    if (location == null) continue;
                                    double lat = ((Number) location.get("latitude")).doubleValue();
                                    double lng = ((Number) location.get("longitude")).doubleValue();
                                    String recordedAt = (String) doc.get("recorded_at");

                                    runOnUiThread(() -> {
                                        Marker marker = new Marker(mapView);
                                        marker.setPosition(new GeoPoint(lat, lng));
                                        marker.setTitle("📸 Photo souvenir");
                                        marker.setSnippet(recordedAt != null ? recordedAt : "");
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                                        marker.setIcon(purpleIcon);
                                        mapView.getOverlays().add(marker);
                                        mapView.invalidate();
                                    });
                                } catch (Exception e) {
                                    // Document mal formé, ignorer
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                          Throwable t) {
                        // Photos non disponibles pour la carte
                    }
                });
    }

    /**
     * Charge les photos du voyage depuis le cloud et les affiche.
     */
    private void loadAndDisplayPhotos(Trip trip) {
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                           retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // Filtrer par trip_id
                            for (java.util.Map<String, Object> doc : response.body()) {
                                String docTripId = (String) doc.get("trip_id");
                                if (trip.getId().equals(docTripId)) {
                                    String base64 = (String) doc.get("photo_base64");
                                    if (base64 != null) {
                                        runOnUiThread(() -> addPhotoToGallery(base64));
                                    }
                                }
                            }
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                          Throwable t) {
                        // Photos non disponibles, pas bloquant
                    }
                });
    }

    /**
     * Ajoute une photo en miniature dans la galerie horizontale.
     */
    private void addPhotoToGallery(String base64) {
        LinearLayout galleryLayout = (LinearLayout) findViewById(R.id.layoutPhotoGallery);
        if (galleryLayout == null) return;

        try {
            byte[] decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    decodedBytes, 0, decodedBytes.length);

            ImageView imgView = new ImageView(this);
            int size = (int) (120 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, 16, 0);
            imgView.setLayoutParams(params);
            imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgView.setImageBitmap(bitmap);
            imgView.setOnClickListener(v -> showFullPhoto(bitmap));
            galleryLayout.addView(imgView);

            // Rendre visible le conteneur
            HorizontalScrollView scrollView = findViewById(R.id.scrollPhotoGallery);
            if (scrollView != null) scrollView.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            // Image corrompue, ignorer
        }
    }

    /**
     * Affiche une photo en plein écran.
     */
    private void showFullPhoto(android.graphics.Bitmap bitmap) {
        ImageView fullImg = new ImageView(this);
        fullImg.setImageBitmap(bitmap);
        fullImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImg.setPadding(16, 16, 16, 16);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(fullImg)
                .setPositiveButton("Fermer", null)
                .show();
    }

    /**
     * Crée un marqueur circulaire rouge de 32 dp (sans ressource externe).
     */
    private BitmapDrawable createRedMarker() {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(32 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFDC2626); // rouge-600
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, fill);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(size / 6f);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - size / 12f, border);

        return new BitmapDrawable(getResources(), bitmap);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    // -------------------------------------------------------------------------
    // Calculs et vues dynamiques
    // -------------------------------------------------------------------------

    /**
     * Distance totale du parcours GPS en mètres (formule Haversine).
     */
    private double calculateTotalDistance(Trip trip) {
        double total = 0;
        for (int i = 1; i < trip.getGpsPoints().size(); i++) {
            total += trip.getGpsPoints().get(i - 1).distanceTo(trip.getGpsPoints().get(i));
        }
        return total;
    }

    /**
     * Crée dynamiquement une carte POI et l'ajoute au layout.
     */
    private void addPoiView(LinearLayout container, Poi poi) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 24, 32, 24);
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setElevation(2f);

        TextView tvName = new TextView(this);
        tvName.setText(poi.getName() + "  (" + poi.getType() + ")");
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF111827);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvName);

        TextView tvRating = new TextView(this);
        tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14);
        tvRating.setTextColor(0xFFD97706); // amber
        tvRating.setPadding(0, 8, 0, 4);
        card.addView(tvRating);

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this);
            tvComment.setText(poi.getComment());
            tvComment.setTextSize(14);
            tvComment.setTextColor(0xFF6B7280);
            tvComment.setPadding(0, 4, 0, 0);
            card.addView(tvComment);
        }

        TextView tvCoords = new TextView(this);
        tvCoords.setText("GPS : " + poi.getLat() + ", " + poi.getLng());
        tvCoords.setTextSize(11);
        tvCoords.setTextColor(0xFF9CA3AF);
        tvCoords.setPadding(0, 8, 0, 0);
        card.addView(tvCoords);

        container.addView(card);
    }
}
