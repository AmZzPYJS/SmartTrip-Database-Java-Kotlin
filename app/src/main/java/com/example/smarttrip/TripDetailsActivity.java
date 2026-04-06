package com.example.smarttrip;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TripDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        TextView tvTripName = findViewById(R.id.tvTripName);

        String tripName = getIntent().getStringExtra("trip_name");
        tvTripName.setText(tripName);
    }
}