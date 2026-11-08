package com.pulse.tracker.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

/**
 * Data Access Object for tracker checkpoint and boot-session state persistence.
 */
@Dao
public interface TrackingStateDao {

    /**
     * Retrieves the singleton tracking state checkpoint.
     */
    @Query("SELECT * FROM tracking_state WHERE id = 1 LIMIT 1")
    TrackingStateEntity getTrackingState();

    /**
     * Inserts or replaces the singleton tracking state checkpoint.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(TrackingStateEntity state);
}

