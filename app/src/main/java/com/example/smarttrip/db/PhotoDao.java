package com.example.smarttrip.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PhotoDao {

    @Insert
    void insert(PhotoEntity entity);

    @Query("SELECT * FROM photos WHERE synced = 0")
    List<PhotoEntity> getUnsynced();

    @Query("UPDATE photos SET synced = 1 WHERE id = :id")
    void markSynced(int id);

    @Query("SELECT * FROM photos WHERE tripId = :tripId")
    List<PhotoEntity> getByTrip(String tripId);
}