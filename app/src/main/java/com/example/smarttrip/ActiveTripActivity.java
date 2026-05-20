package com.example.smarttrip;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.api.BatteryDto;
import com.example.smarttrip.api.GpsDataDto;
import com.example.smarttrip.api.LocationDto;
import com.example.smarttrip.api.PhotoDto;
import com.example.smarttrip.api.PoiDto;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActiveTripActivity extends AppCompatActivity {

    private TextView tvTripStatus, tvGpsCount, tvPoiCount, tvCloudCount;
    private TextView tvBatteryLive, tvPhotoCount, tvPhotoLimit;
    private Button btnAddPoi, btnStopTrip, btnDeleteLastPhoto;
    private ImageView currentImgPreview;
    private TextView currentTvPhotoStatus;

    private String tripName;
    private long tripStartTime;
    private boolean isCollecting = true;
    private int cloudPointsSent = 0;
    private int memoryPhotosCount = 0;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;
    private Location bestLocation = null;

    // ── FIX GPS SPAGHETTI ─────────────────────────────────────────────────────
    // On garde le dernier point ACCEPTÉ pour calculer la distance avec le suivant.
    // Un point est rejeté s'il est à plus de MAX_JUMP_METERS du précédent,
    // ce qui élimine les sauts dus au réseau en intérieur.
    // En extérieur (GPS précis) les points sont proches → jamais rejetés.
    private GpsPoint lastAcceptedPoint = null;
    private static final float MAX_JUMP_METERS = 50f; // saut max acceptable entre 2 points
    // ──────────────────────────────────────────────────────────────────────────

    private final List<GpsPoint> collectedPoints   = new ArrayList<>();
    private final List<Poi>      collectedPois      = new ArrayList<>();
    private final List<String>   memoryPhotosBase64 = new ArrayList<>();
    private final List<double[]> memoryPhotosCoords = new ArrayList<>();

    private Uri photoUri;
    private String photoBase64;
    private Uri memoryPhotoUri;

    private static final int REQUEST_IMAGE_CAPTURE       = 1001;
    private static final int REQUEST_MEMORY_PHOTO        = 1002;
    private static final int REQUEST_PICK_MEMORY_GALLERY = 1003;
    private static final int REQUEST_PICK_GALLERY_POI    = 1004;
    private static final int REQUEST_LOCATION_PERMISSION = 99;
    private static final int MAX_PHOTOS = 20;

    static final double BASE_LAT = 48.8014;
    static final double BASE_LNG = 2.1301;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripStartTime = System.currentTimeMillis();

        tvTripStatus       = findViewById(R.id.tvTripStatus);
        tvGpsCount         = findViewById(R.id.tvGpsCount);
        tvPoiCount         = findViewById(R.id.tvPoiCount);
        tvCloudCount       = findViewById(R.id.tvCloudCount);
        tvBatteryLive      = findViewById(R.id.tvBatteryLive);
        tvPhotoCount       = findViewById(R.id.tvPhotoCount);
        tvPhotoLimit       = findViewById(R.id.tvPhotoLimit);
        btnAddPoi          = findViewById(R.id.btnAddPoi);
        btnStopTrip        = findViewById(R.id.btnStopTrip);
        btnDeleteLastPhoto = findViewById(R.id.btnDeleteLastPhoto);
        Button btnTakeMemoryPhoto   = findViewById(R.id.btnTakeMemoryPhoto);
        Button btnPickMemoryGallery = findViewById(R.id.btnPickMemoryGallery);

        tripName = getIntent().getStringExtra("trip_name");
        if (tripName == null || tripName.isEmpty())
            tripName = "Voyage du " + new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
        tvTripStatus.setText("● " + tripName);

        btnAddPoi.setOnClickListener(v -> showAddPoiDialog());
        btnTakeMemoryPhoto.setOnClickListener(v -> takeMemoryPhoto());
        btnPickMemoryGallery.setOnClickListener(v -> pickMemoryFromGallery());
        btnDeleteLastPhoto.setOnClickListener(v -> showPhotosManager());
        btnStopTrip.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Terminer le voyage ?")
                        .setMessage("Le voyage sera sauvegardé avec "
                                + collectedPoints.size() + " points GPS et "
                                + collectedPois.size() + " POI.")
                        .setPositiveButton("Terminer", (d, w) -> stopTrip())
                        .setNegativeButton("Continuer", null)
                        .show());

        startGpsCollection();
    }

    private void startGpsCollection() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (!isCollecting) return;

                // Sélection du meilleur provider disponible
                if (!isBetterLocation(location, bestLocation)) return;
                bestLocation = location;

                if (!BatteryHelper.shouldCollectData(ActiveTripActivity.this)) {
                    isCollecting = false;
                    tvTripStatus.setText("⚠ Collecte suspendue — batterie faible");
                    locationManager.removeUpdates(locationListener);
                    return;
                }

                // ── FIX SPAGHETTI : filtre de saut spatial ────────────────────────
                // Si on a déjà un point accepté et que le nouveau est à plus de
                // MAX_JUMP_METERS, c'est un artefact réseau → on ignore ce point.
                // Le premier point est toujours accepté (lastAcceptedPoint == null).
                if (lastAcceptedPoint != null) {
                    float[] results = new float[1];
                    Location.distanceBetween(
                            lastAcceptedPoint.getLat(), lastAcceptedPoint.getLng(),
                            location.getLatitude(), location.getLongitude(),
                            results);
                    float distanceMeters = results[0];

                    if (distanceMeters > MAX_JUMP_METERS) {
                        // Point rejeté — on met à jour l'UI mais on n'enregistre pas
                        runOnUiThread(() -> tvGpsCount.setText(
                                collectedPoints.size() + " pts GPS  ⚡ saut " +
                                        Math.round(distanceMeters) + "m ignoré"));
                        return;
                    }
                }
                // ──────────────────────────────────────────────────────────────────

                lastKnownLocation = location;
                GpsPoint point = new GpsPoint(
                        location.getLatitude(), location.getLongitude(),
                        System.currentTimeMillis() / 1000,
                        location.getAltitude(), location.getAccuracy());
                collectedPoints.add(point);
                lastAcceptedPoint = point; // mémoriser pour le prochain filtre

                // Label qualité GPS visible dans l'UI
                float acc = location.getAccuracy();
                String accuracyLabel;
                if (acc <= 10f)      accuracyLabel = "● précis (" + Math.round(acc) + "m)";
                else if (acc <= 50f) accuracyLabel = "◐ moyen (" + Math.round(acc) + "m)";
                else                 accuracyLabel = "○ réseau (" + Math.round(acc) + "m)";

                runOnUiThread(() -> {
                    tvGpsCount.setText(collectedPoints.size() + " pts GPS  " + accuracyLabel);
                    tvBatteryLive.setText(BatteryHelper.getStatusMessage(ActiveTripActivity.this));
                });

                sendGpsToCloud(point);
            }

            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {
                runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                        "GPS désactivé — activez la localisation", Toast.LENGTH_LONG).show());
            }
        };

        // GPS satellite — précis en extérieur
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000, 5f, locationListener);

        // Réseau — fallback en intérieur
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
    }

    private boolean isBetterLocation(Location location, Location currentBest) {
        if (currentBest == null) return true;
        long timeDelta = location.getTime() - currentBest.getTime();
        boolean isSignificantlyNewer = timeDelta > 10_000;
        boolean isSignificantlyOlder = timeDelta < -10_000;
        boolean isNewer = timeDelta > 0;
        if (isSignificantlyNewer) return true;
        if (isSignificantlyOlder) return false;
        int accuracyDelta = (int)(location.getAccuracy() - currentBest.getAccuracy());
        if (accuracyDelta < 0) return true;
        if (isNewer && accuracyDelta <= 200) return true;
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGpsCollection();
        } else {
            Toast.makeText(this,
                    "Permission GPS refusée — la collecte ne fonctionnera pas",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sendGpsToCloud(GpsPoint point) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).format(new Date());
        LocationDto location = new LocationDto(point.getLat(), point.getLng(), point.getAltitude());
        BatteryDto battery = new BatteryDto(BatteryHelper.getBatteryLevel(this), BatteryHelper.isCharging(this));
        GpsDataDto dto = new GpsDataDto("amin", "trip_" + tripStartTime, tripName, location, battery, timestamp);

        ApiClient.getInstance().getApiService().sendGps(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            cloudPointsSent++;
                            runOnUiThread(() -> tvCloudCount.setText(cloudPointsSent + " points envoyés au cloud"));
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                "Erreur GPS cloud : " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void sendPoiToCloud(Poi poi) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).format(new Date());
        LocationDto location = new LocationDto(poi.getLat(), poi.getLng(), 0.0);
        PoiDto dto = new PoiDto("amin", "trip_" + tripStartTime, tripName,
                poi.getName(), poi.getType(), location,
                poi.getRating(), poi.getComment(), timestamp, photoBase64);

        ApiClient.getInstance().getApiService().sendPoi(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                response.isSuccessful()
                                        ? "POI « " + poi.getName() + " » envoyé ✓"
                                        : "Erreur envoi POI",
                                Toast.LENGTH_SHORT).show());
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                "POI non envoyé : " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void sendMemoryPhotoToCloud(String b64, double lat, double lng) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).format(new Date());
        LocationDto location = new LocationDto(lat, lng, 0.0);
        PhotoDto dto = new PhotoDto("amin", "trip_" + tripStartTime, location, b64, timestamp);

        ApiClient.getInstance().getApiService().sendPhoto(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                response.isSuccessful() ? "Photo envoyée ✓" : "Erreur envoi photo",
                                Toast.LENGTH_SHORT).show());
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                "Photo non envoyée", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void showAddPoiDialog() {
        if (lastKnownLocation == null && collectedPoints.isEmpty()) {
            Toast.makeText(this, "Attendez au moins un point GPS avant d'ajouter un POI",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_poi, null);
        EditText editName    = dialogView.findViewById(R.id.editPoiName);
        Spinner spinnerType  = dialogView.findViewById(R.id.spinnerPoiType);
        RatingBar ratingBar  = dialogView.findViewById(R.id.ratingBarPoi);
        EditText editComment = dialogView.findViewById(R.id.editPoiComment);
        TextView tvCoords    = dialogView.findViewById(R.id.tvPoiCoords);
        Button btnPhoto      = dialogView.findViewById(R.id.btnTakePhotoPoi);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPoiPreview);
        TextView tvPhotoSt   = dialogView.findViewById(R.id.tvPhotoStatus);
        Button btnGallery    = dialogView.findViewById(R.id.btnPickGalleryPoi);

        String[] types = {"restaurant", "monument", "hotel", "nature", "parc", "musée", "autre"};
        spinnerType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types));

        photoBase64 = null;
        photoUri = null;

        btnPhoto.setOnClickListener(v -> dispatchTakePictureIntent(imgPreview, tvPhotoSt));
        btnGallery.setOnClickListener(v -> {
            currentImgPreview = imgPreview;
            currentTvPhotoStatus = tvPhotoSt;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_GALLERY_POI);
        });

        final double lat, lng;
        if (lastKnownLocation != null) {
            lat = lastKnownLocation.getLatitude();
            lng = lastKnownLocation.getLongitude();
        } else {
            GpsPoint last = collectedPoints.get(collectedPoints.size() - 1);
            lat = last.getLat();
            lng = last.getLng();
        }
        tvCoords.setText("Position : " + String.format("%.5f", lat) + ", " + String.format("%.5f", lng));

        new AlertDialog.Builder(this)
                .setTitle("Ajouter un point d'intérêt")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Le nom est obligatoire", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String type = (String) spinnerType.getSelectedItem();
                    int rating = (int) ratingBar.getRating();
                    String comment = editComment.getText().toString().trim();
                    // On stocke le base64 de la photo dans le POI pour l'afficher dans TripDetails
                    String poiPhoto = photoBase64;
                    Poi poi = new Poi(name, type, lat, lng, rating, comment, poiPhoto != null ? poiPhoto : "");
                    collectedPois.add(poi);
                    tvPoiCount.setText(collectedPois.size() + " POI");
                    sendPoiToCloud(poi);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void takeMemoryPhoto() {
        if (lastKnownLocation == null && collectedPoints.isEmpty()) {
            Toast.makeText(this, "Attendez au moins un point GPS avant de prendre une photo",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos atteinte", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File f = createImageFile();
                memoryPhotoUri = FileProvider.getUriForFile(this,
                        "com.example.smarttrip.fileprovider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, memoryPhotoUri);
                startActivityForResult(intent, REQUEST_MEMORY_PHOTO);
            } catch (IOException e) {
                Toast.makeText(this, "Erreur création fichier photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pickMemoryFromGallery() {
        if (lastKnownLocation == null && collectedPoints.isEmpty()) {
            Toast.makeText(this, "Attendez au moins un point GPS avant d'ajouter une photo",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos atteinte", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_MEMORY_GALLERY);
    }

    private void addMemoryPhoto(String b64, double lat, double lng) {
        memoryPhotosBase64.add(b64);
        memoryPhotosCoords.add(new double[]{lat, lng});
        memoryPhotosCount++;
        tvPhotoCount.setText(memoryPhotosCount + " photo(s) prise(s)");
        tvPhotoLimit.setText(memoryPhotosCount + " / " + MAX_PHOTOS + " photos");
        btnDeleteLastPhoto.setVisibility(View.VISIBLE);
        sendMemoryPhotoToCloud(b64, lat, lng);
    }

    private void showPhotosManager() {
        if (memoryPhotosBase64.isEmpty()) {
            Toast.makeText(this, "Aucune photo souvenir", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(layout);

        for (int i = 0; i < memoryPhotosBase64.size(); i++) {
            final int idx = i;
            String b64 = memoryPhotosBase64.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            ImageView img = new ImageView(this);
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            img.setImageBitmap(bmp);
            int size = (int) (80 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.setMargins(0, 0, 24, 0);
            img.setLayoutParams(p);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(img);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvInfo = new TextView(this);
            double[] coords = memoryPhotosCoords.get(i);
            tvInfo.setText("Photo " + (i + 1) + "\n"
                    + String.format("%.5f, %.5f", coords[0], coords[1]));
            tvInfo.setTextSize(13);
            info.addView(tvInfo);

            Button btnDel = new Button(this);
            btnDel.setText("Supprimer");
            btnDel.setTextColor(0xFFDC2626);
            btnDel.setBackgroundColor(0x00000000);
            btnDel.setOnClickListener(v -> {
                memoryPhotosBase64.remove(idx);
                memoryPhotosCoords.remove(idx);
                memoryPhotosCount--;
                tvPhotoCount.setText(memoryPhotosCount + " photo(s) prise(s)");
                tvPhotoLimit.setText(memoryPhotosCount + " / " + MAX_PHOTOS + " photos");
                if (memoryPhotosBase64.isEmpty()) btnDeleteLastPhoto.setVisibility(View.GONE);
                Toast.makeText(this, "Photo supprimée", Toast.LENGTH_SHORT).show();
            });
            info.addView(btnDel);
            row.addView(info);
            layout.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(0xFFE5E7EB);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            layout.addView(divider);
        }

        new AlertDialog.Builder(this)
                .setTitle("Mes photos souvenirs (" + memoryPhotosBase64.size() + ")")
                .setView(scrollView)
                .setPositiveButton("Fermer", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            photoBase64 = encodeImageToBase64(photoUri);
            if (currentImgPreview != null && photoUri != null) {
                currentImgPreview.setVisibility(View.VISIBLE);
                currentImgPreview.setImageURI(photoUri);
            }
            if (currentTvPhotoStatus != null)
                currentTvPhotoStatus.setText("✓ Photo prise — sera envoyée avec le POI");
        }

        if (requestCode == REQUEST_PICK_GALLERY_POI && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                photoBase64 = encodeImageToBase64(uri);
                if (currentImgPreview != null) {
                    currentImgPreview.setVisibility(View.VISIBLE);
                    currentImgPreview.setImageURI(uri);
                }
                if (currentTvPhotoStatus != null)
                    currentTvPhotoStatus.setText("✓ Photo sélectionnée depuis la galerie");
            }
        }

        if (requestCode == REQUEST_MEMORY_PHOTO && resultCode == Activity.RESULT_OK) {
            String b64 = encodeImageToBase64(memoryPhotoUri);
            if (b64 != null) {
                double lat = lastKnownLocation != null ? lastKnownLocation.getLatitude() : BASE_LAT;
                double lng = lastKnownLocation != null ? lastKnownLocation.getLongitude() : BASE_LNG;
                addMemoryPhoto(b64, lat, lng);
            }
        }

        if (requestCode == REQUEST_PICK_MEMORY_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                double[] coords = readExifCoordinates(uri);
                if (coords == null) {
                    if (lastKnownLocation != null)
                        coords = new double[]{lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()};
                    else if (!collectedPoints.isEmpty()) {
                        GpsPoint last = collectedPoints.get(collectedPoints.size() - 1);
                        coords = new double[]{last.getLat(), last.getLng()};
                    } else coords = new double[]{BASE_LAT, BASE_LNG};
                }
                String b64 = encodeImageToBase64(uri);
                if (b64 != null) addMemoryPhoto(b64, coords[0], coords[1]);
            }
        }
    }

    private void dispatchTakePictureIntent(ImageView imgPreview, TextView tvStatus) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File f = createImageFile();
                photoUri = FileProvider.getUriForFile(this,
                        "com.example.smarttrip.fileprovider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                currentImgPreview = imgPreview;
                currentTvPhotoStatus = tvStatus;
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException e) {
                Toast.makeText(this, "Erreur création fichier photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
        return File.createTempFile("SMARTTRIP_" + ts, ".jpg", getExternalFilesDir("Pictures"));
    }

    private String encodeImageToBase64(Uri uri) {
        try {
            Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            int maxSize = 800;
            float scale = Math.min((float) maxSize / bmp.getWidth(), (float) maxSize / bmp.getHeight());
            if (scale < 1) bmp = Bitmap.createScaledBitmap(bmp,
                    Math.round(bmp.getWidth() * scale), Math.round(bmp.getHeight() * scale), true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) { return null; }
    }

    private double[] readExifCoordinates(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            if (stream == null) return null;
            androidx.exifinterface.media.ExifInterface exif =
                    new androidx.exifinterface.media.ExifInterface(stream);
            double[] latLng = exif.getLatLong();
            stream.close();
            return latLng;
        } catch (Exception e) { return null; }
    }

    private void stopTrip() {
        isCollecting = false;
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null && locationListener != null)
            locationManager.removeUpdates(locationListener);
        Toast.makeText(this,
                "Voyage terminé : " + collectedPoints.size() + " GPS, "
                        + collectedPois.size() + " POI, "
                        + cloudPointsSent + " envoyés au cloud",
                Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null && locationListener != null)
            locationManager.removeUpdates(locationListener);
    }
}
