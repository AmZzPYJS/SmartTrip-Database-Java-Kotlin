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
 */
@Database(
        entities = { GpsPointEntity.class, PoiEntity.class, PhotoEntity.class },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract GpsPointDao gpsPointDao();
    public abstract PoiDao poiDao();
    public abstract PhotoDao photoDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "smarttrip_local.db"
                            )
                            // Pour ton projet actuel, c'est acceptable.
                            // Attention : ça efface la DB locale si le schéma change.
                            // Les données déjà envoyées au cloud restent dans MongoDB.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}