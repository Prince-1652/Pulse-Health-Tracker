package com.pulse.tracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pulse.tracker.data.local.entity.DailyMetricsEntity;

import java.util.List;

/**
 * Data Access Object for daily metrics persistence and history analytics queries.
 */
@Dao
public interface DailyMetricsDao {

    /**
     * Observable query for a single date's metrics (typically today) for UI observing.
     */
    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    LiveData<DailyMetricsEntity> getTodayMetrics(String date);

    /**
     * Synchronous query for a single date's metrics (used by repository and background workers).
     */
    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    DailyMetricsEntity getMetricsForDate(String date);

    /**
     * Synchronous query alias for today/single date metrics.
     */
    @Query("SELECT * FROM daily_metrics WHERE date = :date LIMIT 1")
    DailyMetricsEntity getTodayMetricsSync(String date);

    /**
     * Returns records within an inclusive date range [startDate, endDate], ordered chronologically.
     */
    @Query("SELECT * FROM daily_metrics WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    List<DailyMetricsEntity> getMetricsBetweenDates(String startDate, String endDate);

    /**
     * Returns all records matching the month prefix (e.g. '2026-11%'), ordered by date ASC.
     */
    @Query("SELECT * FROM daily_metrics WHERE date LIKE :monthPrefix ORDER BY date ASC")
    List<DailyMetricsEntity> getMetricsForMonth(String monthPrefix);

    /**
     * Observable query for monthly records matching the prefix (e.g. '2026-11%').
     */
    @Query("SELECT * FROM daily_metrics WHERE date LIKE :monthPrefix ORDER BY date ASC")
    LiveData<List<DailyMetricsEntity>> getMetricsForMonthLive(String monthPrefix);

    /**
     * Returns the record with the single highest step count.
     */
    @Query("SELECT * FROM daily_metrics ORDER BY steps DESC LIMIT 1")
    DailyMetricsEntity getPersonalBest();

    /**
     * Inserts or updates a daily metric record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DailyMetricsEntity metrics);

    /**
     * Inserts a new daily metric record (fails if conflict exists).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(DailyMetricsEntity metrics);

    /**
     * Updates an existing daily metric record.
     */
    @Update
    void update(DailyMetricsEntity metrics);
}

