package com.example.smarttrip;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.smarttrip.api.ApiClient;
import com.example.smarttrip.api.BatteryDto;
import com.example.smarttrip.api.GpsDataDto;
import com.example.smarttrip.api.LocationDto;
import com.example.smarttrip.db.AppDatabase;
import com.example.smarttrip.db.GpsPointEntity;

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
 * GpsTrackingService — ForegroundService pour la collecte GPS en arrière-plan.
 *
 * POURQUOI un ForegroundService ?
 * Android 8+ tue les services en arrière-plan après quelques minutes.
 * Un ForegroundService avec notification persistante est protégé par le système
 * et continue de tourner même écran éteint ou app minimisée.
 *
 * FONCTIONNEMENT :
 * 1. ActiveTripActivity démarre ce service via startForegroundService()
 * 2. Le service crée une notification persistante → Android ne peut pas le tuer
 * 3. LocationManager collecte les points GPS en continu
 * 4. Chaque point → sauvegardé dans Room + envoyé au cloud
 * 5. ActiveTripActivity se bind au service pour recevoir les mises à jour UI
 * 6. Quand l'utilisateur termine le voyage → stopSelf()
 */
public class GpsTrackingService extends Service {

    private static final String TAG = "GpsTrackingService";
    private static final String CHANNEL_ID = "smarttrip_gps_channel";
    private static final int NOTIF_ID = 1001;

    // Extras Intent
    public static final String EXTRA_TRIP_NAME    = "trip_name";
    public static final String EXTRA_TRIP_ID      = "trip_id";
    public static final String EXTRA_USER_ID      = "user_id";

    // Filtre saut spatial — même logique que ActiveTripActivity
    private static final float MAX_JUMP_METERS = 50f;

    // ── État ──────────────────────────────────────────────────────────────────
    private String tripName;
    private String tripId;
    private String userId = "amin";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location bestLocation = null;
    private GpsPoint lastAcceptedPoint = null;

    private final List<GpsPoint> collectedPoints = new ArrayList<>();
    private int cloudPointsSent = 0;

    // WakeLock — empêche le CPU de s'endormir pendant la collecte
    private PowerManager.WakeLock wakeLock;

    // Callback vers ActiveTripActivity (null si l'app est en arrière-plan)
    private GpsUpdateListener gpsListener;

