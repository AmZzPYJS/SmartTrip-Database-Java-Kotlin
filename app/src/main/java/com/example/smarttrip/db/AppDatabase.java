package com.example.smarttrip.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Base de données Room locale — singleton.
 *
 * Utilisée pour stocker GPS, POI et photos offline.
 * Le SyncWorker lit les entrées non synchronisées et les envoie au cloud
 * dès que le réseau revient.
 *
 * Emplacement : com.example.smarttrip.db.AppDatabase
 *
 * Pour l'accès depuis d'autres packages :
 *   AppDatabase db = AppDatabase.getInstance(context);
 *   db.gpsPointDao().insert(entity);
 */
@Database(
        entities = { GpsPointEntity.class, PoiEntity.class, PhotoEntity.class },
        version  = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract GpsPointDao gpsPointDao();
    public abstract PoiDao      poiDao();
    public abstract PhotoDao    photoDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "smarttrip_local.db"
                            )
                            // Permet d'effacer et recréer la DB si le schéma change
                            // (à remplacer par une migration en production)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}