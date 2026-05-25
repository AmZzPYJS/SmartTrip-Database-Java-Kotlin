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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TripDetailsActivity extends AppCompatActivity {

    private MapView mapView;
    private boolean mapReady = false;
    private static final String API_BASE_URL = "https://smarttrip-api.onrender.com";
    private final Set<String> seenPhotoIds = new HashSet<>();

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

        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }

        Button btnShare = findViewById(R.id.btnShareTrip);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> showQrDialog(trip));
        }

        tvBattery.setText(BatteryHelper.getStatusMessage(this));
        setupMap(trip);
        // loadPhotosOnce est appelé dans setupMap via mapView.post()
    }

    // =========================================================================
    // Carte — photos chargées après que mapView soit rendu
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

        // Charger les photos UNE FOIS que la carte est rendue
        mapView.post(() -> {
            mapReady = true;
            loadPhotosOnce(trip);
        });
    }

    // =========================================================================
    // Photos — filtre strict trip_id + coordonnées valides obligatoires
    // =========================================================================

    private void loadPhotosOnce(Trip trip) {
        final String currentTripId = trip.getId();

        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {

                        if (!response.isSuccessful() || response.body() == null) return;

                        for (java.util.Map<String, Object> doc : response.body()) {

                            // ── Filtre strict trip_id ──────────────────────────
                            String docTripId = (String) doc.get("trip_id");
                            if (docTripId == null || !docTripId.equals(currentTripId)) continue;

                            String base64     = (String) doc.get("photo_base64");
                            String recordedAt = (String) doc.get("recorded_at");

                            // ── Déduplication ──────────────────────────────────
                            String dedupe = recordedAt + "|"
                                    + (base64 != null && base64.length() > 20
                                    ? base64.substring(0, 20) : "null");
                            if (seenPhotoIds.contains(dedupe)) continue;
                            seenPhotoIds.add(dedupe);

                            // ── Coordonnées GPS — VALIDATION STRICTE ──────────
                            // Si lat=-90 ou lng=-180 → valeurs par défaut FastAPI
                            // = photo sans coords réelles → on affiche en galerie
                            // seulement, PAS sur la carte
                            double lat = 0, lng = 0;
                            boolean hasValidCoords = false;
                            try {
                                java.util.Map<String, Object> loc =
                                        (java.util.Map<String, Object>) doc.get("location");
                                if (loc != null) {
                                    double rawLat = ((Number) loc.get("latitude")).doubleValue();
                                    double rawLng = ((Number) loc.get("longitude")).doubleValue();
                                    // Rejeter les coords par défaut FastAPI (-90/-180)
                                    // et les coords nulles (0.0/0.0 = Golfe de Guinée)
                                    if (rawLat != -90.0 && rawLng != -180.0
                                            && !(rawLat == 0.0 && rawLng == 0.0)
                                            && rawLat >= -85.0 && rawLat <= 85.0
                                            && rawLng >= -180.0 && rawLng <= 180.0) {
                                        lat = rawLat;
                                        lng = rawLng;
                                        hasValidCoords = true;
                                    }
                                }
                            } catch (Exception ignored) {}

                            final double fLat = lat;
                            final double fLng = lng;
                            final boolean fHasCoords = hasValidCoords;
                            final String fBase64 = base64;
                            final String fRecordedAt = recordedAt;

                            runOnUiThread(() -> {
                                // Toujours afficher en galerie
                                if (fBase64 != null) addPhotoToGallery(fBase64);

                                // Carte : seulement si coordonnées réelles valides
                                if (fHasCoords && mapReady && mapView != null) {
                                    addPhotoMarkerToMap(fBase64, fLat, fLng, fRecordedAt);
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            Throwable t) {}
                });
    }

    // =========================================================================
    // Galerie photos — format adaptatif
    // =========================================================================

    private void addPhotoToGallery(String base64) {
        LinearLayout galleryLayout = findViewById(R.id.layoutPhotoGallery);
        if (galleryLayout == null) return;
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            float d = getResources().getDisplayMetrics().density;
            int fixedH = Math.round(130 * d);
            float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
            int computedW = Math.round(fixedH * ratio);
            int finalW = Math.max(Math.round(90*d), Math.min(computedW, Math.round(220*d)));

            ImageView img = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(finalW, fixedH);
            params.setMargins(0, 0, Math.round(10*d), 0);
            img.setLayoutParams(params);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(bitmap);
            img.setClipToOutline(true);
            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8*d);
                }
            });
            img.setOnClickListener(v -> showFullPhoto(bitmap));
            galleryLayout.addView(img);

            HorizontalScrollView sv = findViewById(R.id.scrollPhotoGallery);
            if (sv != null) sv.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Marqueur photo carte — format adaptatif
    // =========================================================================

    private void addPhotoMarkerToMap(String base64, double lat, double lng, String recordedAt) {
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(lat, lng));
        marker.setTitle("📸 Photo souvenir");
        marker.setSnippet(recordedAt != null ? recordedAt : "");
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        if (base64 != null) {
            try {
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                marker.setIcon(createPhotoMarkerIcon(bmp));
            } catch (Exception e) { marker.setIcon(createPoiMarker()); }
        } else {
            marker.setIcon(createPoiMarker());
        }

        marker.setOnMarkerClickListener((m, map) -> {
            if (base64 != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    showFullPhoto(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                } catch (Exception ignored) {}
            }
            return true;
        });

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    private BitmapDrawable createPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;
        int border = Math.round(3*d);
        float ratio = (float) photo.getWidth() / photo.getHeight();
        int mW, mH;
        if (ratio >= 1.2f)       { mW = Math.round(80*d); mH = Math.round(52*d); }
        else if (ratio <= 0.85f) { mW = Math.round(44*d); mH = Math.round(64*d); }
        else                     { mW = Math.round(60*d); mH = Math.round(60*d); }

        Bitmap result = Bitmap.createBitmap(mW, mH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBg.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, mW, mH, 8*d, 8*d, paintBg);

        int innerW = mW-border*2, innerH = mH-border*2;
        float scaleX = (float)innerW/photo.getWidth(), scaleY = (float)innerH/photo.getHeight();
        float scale = Math.max(scaleX, scaleY);
        int scaledW = Math.round(photo.getWidth()*scale), scaledH = Math.round(photo.getHeight()*scale);
        Bitmap scaled = Bitmap.createScaledBitmap(photo, scaledW, scaledH, true);
        int cropX = Math.max(0,(scaledW-innerW)/2), cropY = Math.max(0,(scaledH-innerH)/2);
        int safeCropW = Math.min(innerW, scaledW-cropX), safeCropH = Math.min(innerH, scaledH-cropY);
        if (safeCropW > 0 && safeCropH > 0)
            canvas.drawBitmap(Bitmap.createBitmap(scaled, cropX, cropY, safeCropW, safeCropH), border, border, null);
        return new BitmapDrawable(getResources(), result);
    }

    // =========================================================================
    // QR Code
    // =========================================================================

    private void showQrDialog(Trip trip) {
        String tripUrl = API_BASE_URL + "/trip/" + trip.getId();
        Bitmap qrBitmap = generateQrCode(tripUrl, 600);
        if (qrBitmap == null) { Toast.makeText(this, "Erreur QR code", Toast.LENGTH_SHORT).show(); return; }

        LinearLayout dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.VERTICAL); dl.setPadding(48,32,48,16); dl.setGravity(Gravity.CENTER);
        TextView tvT = new TextView(this); tvT.setText("Partager « " + trip.getTitle() + " »");
        tvT.setTextSize(16); tvT.setTextColor(0xFF1A1A2E);
        tvT.setTypeface(null, android.graphics.Typeface.BOLD);
        tvT.setGravity(Gravity.CENTER); tvT.setPadding(0,0,0,16); dl.addView(tvT);
        ImageView ivQr = new ImageView(this);
        int qrSize = Math.round(260 * getResources().getDisplayMetrics().density);
        ivQr.setLayoutParams(new LinearLayout.LayoutParams(qrSize, qrSize));
        ivQr.setImageBitmap(qrBitmap); ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER); dl.addView(ivQr);
        TextView tvUrl = new TextView(this); tvUrl.setText(tripUrl);
        tvUrl.setTextSize(10); tvUrl.setTextColor(0xFF6C63FF); tvUrl.setGravity(Gravity.CENTER); tvUrl.setPadding(0,12,0,0); dl.addView(tvUrl);
        TextView tvI = new TextView(this); tvI.setText("Scannez ce QR code pour accéder au voyage");
        tvI.setTextSize(11); tvI.setTextColor(0xFF9999BB); tvI.setGravity(Gravity.CENTER); tvI.setPadding(0,4,0,16); dl.addView(tvI);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dl)
                .setPositiveButton("Fermer", null).setNeutralButton("📤 Partager le lien", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> shareLink(trip.getTitle(), tripUrl));
    }

    private Bitmap generateQrCode(String content, int size) {
        try {
            BitMatrix bm = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++)
                bitmap.setPixel(x, y, bm.get(x,y) ? 0xFF6C63FF : 0xFFFFFFFF);
            return bitmap;
        } catch (WriterException e) { return null; }
    }

    private void shareLink(String title, String url) {
        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "SmartTrip — " + title);
        i.putExtra(Intent.EXTRA_TEXT, "Découvre mon voyage « " + title + " » sur SmartTrip :\n" + url);
        startActivity(Intent.createChooser(i, "Partager le voyage via…"));
    }

    // =========================================================================
    // Marqueur POI
    // =========================================================================

    private BitmapDrawable createPoiMarker() {
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(44*d);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(0xFFDC2626);
        canvas.drawCircle(size/2f, size/3f, size/3f, fill);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(size/2f-size/4f, size/2f); path.lineTo(size/2f+size/4f, size/2f);
        path.lineTo(size/2f, size*0.85f); path.close(); canvas.drawPath(path, fill);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG); white.setColor(Color.WHITE);
        canvas.drawCircle(size/2f, size/3f, size/8f, white);
        return new BitmapDrawable(getResources(), bmp);
    }

    private void showFullPhoto(Bitmap bitmap) {
        ImageView img = new ImageView(this); img.setImageBitmap(bitmap);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER); img.setPadding(16,16,16,16);
        new AlertDialog.Builder(this).setView(img).setPositiveButton("Fermer", null).show();
    }

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    private double calculateTotalDistance(Trip trip) {
        double total = 0;
        List<GpsPoint> pts = trip.getGpsPoints();
        for (int i = 1; i < pts.size(); i++) total += pts.get(i-1).distanceTo(pts.get(i));
        return total;
    }

    private void addPoiView(LinearLayout container, Poi poi) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Math.round(16*d), Math.round(16*d), Math.round(16*d), Math.round(16*d));
        card.setBackgroundColor(0xFF1E1E35);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0,0,0,Math.round(12*d)); card.setLayoutParams(cp);

        LinearLayout hRow = new LinearLayout(this);
        hRow.setOrientation(LinearLayout.HORIZONTAL);
        hRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tvName = new TextView(this); tvName.setText(poi.getName());
        tvName.setTextSize(16); tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hRow.addView(tvName);
        TextView tvType = new TextView(this); tvType.setText(poi.getType());
        tvType.setTextSize(11); tvType.setTextColor(0xFFA89CFF);
        tvType.setPadding(Math.round(8*d),Math.round(2*d),Math.round(8*d),Math.round(2*d));
        tvType.setBackgroundColor(0xFF2D2D50); hRow.addView(tvType); card.addView(hRow);

        TextView tvRating = new TextView(this); tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14); tvRating.setTextColor(0xFFD97706);
        tvRating.setPadding(0,Math.round(6*d),0,Math.round(2*d)); card.addView(tvRating);

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this); tvComment.setText(poi.getComment());
            tvComment.setTextSize(13); tvComment.setTextColor(0xFF9999BB);
            tvComment.setPadding(0,Math.round(4*d),0,Math.round(6*d)); card.addView(tvComment);
        }

        String photoB64 = poi.getPhotoBase64();
        if (photoB64 != null && !photoB64.isEmpty()) {
            try {
                byte[] bytes = android.util.Base64.decode(photoB64, android.util.Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    ImageView imgPoi = new ImageView(this);
                    imgPoi.setLayoutParams(new LinearLayout.LayoutParams(Math.round(160*d), Math.round(100*d)));
                    imgPoi.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgPoi.setImageBitmap(bmp);
                    imgPoi.setOnClickListener(v -> showFullPhoto(bmp));
                    card.addView(imgPoi);
                }
            } catch (Exception ignored) {}
        }

        TextView tvCoords = new TextView(this);
        tvCoords.setText("GPS : " + String.format("%.5f", poi.getLat()) + ", " + String.format("%.5f", poi.getLng()));
        tvCoords.setTextSize(10); tvCoords.setTextColor(0xFF555570);
        tvCoords.setPadding(0,Math.round(6*d),0,0); card.addView(tvCoords);
        container.addView(card);
    }
}