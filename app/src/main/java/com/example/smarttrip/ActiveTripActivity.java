package com.example.smarttrip;

import android.os.Bundle;
import android.widget.ImageView;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
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

    // Position de base (Versailles / UVSQ) pour la simulation
    static final double BASE_LAT = 48.8014;
    static final double BASE_LNG = 2.1301;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_trip);

        tripStartTime = System.currentTimeMillis();

        tvTripStatus = findViewById(R.id.tvTripStatus);
        tvGpsCount = findViewById(R.id.tvGpsCount);
        tvPoiCount = findViewById(R.id.tvPoiCount);
        tvCloudCount = findViewById(R.id.tvCloudCount);
        tvBatteryLive = findViewById(R.id.tvBatteryLive);
        btnAddPoi = findViewById(R.id.btnAddPoi);
        btnStopTrip = findViewById(R.id.btnStopTrip);

        tvTripStatus.setText("● Voyage en cours");

        startGpsCollection();

        btnAddPoi.setOnClickListener(v -> showAddPoiDialog());

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
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            // Encoder la photo en Base64
            photoBase64 = encodeImageToBase64(photoUri);

            // Afficher l'aperçu dans le dialog
            if (currentImgPreview != null && photoUri != null) {
                currentImgPreview.setVisibility(View.VISIBLE);
                currentImgPreview.setImageURI(photoUri);
            }
            if (currentTvPhotoStatus != null) {
                currentTvPhotoStatus.setText("✓ Photo prise — sera envoyée avec le POI");
            }
        }
    }

    /**
     * Simule la collecte GPS et envoie chaque point au cloud.
     */
    private void startGpsCollection() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isCollecting) return;

                if (!BatteryHelper.shouldCollectData(ActiveTripActivity.this)) {
                    isCollecting = false;
                    tvTripStatus.setText("⚠ Collecte suspendue — batterie faible");
                    Toast.makeText(ActiveTripActivity.this,
                            "Batterie en dessous de " + BatteryHelper.getThreshold()
                                    + "% — collecte GPS arrêtée automatiquement",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                double lat = BASE_LAT + (Math.random() - 0.5) * 0.01;
                double lng = BASE_LNG + (Math.random() - 0.5) * 0.01;
                long timestamp = System.currentTimeMillis() / 1000;

                GpsPoint point = new GpsPoint(lat, lng, timestamp);
                collectedPoints.add(point);
                tvGpsCount.setText(collectedPoints.size() + " points GPS");

                sendGpsToCloud(point);

                tvBatteryLive.setText(BatteryHelper.getStatusMessage(
                        ActiveTripActivity.this));

                handler.postDelayed(this, 5000);
            }
        }, 1000);
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

        GpsDataDto dto = new GpsDataDto("amin", location, battery, timestamp);

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

        Button btnPhoto = dialogView.findViewById(R.id.btnTakePhoto);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPoiPreview);
        TextView tvPhotoStatus = dialogView.findViewById(R.id.tvPhotoStatus);

        btnPhoto.setOnClickListener(v -> dispatchTakePictureIntent(imgPreview, tvPhotoStatus));

        // Récupérer les coords GPS du dernier point collecté
        final double lat;
        final double lng;
        if (!collectedPoints.isEmpty()) {
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
                poi.getName(),
                poi.getType(),
                location,
                poi.getRating(),
                poi.getComment(),
                timestamp,
                photoBase64   // ← nouveau paramètre
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
