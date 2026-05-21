package com.example.smarttrip.api;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @POST("/gps")
    Call<Map<String, Object>> sendGps(@Body GpsDataDto data);

    @GET("/gps")
    Call<List<Map<String, Object>>> getAllGps();

    @GET("/gps/{user_id}")
    Call<List<Map<String, Object>>> getUserGps(@Path("user_id") String userId);

    @POST("/pois")
    Call<Map<String, Object>> sendPoi(@Body PoiDto poi);

    @GET("/pois")
    Call<List<Map<String, Object>>> getAllPois();

    @GET("/pois/{user_id}")
    Call<List<Map<String, Object>>> getUserPois(@Path("user_id") String userId);

    @POST("/photos")
    Call<Map<String, Object>> sendPhoto(@Body PhotoDto photo);

    @GET("/photos")
    Call<List<Map<String, Object>>> getAllPhotos();

    @GET("/photos/{user_id}")
    Call<List<Map<String, Object>>> getUserPhotos(@Path("user_id") String userId);

    @DELETE("/trips/{trip_id}")
    Call<Map<String, Object>> deleteTrip(@Path("trip_id") String tripId);

    // ── Nouveau endpoint — partage public d'un voyage ─────────────────────────
    // Retourne GPS + POI + photos d'un voyage sans authentification
    // Utilisé pour générer le lien QR code
    @GET("/trip/{trip_id}")
    Call<Map<String, Object>> getTripPublic(@Path("trip_id") String tripId);
}