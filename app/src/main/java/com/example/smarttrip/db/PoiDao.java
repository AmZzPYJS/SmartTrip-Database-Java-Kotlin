package com.example.smarttrip.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PoiDao {

    @Insert
    void insert(PoiEntity entity);

    @Query("SELECT * FROM pois WHERE synced = 0")
    List<PoiEntity> getUnsynced();

    @Query("UPDATE pois SET synced = 1 WHERE id = :id")
    void markSynced(int id);

    @Query("SELECT * FROM pois WHERE tripId = :tripId")
    List<PoiEntity> getByTrip(String tripId);
}