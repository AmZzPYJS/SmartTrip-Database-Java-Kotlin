package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class TripsActivity extends AppCompatActivity {

    private ListView listTrips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        listTrips = findViewById(R.id.listTrips);

        List<Trip> voyages = new ArrayList<>();
        voyages.add(new Trip("Voyage Paris", "05/04/2026", "3 points GPS • 2 photos"));
        voyages.add(new Trip("Voyage Marseille", "02/04/2026", "5 points GPS • 1 photo"));
        voyages.add(new Trip("Voyage Lyon", "28/03/2026", "4 points GPS • 4 photos"));

        TripAdapter adapter = new TripAdapter(this, voyages);
        listTrips.setAdapter(adapter);

        listTrips.setOnItemClickListener((parent, view, position, id) -> {
            Trip voyageSelectionne = voyages.get(position);

            Intent intent = new Intent(TripsActivity.this, TripDetailsActivity.class);
            intent.putExtra("trip_name", voyageSelectionne.getTitle());
            startActivity(intent);
        });
    }
}