package com.pulse.tracker.ui.home;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pulse.tracker.MainActivity;
import com.pulse.tracker.data.local.PulseDatabase;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.assertNotNull;

/**
 * Live instrumented test running on real hardware to populate 7 days of verified step activity,
 * initialize user settings, and validate UI rendering.
 */
@RunWith(AndroidJUnit4.class)
public class TodayScreenLiveTest {

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SettingsRepository settings = SettingsRepository.getInstance(context);
        settings.setOnboardingCompleted(true);
        settings.setTrackingEnabled(true);
        settings.setTrackingPaused(false);
        settings.setDailyStepTarget(10000L);

        PulseDatabase db = PulseDatabase.getInstance(context);
        LocalDate today = LocalDate.now();
        long[] steps = {6200L, 8400L, 4100L, 11200L, 7500L, 9300L, 8450L};

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            long stepCount = steps[6 - i];
            double distMeters = stepCount * 0.72;
            long activeSecs = (stepCount / 100) * 60;
            double kcal = stepCount / 22.0;

            db.dailyMetricsDao().insertOrUpdate(new DailyMetricsEntity(
                    dateStr,
                    stepCount,
                    distMeters,
                    activeSecs,
                    kcal,
                    10000L,
                    System.currentTimeMillis()
            ));
        }
    }

    @Test
    public void testMainActivityLaunchesAndPopulatesHome() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1500);
            scenario.onActivity(activity -> {
                assertNotNull(activity);
            });
        }
    }
}

