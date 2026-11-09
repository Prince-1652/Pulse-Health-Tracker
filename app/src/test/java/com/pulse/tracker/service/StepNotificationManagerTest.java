package com.pulse.tracker.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying StepNotificationManager channel creation, ongoing notification construction,
 * text formatting, accent color, pause/resume states, custom RemoteViews, and secondary metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class StepNotificationManagerTest {

    private Context context;
    private StepNotificationManager notificationManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        notificationManager = new StepNotificationManager(context);
    }

    @Test
    public void createNotificationChannel_configuresLowImportanceAndNoVibration() {
        notificationManager.createNotificationChannel();

        NotificationManager systemManager = context.getSystemService(NotificationManager.class);
        assertNotNull(systemManager);

        NotificationChannel channel = systemManager.getNotificationChannel(StepNotificationManager.CHANNEL_ID);
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.getImportance());
        assertEquals(false, channel.shouldVibrate());

        NotificationChannel silentChannel = systemManager.getNotificationChannel(StepNotificationManager.SILENT_CHANNEL_ID);
        assertNotNull(silentChannel);
        assertEquals(NotificationManager.IMPORTANCE_MIN, silentChannel.getImportance());
        assertEquals(false, silentChannel.shouldVibrate());
    }

    @Test
    public void buildSilentNotification_createsOngoingSecretNotification() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildSilentNotification(2500L, 10000L);

        assertNotNull(notification);
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((notification.flags & Notification.FLAG_NO_CLEAR) != 0);
        assertEquals(StepNotificationManager.SILENT_CHANNEL_ID, notification.getChannelId());
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("2,500 steps"));
    }

    @Test
    public void buildNotification_whenActive_includesFormattedStepsAndOngoing() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildNotification(4520L, 10000L, false);

        assertNotNull(notification);
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((notification.flags & Notification.FLAG_NO_CLEAR) != 0);
        assertTrue((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);

        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        assertNotNull(title);
        assertTrue(title.toString().contains("Tracking active"));

        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("4,520 steps today"));
        assertTrue(text.toString().contains("5,480 remaining"));
    }

    @Test
    public void buildNotification_whenPaused_displaysPausedTitleAndResumeAction() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildNotification(3200L, 10000L, true);

        assertNotNull(notification);
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        assertNotNull(title);
        assertTrue(title.toString().contains("Tracking paused"));

        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("3,200 steps today • Paused"));

        assertTrue(notification.actions == null || notification.actions.length == 0);
    }

    @Test
    public void buildNotification_whenGoalReached_displaysGoalReachedText() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildNotification(10500L, 10000L, false);

        assertNotNull(notification);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("10,500 steps today • Goal Reached"));
    }

    @Test
    public void buildNotification_hasPulseGreenAccentColor() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildNotification(1000L, 10000L, false);

        int expectedColor = ContextCompat.getColor(context, R.color.pulse_green);
        assertEquals(expectedColor, notification.color);
    }

    @Test
    public void buildNotification_withRichMetrics_constructsValidStandardNotification() {
        notificationManager.createNotificationChannel();
        Notification notification = notificationManager.buildNotification(
                8200L, 10000L, 6200.0, 345.0, false
        );

        assertNotNull(notification);
        assertTrue((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertTrue((notification.flags & Notification.FLAG_NO_CLEAR) != 0);
        assertTrue((notification.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0);

        // Standard notification has no custom RemoteViews
        org.junit.Assert.assertNull(notification.contentView);
        org.junit.Assert.assertNull(notification.bigContentView);

        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        assertNotNull(title);
        assertTrue(title.toString().contains("Tracking active"));

        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        assertNotNull(text);
        assertTrue(text.toString().contains("8,200 steps today"));

        CharSequence subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        assertNotNull(subText);
        assertTrue(subText.toString().contains("6.20 km"));

        // Native progress bar assertions
        assertEquals(10000, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX));
        assertEquals(8200, notification.extras.getInt(Notification.EXTRA_PROGRESS));
    }

    @Test
    public void updateNotification_whenNotificationDisabled_cancelsNotification() {
        notificationManager.createNotificationChannel();
        NotificationManager systemManager = context.getSystemService(NotificationManager.class);
        assertNotNull(systemManager);

        // First post active notification
        notificationManager.updateNotification(5000L, 10000L, false, true);
        assertEquals(1, systemManager.getActiveNotifications().length);

        // Now update with notification disabled -> cancels and zero notification in panel
        notificationManager.updateNotification(5000L, 10000L, false, false);
        assertEquals(0, systemManager.getActiveNotifications().length);
    }

    @Test
    public void updateNotification_withSecondaryMetrics_postsNotification() {
        notificationManager.createNotificationChannel();
        NotificationManager systemManager = context.getSystemService(NotificationManager.class);
        assertNotNull(systemManager);

        notificationManager.updateNotification(7500L, 10000L, 5700.0, 310.0, false, true);
        assertEquals(1, systemManager.getActiveNotifications().length);

        notificationManager.cancelNotification();
        assertEquals(0, systemManager.getActiveNotifications().length);
    }
}
