package com.example.smarttrip.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entité Room — représente un point GPS stocké localement.
 * Sauvegardé immédiatement à la collecte, même sans réseau.
 * Champ synced = false tant que le cloud n'a pas confirmé la réception.
 */
@Entity(tableName = "gps_points")
public class GpsPointEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userId;
    public String tripId;
    public String tripName;

    public double latitude;
    public double longitude;
    public double altitude;
    public float  accuracy;

    public long   timestamp;     // Unix seconds
    public int    batteryLevel;
    public boolean batteryCharging;

    public String recordedAt;    // ISO string

    // false = pas encore envoyé au cloud, true = confirmé
    public boolean synced = false;
}