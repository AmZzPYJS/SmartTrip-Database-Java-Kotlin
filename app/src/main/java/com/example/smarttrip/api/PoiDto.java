package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

/**
 * DTO envoyé au backend FastAPI quand l'utilisateur ajoute un POI.
 * Correspond au endpoint POST /pois.
 *
 * Format JSON envoyé :
 * {
 *   "user_id": "amin",
 *   "trip_id": "trip_1712843534000",
 *   "name": "Tour Eiffel",
 *   "type": "monument",
 *   "location": { "latitude": 48.85, "longitude": 2.29, "accuracy": 5.0 },
 *   "rating": 5,
 *   "comment": "Vue magnifique au coucher du soleil",
 *   "recorded_at": "2026-04-12T14:30:00"
 * }
 */
public class PoiDto {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("trip_id")
    private String tripId;

    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("location")
    private LocationDto location;

    @SerializedName("rating")
    private int rating;

    @SerializedName("comment")
    private String comment;

    @SerializedName("recorded_at")
    private String recordedAt;

    @SerializedName("photo_base64")
    private String photoBase64;  // null si pas de photo

    @SerializedName("trip_name")
    private String tripName;

    public PoiDto(String userId, String tripId, String tripName, String name, String type,
                  LocationDto location, int rating, String comment, String recordedAt, String photoBase64) {
        this.userId = userId;
        this.tripId = tripId;
        this.tripName = tripName;
        this.name = name;
        this.type = type;
        this.location = location;
        this.rating = rating;
        this.comment = comment;
        this.recordedAt = recordedAt;
        this.photoBase64 = photoBase64;
    }
}

