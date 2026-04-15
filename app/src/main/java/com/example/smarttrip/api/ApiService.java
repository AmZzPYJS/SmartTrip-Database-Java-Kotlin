package com.example.smarttrip.api;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Interface Retrofit pour communiquer avec le backend FastAPI.
 *
 * Endpoints disponibles :
 * - GET /              → root
 * - GET /gps           → tous les points GPS
 * - POST /gps          → envoyer un point GPS
 * - GET /gps/{user_id} → points GPS d'un utilisateur
 * - POST /pois         → envoyer un POI
 * - GET /pois          → tous les POI
 * - GET /pois/{user_id} → POI d'un utilisateur
 */
public interface ApiService {

    // --- GPS ---

    @POST("/gps")
    Call<Map<String, Object>> sendGps(@Body GpsDataDto data);

    @GET("/gps")
    Call<List<Map<String, Object>>> getAllGps();

    @GET("/gps/{user_id}")
    Call<List<Map<String, Object>>> getUserGps(@Path("user_id") String userId);

    // --- POI ---

    @POST("/pois")
    Call<Map<String, Object>> sendPoi(@Body PoiDto poi);

    @GET("/pois")
    Call<List<Map<String, Object>>> getAllPois();

    @GET("/pois/{user_id}")
    Call<List<Map<String, Object>>> getUserPois(@Path("user_id") String userId);

    // --- PHOTOS SOUVENIRS ---

    @POST("/photos")
    Call<Map<String, Object>> sendPhoto(@Body PhotoDto photo);

    @GET("/photos")
    Call<List<Map<String, Object>>> getAllPhotos();

    @GET("/photos/{user_id}")
    Call<List<Map<String, Object>>> getUserPhotos(@Path("user_id") String userId);
}
