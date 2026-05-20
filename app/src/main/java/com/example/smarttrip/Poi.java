package com.example.smarttrip;

import java.io.Serializable;

/**
 * Représente un Point d'Intérêt (restaurant, monument, etc.).
 * Correspond à un élément du tableau "pois" dans le document MongoDB.
 */
public class Poi implements Serializable {

    private String name;
    private String type;
    private double lat;
    private double lng;
    private int    rating;
    private String comment;
    private String photoUrl;    // URL cloud (conservé pour compatibilité)
    private String photoBase64; // contenu image encodé base64 (affichage local)

    // ── Constructeur existant — inchangé, rien ne casse ──────────────────────
    public Poi(String name, String type, double lat, double lng,
               int rating, String comment, String photoUrl) {
        this.name     = name;
        this.type     = type;
        this.lat      = lat;
        this.lng      = lng;
        this.rating   = rating;
        this.comment  = comment;
        this.photoUrl = photoUrl;
    }

    // ── Getters existants — inchangés ─────────────────────────────────────────
    public String getName()     { return name; }
    public String getType()     { return type; }
    public double getLat()      { return lat; }
    public double getLng()      { return lng; }
    public int    getRating()   { return rating; }
    public String getComment()  { return comment; }
    public String getPhotoUrl() { return photoUrl; }

    // ── Nouveau getter/setter pour la photo locale ────────────────────────────
    public String getPhotoBase64()              { return photoBase64; }
    public void   setPhotoBase64(String base64) { this.photoBase64 = base64; }

    public boolean hasPhoto() {
        return (photoUrl != null && !photoUrl.isEmpty())
                || (photoBase64 != null && !photoBase64.isEmpty());
    }

    public String getRatingStars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < rating ? "★" : "☆");
        return sb.toString();
    }
}
