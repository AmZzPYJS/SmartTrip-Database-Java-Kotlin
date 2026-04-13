package com.example.smarttrip.api;

import com.google.gson.annotations.SerializedName;

/**
 * Représente la position GPS dans le payload envoyé à l'API.
 * Correspond au champ "location" du format JSON attendu.
 */
public class LocationDto {

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("accuracy")
    private double accuracy;

    public LocationDto(double latitude, double longitude, double accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getAccuracy() { return accuracy; }
}
