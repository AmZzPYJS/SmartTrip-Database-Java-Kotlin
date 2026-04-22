package com.example.smarttrip;

import android.os.Bundle;
import android.widget.ImageView;
import android.os.Handler;
import java.io.InputStream;
import android.os.Looper;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import com.example.smarttrip.api.PhotoDto;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.api.BatteryDto;
import com.example.smarttrip.api.GpsDataDto;
import com.example.smarttrip.api.LocationDto;
import com.example.smarttrip.api.PoiDto;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Écran de voyage en cours — collecte GPS active + ajout de POI.
 *
 * Fonctionnalités :
 * - Collecte GPS simulée toutes les 5s (envoi cloud automatique)
 * - Ajout manuel de POI via formulaire (envoi cloud)
 * - Surveillance batterie avec arrêt automatique sous 15%
 * - Compteurs live : points GPS, POI, points cloud
 */
public class ActiveTripActivity extends AppCompatActivity {

    private TextView tvPhotoCount;
    private String tripName;
    private int memoryPhotosCount = 0;
    private static final int REQUEST_MEMORY_PHOTO = 1002;
    private Uri memoryPhotoUri;
    private Button btnDeleteLastPhoto;
    private TextView tvTripStatus;
    private TextView tvGpsCount;
    private TextView tvPoiCount;
    private TextView tvCloudCount;
    private TextView tvBatteryLive;
    private Button btnAddPoi;
    private Button btnStopTrip;
    private ImageView currentImgPreview;
    private TextView currentTvPhotoStatus;
    private List<GpsPoint> collectedPoints = new ArrayList<>();
    private List<Poi> collectedPois = new ArrayList<>();
    private boolean isCollecting = true;
    private int cloudPointsSent = 0;
    private Uri photoUri;           // URI de la photo prise
    private String photoBase64;     // Photo encodée pour envoi cloud
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private long tripStartTime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_PICK_MEMORY_GALLERY = 1003;
    private static final int MAX_PHOTOS = 20;
    private TextView tvPhotoLimit;
    private android.location.LocationListener locationListener;
    private LocationManager locationManager;
    private Location lastKnownLocation;
    private static final int REQUEST_PICK_GALLERY_POI = 1004;
    private List<String> memoryPhotosBase64 = new ArrayList<>();
    private List<double[]> memoryPhotosCoords = new ArrayList<>();

    // Position de base (Versailles / UVSQ) pour la simulation
    static final double BASE_LAT = 48.8014;
    static final double BASE_LNG = 2.1301;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripStartTime = System.currentTimeMillis();

        // 1. D'ABORD tous les findViewById
        tvTripStatus = findViewById(R.id.tvTripStatus);
        tvGpsCount = findViewById(R.id.tvGpsCount);
        tvPoiCount = findViewById(R.id.tvPoiCount);
        tvCloudCount = findViewById(R.id.tvCloudCount);
        tvBatteryLive = findViewById(R.id.tvBatteryLive);
        tvPhotoCount = findViewById(R.id.tvPhotoCount);
        btnAddPoi = findViewById(R.id.btnAddPoi);
        btnStopTrip = findViewById(R.id.btnStopTrip);
        Button btnTakeMemoryPhoto = findViewById(R.id.btnTakeMemoryPhoto);
        tvPhotoLimit = findViewById(R.id.tvPhotoLimit);
        Button btnPickMemoryGallery = findViewById(R.id.btnPickMemoryGallery);
        btnPickMemoryGallery.setOnClickListener(v -> pickMemoryFromGallery());
        btnDeleteLastPhoto = findViewById(R.id.btnDeleteLastPhoto);
        btnDeleteLastPhoto.setOnClickListener(v -> showPhotosManager());

        // 2. ENSUITE récupérer le nom du voyage
        tripName = getIntent().getStringExtra("trip_name");
        if (tripName == null || tripName.isEmpty()) {
            tripName = "Voyage du " + new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
        }

        // 3. ENSUITE utiliser les vues
        tvTripStatus.setText("● " + tripName);

