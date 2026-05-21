package com.example.smarttrip.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entité Room — représente une photo souvenir stockée localement.
 */
@Entity(tableName = "photos")
public class PhotoEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userId;
    public String tripId;
    public String tripName;

    public double latitude;
    public double longitude;

    public String photoBase64;
    public String recordedAt;

    // false = pas encore envoyé au cloud
    public boolean synced = false;
}