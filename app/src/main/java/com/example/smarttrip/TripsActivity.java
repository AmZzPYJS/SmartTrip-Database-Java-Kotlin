package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Écran d'historique des voyages.
 *
 * Affiche la liste des voyages enregistrés.
 * Pour l'instant, utilise des données de démonstration réalistes.
 *
 * TODO (intégration cloud) :
 * - Remplacer loadDemoTrips() par un appel à l'API FastAPI
 * - Endpoint prévu : GET /api/trips
 * - Format attendu : JSON array de Trip (voir Trip.java)
 */
public class TripsActivity extends AppCompatActivity implements TripAdapter.OnTripClickListener {

    private RecyclerView recyclerTrips;
    private TripAdapter adapter;
    private List<Trip> voyages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        recyclerTrips = findViewById(R.id.recyclerTrips);
        recyclerTrips.setLayoutManager(new LinearLayoutManager(this));

        // Charger les voyages (démo pour l'instant, API plus tard)
        voyages = loadDemoTrips();

        adapter = new TripAdapter(voyages, this);
        recyclerTrips.setAdapter(adapter);
    }

    @Override
    public void onTripClick(Trip trip, int position) {
        Intent intent = new Intent(TripsActivity.this, TripDetailsActivity.class);
        intent.putExtra("trip", trip); // Trip est Serializable
        startActivity(intent);
    }

    /**
     * Données de démonstration réalistes.
     * Chaque voyage contient de vrais points GPS et POI
     * pour que la démo soit crédible en soutenance.
     *
     * À REMPLACER par l'appel API quand le backend sera prêt.
     */
    private List<Trip> loadDemoTrips() {
        List<Trip> trips = new ArrayList<>();

        // --- Voyage 1 : Paris ---
        List<GpsPoint> gpsParisP = new ArrayList<>(Arrays.asList(
                new GpsPoint(48.8566, 2.3522, 1712300000),  // Châtelet
                new GpsPoint(48.8606, 2.3376, 1712301000),  // Louvre
                new GpsPoint(48.8584, 2.2945, 1712302000),  // Tour Eiffel
                new GpsPoint(48.8530, 2.3499, 1712303000),  // Notre-Dame
                new GpsPoint(48.8738, 2.2950, 1712304000)   // Arc de Triomphe
        ));

        List<Poi> poisParis = new ArrayList<>(Arrays.asList(
                new Poi("Le Bouillon Chartier", "restaurant",
                        48.8738, 2.3430, 4,
                        "Cuisine française traditionnelle, cadre historique",
                        ""),
                new Poi("Musée du Louvre", "monument",
                        48.8606, 2.3376, 5,
                        "Collection exceptionnelle, prévoir 3h minimum",
                        ""),
                new Poi("Tour Eiffel", "monument",
                        48.8584, 2.2945, 5,
                        "Vue imprenable au 2e étage",
                        "")
        ));

        trips.add(new Trip("trip_001", "Week-end à Paris",
                "05/04/2026", "Visite des monuments principaux",
                false, gpsParisP, poisParis));

        // --- Voyage 2 : Marseille ---
        List<GpsPoint> gpsMarseille = new ArrayList<>(Arrays.asList(
                new GpsPoint(43.2965, 5.3698, 1711900000),  // Vieux-Port
                new GpsPoint(43.2842, 5.3745, 1711901000),  // Notre-Dame de la Garde
                new GpsPoint(43.2114, 5.4103, 1711902000),  // Calanques
                new GpsPoint(43.2961, 5.3742, 1711903000)   // Le Panier
        ));

        List<Poi> poisMarseille = new ArrayList<>(Arrays.asList(
                new Poi("Chez Fonfon", "restaurant",
                        43.2856, 5.3523, 4,
                        "Bouillabaisse authentique, un peu cher",
                        ""),
                new Poi("Calanque de Sormiou", "nature",
                        43.2114, 5.4103, 5,
                        "Randonnée de 45min, eau turquoise",
                        "")
        ));

        trips.add(new Trip("trip_002", "Escapade Marseille",
                "02/04/2026", "Calanques et gastronomie",
                false, gpsMarseille, poisMarseille));

        // --- Voyage 3 : Lyon ---
        List<GpsPoint> gpsLyon = new ArrayList<>(Arrays.asList(
                new GpsPoint(45.7640, 4.8357, 1711600000),  // Place Bellecour
                new GpsPoint(45.7578, 4.8320, 1711601000),  // Vieux Lyon
                new GpsPoint(45.7623, 4.8226, 1711602000),  // Fourvière
                new GpsPoint(45.7676, 4.8344, 1711603000)   // Place des Terreaux
        ));

        List<Poi> poisLyon = new ArrayList<>(Arrays.asList(
                new Poi("Bouchon Chez Abel", "restaurant",
                        45.7530, 4.8265, 4,
                        "Quenelles de brochet incontournables",
                        ""),
                new Poi("Basilique de Fourvière", "monument",
                        45.7623, 4.8226, 5,
                        "Panorama sur tout Lyon",
                        ""),
                new Poi("Parc de la Tête d'Or", "nature",
                        45.7750, 4.8555, 4,
                        "Lac, zoo gratuit, idéal pour une pause",
                        ""),
                new Poi("Fresque des Lyonnais", "monument",
                        45.7670, 4.8310, 3,
                        "Mur peint impressionnant, quartier Saint-Jean",
                        "")
        ));

        trips.add(new Trip("trip_003", "Lyon gastronomique",
                "28/03/2026", "Bouchons et patrimoine",
                false, gpsLyon, poisLyon));

        return trips;
    }
}