    // ── Binder — pour que ActiveTripActivity se connecte au service ───────────
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public GpsTrackingService getService() { return GpsTrackingService.this; }
    }

    public interface GpsUpdateListener {
        void onGpsPointCollected(GpsPoint point, int total, String accuracyLabel);
        void onCloudPointSent(int total);
    }

    public void setGpsUpdateListener(GpsUpdateListener listener) {
        this.gpsListener = listener;
    }

    public List<GpsPoint> getCollectedPoints() { return collectedPoints; }
    public int getCloudPointsSent() { return cloudPointsSent; }

    // =========================================================================
    // Lifecycle Service
    // =========================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // WakeLock partiel — CPU actif, écran peut s'éteindre
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartTrip::GpsWakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            tripName = intent.getStringExtra(EXTRA_TRIP_NAME);
            tripId   = intent.getStringExtra(EXTRA_TRIP_ID);
            userId   = intent.getStringExtra(EXTRA_USER_ID) != null
                    ? intent.getStringExtra(EXTRA_USER_ID) : "amin";
        }

        // Démarrer en foreground avec notification persistante
        startForeground(NOTIF_ID, buildNotification("Collecte GPS démarrée…"));

        // Acquérir le WakeLock
        if (!wakeLock.isHeld()) wakeLock.acquire(8 * 60 * 60 * 1000L); // max 8h

        // Démarrer la collecte GPS
        startGpsCollection();

        // START_STICKY → Android redémarre le service s'il le tue (mémoire faible)
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopGpsCollection();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        Log.d(TAG, "Service arrêté — " + collectedPoints.size() + " points collectés");
    }

    // =========================================================================
    // Notification persistante
    // =========================================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SmartTrip GPS",
                    NotificationManager.IMPORTANCE_LOW); // LOW = pas de son
            channel.setDescription("Collecte GPS en arrière-plan");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        // Clic sur la notification → rouvre ActiveTripActivity
        Intent notifIntent = new Intent(this, ActiveTripActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🗺️  SmartTrip — " + (tripName != null ? tripName : "Voyage"))
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)           // non-dismissable par l'utilisateur
                .setSilent(true)            // pas de son/vibration
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /** Met à jour le texte de la notification sans la recréer. */
    private void updateNotification(String status) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(status));
    }

    // =========================================================================
    // Collecte GPS
    // =========================================================================

    private void startGpsCollection() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission GPS non accordée");
            stopSelf();
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (!isBetterLocation(location, bestLocation)) return;
                bestLocation = location;

                if (!BatteryHelper.shouldCollectData(GpsTrackingService.this)) {
                    updateNotification("⚠ Arrêt — batterie faible");
                    stopSelf();
                    return;
                }

                // Filtre saut spatial
                if (lastAcceptedPoint != null) {
                    float[] results = new float[1];
                    Location.distanceBetween(
                            lastAcceptedPoint.getLat(), lastAcceptedPoint.getLng(),
                            location.getLatitude(), location.getLongitude(), results);
                    if (results[0] > MAX_JUMP_METERS) return;
                }

                GpsPoint point = new GpsPoint(
                        location.getLatitude(), location.getLongitude(),
                        System.currentTimeMillis() / 1000,
                        location.getAltitude(), location.getAccuracy());
                collectedPoints.add(point);
                lastAcceptedPoint = point;

                // Label qualité
                float acc = location.getAccuracy();
                String label;
                if (acc <= 10f)      label = "● précis (" + Math.round(acc) + "m)";
                else if (acc <= 50f) label = "◐ moyen (" + Math.round(acc) + "m)";
                else                 label = "○ réseau (" + Math.round(acc) + "m)";

                // Mettre à jour la notification
                updateNotification(collectedPoints.size() + " pts GPS  " + label);

                // Notifier l'Activity si elle est au premier plan
                if (gpsListener != null) {
                    gpsListener.onGpsPointCollected(point, collectedPoints.size(), label);
                }

                // Sauvegarder Room + cloud
                saveGpsPoint(point, location);
            }

            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {
                updateNotification("⚠ GPS désactivé sur l'appareil");
            }
        };

        // GPS satellite — précis
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000, 5f, locationListener);

        // Réseau — fallback en intérieur
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }

        Log.d(TAG, "Collecte GPS démarrée");
    }

    private void stopGpsCollection() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    // =========================================================================
    // Sauvegarde Room + Cloud
    // =========================================================================

    private void saveGpsPoint(GpsPoint point, Location location) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
                .format(new Date());

        // Room en arrière-plan
        Executors.newSingleThreadExecutor().execute(() -> {
            GpsPointEntity entity = new GpsPointEntity();
            entity.userId          = userId;
            entity.tripId          = tripId;
            entity.tripName        = tripName;
            entity.latitude        = point.getLat();
            entity.longitude       = point.getLng();
            entity.altitude        = point.getAltitude();
            entity.accuracy        = location.getAccuracy();
            entity.timestamp       = point.getTimestamp();
            entity.batteryLevel    = BatteryHelper.getBatteryLevel(this);
            entity.batteryCharging = BatteryHelper.isCharging(this);
            entity.recordedAt      = timestamp;
            entity.synced          = false;
            AppDatabase.getInstance(this).gpsPointDao().insert(entity);
            SyncWorker.schedule(this);
        });

        // Tentative cloud directe
        LocationDto locDto = new LocationDto(point.getLat(), point.getLng(), point.getAltitude());
        BatteryDto  batDto = new BatteryDto(
                BatteryHelper.getBatteryLevel(this), BatteryHelper.isCharging(this));
        GpsDataDto dto = new GpsDataDto(userId, tripId, tripName, locDto, batDto, timestamp);

        ApiClient.getInstance().getApiService().sendGps(dto)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            cloudPointsSent++;
                            if (gpsListener != null)
                                gpsListener.onCloudPointSent(cloudPointsSent);
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        // Room s'en charge via SyncWorker
                    }
                });
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    private boolean isBetterLocation(Location location, Location currentBest) {
        if (currentBest == null) return true;
        long timeDelta = location.getTime() - currentBest.getTime();
        if (timeDelta > 10_000) return true;
        if (timeDelta < -10_000) return false;
        int accuracyDelta = (int)(location.getAccuracy() - currentBest.getAccuracy());
        if (accuracyDelta < 0) return true;
        if (timeDelta > 0 && accuracyDelta <= 200) return true;
        return false;
    }

    /** Appelé par ActiveTripActivity quand l'utilisateur termine le voyage. */
    public void stopTrip() {
        stopGpsCollection();
        stopSelf();
    }
}