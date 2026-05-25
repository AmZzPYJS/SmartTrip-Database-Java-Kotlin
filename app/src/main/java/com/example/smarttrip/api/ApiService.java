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

    // GPS
    @POST("gps")
    Call<Map<String, Object>> sendGps(@Body GpsDataDto data);

    @GET("gps/{user_id}")
    Call<List<Map<String, Object>>> getUserGps(@Path("user_id") String userId);

    // POI — "/poi" et non "/pois"
    @POST("poi")
    Call<Map<String, Object>> sendPoi(@Body PoiDto poi);

    @GET("poi/{user_id}")
    Call<List<Map<String, Object>>> getUserPois(@Path("user_id") String userId);

    // Photos
    @POST("photo")
    Call<Map<String, Object>> sendPhoto(@Body PhotoDto photo);

    @GET("photos/{user_id}")
    Call<List<Map<String, Object>>> getUserPhotos(@Path("user_id") String userId);

    // Suppression voyage — "/trip" et non "/trips"
    @DELETE("trip/{trip_id}")
    Call<Map<String, Object>> deleteTrip(@Path("trip_id") String tripId);

    // Partage public QR code
    @GET("trip/{trip_id}")
    Call<Map<String, Object>> getTripPublic(@Path("trip_id") String tripId);
}