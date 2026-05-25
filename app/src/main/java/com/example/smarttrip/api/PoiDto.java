package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

public class PoiDto {

    @SerializedName("poi_id")
    private String poiId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("trip_id")
    private String tripId;

    @SerializedName("trip_name")
    private String tripName;

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
    private String photoBase64;

    public PoiDto(String poiId, String userId, String tripId, String tripName,
                  String name, String type, LocationDto location,
                  int rating, String comment, String recordedAt,
                  String photoBase64) {
        this.poiId = poiId;
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