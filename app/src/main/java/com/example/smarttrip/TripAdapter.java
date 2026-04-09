package com.example.smarttrip;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter pour afficher la liste des voyages avec RecyclerView.
 *
 * Pourquoi RecyclerView et pas ListView ?
 * - RecyclerView recycle les vues automatiquement (pattern ViewHolder obligatoire)
 * - Meilleure performance avec beaucoup d'éléments
 * - Plus flexible (LinearLayout, Grid, animations)
 * - Standard Android depuis 2014, attendu en entretien technique
 */
public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private final List<Trip> trips;
    private final OnTripClickListener listener;

    /**
     * Interface callback pour gérer le clic sur un voyage.
     * C'est le pattern standard Android pour découpler l'adapter de la navigation.
     */
    public interface OnTripClickListener {
        void onTripClick(Trip trip, int position);
    }

    public TripAdapter(List<Trip> trips, OnTripClickListener listener) {
        this.trips = trips;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.bind(trip, listener, position);
    }

    @Override
    public int getItemCount() {
        return trips != null ? trips.size() : 0;
    }

    /**
     * Met à jour la liste de voyages et rafraîchit l'affichage.
     * Utile quand les données arrivent du cloud de manière asynchrone.
     */
    public void updateTrips(List<Trip> newTrips) {
        trips.clear();
        trips.addAll(newTrips);
        notifyDataSetChanged();
    }

    // --- ViewHolder ---

    static class TripViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTitle;
        private final TextView tvDate;
        private final TextView tvSubtitle;
        private final TextView tvStatus;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTripItemTitle);
            tvDate = itemView.findViewById(R.id.tvTripItemDate);
            tvSubtitle = itemView.findViewById(R.id.tvTripItemSubtitle);
            tvStatus = itemView.findViewById(R.id.tvTripItemStatus);
        }

        void bind(Trip trip, OnTripClickListener listener, int position) {
            tvTitle.setText(trip.getTitle());
            tvDate.setText(trip.getDate());
            tvSubtitle.setText(trip.getSummary());

            // Afficher le statut "En cours" si le voyage est actif
            if (tvStatus != null) {
                if (trip.isActive()) {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("● En cours");
                } else {
                    tvStatus.setVisibility(View.GONE);
                }
            }

            // Clic sur l'item
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTripClick(trip, position);
                }
            });
        }
    }
}
