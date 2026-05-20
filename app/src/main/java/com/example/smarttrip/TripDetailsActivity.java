package com.example.smarttrip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smarttrip.api.ApiClient;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TripDetailsActivity extends AppCompatActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid init — load() avant setUserAgentValue() obligatoire
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getFilesDir());
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid"));

        setContentView(R.layout.activity_trip_details);

        Trip trip = (Trip) getIntent().getSerializableExtra("trip");
        if (trip == null) { finish(); return; }

        TextView tvTripName        = findViewById(R.id.tvTripName);
        TextView tvTripDate        = findViewById(R.id.tvTripDate);
        TextView tvTripDescription = findViewById(R.id.tvTripDescription);
        TextView tvStats           = findViewById(R.id.tvStats);
        LinearLayout layoutPois    = findViewById(R.id.layoutPois);
        TextView tvBattery         = findViewById(R.id.tvBatteryDetail);

        tvTripName.setText(trip.getTitle());
        tvTripDate.setText(trip.getDate());
        tvTripDescription.setText(trip.getDescription());

        int nbGps = trip.getGpsPoints().size();
        int nbPoi = trip.getPois().size();
        double distanceKm = calculateTotalDistance(trip) / 1000.0;
        tvStats.setText(nbGps + " points GPS • " + nbPoi + " POI • "
                + String.format("%.1f", distanceKm) + " km parcourus");

        // ── Affichage des POI avec photo miniature si disponible ──────────────
        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }
        // ──────────────────────────────────────────────────────────────────────

        tvBattery.setText(BatteryHelper.getStatusMessage(this));

        setupMap(trip);
        loadPhotosForMap(trip);
        loadAndDisplayPhotos(trip);
    }

    // =========================================================================
    // Carte
    // =========================================================================

    private void setupMap(Trip trip) {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);

        mapView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false); break;
            }
            return false;
        });

        List<GpsPoint> gpsPoints = trip.getGpsPoints();
        List<Poi> pois = trip.getPois();

        // Tri chronologique — évite les croisements de tracé
        Collections.sort(gpsPoints, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        // Tracé violet SmartTrip
        if (gpsPoints.size() >= 2) {
            List<GeoPoint> routePoints = new ArrayList<>();
            for (GpsPoint p : gpsPoints) {
                routePoints.add(new GeoPoint(p.getLat(), p.getLng()));
            }
            Polyline polyline = new Polyline(mapView);
            polyline.setPoints(routePoints);
            polyline.getOutlinePaint().setColor(Color.parseColor("#6C63FF"));
            polyline.getOutlinePaint().setStrokeWidth(8f);
            mapView.getOverlays().add(polyline);
        }

        // Marqueurs POI rouges
        for (Poi poi : pois) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(poi.getLat(), poi.getLng()));
            marker.setTitle(poi.getName());
            marker.setSnippet(poi.getType() + " • " + poi.getRatingStars()
                    + (poi.getComment() != null && !poi.getComment().isEmpty()
                    ? "\n" + poi.getComment() : ""));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(createPoiMarker());
            marker.setOnMarkerClickListener((m, map) -> { m.showInfoWindow(); return true; });
            mapView.getOverlays().add(marker);
        }

        // Zoom automatique
        List<GeoPoint> allPoints = new ArrayList<>();
        for (GpsPoint p : gpsPoints) allPoints.add(new GeoPoint(p.getLat(), p.getLng()));
        for (Poi poi : pois) allPoints.add(new GeoPoint(poi.getLat(), poi.getLng()));

        if (allPoints.isEmpty()) {
            mapView.getController().setZoom(10.0);
            mapView.getController().setCenter(new GeoPoint(48.8566, 2.3522));
        } else if (allPoints.size() == 1) {
            mapView.getController().setZoom(18.0);
            mapView.getController().setCenter(allPoints.get(0));
        } else {
            if (!gpsPoints.isEmpty())
                mapView.getController().setCenter(
                        new GeoPoint(gpsPoints.get(0).getLat(), gpsPoints.get(0).getLng()));
            BoundingBox bbox = BoundingBox.fromGeoPoints(allPoints);
            mapView.post(() -> mapView.zoomToBoundingBox(bbox, true, 80));
        }
    }

    // =========================================================================
    // Marqueurs
    // =========================================================================

    private BitmapDrawable createPoiMarker() {
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(44 * d);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFDC2626);
        canvas.drawCircle(size / 2f, size / 3f, size / 3f, fill);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(size / 2f - size / 4f, size / 2f);
        path.lineTo(size / 2f + size / 4f, size / 2f);
        path.lineTo(size / 2f, size * 0.85f);
        path.close();
        canvas.drawPath(path, fill);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 3f, size / 8f, white);
        return new BitmapDrawable(getResources(), bmp);
    }

    private BitmapDrawable createPhotoMarkerIcon(Bitmap photo) {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(56 * density);
        int border = Math.round(4 * density);
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBg.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, size, size, 8 * density, 8 * density, paintBg);
        Bitmap scaled = Bitmap.createScaledBitmap(photo, size - border * 2, size - border * 2, true);
        canvas.drawBitmap(scaled, border, border, null);
        return new BitmapDrawable(getResources(), result);
    }

    // =========================================================================
    // Photos cloud → galerie paysage + marqueurs carte
    // =========================================================================

    private void loadPhotosForMap(Trip trip) {
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                           retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        for (java.util.Map<String, Object> doc : response.body()) {
                            String docTripId = (String) doc.get("trip_id");
                            if (!trip.getId().equals(docTripId)) continue;
                            try {
                                java.util.Map<String, Object> location =
                                        (java.util.Map<String, Object>) doc.get("location");
                                if (location == null) continue;
                                double lat = ((Number) location.get("latitude")).doubleValue();
                                double lng = ((Number) location.get("longitude")).doubleValue();
                                String base64 = (String) doc.get("photo_base64");
                                String recordedAt = (String) doc.get("recorded_at");

                                runOnUiThread(() -> {
                                    Marker marker = new Marker(mapView);
                                    marker.setPosition(new GeoPoint(lat, lng));
                                    marker.setTitle("📸 Photo souvenir");
                                    marker.setSnippet(recordedAt != null ? recordedAt : "");
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                                    if (base64 != null) {
                                        try {
                                            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                            marker.setIcon(createPhotoMarkerIcon(bmp));
                                        } catch (Exception e) { marker.setIcon(createPoiMarker()); }
                                    } else { marker.setIcon(createPoiMarker()); }
                                    marker.setOnMarkerClickListener((m, map) -> {
                                        if (base64 != null) {
                                            try {
                                                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                                showFullPhoto(bmp);
                                            } catch (Exception ignored) {}
                                        }
                                        return true;
                                    });
                                    mapView.getOverlays().add(marker);
                                    mapView.invalidate();
                                });
                            } catch (Exception ignored) {}
                        }
                    }
                    @Override public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) {}
                });
    }

    private void loadAndDisplayPhotos(Trip trip) {
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                                           retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        for (java.util.Map<String, Object> doc : response.body()) {
                            String docTripId = (String) doc.get("trip_id");
                            if (trip.getId().equals(docTripId)) {
                                String base64 = (String) doc.get("photo_base64");
                                if (base64 != null) runOnUiThread(() -> addPhotoToGallery(base64));
                            }
                        }
                    }
                    @Override public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) {}
                });
    }

    /**
     * Galerie horizontale — format PAYSAGE carte postale (200×140dp, centerCrop).
     * Les marqueurs photo sur la carte sont des miniatures carrées distinctes.
     */
    private void addPhotoToGallery(String base64) {
        LinearLayout galleryLayout = findViewById(R.id.layoutPhotoGallery);
        if (galleryLayout == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            float density = getResources().getDisplayMetrics().density;
            int width  = Math.round(200 * density); // largeur paysage
            int height = Math.round(130 * density); // hauteur fixe → format carte postale

            ImageView img = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMargins(0, 0, Math.round(10 * density), 0);
            img.setLayoutParams(params);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(bitmap);
            img.setClipToOutline(true);
            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8 * density);
                }
            });
            img.setOnClickListener(v -> showFullPhoto(bitmap));
            galleryLayout.addView(img);

            HorizontalScrollView sv = findViewById(R.id.scrollPhotoGallery);
            if (sv != null) sv.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    private void showFullPhoto(Bitmap bitmap) {
        ImageView fullImg = new ImageView(this);
        fullImg.setImageBitmap(bitmap);
        fullImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImg.setPadding(16, 16, 16, 16);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(fullImg)
                .setPositiveButton("Fermer", null)
                .show();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    // =========================================================================
    // Calculs
    // =========================================================================

    private double calculateTotalDistance(Trip trip) {
        double total = 0;
        List<GpsPoint> pts = trip.getGpsPoints();
        for (int i = 1; i < pts.size(); i++) total += pts.get(i - 1).distanceTo(pts.get(i));
        return total;
    }

    /**
     * Card POI — dark mode + photo miniature paysage si disponible.
     *
     * ── FIX PHOTO POI ─────────────────────────────────────────────────────────
     * Le champ poi.getPhotoBase64() est maintenant lu et affiché comme
     * ImageView 160×100dp en format paysage sous le commentaire.
     * Si pas de photo → rien affiché (pas d'espace vide).
     * ──────────────────────────────────────────────────────────────────────────
     */
    private void addPoiView(LinearLayout container, Poi poi) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Math.round(16*d), Math.round(16*d), Math.round(16*d), Math.round(16*d));
        card.setBackgroundColor(0xFF1E1E35);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, Math.round(12*d));
        card.setLayoutParams(cardParams);

        // Ligne nom + type
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvName = new TextView(this);
        tvName.setText(poi.getName());
        tvName.setTextSize(16);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(tvName);

        TextView tvType = new TextView(this);
        tvType.setText(poi.getType());
        tvType.setTextSize(11);
        tvType.setTextColor(0xFFA89CFF);
        tvType.setPadding(Math.round(8*d), Math.round(2*d), Math.round(8*d), Math.round(2*d));
        tvType.setBackgroundColor(0xFF2D2D50);
        headerRow.addView(tvType);
        card.addView(headerRow);

        // Étoiles
        TextView tvRating = new TextView(this);
        tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14);
        tvRating.setTextColor(0xFFD97706);
        tvRating.setPadding(0, Math.round(6*d), 0, Math.round(2*d));
        card.addView(tvRating);

        // Commentaire
        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this);
            tvComment.setText(poi.getComment());
            tvComment.setTextSize(13);
            tvComment.setTextColor(0xFF9999BB);
            tvComment.setPadding(0, Math.round(4*d), 0, Math.round(6*d));
            card.addView(tvComment);
        }

        // ── Photo associée au POI — format paysage 160×100dp ──────────────────
        String photoB64 = poi.getPhotoBase64();
        if (photoB64 != null && !photoB64.isEmpty()) {
            try {
                byte[] bytes = android.util.Base64.decode(photoB64, android.util.Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    ImageView imgPoi = new ImageView(this);
                    int w = Math.round(160 * d);
                    int h = Math.round(100 * d);
                    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(w, h);
                    imgParams.setMargins(0, Math.round(8*d), 0, 0);
                    imgPoi.setLayoutParams(imgParams);
                    imgPoi.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgPoi.setImageBitmap(bmp);
                    imgPoi.setClipToOutline(true);
                    imgPoi.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 6*d);
                        }
                    });
                    // Clic → plein écran
                    imgPoi.setOnClickListener(v -> showFullPhoto(bmp));
                    card.addView(imgPoi);
                }
            } catch (Exception ignored) {}
        }
        // ──────────────────────────────────────────────────────────────────────

        // Coordonnées GPS
        TextView tvCoords = new TextView(this);
        tvCoords.setText("GPS : " + String.format("%.5f", poi.getLat())
                + ", " + String.format("%.5f", poi.getLng()));
        tvCoords.setTextSize(10);
        tvCoords.setTextColor(0xFF555570);
        tvCoords.setPadding(0, Math.round(6*d), 0, 0);
        card.addView(tvCoords);

        container.addView(card);
    }
}
