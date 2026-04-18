package com.example.smarttrip;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

/**
 * Utilitaire pour gérer la batterie du téléphone.
 * Le seuil d'arrêt est maintenant configurable par l'utilisateur
 * et sauvegardé dans les SharedPreferences.
 */
public class BatteryHelper {

    private static final String PREFS_NAME = "SmartTripPrefs";
    private static final String KEY_THRESHOLD = "battery_threshold";
    private static final int DEFAULT_THRESHOLD = 15;

    /**
     * Retourne le seuil configuré par l'utilisateur (défaut : 15%).
     */
    public static int getThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    /**
     * Sauvegarde le nouveau seuil choisi par l'utilisateur.
     */
    public static void setThreshold(Context context, int threshold) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THRESHOLD, threshold).apply();
    }

    /**
     * Retourne le seuil (version sans contexte pour compatibilité).
     * Utilise la valeur par défaut.
     */
    public static int getThreshold() {
        return DEFAULT_THRESHOLD;
    }

    public static int getBatteryLevel(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return -1;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level == -1 || scale == -1) return -1;
        return (int) ((level / (float) scale) * 100);
    }

    public static boolean isCharging(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean shouldCollectData(Context context) {
        int level = getBatteryLevel(context);
        if (level == -1) return true;
        return level > getThreshold(context) || isCharging(context);
    }

    public static String getStatusMessage(Context context) {
        int level = getBatteryLevel(context);
        boolean charging = isCharging(context);
        if (level == -1) return "Batterie : état inconnu";
        String status = "Batterie : " + level + "%";
        if (charging) status += " (en charge)";
        if (!shouldCollectData(context)) {
            status += " — Collecte GPS suspendue";
        }
        return status;
    }
}