package com.example.smarttrip;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.api.LocationDto;
import com.example.smarttrip.api.PhotoDto;
import com.example.smarttrip.api.PoiDto;
import com.example.smarttrip.db.AppDatabase;
import com.example.smarttrip.db.PhotoEntity;
import com.example.smarttrip.db.PoiEntity;

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
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ActiveTripActivity — se bind au GpsTrackingService pour recevoir les mises à jour GPS.
 *
 * ARCHITECTURE :
 * - GpsTrackingService tourne en ForegroundService (arrière-plan, écran éteint)
 * - ActiveTripActivity se bind au service pour afficher les compteurs en temps réel
 * - Si l'utilisateur quitte l'app, le service continue, l'Activity se déconnecte
 * - Au retour dans l'app, l'Activity se rebind et récupère l'état actuel
 */
public class ActiveTripActivity extends AppCompatActivity
        implements GpsTrackingService.GpsUpdateListener {

    private TextView tvTripStatus, tvGpsCount, tvPoiCount, tvCloudCount;
    private TextView tvBatteryLive, tvPhotoCount, tvPhotoLimit;
    private Button btnAddPoi, btnStopTrip, btnDeleteLastPhoto;
    private ImageView currentImgPreview;
    private TextView currentTvPhotoStatus;

    private String tripName;
    private long tripStartTime;
    private int memoryPhotosCount = 0;

    // ── Service binding ───────────────────────────────────────────────────────
    private GpsTrackingService gpsService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            GpsTrackingService.LocalBinder localBinder = (GpsTrackingService.LocalBinder) binder;
            gpsService = localBinder.getService();
            gpsService.setGpsUpdateListener(ActiveTripActivity.this);
            serviceBound = true;

            // Sync UI avec l'état actuel du service (si on revient dans l'app)
            int pts = gpsService.getCollectedPoints().size();
            int cloud = gpsService.getCloudPointsSent();
            if (pts > 0) tvGpsCount.setText(pts + " pts GPS");
            if (cloud > 0) tvCloudCount.setText(cloud + " points envoyés au cloud");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            gpsService = null;
        }
    };
    // ──────────────────────────────────────────────────────────────────────────

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
    private static final int REQUEST_NOTIFICATION_PERM   = 100;
    private static final int MAX_PHOTOS = 20;

    static final double BASE_LAT = 48.8014;
    static final double BASE_LNG = 2.1301;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private AppDatabase localDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripStartTime = System.currentTimeMillis();
        localDb = AppDatabase.getInstance(this);

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
            tripName = "Voyage du " + new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                    .format(new Date());
        tvTripStatus.setText("● " + tripName);

        btnAddPoi.setOnClickListener(v -> showAddPoiDialog());
        btnTakeMemoryPhoto.setOnClickListener(v -> takeMemoryPhoto());
        btnPickMemoryGallery.setOnClickListener(v -> pickMemoryFromGallery());
        btnDeleteLastPhoto.setOnClickListener(v -> showPhotosManager());
        btnStopTrip.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Terminer le voyage ?")
                        .setPositiveButton("Terminer", (d, w) -> stopTrip())
                        .setNegativeButton("Continuer", null)
                        .show());

        // Demander les permissions puis démarrer le service
        requestPermissionsAndStartService();

        // Batterie live (rafraîchi toutes les 30s)
        handler.post(batteryUpdater);
    }

    // =========================================================================
    // Permissions + démarrage du ForegroundService
    // =========================================================================

    private void requestPermissionsAndStartService() {
        List<String> needed = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);

        // Android 13+ : permission notification obligatoire
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQUEST_LOCATION_PERMISSION);
        } else {
            startAndBindService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            boolean gpsGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (gpsGranted) {
                startAndBindService();
            } else {
                Toast.makeText(this, "Permission GPS refusée", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Démarre le ForegroundService puis s'y connecte (bind).
     * Le service tourne même si l'Activity est détruite.
     */
    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, GpsTrackingService.class);
        serviceIntent.putExtra(GpsTrackingService.EXTRA_TRIP_NAME, tripName);
        serviceIntent.putExtra(GpsTrackingService.EXTRA_TRIP_ID, "trip_" + tripStartTime);
        serviceIntent.putExtra(GpsTrackingService.EXTRA_USER_ID, "amin");

        // startForegroundService() obligatoire pour démarrer un ForegroundService
        ContextCompat.startForegroundService(this, serviceIntent);

        // Bind pour recevoir les callbacks GPS dans l'Activity
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    // =========================================================================
    // Callbacks GPS depuis le service → mise à jour UI
    // =========================================================================

    @Override
    public void onGpsPointCollected(GpsPoint point, int total, String accuracyLabel) {
        // Appelé depuis le service sur le thread principal
        runOnUiThread(() -> {
            tvGpsCount.setText(total + " pts GPS  " + accuracyLabel);
            tvBatteryLive.setText(BatteryHelper.getStatusMessage(this));
        });
    }

    @Override
    public void onCloudPointSent(int total) {
        runOnUiThread(() -> tvCloudCount.setText(total + " points envoyés au cloud"));
    }

    // =========================================================================
    // Lifecycle — bind/unbind
    // =========================================================================

    @Override
    protected void onStart() {
        super.onStart();
        // Rebind si le service tourne déjà (retour dans l'app)
        if (!serviceBound) {
            Intent serviceIntent = new Intent(this, GpsTrackingService.class);
            bindService(serviceIntent, serviceConnection, 0); // 0 = pas de démarrage auto
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind quand l'Activity passe en arrière-plan
        // Le SERVICE continue de tourner — seul le callback UI est déconnecté
        if (serviceBound) {
            if (gpsService != null) gpsService.setGpsUpdateListener(null);
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    // =========================================================================
    // Arrêt du voyage
    // =========================================================================

    private void stopTrip() {
        if (serviceBound && gpsService != null) {
            int pts = gpsService.getCollectedPoints().size();
            gpsService.stopTrip(); // arrête le service proprement
        }
        // Arrêter le service explicitement
        stopService(new Intent(this, GpsTrackingService.class));
        Toast.makeText(this, "Voyage terminé ✓", Toast.LENGTH_LONG).show();
        finish();
    }

    // =========================================================================
    // Batterie live — rafraîchi toutes les 30 secondes
    // =========================================================================

    private final Runnable batteryUpdater = new Runnable() {
        @Override public void run() {
            if (tvBatteryLive != null)
                tvBatteryLive.setText(BatteryHelper.getStatusMessage(ActiveTripActivity.this));
            handler.postDelayed(this, 30_000);
        }
    };

    // =========================================================================
    // POI Dialog
    // =========================================================================

    private void showAddPoiDialog() {
        // Récupérer la dernière position connue depuis le service
        GpsPoint lastPoint = null;
        if (serviceBound && gpsService != null) {
            List<GpsPoint> pts = gpsService.getCollectedPoints();
            if (!pts.isEmpty()) lastPoint = pts.get(pts.size() - 1);
        }

        if (lastPoint == null) {
            Toast.makeText(this, "Attendez au moins un point GPS", Toast.LENGTH_SHORT).show();
            return;
        }

        final GpsPoint finalLastPoint = lastPoint;

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

        String[] types = {"restaurant","monument","hotel","nature","parc","musée","autre"};
        spinnerType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types));

        photoBase64 = null; photoUri = null;
        btnPhoto.setOnClickListener(v -> dispatchTakePictureIntent(imgPreview, tvPhotoSt));
        btnGallery.setOnClickListener(v -> {
            currentImgPreview = imgPreview; currentTvPhotoStatus = tvPhotoSt;
            Intent intent = new Intent(Intent.ACTION_PICK); intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_GALLERY_POI);
        });

        tvCoords.setText("Position : " + String.format("%.5f", finalLastPoint.getLat())
                + ", " + String.format("%.5f", finalLastPoint.getLng()));

        new AlertDialog.Builder(this)
                .setTitle("Ajouter un point d'intérêt")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Le nom est obligatoire", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String type    = (String) spinnerType.getSelectedItem();
                    int    rating  = (int) ratingBar.getRating();
                    String comment = editComment.getText().toString().trim();
                    String poiPhoto = photoBase64;
                    Poi poi = new Poi(name, type, finalLastPoint.getLat(),
                            finalLastPoint.getLng(), rating, comment,
                            poiPhoto != null ? poiPhoto : "");
                    poi.setPhotoBase64(poiPhoto);
                    collectedPois.add(poi);
                    tvPoiCount.setText(collectedPois.size() + " POI");
                    savePoiToRoomAndSync(poi);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // =========================================================================
    // Photos souvenirs
    // =========================================================================

    private void takeMemoryPhoto() {
        if (serviceBound && gpsService != null
                && gpsService.getCollectedPoints().isEmpty()) {
            Toast.makeText(this, "Attendez un point GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Erreur fichier photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pickMemoryFromGallery() {
        if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos", Toast.LENGTH_SHORT).show();
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
        savePhotoToRoomAndSync(b64, lat, lng);
    }

    private void showPhotosManager() {
        if (memoryPhotosBase64.isEmpty()) {
            Toast.makeText(this, "Aucune photo souvenir", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(layout);

        for (int i = 0; i < memoryPhotosBase64.size(); i++) {
            final int idx = i;
            String b64 = memoryPhotosBase64.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            ImageView img = new ImageView(this);
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
            int size = (int)(80 * getResources().getDisplayMetrics().density);
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
            tvInfo.setText("Photo " + (i+1) + "\n"
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
                .setTitle("Mes photos (" + memoryPhotosBase64.size() + ")")
                .setView(sv)
                .setPositiveButton("Fermer", null)
                .show();
    }

    // =========================================================================
    // Room + Cloud — POI & Photo
    // =========================================================================

    private void savePoiToRoomAndSync(Poi poi) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());
        Executors.newSingleThreadExecutor().execute(() -> {
            PoiEntity entity = new PoiEntity();
            entity.userId = "amin"; entity.tripId = "trip_" + tripStartTime;
            entity.tripName = tripName; entity.name = poi.getName();
            entity.type = poi.getType(); entity.latitude = poi.getLat();
            entity.longitude = poi.getLng(); entity.rating = poi.getRating();
            entity.comment = poi.getComment(); entity.photoBase64 = poi.getPhotoBase64();
            entity.recordedAt = timestamp; entity.synced = false;
            localDb.poiDao().insert(entity);
            SyncWorker.schedule(this);
        });
        LocationDto loc = new LocationDto(poi.getLat(), poi.getLng(), 0.0);
        PoiDto dto = new PoiDto("amin", "trip_" + tripStartTime, tripName,
                poi.getName(), poi.getType(), loc, poi.getRating(),
                poi.getComment(), timestamp, photoBase64);
        ApiClient.getInstance().getApiService().sendPoi(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> c, Response<Map<String, Object>> r) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                r.isSuccessful() ? "POI envoyé ✓" : "POI sauvegardé localement",
                                Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onFailure(Call<Map<String, Object>> c, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                "POI sauvegardé — sync au retour réseau", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void savePhotoToRoomAndSync(String b64, double lat, double lng) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());
        Executors.newSingleThreadExecutor().execute(() -> {
            PhotoEntity entity = new PhotoEntity();
            entity.userId = "amin"; entity.tripId = "trip_" + tripStartTime;
            entity.tripName = tripName; entity.latitude = lat; entity.longitude = lng;
            entity.photoBase64 = b64; entity.recordedAt = timestamp; entity.synced = false;
            localDb.photoDao().insert(entity);
            SyncWorker.schedule(this);
        });
        LocationDto loc = new LocationDto(lat, lng, 0.0);
        PhotoDto dto = new PhotoDto("amin", "trip_" + tripStartTime, loc, b64, timestamp);
        ApiClient.getInstance().getApiService().sendPhoto(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> c, Response<Map<String, Object>> r) {
                        runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                                r.isSuccessful() ? "Photo envoyée ✓" : "Photo sauvegardée localement",
                                Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onFailure(Call<Map<String, Object>> c, Throwable t) {}
                });
    }

    // =========================================================================
    // onActivityResult
    // =========================================================================

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
                currentTvPhotoStatus.setText("✓ Photo prise");
        }
        if (requestCode == REQUEST_PICK_GALLERY_POI && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                photoBase64 = encodeImageToBase64(uri);
                if (currentImgPreview != null) { currentImgPreview.setVisibility(View.VISIBLE); currentImgPreview.setImageURI(uri); }
                if (currentTvPhotoStatus != null) currentTvPhotoStatus.setText("✓ Photo sélectionnée");
            }
        }
        if (requestCode == REQUEST_MEMORY_PHOTO && resultCode == RESULT_OK) {
            String b64 = encodeImageToBase64(memoryPhotoUri);
            if (b64 != null) {
                double lat = BASE_LAT, lng = BASE_LNG;
                if (serviceBound && gpsService != null && !gpsService.getCollectedPoints().isEmpty()) {
                    GpsPoint last = gpsService.getCollectedPoints().get(gpsService.getCollectedPoints().size() - 1);
                    lat = last.getLat(); lng = last.getLng();
                }
                addMemoryPhoto(b64, lat, lng);
            }
        }
        if (requestCode == REQUEST_PICK_MEMORY_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                double[] coords = readExifCoordinates(uri);
                if (coords == null) {
                    if (serviceBound && gpsService != null && !gpsService.getCollectedPoints().isEmpty()) {
                        GpsPoint last = gpsService.getCollectedPoints().get(gpsService.getCollectedPoints().size() - 1);
                        coords = new double[]{last.getLat(), last.getLng()};
                    } else coords = new double[]{BASE_LAT, BASE_LNG};
                }
                String b64 = encodeImageToBase64(uri);
                if (b64 != null) addMemoryPhoto(b64, coords[0], coords[1]);
            }
        }
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    private void dispatchTakePictureIntent(ImageView imgPreview, TextView tvStatus) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File f = createImageFile();
                photoUri = FileProvider.getUriForFile(this,
                        "com.example.smarttrip.fileprovider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                currentImgPreview = imgPreview; currentTvPhotoStatus = tvStatus;
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            } catch (IOException e) {
                Toast.makeText(this, "Erreur fichier photo", Toast.LENGTH_SHORT).show();
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
            float scale = Math.min((float)maxSize / bmp.getWidth(), (float)maxSize / bmp.getHeight());
            if (scale < 1) bmp = Bitmap.createScaledBitmap(bmp,
                    Math.round(bmp.getWidth()*scale), Math.round(bmp.getHeight()*scale), true);
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
}