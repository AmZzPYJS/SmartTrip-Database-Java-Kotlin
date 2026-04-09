package com.example.smarttrip;

import java.io.Serializable;

/**
 * Représente un Point d'Intérêt (restaurant, monument, etc.).
 * Correspond à un élément du tableau "pois" dans le document MongoDB.
 *
 * Exemple JSON depuis l'API :
 * {
 *   "name": "Le Bouillon Chartier",
 *   "type": "restaurant",
 *   "lat": 48.8738,
 *   "lng": 2.3430,
 *   "rating": 4,
 *   "comment": "Bon rapport qualité-prix",
 *   "photoUrl": "https://..."
 * }
 */
public class Poi implements Serializable {

    private String name;
    private String type;       // "restaurant", "monument", "hotel", "autre"
    private double lat;
    private double lng;
    private int rating;        // note de 1 à 5
    private String comment;
    private String photoUrl;   // URL de la photo stockée dans le cloud

    public Poi(String name, String type, double lat, double lng,
               int rating, String comment, String photoUrl) {
        this.name = name;
        this.type = type;
        this.lat = lat;
        this.lng = lng;
        this.rating = rating;
        this.comment = comment;
        this.photoUrl = photoUrl;
    }

    // --- Getters ---
    public String getName() { return name; }
    public String getType() { return type; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public String getPhotoUrl() { return photoUrl; }

    /**
     * Retourne les étoiles sous forme de texte.
     * Ex: "★★★★☆" pour rating=4
     */
    public String getRatingStars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < rating ? "★" : "☆");
        }
        return sb.toString();
    }

    /**
     * Vérifie si ce POI a une photo associée.
     */
    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.isEmpty();
    }
}
