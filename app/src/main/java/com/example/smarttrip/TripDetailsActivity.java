package com.example.smarttrip;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smarttrip.api.ApiClient;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

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
    private static final String API_BASE_URL = "https://smarttrip-api.onrender.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // ── POI — UN SEUL appel ici, pas de doublon ───────────────────────────
        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }

        // ── Bouton QR — branché sur le bouton XML (pas de création dynamique) ─
        // Le bouton android:id="@+id/btnShareTrip" est déclaré dans le XML
        Button btnShare = findViewById(R.id.btnShareTrip);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> showQrDialog(trip));
        }

        tvBattery.setText(BatteryHelper.getStatusMessage(this));
        setupMap(trip);

        // ── Photos : UN SEUL appel API pour galerie + marqueurs carte ─────────
        loadPhotosOnce(trip);
    }

    // =========================================================================
    // Photos — UN SEUL appel API pour éviter le doublon
    // =========================================================================

    /**
     * Charge les photos UNE SEULE FOIS depuis l'API.
     * Avant : loadPhotosForMap() + loadAndDisplayPhotos() → 2 appels → doublons
     * Maintenant : un seul appel, on remplit galerie ET marqueurs carte.
     */
    private void loadPhotosOnce(Trip trip) {
        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {

                        if (!response.isSuccessful() || response.body() == null) return;

                        for (java.util.Map<String, Object> doc : response.body()) {
                            String docTripId = (String) doc.get("trip_id");
                            if (!trip.getId().equals(docTripId)) continue;

                            String base64    = (String) doc.get("photo_base64");
                            String recordedAt = (String) doc.get("recorded_at");

                            // Coordonnées GPS de la photo
                            double lat = 0, lng = 0;
                            boolean hasCoords = false;
                            try {
                                java.util.Map<String, Object> loc =
                                        (java.util.Map<String, Object>) doc.get("location");
                                if (loc != null) {
                                    lat = ((Number) loc.get("latitude")).doubleValue();
                                    lng = ((Number) loc.get("longitude")).doubleValue();
                                    hasCoords = true;
                                }
                            } catch (Exception ignored) {}

                            final double fLat = lat;
                            final double fLng = lng;
                            final boolean fHasCoords = hasCoords;

                            runOnUiThread(() -> {
                                // 1. Ajouter dans la galerie paysage
                                if (base64 != null) {
                                    addPhotoToGallery(base64);
                                }

                                // 2. Ajouter marqueur sur la carte
                                if (fHasCoords && mapView != null) {
                                    Marker marker = new Marker(mapView);
                                    marker.setPosition(new GeoPoint(fLat, fLng));
                                    marker.setTitle("📸 Photo souvenir");
                                    marker.setSnippet(recordedAt != null ? recordedAt : "");
                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                                    if (base64 != null) {
                                        try {
                                            byte[] bytes = android.util.Base64.decode(
                                                    base64, android.util.Base64.DEFAULT);
                                            Bitmap bmp = BitmapFactory.decodeByteArray(
                                                    bytes, 0, bytes.length);
                                            // Marqueur au bon format (paysage ou portrait auto)
                                            marker.setIcon(createPhotoMarkerIcon(bmp));
                                        } catch (Exception e) {
                                            marker.setIcon(createPoiMarker());
                                        }
                                    } else {
                                        marker.setIcon(createPoiMarker());
                                    }

                                    marker.setOnMarkerClickListener((m, map) -> {
                                        if (base64 != null) {
                                            try {
                                                byte[] bytes = android.util.Base64.decode(
                                                        base64, android.util.Base64.DEFAULT);
                                                Bitmap bmp = BitmapFactory.decodeByteArray(
                                                        bytes, 0, bytes.length);
                                                showFullPhoto(bmp);
                                            } catch (Exception ignored2) {}
                                        }
                                        return true;
                                    });

                                    mapView.getOverlays().add(marker);
                                    mapView.invalidate();
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            Throwable t) {
                        // Photos non disponibles — pas bloquant
                    }
                });
    }

    // =========================================================================
    // Galerie photos — format adaptatif (paysage ou portrait selon l'image)
    // =========================================================================

    private void addPhotoToGallery(String base64) {
        LinearLayout galleryLayout = findViewById(R.id.layoutPhotoGallery);
        if (galleryLayout == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            float density = getResources().getDisplayMetrics().density;

            // Format adaptatif : paysage si largeur > hauteur, portrait sinon
            // Hauteur fixe 130dp, largeur proportionnelle
            int fixedH = Math.round(130 * density);
            float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
            int computedW = Math.round(fixedH * ratio);
            // Min 90dp, max 220dp pour rester raisonnable
            int finalW = Math.max(Math.round(90 * density), Math.min(computedW, Math.round(220 * density)));

            ImageView img = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(finalW, fixedH);
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

    // =========================================================================
    // Marqueur photo — format adaptatif paysage/portrait sur la carte
    // =========================================================================

    /**
     * Crée un marqueur carte qui respecte le format original de la photo.
     * Paysage → 80×52dp  |  Portrait → 44×64dp  |  Carré → 60×60dp
     * Bordure blanche + coins arrondis pour un look carte postale.
     */
    private BitmapDrawable createPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;
        int border = Math.round(3 * d);

        // Dimensions du marqueur selon le format de la photo
        int mW, mH;
        float ratio = (float) photo.getWidth() / photo.getHeight();
        if (ratio >= 1.2f) {
            // Paysage
            mW = Math.round(80 * d);
            mH = Math.round(52 * d);
        } else if (ratio <= 0.85f) {
            // Portrait
            mW = Math.round(44 * d);
            mH = Math.round(64 * d);
        } else {
            // Carré ou proche
            mW = Math.round(60 * d);
            mH = Math.round(60 * d);
        }

        Bitmap result = Bitmap.createBitmap(mW, mH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // Fond blanc avec coins arrondis
        Paint paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBg.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, mW, mH, 8 * d, 8 * d, paintBg);

        // Photo redimensionnée en centerCrop dans le marqueur
        int innerW = mW - border * 2;
        int innerH = mH - border * 2;
        float scaleX = (float) innerW / photo.getWidth();
        float scaleY = (float) innerH / photo.getHeight();
        float scale  = Math.max(scaleX, scaleY);
        int scaledW  = Math.round(photo.getWidth() * scale);
        int scaledH  = Math.round(photo.getHeight() * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(photo, scaledW, scaledH, true);

        int cropX = Math.max(0, (scaledW - innerW) / 2);
        int cropY = Math.max(0, (scaledH - innerH) / 2);
        int safeCropW = Math.min(innerW, scaledW - cropX);
        int safeCropH = Math.min(innerH, scaledH - cropY);
        if (safeCropW > 0 && safeCropH > 0) {
            Bitmap cropped = Bitmap.createBitmap(scaled, cropX, cropY, safeCropW, safeCropH);
            canvas.drawBitmap(cropped, border, border, null);
        }

        return new BitmapDrawable(getResources(), result);
    }

    // =========================================================================
    // QR Code
    // =========================================================================

    private void showQrDialog(Trip trip) {
        String tripUrl = API_BASE_URL + "/trip/" + trip.getId();
        Bitmap qrBitmap = generateQrCode(tripUrl, 600);
        if (qrBitmap == null) {
            Toast.makeText(this, "Erreur génération QR code", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);
        dialogLayout.setGravity(Gravity.CENTER);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Partager « " + trip.getTitle() + " »");
        tvTitle.setTextSize(16);
        tvTitle.setTextColor(0xFF1A1A2E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 16);
        dialogLayout.addView(tvTitle);

        ImageView ivQr = new ImageView(this);
        int qrSize = Math.round(260 * getResources().getDisplayMetrics().density);
        ivQr.setLayoutParams(new LinearLayout.LayoutParams(qrSize, qrSize));
        ivQr.setImageBitmap(qrBitmap);
        ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dialogLayout.addView(ivQr);

        TextView tvUrl = new TextView(this);
        tvUrl.setText(tripUrl);
        tvUrl.setTextSize(10);
        tvUrl.setTextColor(0xFF6C63FF);
        tvUrl.setGravity(Gravity.CENTER);
        tvUrl.setPadding(0, 12, 0, 0);
        dialogLayout.addView(tvUrl);

        TextView tvInstruction = new TextView(this);
        tvInstruction.setText("Scannez ce QR code pour accéder au voyage");
        tvInstruction.setTextSize(11);
        tvInstruction.setTextColor(0xFF9999BB);
        tvInstruction.setGravity(Gravity.CENTER);
        tvInstruction.setPadding(0, 4, 0, 16);
        dialogLayout.addView(tvInstruction);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogLayout)
                .setPositiveButton("Fermer", null)
                .setNeutralButton("📤 Partager le lien", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> shareLink(trip.getTitle(), tripUrl));
    }

    private Bitmap generateQrCode(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF6C63FF : 0xFFFFFFFF);
            return bitmap;
        } catch (WriterException e) { return null; }
    }

    private void shareLink(String tripTitle, String tripUrl) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SmartTrip — " + tripTitle);
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Découvre mon voyage « " + tripTitle + " » sur SmartTrip :\n" + tripUrl);
        startActivity(Intent.createChooser(shareIntent, "Partager le voyage via…"));
    }

    // =========================================================================
    // Carte OSMDroid
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
        List<Poi>      pois      = trip.getPois();

        Collections.sort(gpsPoints, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        if (gpsPoints.size() >= 2) {
            List<GeoPoint> routePoints = new ArrayList<>();
            for (GpsPoint p : gpsPoints) routePoints.add(new GeoPoint(p.getLat(), p.getLng()));
            Polyline polyline = new Polyline(mapView);
            polyline.setPoints(routePoints);
            polyline.getOutlinePaint().setColor(Color.parseColor("#6C63FF"));
            polyline.getOutlinePaint().setStrokeWidth(8f);
            mapView.getOverlays().add(polyline);
        }

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
    // Marqueurs POI
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

    // =========================================================================
    // Plein écran photo
    // =========================================================================

    private void showFullPhoto(Bitmap bitmap) {
        ImageView fullImg = new ImageView(this);
        fullImg.setImageBitmap(bitmap);
        fullImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fullImg.setPadding(16, 16, 16, 16);
        new AlertDialog.Builder(this)
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
    // Calculs & card POI
    // =========================================================================

    private double calculateTotalDistance(Trip trip) {
        double total = 0;
        List<GpsPoint> pts = trip.getGpsPoints();
        for (int i = 1; i < pts.size(); i++) total += pts.get(i - 1).distanceTo(pts.get(i));
        return total;
    }

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

        // Photo POI
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
                    imgPoi.setOnClickListener(v -> showFullPhoto(bmp));
                    card.addView(imgPoi);
                }
            } catch (Exception ignored) {}
        }

        // Coordonnées
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