package com.example.smarttrip;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class TripAdapter extends ArrayAdapter<Trip> {

    public TripAdapter(Context context, List<Trip> trips) {
        super(context, 0, trips);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Trip trip = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_trip, parent, false);
        }

        TextView tvTitle = convertView.findViewById(R.id.tvTripItemTitle);
        TextView tvDate = convertView.findViewById(R.id.tvTripItemDate);
        TextView tvSubtitle = convertView.findViewById(R.id.tvTripItemSubtitle);

        if (trip != null) {
            tvTitle.setText(trip.getTitle());
            tvDate.setText(trip.getDate());
            tvSubtitle.setText(trip.getSubtitle());
        }

        return convertView;
    }
}