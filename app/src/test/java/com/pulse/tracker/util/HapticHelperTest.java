package com.pulse.tracker.util;

import android.content.Context;
import android.os.Vibrator;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests verifying precision HapticHelper tactile triggers, parameters,
 * and graceful fallback handling under Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HapticHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testConstants_matchApplePrecisionProfile() {
        assertEquals(11L, HapticHelper.TICK_DURATION_MS);
        assertEquals(255, HapticHelper.TICK_AMPLITUDE);
        assertEquals(20L, HapticHelper.MILESTONE_DURATION_MS);
        assertEquals(255, HapticHelper.MILESTONE_AMPLITUDE);
        assertEquals(12L, HapticHelper.THROTTLE_WINDOW_MS);
    }

    @Test
    public void testPerformTick_executesWithoutCrash() {
        HapticHelper.performTick(context);
        HapticHelper.performTick(context, true);
        HapticHelper.performTick(context, false);
    }

    @Test
    public void testPerformMilestone_executesWithoutCrash() {
        HapticHelper.performMilestone(context);
    }

    @Test
    public void testNullContext_handledGracefully() {
        HapticHelper.performTick(null);
        HapticHelper.performTick(null, true);
        HapticHelper.performMilestone(null);
    }
}
