package com.example.smarttrip;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnStartTrip;
    private Button btnViewTrips;
    private Button btnSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnViewTrips = findViewById(R.id.btnViewTrips);
        btnSettings = findViewById(R.id.btnSettings);

        btnStartTrip.setOnClickListener(v ->
                Toast.makeText(MainActivity.this, "Fonction démarrer voyage à venir", Toast.LENGTH_SHORT).show()
        );

        btnViewTrips.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TripsActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v ->
                Toast.makeText(MainActivity.this, "Page paramètres bientôt disponible", Toast.LENGTH_SHORT).show()
        );
    }
}