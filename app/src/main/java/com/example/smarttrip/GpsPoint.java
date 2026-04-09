package com.example.smarttrip;

import java.io.Serializable;

/**
 * Représente un point GPS collecté pendant un voyage.
 * Correspond à un élément du tableau "gpsPoints" dans le document MongoDB.
 *
 * Exemple JSON depuis l'API :
 * {
 *   "lat": 48.8566,
 *   "lng": 2.3522,
 *   "timestamp": 1712300000,
 *   "altitude": 35.0,
 *   "accuracy": 10.5
 * }
 */
public class GpsPoint implements Serializable {

    private double lat;
    private double lng;
    private long timestamp;    // Unix timestamp en secondes
    private double altitude;   // en mètres (optionnel, 0 si non dispo)
    private float accuracy;    // précision en mètres (optionnel)

    public GpsPoint(double lat, double lng, long timestamp) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.altitude = 0;
        this.accuracy = 0;
    }

    public GpsPoint(double lat, double lng, long timestamp, double altitude, float accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.altitude = altitude;
        this.accuracy = accuracy;
    }

    // --- Getters ---
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public long getTimestamp() { return timestamp; }
    public double getAltitude() { return altitude; }
    public float getAccuracy() { return accuracy; }

    /**
     * Distance en mètres entre ce point et un autre (formule Haversine).
     * Utile pour calculer la distance totale d'un voyage.
     */
    public double distanceTo(GpsPoint other) {
        final int R = 6371000; // rayon Terre en mètres
        double lat1 = Math.toRadians(this.lat);
        double lat2 = Math.toRadians(other.lat);
        double dLat = Math.toRadians(other.lat - this.lat);
        double dLng = Math.toRadians(other.lng - this.lng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
