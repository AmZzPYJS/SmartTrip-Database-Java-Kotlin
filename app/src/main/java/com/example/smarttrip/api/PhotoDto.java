package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

public class PhotoDto {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("trip_id")
    private String tripId;

    @SerializedName("trip_name")
    private String tripName;

    @SerializedName("location")
    private LocationDto location;

    @SerializedName("photo_base64")
    private String photoBase64;

    @SerializedName("recorded_at")
    private String recordedAt;

    @SerializedName("linked_poi_id")
    private String linkedPoiId;

    public PhotoDto(String userId, String tripId, String tripName,
                    LocationDto location, String photoBase64,
                    String recordedAt, String linkedPoiId) {
        this.userId = userId;
        this.tripId = tripId;
        this.tripName = tripName;
        this.location = location;
        this.photoBase64 = photoBase64;
        this.recordedAt = recordedAt;
        this.linkedPoiId = linkedPoiId;
    }
}