package com.example.smarttrip;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Utilitaire pour gérer la batterie du téléphone.
 *
 * La prof a insisté : "la batterie du téléphone est limitée,
 * en cas de manque de batterie, désactiver la collecte de données."
 *
 * Ce helper centralise toute la logique batterie en un seul endroit.
 */
public class BatteryHelper {

    // Seuil en dessous duquel on arrête la collecte GPS
    // 15% est un bon compromis : assez pour que l'utilisateur
    // puisse encore utiliser son téléphone pour appeler/naviguer
    private static final int LOW_BATTERY_THRESHOLD = 15;

    /**
     * Retourne le niveau de batterie actuel (0-100).
     */
    public static int getBatteryLevel(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) return -1;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level == -1 || scale == -1) return -1;

        return (int) ((level / (float) scale) * 100);
    }

    /**
     * Vérifie si le téléphone est en charge.
     */
    public static boolean isCharging(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) return false;

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * Détermine si la collecte GPS doit être active.
     * Retourne true si :
     *   - la batterie est au-dessus du seuil, OU
     *   - le téléphone est en charge (même si batterie faible)
     */
    public static boolean shouldCollectData(Context context) {
        int level = getBatteryLevel(context);
        if (level == -1) return true; // en cas d'erreur, on collecte par défaut

        return level > LOW_BATTERY_THRESHOLD || isCharging(context);
    }

    /**
     * Retourne un message d'état lisible pour l'UI.
     */
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

    /**
     * Retourne le seuil configuré.
     */
    public static int getThreshold() {
        return LOW_BATTERY_THRESHOLD;
    }
}
