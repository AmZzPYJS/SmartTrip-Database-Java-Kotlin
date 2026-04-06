package com.example.smarttrip;

public class Trip {
    private final String title;
    private final String date;
    private final String subtitle;

    public Trip(String title, String date, String subtitle) {
        this.title = title;
        this.date = date;
        this.subtitle = subtitle;
    }

    public String getTitle() {
        return title;
    }

    public String getDate() {
        return date;
    }

    public String getSubtitle() {
        return subtitle;
    }
}