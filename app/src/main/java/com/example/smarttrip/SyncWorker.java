package com.example.smarttrip;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.api.BatteryDto;
import com.example.smarttrip.api.GpsDataDto;
import com.example.smarttrip.api.LocationDto;
import com.example.smarttrip.api.PhotoDto;
import com.example.smarttrip.api.PoiDto;
import com.example.smarttrip.db.AppDatabase;
import com.example.smarttrip.db.GpsPointEntity;
import com.example.smarttrip.db.PhotoEntity;
import com.example.smarttrip.db.PoiEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Response;

/**
 * SyncWorker — s'exécute automatiquement quand le réseau revient.
 *
 * Fonctionnement :
 * 1. Lit tous les GPS/POI/photos avec synced=false dans Room
 * 2. Les envoie à l'API FastAPI de manière synchrone (Worker thread)
 * 3. Marque chaque entrée synced=true après confirmation HTTP 200/201
 *
 * Planification : appelez SyncWorker.schedule(context) dès qu'un point
 * est sauvegardé localement. WorkManager gère la deduplication automatiquement.
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        boolean allOk = true;

        // ── 1. Sync points GPS ────────────────────────────────────────────────
        List<GpsPointEntity> unsyncedGps = db.gpsPointDao().getUnsynced();
        Log.d(TAG, "GPS à synchroniser : " + unsyncedGps.size());

        for (GpsPointEntity e : unsyncedGps) {
            try {
                LocationDto loc = new LocationDto(e.latitude, e.longitude, e.altitude);
                BatteryDto  bat = new BatteryDto(e.batteryLevel, e.batteryCharging);
                GpsDataDto  dto = new GpsDataDto(e.userId, e.tripId, e.tripName, loc, bat, e.recordedAt);

                Response<Map<String, Object>> resp =
                        ApiClient.getInstance().getApiService().sendGps(dto).execute();

                if (resp.isSuccessful()) {
                    db.gpsPointDao().markSynced(e.id);
                    Log.d(TAG, "GPS synced id=" + e.id);
                } else {
                    Log.w(TAG, "GPS sync failed HTTP " + resp.code() + " id=" + e.id);
                    allOk = false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "GPS sync exception id=" + e.id, ex);
                allOk = false;
            }
        }

        // ── 2. Sync POI ───────────────────────────────────────────────────────
        List<PoiEntity> unsyncedPois = db.poiDao().getUnsynced();
        Log.d(TAG, "POI à synchroniser : " + unsyncedPois.size());

        for (PoiEntity e : unsyncedPois) {
            try {
                LocationDto loc = new LocationDto(e.latitude, e.longitude, 0.0);
                PoiDto dto = new PoiDto(
                        e.userId, e.tripId, e.tripName,
                        e.name, e.type, loc,
                        e.rating, e.comment, e.recordedAt, e.photoBase64
                );

                Response<Map<String, Object>> resp =
                        ApiClient.getInstance().getApiService().sendPoi(dto).execute();

                if (resp.isSuccessful()) {
                    db.poiDao().markSynced(e.id);
                    Log.d(TAG, "POI synced id=" + e.id);
                } else {
                    Log.w(TAG, "POI sync failed HTTP " + resp.code() + " id=" + e.id);
                    allOk = false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "POI sync exception id=" + e.id, ex);
                allOk = false;
            }
        }

        // ── 3. Sync Photos ────────────────────────────────────────────────────
        List<PhotoEntity> unsyncedPhotos = db.photoDao().getUnsynced();
        Log.d(TAG, "Photos à synchroniser : " + unsyncedPhotos.size());

        for (PhotoEntity e : unsyncedPhotos) {
            try {
                LocationDto loc = new LocationDto(e.latitude, e.longitude, 0.0);
                PhotoDto dto = new PhotoDto(e.userId, e.tripId, loc, e.photoBase64, e.recordedAt);

                Response<Map<String, Object>> resp =
                        ApiClient.getInstance().getApiService().sendPhoto(dto).execute();

                if (resp.isSuccessful()) {
                    db.photoDao().markSynced(e.id);
                    Log.d(TAG, "Photo synced id=" + e.id);
                } else {
                    Log.w(TAG, "Photo sync failed HTTP " + resp.code() + " id=" + e.id);
                    allOk = false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "Photo sync exception id=" + e.id, ex);
                allOk = false;
            }
        }

        // Si certains éléments ont échoué → RETRY (WorkManager replanifie)
        return allOk ? Result.success() : Result.retry();
    }

    /**
     * Planifie une synchronisation dès que le réseau est disponible.
     * WorkManager déduplique automatiquement si une tâche est déjà en attente.
     *
     * Appelez cette méthode après chaque sauvegarde Room :
     *   SyncWorker.schedule(context);
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        // KEEP_EXISTING : si une sync est déjà planifiée, on ne double pas
        WorkManager.getInstance(context).enqueueUniqueWork(
                "smarttrip_sync",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
        );

        Log.d(TAG, "SyncWorker planifié (réseau requis)");
    }
}