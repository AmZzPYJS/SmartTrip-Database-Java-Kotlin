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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class TripDetailsActivity extends AppCompatActivity {

    private static final String TAG = "TripDetailsActivity";
    private static final String USER_ID = "amin";
    private static final String API_BASE_URL = "https://travel-tracker-backend-j5q0.onrender.com";

    private MapView mapView;
    private boolean mapReady = false;
    private Trip currentTrip;

    private final HashSet<String> seenPhotoIds = new HashSet<>();
    private final Map<String, List<Bitmap>> photosByPoiId = new HashMap<>();
    private final List<Bitmap> freePhotoBitmaps = new ArrayList<>();
    private LinearLayout layoutPoisContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(getFilesDir());
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "osmdroid"));
        setContentView(R.layout.activity_trip_details);

        currentTrip = (Trip) getIntent().getSerializableExtra("trip");
        if (currentTrip == null) { finish(); return; }

        TextView tvTripName        = findViewById(R.id.tvTripName);
        TextView tvTripDate        = findViewById(R.id.tvTripDate);
        TextView tvTripDescription = findViewById(R.id.tvTripDescription);
        TextView tvStats           = findViewById(R.id.tvStats);
        TextView tvBattery         = findViewById(R.id.tvBatteryDetail);
        layoutPoisContainer        = findViewById(R.id.layoutPois);

        tvTripName.setText(currentTrip.getTitle());
        tvTripDate.setText(currentTrip.getDate());
        tvTripDescription.setText(currentTrip.getDescription());
        updateStats(tvStats, currentTrip);

        layoutPoisContainer.removeAllViews();
        for (Poi poi : currentTrip.getPois()) addPoiView(layoutPoisContainer, poi);

        Button btnShare = findViewById(R.id.btnShareTrip);
        if (btnShare != null) btnShare.setOnClickListener(v -> showQrDialog(currentTrip));

        tvBattery.setText(BatteryHelper.getStatusMessage(this));
        setupMap(currentTrip);
    }

    private void updateStats(TextView tvStats, Trip trip) {
        int nbGps = trip.getGpsPoints().size();
        int nbPoi = trip.getPois().size();
        double distanceKm = calculateTotalDistance(trip) / 1000.0;
        tvStats.setText(nbGps + " points GPS • " + nbPoi + " POI • "
                + String.format(java.util.Locale.FRANCE, "%.1f", distanceKm) + " km parcourus");
    }

    // =========================================================================
    // Carte
    // =========================================================================

    private void setupMap(Trip trip) {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setTilesScaledToDpi(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);

        mapView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    view.getParent().requestDisallowInterceptTouchEvent(true); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false); break;
            }
            return false;
        });

        List<GpsPoint> gpsPoints = trip.getGpsPoints();
        List<Poi> pois = trip.getPois();
        Collections.sort(gpsPoints, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        if (gpsPoints.size() >= 2) {
            List<GeoPoint> routePoints = new ArrayList<>();
            for (GpsPoint point : gpsPoints) routePoints.add(new GeoPoint(point.getLat(), point.getLng()));
            Polyline polyline = new Polyline(mapView);
            polyline.setPoints(routePoints);
            polyline.getOutlinePaint().setColor(Color.parseColor("#6C63FF"));
            polyline.getOutlinePaint().setStrokeWidth(8f);
            mapView.getOverlays().add(polyline);
        }

        for (Poi poi : pois) addPoiMarkerToMap(poi);

        List<GeoPoint> allPoints = new ArrayList<>();
        for (GpsPoint point : gpsPoints) allPoints.add(new GeoPoint(point.getLat(), point.getLng()));
        for (Poi poi : pois) allPoints.add(new GeoPoint(poi.getLat(), poi.getLng()));

        if (allPoints.isEmpty()) {
            mapView.getController().setZoom(10.0);
            mapView.getController().setCenter(new GeoPoint(48.8566, 2.3522));
        } else if (allPoints.size() == 1) {
            mapView.getController().setZoom(18.0);
            mapView.getController().setCenter(allPoints.get(0));
        } else {
            BoundingBox bbox = BoundingBox.fromGeoPoints(allPoints);
            mapView.post(() -> mapView.zoomToBoundingBox(bbox, true, 80));
        }

        mapView.post(() -> {
            mapReady = true;
            loadPhotosOnce(currentTrip);
        });
    }

    private void addPoiMarkerToMap(Poi poi) {
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(poi.getLat(), poi.getLng()));
        marker.setTitle(poi.getName());
        marker.setSnippet(poi.getType() + " • " + poi.getRatingStars());
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Bitmap poiBitmap = decodeBitmap(poi.getPhotoBase64());
        marker.setIcon(poiBitmap != null ? createPoiPhotoMarkerIcon(poiBitmap) : createModernPoiMarker());
        marker.setOnMarkerClickListener((m, map) -> { showPoiDialog(poi); return true; });
        mapView.getOverlays().add(marker);
    }

    // =========================================================================
    // Photos cloud
    // =========================================================================

    private void loadPhotosOnce(Trip trip) {
        final String currentTripId = trip.getId();
        ApiClient.getInstance().getApiService().getUserPhotos(USER_ID)
                .enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call,
                            retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        for (Map<String, Object> doc : response.body()) {
                            parsePhotoDocForDisplay(currentTripId, doc);
                        }
                        runOnUiThread(() -> {
                            refreshPhotoGallery();
                            refreshPoiList();
                            mapView.invalidate();
                        });
                    }
                    @Override
                    public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(TripDetailsActivity.this, "Erreur chargement photos", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void parsePhotoDocForDisplay(String currentTripId, Map<String, Object> doc) {
        try {
            String docTripId = getString(doc, "trip_id", "");
            if (!currentTripId.equals(docTripId)) return;

            String base64 = getString(doc, "photo_base64", "");
            if (base64.isEmpty()) return;

            String recordedAt  = getString(doc, "recorded_at", "");
            String linkedPoiId = getString(doc, "linked_poi_id", "");

            // Déduplication
            String dedupe = recordedAt + "|" + (base64.length() > 20 ? base64.substring(0, 20) : base64);
            if (seenPhotoIds.contains(dedupe)) return;
            seenPhotoIds.add(dedupe);

            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            // Photo liée à un POI
            if (!linkedPoiId.isEmpty() && !"null".equalsIgnoreCase(linkedPoiId)) {
                if (!photosByPoiId.containsKey(linkedPoiId)) photosByPoiId.put(linkedPoiId, new ArrayList<>());
                photosByPoiId.get(linkedPoiId).add(bitmap);
                return;
            }

            // Photo libre
            freePhotoBitmaps.add(bitmap);

            // Coordonnées GPS
            Map<String, Object> loc = (Map<String, Object>) doc.get("location");
            if (loc == null) return;

            double lat = getDouble(loc, "latitude", 0.0);
            double lng = getDouble(loc, "longitude", 0.0);

            // ── Validation coordonnées — rejette les valeurs par défaut FastAPI ──
            // lat=-90/lng=-180 = défaut FastAPI = pas de vraies coords
            // On accepte toute valeur réaliste y compris proche de 0
            // (ex: Afrique équatoriale est une vraie position)
            boolean validCoords = lat != -90.0 && lng != -180.0
                    && lat >= -85.0 && lat <= 85.0
                    && lng > -180.0 && lng < 180.0;

            if (validCoords && mapReady && mapView != null) {
                final double fLat = lat, fLng = lng;
                final String fRecordedAt = recordedAt;
                runOnUiThread(() -> addFreePhotoMarkerToMap(bitmap, fLat, fLng, fRecordedAt));
            }

        } catch (Exception e) {
            android.util.Log.e(TAG, "Erreur parse photo", e);
        }
    }

    private void addFreePhotoMarkerToMap(Bitmap bitmap, double lat, double lng, String recordedAt) {
        Marker marker = new Marker(mapView);
        marker.setPosition(new GeoPoint(lat, lng));
        marker.setTitle("📸 Photo souvenir");
        marker.setSnippet(recordedAt != null ? recordedAt : "");
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // Marqueur bleu pour distinguer des POI rouges
        marker.setIcon(createPhotoMarkerIcon(bitmap));
        marker.setOnMarkerClickListener((m, map) -> { showFullPhoto(bitmap); return true; });
        mapView.getOverlays().add(marker);
        mapView.invalidate();
    }

    // =========================================================================
    // Galerie photos — format adaptatif paysage/portrait
    // =========================================================================

    private void refreshPhotoGallery() {
        LinearLayout galleryLayout = findViewById(R.id.layoutPhotoGallery);
        HorizontalScrollView scrollView = findViewById(R.id.scrollPhotoGallery);
        if (galleryLayout == null || scrollView == null) return;
        galleryLayout.removeAllViews();
        if (freePhotoBitmaps.isEmpty()) { scrollView.setVisibility(View.GONE); return; }
        scrollView.setVisibility(View.VISIBLE);
        for (Bitmap bitmap : freePhotoBitmaps) addBitmapToGallery(galleryLayout, bitmap);
    }

    private void addBitmapToGallery(LinearLayout galleryLayout, Bitmap bitmap) {
        float d = getResources().getDisplayMetrics().density;
        // Hauteur fixe 130dp, largeur calculée selon ratio réel de la photo
        int fixedH = Math.round(130 * d);
        float ratio = (float) bitmap.getWidth() / bitmap.getHeight();
        int computedW = Math.round(fixedH * ratio);
        // Min 90dp, max 220dp
        int finalW = Math.max(Math.round(90*d), Math.min(computedW, Math.round(220*d)));

        ImageView img = new ImageView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(finalW, fixedH);
        p.setMargins(0, 0, Math.round(10*d), 0);
        img.setLayoutParams(p);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageBitmap(bitmap);
        img.setClipToOutline(true);
        img.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.round(10*d));
            }
        });
        img.setOnClickListener(v -> showFullPhoto(bitmap));
        galleryLayout.addView(img);
    }

    private void refreshPoiList() {
        if (layoutPoisContainer == null || currentTrip == null) return;
        layoutPoisContainer.removeAllViews();
        for (Poi poi : currentTrip.getPois()) addPoiView(layoutPoisContainer, poi);
    }

    // =========================================================================
    // Dialog POI
    // =========================================================================

    private void showPoiDialog(Poi poi) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Math.round(22*d), Math.round(18*d), Math.round(22*d), Math.round(12*d));

        TextView title = new TextView(this);
        title.setText(poi.getName()); title.setTextSize(20); title.setTextColor(0xFF1A1A2E);
        title.setTypeface(null, android.graphics.Typeface.BOLD); layout.addView(title);

        TextView type = new TextView(this);
        type.setText(poi.getType() + " • " + poi.getRatingStars());
        type.setTextSize(14); type.setTextColor(0xFF6C63FF);
        type.setPadding(0, Math.round(4*d), 0, Math.round(10*d)); layout.addView(type);

        // Photo POI au bon format
        Bitmap poiBitmap = decodeBitmap(poi.getPhotoBase64());
        if (poiBitmap != null) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(poiBitmap);
            // Format adaptatif : largeur match_parent, hauteur proportionnelle
            float poiRatio = (float) poiBitmap.getWidth() / poiBitmap.getHeight();
            int poiW = (int)(getResources().getDisplayMetrics().widthPixels * 0.75f);
            int poiH = Math.round(poiW / poiRatio);
            // Limiter hauteur max à 200dp
            int maxH = Math.round(200 * d);
            if (poiH > maxH) { poiH = maxH; poiW = Math.round(poiH * poiRatio); }
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(poiW, poiH);
            pp.setMargins(0, 0, 0, Math.round(12*d));
            img.setLayoutParams(pp);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setOnClickListener(v -> showFullPhoto(poiBitmap));
            layout.addView(img);
        }

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView comment = new TextView(this);
            comment.setText(poi.getComment()); comment.setTextSize(14); comment.setTextColor(0xFF333344);
            comment.setPadding(0, 0, 0, Math.round(10*d)); layout.addView(comment);
        }

        List<Bitmap> linkedPhotos = photosByPoiId.get(poi.getPoiId());
        if (linkedPhotos != null && !linkedPhotos.isEmpty()) {
            TextView st = new TextView(this);
            st.setText("Photos souvenirs prises ici"); st.setTextSize(16); st.setTextColor(0xFF1A1A2E);
            st.setTypeface(null, android.graphics.Typeface.BOLD);
            st.setPadding(0, Math.round(12*d), 0, Math.round(8*d)); layout.addView(st);
            HorizontalScrollView sv = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (Bitmap bmp : linkedPhotos) {
                ImageView iv = new ImageView(this);
                // Format adaptatif pour les photos liées aussi
                float r = (float) bmp.getWidth() / bmp.getHeight();
                int fH = Math.round(90 * d);
                int fW = Math.round(fH * r);
                fW = Math.max(Math.round(60*d), Math.min(fW, Math.round(150*d)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(fW, fH);
                lp.setMargins(0, 0, Math.round(8*d), 0);
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                iv.setOnClickListener(v -> showFullPhoto(bmp));
                row.addView(iv);
            }
            sv.addView(row); layout.addView(sv);
        }

        TextView coords = new TextView(this);
        coords.setText("GPS : " + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLat())
                + ", " + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLng()));
        coords.setTextSize(12); coords.setTextColor(0xFF777788);
        coords.setPadding(0, Math.round(10*d), 0, 0); layout.addView(coords);

        new AlertDialog.Builder(this).setView(layout).setPositiveButton("Fermer", null).show();
    }

    // =========================================================================
    // POI liste — photo au bon format paysage/portrait
    // =========================================================================

    private void addPoiView(LinearLayout container, Poi poi) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Math.round(16*d), Math.round(16*d), Math.round(16*d), Math.round(16*d));
        card.setBackgroundColor(0xFF1E1E35);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, Math.round(12*d)); card.setLayoutParams(cp);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvName = new TextView(this); tvName.setText(poi.getName());
        tvName.setTextSize(16); tvName.setTextColor(0xFFFFFFFF);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(tvName);
        TextView tvType = new TextView(this); tvType.setText(poi.getType());
        tvType.setTextSize(11); tvType.setTextColor(0xFFA89CFF);
        tvType.setPadding(Math.round(8*d), Math.round(2*d), Math.round(8*d), Math.round(2*d));
        tvType.setBackgroundColor(0xFF2D2D50);
        headerRow.addView(tvType); card.addView(headerRow);

        TextView tvRating = new TextView(this); tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14); tvRating.setTextColor(0xFFD97706);
        tvRating.setPadding(0, Math.round(6*d), 0, Math.round(2*d)); card.addView(tvRating);

        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this); tvComment.setText(poi.getComment());
            tvComment.setTextSize(13); tvComment.setTextColor(0xFF9999BB);
            tvComment.setPadding(0, Math.round(4*d), 0, Math.round(6*d)); card.addView(tvComment);
        }

        // ── Photo POI — format adaptatif selon ratio réel ────────────────────
        Bitmap poiBitmap = decodeBitmap(poi.getPhotoBase64());
        if (poiBitmap != null) {
            ImageView imgPoi = new ImageView(this);
            // Hauteur fixe 120dp, largeur proportionnelle au ratio
            int fixH = Math.round(120 * d);
            float ratio = (float) poiBitmap.getWidth() / poiBitmap.getHeight();
            int fixW = Math.round(fixH * ratio);
            fixW = Math.max(Math.round(80*d), Math.min(fixW, Math.round(280*d)));
            LinearLayout.LayoutParams imgP = new LinearLayout.LayoutParams(fixW, fixH);
            imgP.setMargins(0, Math.round(6*d), 0, Math.round(6*d));
            imgPoi.setLayoutParams(imgP);
            imgPoi.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgPoi.setImageBitmap(poiBitmap);
            imgPoi.setClipToOutline(true);
            imgPoi.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.round(8*d));
                }
            });
            imgPoi.setOnClickListener(v -> showFullPhoto(poiBitmap));
            card.addView(imgPoi);
        }

        List<Bitmap> linkedPhotos = photosByPoiId.get(poi.getPoiId());
        int linkedCount = linkedPhotos == null ? 0 : linkedPhotos.size();
        TextView tvLinked = new TextView(this);
        tvLinked.setText("📸 " + linkedCount + " photo(s) souvenir liée(s)");
        tvLinked.setTextSize(12); tvLinked.setTextColor(0xFFA89CFF);
        tvLinked.setPadding(0, Math.round(6*d), 0, 0); card.addView(tvLinked);

        TextView tvCoords = new TextView(this);
        tvCoords.setText("GPS : " + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLat())
                + ", " + String.format(java.util.Locale.FRANCE, "%.5f", poi.getLng()));
        tvCoords.setTextSize(10); tvCoords.setTextColor(0xFF555570);
        tvCoords.setPadding(0, Math.round(6*d), 0, 0); card.addView(tvCoords);

        card.setOnClickListener(v -> showPoiDialog(poi));
        container.addView(card);
    }

    // =========================================================================
    // Icônes carte
    // =========================================================================

    private BitmapDrawable createPoiPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(78*d), h = Math.round(92*d);
        int imgSize = Math.round(64*d), border = Math.round(3*d);
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG); shadow.setColor(0x55000000);
        canvas.drawRoundRect(Math.round(6*d), Math.round(6*d), w-Math.round(6*d), imgSize+Math.round(14*d), Math.round(14*d), Math.round(14*d), shadow);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(Color.WHITE);
        canvas.drawRoundRect(Math.round(4*d), Math.round(2*d), w-Math.round(4*d), imgSize+Math.round(10*d), Math.round(14*d), Math.round(14*d), bg);
        int left=(w-imgSize)/2, top=Math.round(6*d), inner=imgSize-border*2;
        float sc=Math.max((float)inner/photo.getWidth(),(float)inner/photo.getHeight());
        int sW=Math.round(photo.getWidth()*sc), sH=Math.round(photo.getHeight()*sc);
        Bitmap scaled=Bitmap.createScaledBitmap(photo,sW,sH,true);
        int cX=Math.max(0,(sW-inner)/2), cY=Math.max(0,(sH-inner)/2);
        int cW=Math.min(inner,sW-cX), cH=Math.min(inner,sH-cY);
        if (cW>0&&cH>0) {
            Bitmap cropped=Bitmap.createBitmap(scaled,cX,cY,cW,cH);
            android.graphics.RectF rect=new android.graphics.RectF(left+border,top+border,left+border+inner,top+border+inner);
            canvas.save();
            android.graphics.Path p2=new android.graphics.Path(); p2.addRoundRect(rect,Math.round(10*d),Math.round(10*d),android.graphics.Path.Direction.CW);
            canvas.clipPath(p2); canvas.drawBitmap(cropped,null,rect,null); canvas.restore();
        }
        Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG); pin.setColor(0xFFDC2626);
        android.graphics.Path pinPath=new android.graphics.Path();
        pinPath.moveTo(w/2f-Math.round(12*d),imgSize+Math.round(6*d));
        pinPath.lineTo(w/2f+Math.round(12*d),imgSize+Math.round(6*d));
        pinPath.lineTo(w/2f,h-Math.round(4*d)); pinPath.close();
        canvas.drawPath(pinPath,pin);
        Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG); dot.setColor(Color.WHITE);
        canvas.drawCircle(w/2f,imgSize+Math.round(10*d),Math.round(4*d),dot);
        return new BitmapDrawable(getResources(),result);
    }

    private BitmapDrawable createModernPoiMarker() {
        float d = getResources().getDisplayMetrics().density;
        int w=Math.round(54*d), h=Math.round(68*d);
        Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bmp);
        Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG); shadow.setColor(0x55000000);
        canvas.drawCircle(w/2f,Math.round(24*d),Math.round(19*d),shadow);
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG); fill.setColor(0xFFDC2626);
        canvas.drawCircle(w/2f,Math.round(22*d),Math.round(18*d),fill);
        android.graphics.Path path=new android.graphics.Path();
        path.moveTo(w/2f-Math.round(13*d),Math.round(34*d));
        path.lineTo(w/2f+Math.round(13*d),Math.round(34*d));
        path.lineTo(w/2f,h-Math.round(6*d)); path.close();
        canvas.drawPath(path,fill);
        Paint white=new Paint(Paint.ANTI_ALIAS_FLAG); white.setColor(Color.WHITE);
        canvas.drawCircle(w/2f,Math.round(22*d),Math.round(7*d),white);
        return new BitmapDrawable(getResources(),bmp);
    }

    /**
     * Marqueur photo souvenir — BLEU pour distinguer des POI rouges.
     * Format adaptatif : paysage 80×52dp / portrait 44×64dp / carré 60×60dp
     * Pointe bleue en bas pour ancrage précis sur la carte.
     */
    private BitmapDrawable createPhotoMarkerIcon(Bitmap photo) {
        float d = getResources().getDisplayMetrics().density;
        int border = Math.round(3*d);

        // Format selon ratio de la photo
        float ratio = (float) photo.getWidth() / photo.getHeight();
        int mW, mH;
        if (ratio >= 1.2f)       { mW=Math.round(80*d); mH=Math.round(52*d); }
        else if (ratio <= 0.85f) { mW=Math.round(44*d); mH=Math.round(64*d); }
        else                     { mW=Math.round(60*d); mH=Math.round(60*d); }

        // Hauteur totale = image + pointe bleue (14dp)
        int pinH = Math.round(14*d);
        int totalH = mH + pinH;

        Bitmap result = Bitmap.createBitmap(mW, totalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);

        // Ombre
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG); shadow.setColor(0x44000000);
        canvas.drawRoundRect(Math.round(2*d), Math.round(3*d), mW, mH, Math.round(8*d), Math.round(8*d), shadow);

        // Fond blanc cadre photo
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG); bgPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, mW-Math.round(2*d), mH-Math.round(2*d), Math.round(8*d), Math.round(8*d), bgPaint);

        // Photo en centerCrop
        int innerW = mW-border*2-Math.round(2*d);
        int innerH = mH-border*2-Math.round(2*d);
        float sc = Math.max((float)innerW/photo.getWidth(), (float)innerH/photo.getHeight());
        int sW=Math.round(photo.getWidth()*sc), sH=Math.round(photo.getHeight()*sc);
        Bitmap scaled = Bitmap.createScaledBitmap(photo, sW, sH, true);
        int cX=Math.max(0,(sW-innerW)/2), cY=Math.max(0,(sH-innerH)/2);
        int cW=Math.min(innerW,sW-cX), cH=Math.min(innerH,sH-cY);
        if (cW>0 && cH>0) {
            Bitmap cropped = Bitmap.createBitmap(scaled, cX, cY, cW, cH);
            android.graphics.RectF rect = new android.graphics.RectF(border, border, border+innerW, border+innerH);
            canvas.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(rect, Math.round(6*d), Math.round(6*d), android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(cropped, null, rect, null);
            canvas.restore();
        }

        // Pointe BLEUE en bas — distingue des POI rouges
        Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG); pinPaint.setColor(0xFF3B82F6);
        android.graphics.Path pinPath = new android.graphics.Path();
        pinPath.moveTo(mW/2f - Math.round(10*d), mH - Math.round(2*d));
        pinPath.lineTo(mW/2f + Math.round(10*d), mH - Math.round(2*d));
        pinPath.lineTo(mW/2f, totalH - Math.round(2*d));
        pinPath.close();
        canvas.drawPath(pinPath, pinPaint);

        // Point blanc sur la pointe
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG); dot.setColor(Color.WHITE);
        canvas.drawCircle(mW/2f, mH + Math.round(3*d), Math.round(3*d), dot);

        return new BitmapDrawable(getResources(), result);
    }

    // =========================================================================
    // QR Code
    // =========================================================================

    private void showQrDialog(Trip trip) {
        String tripUrl = API_BASE_URL + "/trip/" + trip.getId();
        Bitmap qrBitmap = generateQrCode(tripUrl, 600);
        if (qrBitmap == null) { Toast.makeText(this, "Erreur QR code", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout dl = new LinearLayout(this); dl.setOrientation(LinearLayout.VERTICAL);
        dl.setPadding(48,32,48,16); dl.setGravity(Gravity.CENTER);
        TextView tvT = new TextView(this); tvT.setText("Partager « " + trip.getTitle() + " »");
        tvT.setTextSize(16); tvT.setTextColor(0xFF1A1A2E);
        tvT.setTypeface(null, android.graphics.Typeface.BOLD);
        tvT.setGravity(Gravity.CENTER); tvT.setPadding(0,0,0,16); dl.addView(tvT);
        ImageView ivQr = new ImageView(this);
        int qrSize = Math.round(260*getResources().getDisplayMetrics().density);
        ivQr.setLayoutParams(new LinearLayout.LayoutParams(qrSize,qrSize));
        ivQr.setImageBitmap(qrBitmap); ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER); dl.addView(ivQr);
        TextView tvUrl = new TextView(this); tvUrl.setText(tripUrl);
        tvUrl.setTextSize(10); tvUrl.setTextColor(0xFF6C63FF); tvUrl.setGravity(Gravity.CENTER); tvUrl.setPadding(0,12,0,0); dl.addView(tvUrl);
        TextView tvI = new TextView(this); tvI.setText("Scannez ce QR code pour accéder au voyage");
        tvI.setTextSize(11); tvI.setTextColor(0xFF9999BB); tvI.setGravity(Gravity.CENTER); tvI.setPadding(0,4,0,16); dl.addView(tvI);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dl)
                .setPositiveButton("Fermer",null).setNeutralButton("📤 Partager le lien",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> shareLink(trip.getTitle(), tripUrl));
    }

    private Bitmap generateQrCode(String content, int size) {
        try {
            BitMatrix bm = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x=0;x<size;x++) for (int y=0;y<size;y++)
                bitmap.setPixel(x,y,bm.get(x,y)?0xFF6C63FF:0xFFFFFFFF);
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
    // Utilitaires
    // =========================================================================

    private Bitmap decodeBitmap(String base64) {
        try {
            if (base64 == null || base64.isEmpty()) return null;
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    private void showFullPhoto(Bitmap bitmap) {
        ImageView img = new ImageView(this); img.setImageBitmap(bitmap);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER); img.setPadding(16,16,16,16);
        new AlertDialog.Builder(this).setView(img).setPositiveButton("Fermer",null).show();
    }

    private double calculateTotalDistance(Trip trip) {
        double total=0; List<GpsPoint> pts=trip.getGpsPoints();
        for (int i=1;i<pts.size();i++) total+=pts.get(i-1).distanceTo(pts.get(i));
        return total;
    }

    private String getString(Map<String,Object> map, String key, String def) {
        Object v=map.get(key); return v==null?def:String.valueOf(v);
    }

    private double getDouble(Map<String,Object> map, String key, double def) {
        Object v=map.get(key);
        if (v instanceof Number) return ((Number)v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    @Override protected void onResume() { super.onResume(); if (mapView!=null) mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (mapView!=null) mapView.onPause(); }
}