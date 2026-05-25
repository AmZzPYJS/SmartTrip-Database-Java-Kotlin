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

    private static final String API_BASE_URL = "https://travel-tracker-backend-j5q0.onrender.com";

    private final Set<String> seenPhotoIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();

        Configuration.getInstance().load(
                ctx,
                PreferenceManager.getDefaultSharedPreferences(ctx)
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getFilesDir());
        Configuration.getInstance().setOsmdroidTileCache(
                new File(getCacheDir(), "osmdroid")
        );

        setContentView(R.layout.activity_trip_details);

        Trip trip = (Trip) getIntent().getSerializableExtra("trip");

        if (trip == null) {
            finish();
            return;
        }

        TextView tvTripName = findViewById(R.id.tvTripName);
        TextView tvTripDate = findViewById(R.id.tvTripDate);
        TextView tvTripDescription = findViewById(R.id.tvTripDescription);
        TextView tvStats = findViewById(R.id.tvStats);
        LinearLayout layoutPois = findViewById(R.id.layoutPois);
        TextView tvBattery = findViewById(R.id.tvBatteryDetail);

        tvTripName.setText(trip.getTitle());
        tvTripDate.setText(trip.getDate());
        tvTripDescription.setText(trip.getDescription());

        int nbGps = trip.getGpsPoints().size();
        int nbPoi = trip.getPois().size();
        double distanceKm = calculateTotalDistance(trip) / 1000.0;

        tvStats.setText(
                nbGps + " points GPS • "
                        + nbPoi + " POI • "
                        + String.format(java.util.Locale.FRANCE, "%.1f", distanceKm)
                        + " km parcourus"
        );

        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }

        Button btnShare = findViewById(R.id.btnShareTrip);

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> showQrDialog(trip));
        }

        tvBattery.setText(BatteryHelper.getStatusMessage(this));

        setupMap(trip);
    }

    // =========================================================================
    // Carte
    // =========================================================================

    private void setupMap(Trip trip) {
        mapView = findViewById(R.id.mapView);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Meilleure esthétique : on retire les gros boutons + / -
        mapView.setBuiltInZoomControls(false);
        mapView.setTilesScaledToDpi(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);

        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);

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

        Collections.sort(
                gpsPoints,
                (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp())
        );

        // Trajet
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

        // POI sur la carte : photo si disponible, sinon marker moderne
        for (Poi poi : pois) {
            Marker marker = new Marker(mapView);

            marker.setPosition(new GeoPoint(poi.getLat(), poi.getLng()));
            marker.setTitle(poi.getName());
            marker.setSnippet(poi.getType() + " • " + poi.getRatingStars());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            String poiPhotoBase64 = poi.getPhotoBase64();

            if (poiPhotoBase64 != null && !poiPhotoBase64.isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(
                            poiPhotoBase64,
                            android.util.Base64.DEFAULT
                    );

                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    if (bmp != null) {
                        marker.setIcon(createPoiPhotoMarkerIcon(bmp));
                    } else {
                        marker.setIcon(createModernPoiMarker());
                    }

                } catch (Exception e) {
                    marker.setIcon(createModernPoiMarker());
                }
            } else {
                marker.setIcon(createModernPoiMarker());
            }

            marker.setOnMarkerClickListener((m, map) -> {
                showPoiDialog(poi);
                return true;
            });

            mapView.getOverlays().add(marker);
        }

        // Points utilisés pour centrer la carte
        List<GeoPoint> allPoints = new ArrayList<>();

        for (GpsPoint p : gpsPoints) {
            allPoints.add(new GeoPoint(p.getLat(), p.getLng()));
        }

        for (Poi poi : pois) {
            allPoints.add(new GeoPoint(poi.getLat(), poi.getLng()));
        }

        if (allPoints.isEmpty()) {
            mapView.getController().setZoom(10.0);
            mapView.getController().setCenter(new GeoPoint(48.8566, 2.3522));
        } else if (allPoints.size() == 1) {
            mapView.getController().setZoom(18.0);
            mapView.getController().setCenter(allPoints.get(0));
        } else {
            if (!gpsPoints.isEmpty()) {
                mapView.getController().setCenter(
                        new GeoPoint(
                                gpsPoints.get(0).getLat(),
                                gpsPoints.get(0).getLng()
                        )
                );
            }

            BoundingBox bbox = BoundingBox.fromGeoPoints(allPoints);

            mapView.post(() -> mapView.zoomToBoundingBox(bbox, true, 80));
        }

        // Photos souvenirs sur la carte après rendu de la map
        mapView.post(() -> {
            mapReady = true;
            loadPhotosOnce(trip);
        });
    }

    // =========================================================================
    // Photos souvenirs cloud
    // =========================================================================

    private void loadPhotosOnce(Trip trip) {
        final String currentTripId = trip.getId();

        ApiClient.getInstance().getApiService().getUserPhotos("amin")
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response
                    ) {
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }

                        for (java.util.Map<String, Object> doc : response.body()) {
                            String docTripId = (String) doc.get("trip_id");

                            if (docTripId == null || !docTripId.equals(currentTripId)) {
                                continue;
                            }

                            String base64 = (String) doc.get("photo_base64");
                            String recordedAt = (String) doc.get("recorded_at");

                            String dedupe = recordedAt + "|"
                                    + (
                                    base64 != null && base64.length() > 20
                                            ? base64.substring(0, 20)
                                            : "null"
                            );

                            if (seenPhotoIds.contains(dedupe)) {
                                continue;
                            }

                            seenPhotoIds.add(dedupe);

                            double lat = 0;
                            double lng = 0;
                            boolean hasValidCoords = false;

                            try {
                                java.util.Map<String, Object> loc =
                                        (java.util.Map<String, Object>) doc.get("location");

                                if (loc != null) {
                                    double rawLat = ((Number) loc.get("latitude")).doubleValue();
                                    double rawLng = ((Number) loc.get("longitude")).doubleValue();

                                    if (rawLat != -90.0
                                            && rawLng != -180.0
                                            && !(rawLat == 0.0 && rawLng == 0.0)
                                            && rawLat >= -85.0
                                            && rawLat <= 85.0
                                            && rawLng >= -180.0
                                            && rawLng <= 180.0) {

                                        lat = rawLat;
                                        lng = rawLng;
                                        hasValidCoords = true;
                                    }
                                }

                            } catch (Exception ignored) {
                            }

                            final double fLat = lat;
                            final double fLng = lng;
                            final boolean fHasCoords = hasValidCoords;
                            final String fBase64 = base64;
                            final String fRecordedAt = recordedAt;

                            runOnUiThread(() -> {
                                if (fBase64 != null) {
                                    addPhotoToGallery(fBase64);
                                }

                                if (fHasCoords && mapReady && mapView != null) {
                                    addPhotoMarkerToMap(
                                            fBase64,
                                            fLat,
                                            fLng,
                                            fRecordedAt
                                    );
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            Throwable t
                    ) {
                        Toast.makeText(
                                TripDetailsActivity.this,
                                "Erreur chargement photos : " + t.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void addPhotoToGallery(String base64) {
        LinearLayout galleryLayout = findViewById(R.id.layoutPhotoGallery);

        if (galleryLayout == null) {
            return;
        }

        try {
            byte[] bytes = android.util.Base64.decode(
                    base64,
                    android.util.Base64.DEFAULT
            );

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (bitmap == null) {
                return;
            }

            float d = getResources().getDisplayMetrics().density;

            int fixedH = Math.round(130 * d);
            float ratio = (float) bitmap.getWidth() / bitmap.getHeight();

            int computedW = Math.round(fixedH * ratio);
            int finalW = Math.max(
                    Math.round(90 * d),
                    Math.min(computedW, Math.round(220 * d))
            );

            ImageView img = new ImageView(this);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(finalW, fixedH);

            params.setMargins(0, 0, Math.round(10 * d), 0);

            img.setLayoutParams(params);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(bitmap);
            img.setClipToOutline(true);

            img.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline outline) {
                    outline.setRoundRect(
                            0,
                            0,
                            v.getWidth(),
                            v.getHeight(),
                            Math.round(10 * d)
                    );
                }
            });

            img.setOnClickListener(v -> showFullPhoto(bitmap));

            galleryLayout.addView(img);

            HorizontalScrollView sv = findViewById(R.id.scrollPhotoGallery);

            if (sv != null) {
                sv.setVisibility(View.VISIBLE);
            }

        } catch (Exception ignored) {
        }
    }

    private void addPhotoMarkerToMap(
            String base64,
            double lat,
            double lng,
            String recordedAt
    ) {
        Marker marker = new Marker(mapView);

        marker.setPosition(new GeoPoint(lat, lng));
        marker.setTitle("📸 Photo souvenir");
        marker.setSnippet(recordedAt != null ? recordedAt : "");
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        if (base64 != null) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        base64,
                        android.util.Base64.DEFAULT
                );

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                if (bmp != null) {
                    marker.setIcon(createPhotoMarkerIcon(bmp));
                } else {
                    marker.setIcon(createPhotoFallbackMarker());
                }

            } catch (Exception e) {
                marker.setIcon(createPhotoFallbackMarker());
            }
        } else {
            marker.setIcon(createPhotoFallbackMarker());
        }

        marker.setOnMarkerClickListener((m, map) -> {
            if (base64 != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(
                            base64,
                            android.util.Base64.DEFAULT
                    );

                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                    if (bmp != null) {
                        showFullPhoto(bmp);
                    }

                } catch (Exception ignored) {
                }
            }

            return true;
        });

        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    // =========================================================================
    // Icônes carte
    // =========================================================================

    private BitmapDrawable createPoiPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;

        int width = Math.round(78 * d);
        int height = Math.round(92 * d);
        int imageSize = Math.round(64 * d);
        int border = Math.round(3 * d);

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x55000000);

        canvas.drawRoundRect(
                Math.round(6 * d),
                Math.round(6 * d),
                width - Math.round(6 * d),
                imageSize + Math.round(14 * d),
                Math.round(14 * d),
                Math.round(14 * d),
                shadow
        );

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.WHITE);

        canvas.drawRoundRect(
                Math.round(4 * d),
                Math.round(2 * d),
                width - Math.round(4 * d),
                imageSize + Math.round(10 * d),
                Math.round(14 * d),
                Math.round(14 * d),
                bg
        );

        int left = (width - imageSize) / 2;
        int top = Math.round(6 * d);
        int innerSize = imageSize - border * 2;

        float scale = Math.max(
                (float) innerSize / photo.getWidth(),
                (float) innerSize / photo.getHeight()
        );

        int scaledW = Math.round(photo.getWidth() * scale);
        int scaledH = Math.round(photo.getHeight() * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(photo, scaledW, scaledH, true);

        int cropX = Math.max(0, (scaledW - innerSize) / 2);
        int cropY = Math.max(0, (scaledH - innerSize) / 2);

        int cropW = Math.min(innerSize, scaledW - cropX);
        int cropH = Math.min(innerSize, scaledH - cropY);

        if (cropW > 0 && cropH > 0) {
            Bitmap cropped = Bitmap.createBitmap(
                    scaled,
                    cropX,
                    cropY,
                    cropW,
                    cropH
            );

            android.graphics.RectF imageRect = new android.graphics.RectF(
                    left + border,
                    top + border,
                    left + border + innerSize,
                    top + border + innerSize
            );

            canvas.save();

            android.graphics.Path clipPath = new android.graphics.Path();

            clipPath.addRoundRect(
                    imageRect,
                    Math.round(10 * d),
                    Math.round(10 * d),
                    android.graphics.Path.Direction.CW
            );

            canvas.clipPath(clipPath);
            canvas.drawBitmap(cropped, null, imageRect, null);
            canvas.restore();
        }

        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinPaint.setColor(0xFFDC2626);

        android.graphics.Path pin = new android.graphics.Path();

        pin.moveTo(
                width / 2f - Math.round(12 * d),
                imageSize + Math.round(6 * d)
        );

        pin.lineTo(
                width / 2f + Math.round(12 * d),
                imageSize + Math.round(6 * d)
        );

        pin.lineTo(
                width / 2f,
                height - Math.round(4 * d)
        );

        pin.close();

        canvas.drawPath(pin, pinPaint);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(Color.WHITE);

        canvas.drawCircle(
                width / 2f,
                imageSize + Math.round(10 * d),
                Math.round(4 * d),
                dot
        );

        return new BitmapDrawable(getResources(), result);
    }

    private BitmapDrawable createModernPoiMarker() {
        float d = getResources().getDisplayMetrics().density;

        int width = Math.round(54 * d);
        int height = Math.round(68 * d);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x55000000);

        canvas.drawCircle(
                width / 2f,
                Math.round(24 * d),
                Math.round(19 * d),
                shadow
        );

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFDC2626);

        canvas.drawCircle(
                width / 2f,
                Math.round(22 * d),
                Math.round(18 * d),
                fill
        );

        android.graphics.Path path = new android.graphics.Path();

        path.moveTo(
                width / 2f - Math.round(13 * d),
                Math.round(34 * d)
        );

        path.lineTo(
                width / 2f + Math.round(13 * d),
                Math.round(34 * d)
        );

        path.lineTo(
                width / 2f,
                height - Math.round(6 * d)
        );

        path.close();

        canvas.drawPath(path, fill);

        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);

        canvas.drawCircle(
                width / 2f,
                Math.round(22 * d),
                Math.round(7 * d),
                white
        );

        return new BitmapDrawable(getResources(), bmp);
    }

    private BitmapDrawable createPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;

        int border = Math.round(3 * d);

        float ratio = (float) photo.getWidth() / photo.getHeight();

        int markerW;
        int markerH;

        if (ratio >= 1.2f) {
            markerW = Math.round(84 * d);
            markerH = Math.round(58 * d);
        } else if (ratio <= 0.85f) {
            markerW = Math.round(52 * d);
            markerH = Math.round(72 * d);
        } else {
            markerW = Math.round(66 * d);
            markerH = Math.round(66 * d);
        }

        Bitmap result = Bitmap.createBitmap(
                markerW,
                markerH,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(result);

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x55000000);

        canvas.drawRoundRect(
                Math.round(2 * d),
                Math.round(3 * d),
                markerW,
                markerH,
                Math.round(10 * d),
                Math.round(10 * d),
                shadow
        );

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.WHITE);

        canvas.drawRoundRect(
                0,
                0,
                markerW - Math.round(3 * d),
                markerH - Math.round(3 * d),
                Math.round(10 * d),
                Math.round(10 * d),
                background
        );

        int innerW = markerW - border * 2 - Math.round(3 * d);
        int innerH = markerH - border * 2 - Math.round(3 * d);

        float scaleX = (float) innerW / photo.getWidth();
        float scaleY = (float) innerH / photo.getHeight();
        float scale = Math.max(scaleX, scaleY);

        int scaledW = Math.round(photo.getWidth() * scale);
        int scaledH = Math.round(photo.getHeight() * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(photo, scaledW, scaledH, true);

        int cropX = Math.max(0, (scaledW - innerW) / 2);
        int cropY = Math.max(0, (scaledH - innerH) / 2);

        int cropW = Math.min(innerW, scaledW - cropX);
        int cropH = Math.min(innerH, scaledH - cropY);

        if (cropW > 0 && cropH > 0) {
            Bitmap cropped = Bitmap.createBitmap(
                    scaled,
                    cropX,
                    cropY,
                    cropW,
                    cropH
            );

            android.graphics.RectF imageRect = new android.graphics.RectF(
                    border,
                    border,
                    border + innerW,
                    border + innerH
            );

            canvas.save();

            android.graphics.Path clipPath = new android.graphics.Path();

            clipPath.addRoundRect(
                    imageRect,
                    Math.round(8 * d),
                    Math.round(8 * d),
                    android.graphics.Path.Direction.CW
            );

            canvas.clipPath(clipPath);
            canvas.drawBitmap(cropped, null, imageRect, null);
            canvas.restore();
        }

        return new BitmapDrawable(getResources(), result);
    }

    private BitmapDrawable createPhotoFallbackMarker() {
        float d = getResources().getDisplayMetrics().density;

        int size = Math.round(44 * d);

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF6C63FF);

        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, bg);

        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        white.setTextAlign(Paint.Align.CENTER);
        white.setTextSize(Math.round(20 * d));

        canvas.drawText(
                "📸",
                size / 2f,
                size / 2f + Math.round(7 * d),
                white
        );

        return new BitmapDrawable(getResources(), bmp);
    }

    // Ancienne méthode gardée au cas où
    private BitmapDrawable createPoiMarker() {
        return createModernPoiMarker();
    }

    // =========================================================================
    // Dialog POI amélioré
    // =========================================================================

    private void showPoiDialog(Poi poi) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                Math.round(22 * d),
                Math.round(18 * d),
                Math.round(22 * d),
                Math.round(12 * d)
        );

        TextView title = new TextView(this);
        title.setText(poi.getName());
        title.setTextSize(20);
        title.setTextColor(0xFF1A1A2E);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        layout.addView(title);

        TextView type = new TextView(this);
        type.setText(poi.getType() + " • " + poi.getRatingStars());
        type.setTextSize(14);
        type.setTextColor(0xFF6C63FF);
        type.setPadding(
                0,
                Math.round(4 * d),
                0,
                Math.round(10 * d)
        );

        layout.addView(type);

        String photoBase64 = poi.getPhotoBase64();

        if (photoBase64 != null && !photoBase64.isEmpty()) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        photoBase64,
                        android.util.Base64.DEFAULT
                );

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                if (bmp != null) {
                    ImageView image = new ImageView(this);
                    image.setImageBitmap(bmp);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);

                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    Math.round(180 * d)
                            );

                    params.setMargins(0, 0, 0, Math.round(12 * d));

                    image.setLayoutParams(params);
                    image.setOnClickListener(v -> showFullPhoto(bmp));

                    layout.addView(image);
                }

            } catch (Exception ignored) {
            }
        }

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView comment = new TextView(this);
            comment.setText(poi.getComment());
            comment.setTextSize(14);
            comment.setTextColor(0xFF333344);
            comment.setPadding(
                    0,
                    0,
                    0,
                    Math.round(10 * d)
            );

            layout.addView(comment);
        }

        TextView coords = new TextView(this);

        coords.setText(
                "GPS : "
                        + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLat())
                        + ", "
                        + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLng())
        );

        coords.setTextSize(12);
        coords.setTextColor(0xFF777788);

        layout.addView(coords);

        new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton("Fermer", null)
                .show();
    }

    // =========================================================================
    // Galerie / photo plein écran
    // =========================================================================

    private void showFullPhoto(Bitmap bitmap) {
        ImageView img = new ImageView(this);

        img.setImageBitmap(bitmap);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(16, 16, 16, 16);

        new AlertDialog.Builder(this)
                .setView(img)
                .setPositiveButton("Fermer", null)
                .show();
    }

    // =========================================================================
    // QR Code
    // =========================================================================

    private void showQrDialog(Trip trip) {
        String tripUrl = API_BASE_URL + "/trip/" + trip.getId();

        Bitmap qrBitmap = generateQrCode(tripUrl, 600);

        if (qrBitmap == null) {
            Toast.makeText(this, "Erreur QR code", Toast.LENGTH_SHORT).show();
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

        int qrSize = Math.round(
                260 * getResources().getDisplayMetrics().density
        );

        ivQr.setLayoutParams(
                new LinearLayout.LayoutParams(qrSize, qrSize)
        );

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

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Scannez ce QR code pour accéder au voyage");
        tvInfo.setTextSize(11);
        tvInfo.setTextColor(0xFF9999BB);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding(0, 4, 0, 16);

        dialogLayout.addView(tvInfo);

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
            BitMatrix bitMatrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size
            );

            Bitmap bitmap = Bitmap.createBitmap(
                    size,
                    size,
                    Bitmap.Config.RGB_565
            );

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(
                            x,
                            y,
                            bitMatrix.get(x, y) ? 0xFF6C63FF : 0xFFFFFFFF
                    );
                }
            }

            return bitmap;

        } catch (WriterException e) {
            return null;
        }
    }

    private void shareLink(String title, String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);

        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "SmartTrip — " + title);
        intent.putExtra(
                Intent.EXTRA_TEXT,
                "Découvre mon voyage « " + title + " » sur SmartTrip :\n" + url
        );

        startActivity(Intent.createChooser(intent, "Partager le voyage via…"));
    }

    // =========================================================================
    // POI en liste sous la carte
    // =========================================================================

    private void addPoiView(LinearLayout container, Poi poi) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                Math.round(16 * d),
                Math.round(16 * d),
                Math.round(16 * d),
                Math.round(16 * d)
        );
        card.setBackgroundColor(0xFF1E1E35);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, Math.round(12 * d));

        card.setLayoutParams(cardParams);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);

        headerRow.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        TextView tvName = new TextView(this);

        tvName.setText(poi.getName());
        tvName.setTextSize(16);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);

        tvName.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        headerRow.addView(tvName);

        TextView tvType = new TextView(this);

        tvType.setText(poi.getType());
        tvType.setTextSize(11);
        tvType.setTextColor(0xFFA89CFF);
        tvType.setPadding(
                Math.round(8 * d),
                Math.round(2 * d),
                Math.round(8 * d),
                Math.round(2 * d)
        );
        tvType.setBackgroundColor(0xFF2D2D50);

        headerRow.addView(tvType);
        card.addView(headerRow);

        TextView tvRating = new TextView(this);

        tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14);
        tvRating.setTextColor(0xFFD97706);
        tvRating.setPadding(
                0,
                Math.round(6 * d),
                0,
                Math.round(2 * d)
        );

        card.addView(tvRating);

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this);

            tvComment.setText(poi.getComment());
            tvComment.setTextSize(13);
            tvComment.setTextColor(0xFF9999BB);
            tvComment.setPadding(
                    0,
                    Math.round(4 * d),
                    0,
                    Math.round(6 * d)
            );

            card.addView(tvComment);
        }

        String photoBase64 = poi.getPhotoBase64();

        if (photoBase64 != null && !photoBase64.isEmpty()) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        photoBase64,
                        android.util.Base64.DEFAULT
                );

                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                if (bmp != null) {
                    ImageView imgPoi = new ImageView(this);

                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(
                                    Math.round(170 * d),
                                    Math.round(105 * d)
                            );

                    params.setMargins(0, Math.round(6 * d), 0, Math.round(6 * d));

                    imgPoi.setLayoutParams(params);
                    imgPoi.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgPoi.setImageBitmap(bmp);
                    imgPoi.setOnClickListener(v -> showFullPhoto(bmp));

                    card.addView(imgPoi);
                }

            } catch (Exception ignored) {
            }
        }

        TextView tvCoords = new TextView(this);

        tvCoords.setText(
                "GPS : "
                        + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLat())
                        + ", "
                        + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLng())
        );

        tvCoords.setTextSize(10);
        tvCoords.setTextColor(0xFF555570);
        tvCoords.setPadding(0, Math.round(6 * d), 0, 0);

        card.addView(tvCoords);

        container.addView(card);
    }

    // =========================================================================
    // Distance
    // =========================================================================

    private double calculateTotalDistance(Trip trip) {
        double total = 0;

        List<GpsPoint> points = trip.getGpsPoints();

        for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
        }

        return total;
    }

    // =========================================================================
    // Cycle de vie MapView
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mapView != null) {
            mapView.onPause();
        }
    }
}