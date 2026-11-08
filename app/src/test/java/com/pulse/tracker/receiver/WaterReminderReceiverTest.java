package com.pulse.tracker.receiver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.WaterRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WaterReminderReceiverTest {

    private Context context;
    private WaterReminderReceiver receiver;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        receiver = new WaterReminderReceiver();
        WaterRepository.resetTodayWater(context);
        WaterRepository.setRemindersEnabled(context, true);
    }

    @Test
    public void testQuickAddWaterIncrementsIntake() {
        assertEquals(0, WaterRepository.getTodayIntakeMl(context));

        Intent addIntent = new Intent(WaterReminderReceiver.ACTION_QUICK_ADD_WATER);
        addIntent.putExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 250);
        receiver.onReceive(context, addIntent);

        assertEquals(250, WaterRepository.getTodayIntakeMl(context));

        receiver.onReceive(context, addIntent);
        assertEquals(500, WaterRepository.getTodayIntakeMl(context));
    }

    @Test
    public void testQuickAddWaterCustomAmount() {
        Intent addIntent = new Intent(WaterReminderReceiver.ACTION_QUICK_ADD_WATER);
        addIntent.putExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 750);
        receiver.onReceive(context, addIntent);

        assertEquals(750, WaterRepository.getTodayIntakeMl(context));
    }

    @Test
    public void testResetWaterIntake() {
        WaterRepository.addWaterIntake(context, 1000);
        assertEquals(1000, WaterRepository.getTodayIntakeMl(context));

        WaterRepository.resetTodayWater(context);
        assertEquals(0, WaterRepository.getTodayIntakeMl(context));
    }

    @Test
    public void testReminderTickDoesNotCrash() {
        Intent tickIntent = new Intent(WaterReminderReceiver.ACTION_REMINDER_TICK);
        receiver.onReceive(context, tickIntent);
        // Ensure execution completes cleanly without uncaught exceptions
        assertTrue(true);
    }

    @Test
    public void testNullIntentOrContextHandledSafely() {
        receiver.onReceive(null, null);
        receiver.onReceive(context, null);
        receiver.onReceive(null, new Intent(WaterReminderReceiver.ACTION_REMINDER_TICK));
        assertTrue(true);
    }

    @Test
    public void testNextReminderTimestampIsFuture() {
        long next = WaterReminderManager.getNextReminderTimeMillis();
        assertTrue("Next reminder should be in the future", next >= System.currentTimeMillis() - 1000L);
    }

    @Test
    public void testResolveTodayTargetReturnsClinicallySafeBound() {
        int target = WaterReminderManager.resolveTodayTargetMl(context);
        assertTrue("Target should be within [1200, 5000]", target >= 1200 && target <= 5000);
        assertEquals(0, target % 50);
    }
}
