package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

/**
 * Payload complet envoyé à POST /gps.
 *
 * Format JSON produit :
 * {
 *   "user_id": "amin",
 *   "location": { "latitude": 48.8566, "longitude": 2.3522, "accuracy": 5.0 },
 *   "battery":  { "level": 67, "is_charging": false },
 *   "recorded_at": "2026-04-11T15:00:00"
 * }
 */
public class GpsDataDto {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("location")
    private LocationDto location;

    @SerializedName("battery")
    private BatteryDto battery;

    @SerializedName("recorded_at")
    private String recordedAt;

    @SerializedName("trip_id")
    private String tripId;

    public GpsDataDto(String userId, String tripId, LocationDto location,
                      BatteryDto battery, String recordedAt) {
        this.userId = userId;
        this.tripId = tripId;
        this.location = location;
        this.battery = battery;
        this.recordedAt = recordedAt;
    }

    public String getUserId() { return userId; }
    public LocationDto getLocation() { return location; }
    public BatteryDto getBattery() { return battery; }
    public String getRecordedAt() { return recordedAt; }
}
