package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

/**
 * DTO pour envoyer une photo souvenir au backend.
 * Une photo souvenir n'est PAS liée à un POI — c'est une capture
 * spontanée pendant le voyage avec ses métadonnées GPS.
 */
public class PhotoDto {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("trip_id")
    private String tripId;

    @SerializedName("location")
    private LocationDto location;

    @SerializedName("photo_base64")
    private String photoBase64;

    @SerializedName("recorded_at")
    private String recordedAt;

    public PhotoDto(String userId, String tripId, LocationDto location,
                    String photoBase64, String recordedAt) {
        this.userId = userId;
        this.tripId = tripId;
        this.location = location;
        this.photoBase64 = photoBase64;
        this.recordedAt = recordedAt;
    }
}