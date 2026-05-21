package com.example.smarttrip.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entité Room — représente un POI stocké localement.
 * Sauvegardé immédiatement à la création, même sans réseau.
 */
@Entity(tableName = "pois")
public class PoiEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userId;
    public String tripId;
    public String tripName;

    public String name;
    public String type;
    public double latitude;
    public double longitude;
    public int    rating;
    public String comment;
    public String photoBase64;   // peut être null
    public String recordedAt;

    // false = pas encore envoyé au cloud
    public boolean synced = false;
}