        // 4. Le reste
        startGpsCollection();
        btnAddPoi.setOnClickListener(v -> showAddPoiDialog());
        btnTakeMemoryPhoto.setOnClickListener(v -> takeMemoryPhoto());
        btnStopTrip.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Terminer le voyage ?")
                    .setMessage("Le voyage sera sauvegardé avec "
                            + collectedPoints.size() + " points GPS et "
                            + collectedPois.size() + " POI.")
                    .setPositiveButton("Terminer", (dialog, which) -> stopTrip())
                    .setNegativeButton("Continuer", null)
                    .show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Photo pour POI (existant)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            photoBase64 = encodeImageToBase64(photoUri);
            if (currentImgPreview != null && photoUri != null) {
                currentImgPreview.setVisibility(View.VISIBLE);
                currentImgPreview.setImageURI(photoUri);
            }
            if (currentTvPhotoStatus != null) {
                currentTvPhotoStatus.setText("✓ Photo prise — sera envoyée avec le POI");
            }
        }

        // Photo POI depuis galerie
        if (requestCode == REQUEST_PICK_GALLERY_POI && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            if (selectedImage != null) {
                photoBase64 = encodeImageToBase64(selectedImage);
                if (currentImgPreview != null) {
                    currentImgPreview.setVisibility(View.VISIBLE);
                    currentImgPreview.setImageURI(selectedImage);
                }
                if (currentTvPhotoStatus != null) {
                    currentTvPhotoStatus.setText("✓ Photo sélectionnée depuis la galerie");
                }
            }
        }

        // Photo souvenir depuis galerie
        if (requestCode == REQUEST_PICK_MEMORY_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            if (selectedImage != null) {
                // Lire les coordonnées GPS depuis les métadonnées EXIF
                double[] coords = readExifCoordinates(selectedImage);
                if (coords == null) {
                    // Fallback : utiliser la position GPS actuelle
                    if (!collectedPoints.isEmpty()) {
                        GpsPoint last = collectedPoints.get(collectedPoints.size() - 1);
                        coords = new double[]{last.getLat(), last.getLng()};
                    } else {
                        coords = new double[]{BASE_LAT, BASE_LNG};
                    }
                }
                // Encoder en Base64
                String base64 = encodeImageToBase64(selectedImage);
                if (base64 != null) {
                    memoryPhotosBase64.add(base64);
                    memoryPhotosCoords.add(coords);
                    memoryPhotosCount++;
                    final double lat = coords[0];
                    final double lng = coords[1];
                    btnDeleteLastPhoto.setVisibility(View.VISIBLE);
                    runOnUiThread(() -> {
                        tvPhotoCount.setText(memoryPhotosCount + " photo(s) prise(s)");
                        tvPhotoLimit.setText(memoryPhotosCount + " / " + MAX_PHOTOS + " photos");
                        Toast.makeText(this,
                                "Photo ajoutée — GPS: " + String.format("%.4f", lat) + ", " + String.format("%.4f", lng),
                                Toast.LENGTH_SHORT).show();
                    });
                    // Envoyer au cloud
                    sendMemoryPhotoToCloud(base64, coords[0], coords[1]);
                }
            }
        }

        // Photo souvenir (NOUVEAU)
        if (requestCode == REQUEST_MEMORY_PHOTO && resultCode == Activity.RESULT_OK) {
            if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
                Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos atteinte", Toast.LENGTH_SHORT).show();
                return;
            }
            String memoryBase64 = encodeImageToBase64(memoryPhotoUri);
            if (memoryBase64 != null) {
                double lat = BASE_LAT;
                double lng = BASE_LNG;
                if (!collectedPoints.isEmpty()) {
                    GpsPoint lastPoint = collectedPoints.get(collectedPoints.size() - 1);
                    lat = lastPoint.getLat();
                    lng = lastPoint.getLng();
                }
                memoryPhotosBase64.add(memoryBase64);
                memoryPhotosCoords.add(new double[]{lat, lng});
                memoryPhotosCount++;
                tvPhotoCount.setText(memoryPhotosCount + " photo(s) prise(s)");
                tvPhotoLimit.setText(memoryPhotosCount + " / " + MAX_PHOTOS + " photos");
                sendMemoryPhotoToCloud(memoryBase64, lat, lng);
            }
        }
    }

    /**
     * Simule la collecte GPS et envoie chaque point au cloud.
     */

    private void startGpsCollection() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (!isCollecting) return;

                if (!BatteryHelper.shouldCollectData(ActiveTripActivity.this)) {
                    isCollecting = false;
                    tvTripStatus.setText("⚠ Collecte suspendue — batterie faible");
                    locationManager.removeUpdates(locationListener);
                    return;
                }

                lastKnownLocation = location;
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                long timestamp = System.currentTimeMillis() / 1000;

                GpsPoint point = new GpsPoint(lat, lng, timestamp,
                        location.getAltitude(), location.getAccuracy());
                collectedPoints.add(point);

                runOnUiThread(() -> {
                    tvGpsCount.setText(collectedPoints.size() + " points GPS");
                    tvBatteryLive.setText(BatteryHelper.getStatusMessage(
                            ActiveTripActivity.this));
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

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                5f,
                locationListener);
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
            final int index = i;
            String b64 = memoryPhotosBase64.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            // Miniature
            ImageView img = new ImageView(this);
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            img.setImageBitmap(bmp);
            int size = (int) (80 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(size, size);
            imgParams.setMargins(0, 0, 24, 0);
            img.setLayoutParams(imgParams);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(img);

            // Infos + bouton supprimer
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvInfo = new TextView(this);
            double[] coords = memoryPhotosCoords.get(i);
            tvInfo.setText("Photo " + (i + 1) + "\n"
                    + String.format("%.5f, %.5f", coords[0], coords[1]));
            tvInfo.setTextSize(13);
            infoLayout.addView(tvInfo);

            Button btnDel = new Button(this);
            btnDel.setText("Supprimer");
            btnDel.setTextColor(0xFFDC2626);
            btnDel.setBackgroundColor(0x00000000);
            btnDel.setOnClickListener(v -> {
                memoryPhotosBase64.remove(index);
                memoryPhotosCoords.remove(index);
                memoryPhotosCount--;
                tvPhotoCount.setText(memoryPhotosCount + " photo(s) prise(s)");
                tvPhotoLimit.setText(memoryPhotosCount + " / " + MAX_PHOTOS + " photos");
                Toast.makeText(this, "Photo supprimée", Toast.LENGTH_SHORT).show();
            });
            infoLayout.addView(btnDel);
            row.addView(infoLayout);
            layout.addView(row);

            // Séparateur
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

    /**
     * Ouvre la galerie pour choisir une photo souvenir.
     */
    private void pickMemoryFromGallery() {
        if (memoryPhotosBase64.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "Limite de " + MAX_PHOTOS + " photos atteinte", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_MEMORY_GALLERY);
    }

    /**
     * Lit les métadonnées EXIF d'une image (coordonnées GPS et date).
     * Retourne un tableau [lat, lng] ou null si pas de données GPS.
     */
    private double[] readExifCoordinates(Uri imageUri) {
        try {
            InputStream stream = getContentResolver().openInputStream(imageUri);
            if (stream == null) return null;
            androidx.exifinterface.media.ExifInterface exif =
                    new androidx.exifinterface.media.ExifInterface(stream);
            double[] latLng = exif.getLatLong();
            stream.close();
            return latLng; // null si pas de GPS dans les métadonnées
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Crée un fichier temporaire pour stocker la photo.
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
        String imageFileName = "SMARTTRIP_" + timeStamp;
        File storageDir = getExternalFilesDir("Pictures");
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }


    /**
     * Lance la caméra pour prendre une photo souvenir.
     * Différent du POI : la photo est envoyée seule, sans formulaire.
     */
    private void takeMemoryPhoto() {
        if (collectedPoints.isEmpty()) {
            Toast.makeText(this,
                    "Attendez au moins un point GPS avant de prendre une photo",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Erreur création fichier photo", Toast.LENGTH_SHORT).show();
                return;
            }
            memoryPhotoUri = FileProvider.getUriForFile(this,
                    "com.example.smarttrip.fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, memoryPhotoUri);
            startActivityForResult(takePictureIntent, REQUEST_MEMORY_PHOTO);
        } else {
            Toast.makeText(this, "Aucune caméra disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Envoie une photo souvenir au backend.
     */
    private void sendMemoryPhotoToCloud(String photoBase64, double lat, double lng) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());
        LocationDto location = new LocationDto(lat, lng, 5.0);
        PhotoDto dto = new PhotoDto(
                "amin",
                "trip_" + tripStartTime,
                location,
                photoBase64,
                timestamp
        );
        ApiClient.getInstance().getApiService().sendPhoto(dto).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    // ← SUPPRIMÉ : memoryPhotosCount++ ici c'était le double comptage
                    runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                            "Photo souvenir envoyée au cloud ✓",
                            Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                            "Erreur envoi photo cloud", Toast.LENGTH_SHORT).show());
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                        "Erreur réseau : photo non envoyée", Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Lance la caméra pour prendre une photo.
     */
    private void dispatchTakePictureIntent(ImageView imgPreview, TextView tvStatus) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Erreur création fichier photo", Toast.LENGTH_SHORT).show();
                return;
            }
            photoUri = FileProvider.getUriForFile(this,
                    "com.example.smarttrip.fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

            // Stocker refs pour les utiliser dans onActivityResult
            currentImgPreview = imgPreview;
            currentTvPhotoStatus = tvStatus;

            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "Aucune caméra disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Convertit un fichier image en String Base64 pour envoi JSON.
     */
    private String encodeImageToBase64(Uri imageUri) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(
                    getContentResolver().openInputStream(imageUri));
            // Réduire la taille pour ne pas surcharger la BDD (max 800px)
            int maxSize = 800;
            float scale = Math.min((float) maxSize / bitmap.getWidth(),
                    (float) maxSize / bitmap.getHeight());
            if (scale < 1) {
                int newWidth = Math.round(bitmap.getWidth() * scale);
                int newHeight = Math.round(bitmap.getHeight() * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Envoie un point GPS au backend FastAPI.
     */
    private void sendGpsToCloud(GpsPoint point) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());

        LocationDto location = new LocationDto(point.getLat(), point.getLng(), 5.0);
        BatteryDto battery = new BatteryDto(
                BatteryHelper.getBatteryLevel(this),
                BatteryHelper.isCharging(this)
        );

        GpsDataDto dto = new GpsDataDto("amin", "trip_" + tripStartTime, tripName, location, battery, timestamp);

        ApiClient.getInstance().getApiService().sendGps(dto).enqueue(new Callback<Map<String, Object>>() {
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
                        "Erreur cloud : " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Affiche le formulaire d'ajout de POI.
     */
    private void showAddPoiDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_poi, null);

        EditText editName = dialogView.findViewById(R.id.editPoiName);
        Spinner spinnerType = dialogView.findViewById(R.id.spinnerPoiType);
        RatingBar ratingBar = dialogView.findViewById(R.id.ratingBarPoi);
        EditText editComment = dialogView.findViewById(R.id.editPoiComment);
        TextView tvCoords = dialogView.findViewById(R.id.tvPoiCoords);

        // Spinner avec les types de POI
        String[] types = {"restaurant", "monument", "hotel", "nature", "autre"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, types);
        spinnerType.setAdapter(adapter);

        // Réinitialiser photo à chaque nouveau dialog
        photoBase64 = null;
        photoUri = null;

        Button btnPhoto = dialogView.findViewById(R.id.btnTakePhotoPoi);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPoiPreview);
        TextView tvPhotoStatus = dialogView.findViewById(R.id.tvPhotoStatus);

        btnPhoto.setOnClickListener(v -> dispatchTakePictureIntent(imgPreview, tvPhotoStatus));

        Button btnPickGallery = dialogView.findViewById(R.id.btnPickGalleryPoi);
        btnPickGallery.setOnClickListener(v -> {
            // Stocker refs pour récupérer dans onActivityResult
            currentImgPreview = imgPreview;
            currentTvPhotoStatus = tvPhotoStatus;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_PICK_GALLERY_POI);
        });

        // Récupérer les coords GPS du dernier point collecté
        final double lat;
        final double lng;
        if (lastKnownLocation != null) {
            lat = lastKnownLocation.getLatitude();
            lng = lastKnownLocation.getLongitude();
        } else if (!collectedPoints.isEmpty()) {
            GpsPoint lastPoint = collectedPoints.get(collectedPoints.size() - 1);
            lat = lastPoint.getLat();
            lng = lastPoint.getLng();
        } else {
            lat = BASE_LAT;
            lng = BASE_LNG;
        }
        tvCoords.setText("Position : " + String.format("%.5f", lat) + ", " + String.format("%.5f", lng));

        new AlertDialog.Builder(this)
                .setTitle("Ajouter un point d'intérêt")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String type = (String) spinnerType.getSelectedItem();
                    int rating = (int) ratingBar.getRating();
                    String comment = editComment.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Le nom est obligatoire", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Poi poi = new Poi(name, type, lat, lng, rating, comment, "");
                    collectedPois.add(poi);
                    tvPoiCount.setText(collectedPois.size() + " POI");

                    String photoToSend = photoBase64;
                    sendPoiToCloud(poi, photoToSend);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /**
     * Envoie un POI au backend FastAPI.
     */
    private void sendPoiToCloud(Poi poi, String photoToSend) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());

        LocationDto location = new LocationDto(poi.getLat(), poi.getLng(), 5.0);

        PoiDto dto = new PoiDto(
                "amin",
                "trip_" + tripStartTime,
                tripName,                    // ← AJOUT
                poi.getName(),
                poi.getType(),
                location,
                poi.getRating(),
                poi.getComment(),
                timestamp,
                photoBase64
        );

        ApiClient.getInstance().getApiService().sendPoi(dto).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                            "POI « " + poi.getName() + " » envoyé au cloud ✓",
                            Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                            "POI sauvegardé localement (erreur cloud)",
                            Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(ActiveTripActivity.this,
                        "POI sauvegardé localement (pas de réseau)",
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Termine le voyage et retourne à l'accueil.
     */
    private void stopTrip() {
        isCollecting = false;
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener); // arrêter le GPS
        }
        isCollecting = false;
        handler.removeCallbacksAndMessages(null);

        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                .format(new Date());

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
    }
}
