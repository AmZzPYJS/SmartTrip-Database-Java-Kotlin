package com.example.smarttrip;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Écran de détail d'un voyage.
 *
 * Affiche les informations complètes :
 * - Titre, date, description
 * - Statistiques (nb points GPS, nb POI, distance totale)
 * - Liste des POI avec note et commentaire
 * - Statut batterie au moment de la consultation
 *
 * TODO (prochaine étape) :
 * - Intégrer OSMDroid pour afficher les points sur une carte
 * - Afficher les photos des POI
 * - Tracer l'itinéraire entre les points GPS
 */
public class TripDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        // Récupérer le voyage depuis l'intent
        Trip trip = (Trip) getIntent().getSerializableExtra("trip");

        if (trip == null) {
            finish(); // Sécurité : retour si pas de données
            return;
        }

        // --- Liaison des vues ---
        TextView tvTripName = findViewById(R.id.tvTripName);
        TextView tvTripDate = findViewById(R.id.tvTripDate);
        TextView tvTripDescription = findViewById(R.id.tvTripDescription);
        TextView tvStats = findViewById(R.id.tvStats);
        LinearLayout layoutPois = findViewById(R.id.layoutPois);
        TextView tvBattery = findViewById(R.id.tvBatteryDetail);

        // --- Remplir les données ---
        tvTripName.setText(trip.getTitle());
        tvTripDate.setText(trip.getDate());
        tvTripDescription.setText(trip.getDescription());

        // Statistiques
        int nbGps = trip.getGpsPoints().size();
        int nbPoi = trip.getPois().size();
        double distanceKm = calculateTotalDistance(trip) / 1000.0;
        String stats = nbGps + " points GPS • " + nbPoi + " POI • "
                + String.format("%.1f", distanceKm) + " km parcourus";
        tvStats.setText(stats);

        // Liste des POI
        for (Poi poi : trip.getPois()) {
            addPoiView(layoutPois, poi);
        }

        // Statut batterie
        tvBattery.setText(BatteryHelper.getStatusMessage(this));
    }

    /**
     * Calcule la distance totale du parcours GPS en mètres.
     * Utilise la méthode distanceTo() de GpsPoint (formule Haversine).
     */
    private double calculateTotalDistance(Trip trip) {
        double total = 0;
        for (int i = 1; i < trip.getGpsPoints().size(); i++) {
            total += trip.getGpsPoints().get(i - 1)
                    .distanceTo(trip.getGpsPoints().get(i));
        }
        return total;
    }

    /**
     * Crée dynamiquement une vue pour un POI et l'ajoute au layout.
     * C'est une approche simple — en production on utiliserait
     * un RecyclerView imbriqué ou un ExpandableListView.
     */
    private void addPoiView(LinearLayout container, Poi poi) {
        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 24, 32, 24);
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setElevation(2f);

        // Nom + type
        TextView tvName = new TextView(this);
        tvName.setText(poi.getName() + "  (" + poi.getType() + ")");
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF111827);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(tvName);

        // Étoiles
        TextView tvRating = new TextView(this);
        tvRating.setText(poi.getRatingStars());
        tvRating.setTextSize(14);
        tvRating.setTextColor(0xFFD97706); // amber
        tvRating.setPadding(0, 8, 0, 4);
        card.addView(tvRating);

        // Commentaire
        if (poi.getComment() != null && !poi.getComment().isEmpty()) {
            TextView tvComment = new TextView(this);
            tvComment.setText(poi.getComment());
            tvComment.setTextSize(14);
            tvComment.setTextColor(0xFF6B7280);
            tvComment.setPadding(0, 4, 0, 0);
            card.addView(tvComment);
        }

        // Coordonnées GPS (utile en mode debug / soutenance)
        TextView tvCoords = new TextView(this);
        tvCoords.setText("GPS : " + poi.getLat() + ", " + poi.getLng());
        tvCoords.setTextSize(11);
        tvCoords.setTextColor(0xFF9CA3AF);
        tvCoords.setPadding(0, 8, 0, 0);
        card.addView(tvCoords);

        container.addView(card);
    }
}
