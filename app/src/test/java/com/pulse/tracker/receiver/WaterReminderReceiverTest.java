package com.pulse.tracker.receiver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.WaterRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

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

    @Test
    public void testClinicalPacingIntervalCalculation_daytimeDistribution() {
        // Daytime 9:00 AM, goal 2500 ml, intake 0 ml (remaining 10 servings of 250 ml, 13 hours waking time)
        Calendar morning = Calendar.getInstance();
        morning.set(Calendar.HOUR_OF_DAY, 9);
        morning.set(Calendar.MINUTE, 0);
        morning.set(Calendar.SECOND, 0);

        long nextTrigger = WaterReminderManager.calculateNextReminderMillis(morning, 0, 2500);
        long deltaMinutes = (nextTrigger - morning.getTimeInMillis()) / (60 * 1000);

        // 13 hours / 10 servings = 1.3 hours = 78 minutes (clamped between 60 and 150)
        assertEquals(78, deltaMinutes);
    }

    @Test
    public void testClinicalPacingIntervalCalculation_nighttimeScheduleToMorning() {
        // Late night 23:00 (11:00 PM) -> should schedule to 8:00 AM tomorrow
        Calendar night = Calendar.getInstance();
        night.set(Calendar.HOUR_OF_DAY, 23);
        night.set(Calendar.MINUTE, 30);

        long nextTrigger = WaterReminderManager.calculateNextReminderMillis(night, 500, 2500);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(nextTrigger);

        assertEquals(WaterReminderManager.START_HOUR, target.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, target.get(Calendar.MINUTE));
        assertTrue(target.after(night));
    }

    @Test
    public void testClinicalPacingIntervalCalculation_goalReachedSilencesUntilTomorrow() {
        // Goal achieved during daytime (e.g. 2500 / 2500) -> schedules for 8:00 AM tomorrow
        Calendar noon = Calendar.getInstance();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);

        long nextTrigger = WaterReminderManager.calculateNextReminderMillis(noon, 2500, 2500);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(nextTrigger);

        assertEquals(WaterReminderManager.START_HOUR, target.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, target.get(Calendar.MINUTE));
        assertTrue(target.after(noon));
    }

    @Test
    public void testEvidenceBasedMessageCopyForTimeOfDay() {
        // Morning (9 AM)
        WaterReminderManager.EvidenceMessage morningMsg = WaterReminderManager.resolveEvidenceMessage(
                context, 500, 2500, 9
        );
        assertTrue(morningMsg.title.contains("Morning"));

        // Midday (13 PM)
        WaterReminderManager.EvidenceMessage middayMsg = WaterReminderManager.resolveEvidenceMessage(
                context, 1000, 2500, 13
        );
        assertTrue(middayMsg.title.contains("Midday"));

        // Afternoon (16 PM)
        WaterReminderManager.EvidenceMessage afternoonMsg = WaterReminderManager.resolveEvidenceMessage(
                context, 1500, 2500, 16
        );
        assertTrue(afternoonMsg.title.contains("Afternoon"));

        // Evening (19 PM)
        WaterReminderManager.EvidenceMessage eveningMsg = WaterReminderManager.resolveEvidenceMessage(
                context, 2000, 2500, 19
        );
        assertTrue(eveningMsg.title.contains("Evening"));

        // Goal reached
        WaterReminderManager.EvidenceMessage goalMsg = WaterReminderManager.resolveEvidenceMessage(
                context, 2500, 2500, 14
        );
        assertTrue(goalMsg.title.contains("Goal Achieved"));
    }

    @Test
    public void testQuickAddWaterWhenGoalReached_showsCelebratoryNotification() {
        int target = WaterReminderManager.resolveTodayTargetMl(context);
        WaterRepository.setWaterIntake(context, target - 200);

        Intent addIntent = new Intent(WaterReminderReceiver.ACTION_QUICK_ADD_WATER);
        addIntent.putExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 250);
        receiver.onReceive(context, addIntent);

        assertTrue(WaterRepository.getTodayIntakeMl(context) >= target);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(nm);
        assertEquals(1, nm.getActiveNotifications().length);
        assertEquals(5000L, nm.getActiveNotifications()[0].getNotification().getTimeoutAfter());
    }

    @Test
    public void testQuickAddWaterUnderGoal_showsAutoDismissingConfirmation() {
        int target = WaterReminderManager.resolveTodayTargetMl(context);
        WaterRepository.setWaterIntake(context, 0);

        Intent addIntent = new Intent(WaterReminderReceiver.ACTION_QUICK_ADD_WATER);
        addIntent.putExtra(WaterReminderReceiver.EXTRA_AMOUNT_ML, 250);
        receiver.onReceive(context, addIntent);

        assertEquals(250, WaterRepository.getTodayIntakeMl(context));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(nm);
        assertEquals(1, nm.getActiveNotifications().length);
        android.app.Notification n = nm.getActiveNotifications()[0].getNotification();
        assertEquals(3000L, n.getTimeoutAfter());
        CharSequence title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        assertNotNull(title);
        assertTrue(title.toString().contains("250"));
        CharSequence text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("250"));
    }

    @Test
    public void testDaytimeNotificationWindowBounds() {
        // Morning jitter check: 7:55 AM+ is morning reminder
        assertTrue(WaterReminderReceiver.isDaytimeNotificationWindow(7, 55));
        assertTrue(WaterReminderReceiver.isDaytimeNotificationWindow(7, 59));
        // Before 7:55 AM is outside daytime window
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(7, 54));
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(6, 30));

        // Daytime 8:00 AM to 21:59 is inside
        assertTrue(WaterReminderReceiver.isDaytimeNotificationWindow(8, 0));
        assertTrue(WaterReminderReceiver.isDaytimeNotificationWindow(14, 30));
        assertTrue(WaterReminderReceiver.isDaytimeNotificationWindow(21, 59));

        // 22:00 (10:00 PM) is end of daytime window, do not post reminders
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(22, 0));
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(22, 30));
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(23, 0));
        org.junit.Assert.assertFalse(WaterReminderReceiver.isDaytimeNotificationWindow(0, 0));
    }

    @Test
    public void testNextTriggerStrictlyFutureAcrossBoundaries() {
        // Test midnight boundary (00:00)
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        long nextMidnight = WaterReminderManager.calculateNextReminderMillis(midnight, 0, 2500);
        assertTrue(nextMidnight > midnight.getTimeInMillis() + 60000L);

        // Test morning jitter boundary (07:55)
        Calendar jitter = Calendar.getInstance();
        jitter.set(Calendar.HOUR_OF_DAY, 7);
        jitter.set(Calendar.MINUTE, 55);
        long nextJitter = WaterReminderManager.calculateNextReminderMillis(jitter, 0, 2500);
        assertTrue(nextJitter > jitter.getTimeInMillis() + 60000L);

        // Test evening boundary (22:00)
        Calendar eveningEnd = Calendar.getInstance();
        eveningEnd.set(Calendar.HOUR_OF_DAY, 22);
        eveningEnd.set(Calendar.MINUTE, 0);
        long nextEvening = WaterReminderManager.calculateNextReminderMillis(eveningEnd, 500, 2500);
        assertTrue(nextEvening > eveningEnd.getTimeInMillis() + 60000L);

        // Test goal reached during midday
        Calendar noon = Calendar.getInstance();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        long nextGoal = WaterReminderManager.calculateNextReminderMillis(noon, 2500, 2500);
        assertTrue(nextGoal > noon.getTimeInMillis() + 60000L);
    }

    @Test
    public void testMorningJitterEvidenceMessageIsMorning() {
        WaterReminderManager.EvidenceMessage msg = WaterReminderManager.resolveEvidenceMessage(
                context, 0, 2500, 7
        );
        assertTrue(msg.title.contains("Morning"));
    }
}
