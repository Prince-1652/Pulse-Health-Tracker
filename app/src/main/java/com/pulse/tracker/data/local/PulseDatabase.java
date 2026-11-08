package com.pulse.tracker.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.pulse.tracker.data.local.dao.DailyMetricsDao;
import com.pulse.tracker.data.local.dao.TrackingStateDao;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.local.entity.TrackingStateEntity;

/**
 * Main Room database for Pulse.
 * Maintains daily activity records and the singleton step tracking checkpoint.
 */
@Database(
        entities = {DailyMetricsEntity.class, TrackingStateEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class PulseDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "pulse_database";
    private static volatile PulseDatabase INSTANCE;

    public abstract DailyMetricsDao dailyMetricsDao();

    public abstract TrackingStateDao trackingStateDao();

    /**
     * Thread-safe singleton accessor for application-wide database access.
     * Note: allowMainThreadQueries() is intentionally omitted per architecture rules.
     */
    public static PulseDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (PulseDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            PulseDatabase.class,
                            DATABASE_NAME
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}

