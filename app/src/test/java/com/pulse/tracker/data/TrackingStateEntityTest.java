package com.pulse.tracker.data;

import com.pulse.tracker.data.local.entity.TrackingStateEntity;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying TrackingStateEntity initialization, builder mutations, and clamping.
 */
public class TrackingStateEntityTest {

    @Test
    public void testCreateInitial_hasCleanDefaults() {
        TrackingStateEntity initial = TrackingStateEntity.createInitial();

        assertEquals(TrackingStateEntity.SINGLETON_ID, initial.getId());
        assertEquals(0L, initial.getLastCumulativeSteps());
        assertEquals(0L, initial.getBootSessionMarker());
        assertFalse(initial.isBaselineInitialized());
        assertEquals(0L, initial.getLastEventTimestamp());
        assertFalse(initial.isSessionActive());
        assertEquals(0L, initial.getSessionStartMonotonicMs());
        assertEquals(0L, initial.getLastMovementMonotonicMs());
        assertEquals(0L, initial.getAccumulatedSessionSeconds());
        assertTrue(initial.getUpdatedAtMillis() > 0);
    }

    @Test
    public void testBuilder_clampsNegativeValues() {
        TrackingStateEntity state = new TrackingStateEntity.Builder()
                .setId(1)
                .setLastCumulativeSteps(-500L)
                .setBootSessionMarker(123456L)
                .setBaselineInitialized(true)
                .setLastEventTimestamp(999999L)
                .setSessionActive(true)
                .setSessionStartMonotonicMs(1000L)
                .setLastMovementMonotonicMs(1500L)
                .setAccumulatedSessionSeconds(-100L)
                .setUpdatedAtMillis(2000L)
                .build();

        assertEquals(0L, state.getLastCumulativeSteps());
        assertEquals(0L, state.getAccumulatedSessionSeconds());
        assertTrue(state.isBaselineInitialized());
        assertTrue(state.isSessionActive());
    }

    @Test
    public void testToBuilder_copiesFieldsAccurately() {
        TrackingStateEntity original = new TrackingStateEntity(
                1, 15000L, 8888L, true, 99999L, true, 1000L, 2000L, 45L, 5000L
        );

        TrackingStateEntity copy = original.toBuilder()
                .setLastCumulativeSteps(15500L)
                .build();

        assertEquals(1, copy.getId());
        assertEquals(15500L, copy.getLastCumulativeSteps());
        assertEquals(8888L, copy.getBootSessionMarker());
        assertTrue(copy.isBaselineInitialized());
        assertTrue(copy.isSessionActive());
        assertEquals(45L, copy.getAccumulatedSessionSeconds());
    }

    @Test
    public void testEqualsAndHashCode() {
        TrackingStateEntity s1 = new TrackingStateEntity(1, 100L, 200L, true, 300L, false, 0L, 0L, 0L, 400L);
        TrackingStateEntity s2 = new TrackingStateEntity(1, 100L, 200L, true, 300L, false, 0L, 0L, 0L, 400L);
        TrackingStateEntity s3 = new TrackingStateEntity(1, 101L, 200L, true, 300L, false, 0L, 0L, 0L, 400L);

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, s3);
    }
}

