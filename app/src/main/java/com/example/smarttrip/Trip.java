package com.example.smarttrip;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Modèle principal d'un voyage.
 * Correspond à un document dans la collection "trips" de MongoDB Atlas.
 * Implémente Serializable pour pouvoir être passé entre Activities via Intent.
 */
public class Trip implements Serializable {

    private String id;            // _id MongoDB
    private String title;
    private String date;
    private String description;
    private boolean isActive;     // voyage en cours ou terminé
    private List<GpsPoint> gpsPoints;
    private List<Poi> pois;

    // Constructeur complet (données réelles depuis l'API)
    public Trip(String id, String title, String date, String description,
                boolean isActive, List<GpsPoint> gpsPoints, List<Poi> pois) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.description = description;
        this.isActive = isActive;
        this.gpsPoints = gpsPoints != null ? gpsPoints : new ArrayList<>();
        this.pois = pois != null ? pois : new ArrayList<>();
    }

    // Constructeur simplifié (pour la liste de voyages avant chargement détaillé)
    public Trip(String id, String title, String date) {
        this(id, title, date, "", false, new ArrayList<>(), new ArrayList<>());
    }

    // --- Getters ---

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getDescription() { return description; }
    public boolean isActive() { return isActive; }
    public List<GpsPoint> getGpsPoints() { return gpsPoints; }
    public List<Poi> getPois() { return pois; }

    // --- Méthodes utilitaires ---

    /**
     * Résumé affiché dans la liste des voyages.
     * Ex: "5 points GPS • 3 POI"
     */
    public String getSummary() {
        int nbGps = gpsPoints != null ? gpsPoints.size() : 0;
        int nbPoi = pois != null ? pois.size() : 0;
        return nbGps + " points GPS • " + nbPoi + " POI";
    }

    /**
     * Vérifie si le voyage contient des données exploitables.
     */
    public boolean hasData() {
        return (gpsPoints != null && !gpsPoints.isEmpty())
                || (pois != null && !pois.isEmpty());
    }

    // --- Setters pour mise à jour dynamique ---

    public void setActive(boolean active) { this.isActive = active; }

    public void addGpsPoint(GpsPoint point) {
        if (gpsPoints == null) gpsPoints = new ArrayList<>();
        gpsPoints.add(point);
    }

    public void addPoi(Poi poi) {
        if (pois == null) pois = new ArrayList<>();
        pois.add(poi);
    }
}
