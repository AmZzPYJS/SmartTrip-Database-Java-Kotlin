package com.example.smarttrip;

import java.io.Serializable;

public class Poi implements Serializable {

    private String poiId;
    private String name;
    private String type;
    private double lat;
    private double lng;
    private int rating;
    private String comment;
    private String photoUrl;
    private String photoBase64;

    public Poi(String name, String type, double lat, double lng,
               int rating, String comment, String photoUrl) {
        this.poiId = "poi_" + System.currentTimeMillis();
        this.name = name;
        this.type = type;
        this.lat = lat;
        this.lng = lng;
        this.rating = rating;
        this.comment = comment;
        this.photoUrl = photoUrl;
        this.photoBase64 = null;
    }

    public Poi(String poiId, String name, String type, double lat, double lng,
               int rating, String comment, String photoUrl) {
        this.poiId = poiId != null && !poiId.isEmpty()
                ? poiId
                : "poi_" + System.currentTimeMillis();
        this.name = name;
        this.type = type;
        this.lat = lat;
        this.lng = lng;
        this.rating = rating;
        this.comment = comment;
        this.photoUrl = photoUrl;
        this.photoBase64 = null;
    }

    public String getPoiId() {
        return poiId;
    }

    public void setPoiId(String poiId) {
        this.poiId = poiId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getPhotoBase64() {
        if (photoBase64 != null && !photoBase64.isEmpty()) {
            return photoBase64;
        }

        // Fallback utile si une ancienne partie du code a stocké le base64 dans photoUrl
        if (photoUrl != null && !photoUrl.isEmpty() && !photoUrl.startsWith("http")) {
            return photoUrl;
        }

        return photoBase64;
    }

    public void setPhotoBase64(String base64) {
        this.photoBase64 = base64;
    }

    public boolean hasPhoto() {
        return (photoBase64 != null && !photoBase64.isEmpty())
                || (photoUrl != null && !photoUrl.isEmpty());
    }

    public String getRatingStars() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 5; i++) {
            sb.append(i < rating ? "★" : "☆");
        }

        return sb.toString();
    }
}