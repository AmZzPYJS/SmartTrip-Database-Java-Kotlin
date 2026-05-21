package com.example.smarttrip.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface GpsPointDao {

    @Insert
    void insert(GpsPointEntity entity);

    @Query("SELECT * FROM gps_points WHERE synced = 0")
    List<GpsPointEntity> getUnsynced();

    @Query("UPDATE gps_points SET synced = 1 WHERE id = :id")
    void markSynced(int id);

    @Query("SELECT * FROM gps_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    List<GpsPointEntity> getByTrip(String tripId);
}