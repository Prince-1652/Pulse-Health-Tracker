package com.pulse.tracker.data;

import com.pulse.tracker.data.local.entity.DailyMetricsEntity;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying DailyMetricsEntity immutability, negative value clamping,
 * builder functionality, and equality logic.
 */
public class DailyMetricsEntityTest {

    @Test
    public void testEntityConstructor_clampsNegativeValuesToZero() {
        DailyMetricsEntity entity = new DailyMetricsEntity(
                "2026-11-07",
                -500L,
                -1200.5,
                -300L,
                -45.2,
                -10000L,
                1000L
        );

        assertEquals("2026-11-07", entity.getDate());
        assertEquals(0L, entity.getSteps());
        assertEquals(0.0, entity.getDistanceMeters(), 0.001);
        assertEquals(0L, entity.getActiveSeconds());
        assertEquals(0.0, entity.getActiveCaloriesKcal(), 0.001);
        assertEquals(0L, entity.getStepGoal());
        assertEquals(1000L, entity.getUpdatedAtMillis());
    }

    @Test
    public void testEmptyFactoryMethod_initializesCleanDefaults() {
        DailyMetricsEntity empty = DailyMetricsEntity.empty("2026-11-01", 8000L);

        assertEquals("2026-11-01", empty.getDate());
        assertEquals(0L, empty.getSteps());
        assertEquals(0.0, empty.getDistanceMeters(), 0.001);
        assertEquals(0L, empty.getActiveSeconds());
        assertEquals(0.0, empty.getActiveCaloriesKcal(), 0.001);
        assertEquals(8000L, empty.getStepGoal());
        assertTrue(empty.getUpdatedAtMillis() > 0);
    }

    @Test
    public void testBuilder_createsAndModifiesEntity() {
        DailyMetricsEntity entity = new DailyMetricsEntity.Builder()
                .setDate("2026-11-07")
                .setSteps(6420L)
                .setDistanceMeters(4815.0)
                .setActiveSeconds(2400L)
                .setActiveCaloriesKcal(210.5)
                .setStepGoal(10000L)
                .setUpdatedAtMillis(123456789L)
                .build();

        assertEquals("2026-11-07", entity.getDate());
        assertEquals(6420L, entity.getSteps());
        assertEquals(4815.0, entity.getDistanceMeters(), 0.001);
        assertEquals(2400L, entity.getActiveSeconds());
        assertEquals(210.5, entity.getActiveCaloriesKcal(), 0.001);
        assertEquals(10000L, entity.getStepGoal());
        assertEquals(123456789L, entity.getUpdatedAtMillis());

        DailyMetricsEntity modified = entity.toBuilder()
                .setSteps(7000L)
                .build();

        assertEquals(7000L, modified.getSteps());
        assertEquals("2026-11-07", modified.getDate());
        assertEquals(entity.getDistanceMeters(), modified.getDistanceMeters(), 0.001);
    }

    @Test
    public void testSetters_clampNegativeValues() {
        DailyMetricsEntity entity = DailyMetricsEntity.empty("2026-11-07", 10000L);
        entity.setSteps(-100L);
        entity.setDistanceMeters(-50.0);
        entity.setActiveSeconds(-20L);
        entity.setActiveCaloriesKcal(-10.0);
        entity.setStepGoal(-5000L);

        assertEquals(0L, entity.getSteps());
        assertEquals(0.0, entity.getDistanceMeters(), 0.001);
        assertEquals(0L, entity.getActiveSeconds());
        assertEquals(0.0, entity.getActiveCaloriesKcal(), 0.001);
        assertEquals(0L, entity.getStepGoal());
    }

    @Test
    public void testEqualsAndHashCode() {
        DailyMetricsEntity e1 = new DailyMetricsEntity("2026-11-07", 5000L, 3500.0, 1800L, 150.0, 10000L, 500L);
        DailyMetricsEntity e2 = new DailyMetricsEntity("2026-11-07", 5000L, 3500.0, 1800L, 150.0, 10000L, 500L);
        DailyMetricsEntity diff = new DailyMetricsEntity("2026-11-08", 5000L, 3500.0, 1800L, 150.0, 10000L, 500L);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, diff);
    }
}

