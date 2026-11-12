package com.pulse.tracker.ui.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.CalorieCalculator;
import com.pulse.tracker.domain.DistanceCalculator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HistoryFragmentTest {

    private Context context;
    private SettingsRepository settingsRepository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        settingsRepository = SettingsRepository.getInstance(context);
        settingsRepository.setOnboardingCompleted(true);
        settingsRepository.setDailyStepTarget(10000L);
        settingsRepository.setHeightCm(170.0);
        settingsRepository.setWeightKg(70.0);
        settingsRepository.setUnitSystem(SettingsRepository.UnitSystem.METRIC);
        settingsRepository.clearManualStride();
    }

    private MainActivity launchHistoryTab() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_HISTORY);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent);
        MainActivity activity = controller.create().start().resume().visible().get();
        activity.selectTab(MainActivity.POSITION_HISTORY, false);
        return activity;
    }

    @Test
    public void testDetailCardZeroFallback_caloriesCalculatedWhenZeroWithSteps() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // Entity with 2000 steps but 0 kcal, 0 seconds, 0 distance
        DailyMetricsEntity entity = new DailyMetricsEntity("2026-11-12", 2000L, 0.0, 0L, 0.0, 10000L, 1L);
        fragment.bindSelectedDateMetricsForTest(entity);

        double expectedKcal = CalorieCalculator.calculateStepCalories(2000L, settingsRepository.getWeightKg(), settingsRepository.getStrideLengthCm());
        long roundedKcal = Math.round(expectedKcal);

        String calorieText = fragment.getTextSelectedDayCalories().getText().toString();
        assertFalse("Calories should not be 0 after walking 2000 steps", "0".equals(calorieText));
        assertEquals(String.valueOf(roundedKcal), calorieText);
    }

    @Test
    public void testDetailCardZeroFallback_activeSecondsCalculatedWhenZeroWithSteps() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // Entity with 1000 steps but 0 active seconds
        DailyMetricsEntity entity = new DailyMetricsEntity("2026-11-12", 1000L, 0.0, 0L, 0.0, 10000L, 1L);
        fragment.bindSelectedDateMetricsForTest(entity);

        // Fallback active seconds: Math.max(1, Math.round(1000 * 0.55)) = 550 seconds = 9 minutes (550 / 60)
        String activeTimeText = fragment.getTextSelectedDayActiveTime().getText().toString();
        assertEquals("9", activeTimeText);
    }

    @Test
    public void testDetailCardZeroFallback_distanceCalculatedWhenZeroWithSteps() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // Entity with 1000 steps but 0.0 distance
        DailyMetricsEntity entity = new DailyMetricsEntity("2026-11-12", 1000L, 0.0, 0L, 0.0, 10000L, 1L);
        fragment.bindSelectedDateMetricsForTest(entity);

        String distanceText = fragment.getTextSelectedDayDistance().getText().toString();
        assertFalse("Distance should not be 0.00 after walking 1000 steps", "0.00".equals(distanceText));
        // Stride is 170 * 0.415 = 70.55 cm. Distance for 1000 steps = 705.5m = 0.71 km
        assertEquals("0.71", distanceText);
    }

    @Test
    public void testMonthlyAveragePace_estimatesSecondsWhenDistancePresentButSecondsZero() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // 2000 steps, 1400m distance, 0 total active seconds
        MetricsRepository.MonthlyAnalytics analytics = new MetricsRepository.MonthlyAnalytics(
                2000L, 1400.0, 0L, 100.0, 0.0, null);
        fragment.bindMonthlyAnalyticsForTest(analytics);

        String pace = fragment.getTextPaceValue().getText().toString();
        assertFalse("Pace should not display empty placeholder when distance and steps exist",
                activity.getString(R.string.history_empty_placeholder).equals(pace));
        // 2000 * 0.55 = 1100s. Distance = 1.4km -> paceSecondsPerKm = 1100 / 1.4 = 785.7s/km = 13'05"
        assertEquals("13'05\"", pace);
    }

    @Test
    public void testMonthlyAveragePace_clampedToReasonableBounds() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // Case 1: Super fast sprint / glitch -> 1000m in 60s (1:00/km) -> clamped to 2:00/km
        MetricsRepository.MonthlyAnalytics fastAnalytics = new MetricsRepository.MonthlyAnalytics(
                1000L, 1000.0, 60L, 100.0, 1.0, null);
        fragment.bindMonthlyAnalyticsForTest(fastAnalytics);
        assertEquals("2'00\"", fragment.getTextPaceValue().getText().toString());

        // Case 2: Excessively slow / glitch -> 1000m in 3600s (60:00/km) -> clamped to 30:00/km
        MetricsRepository.MonthlyAnalytics slowAnalytics = new MetricsRepository.MonthlyAnalytics(
                1000L, 1000.0, 3600L, 100.0, 60.0, null);
        fragment.bindMonthlyAnalyticsForTest(slowAnalytics);
        assertEquals("30'00\"", fragment.getTextPaceValue().getText().toString());
    }

    @Test
    public void testMonthlySummaryDistance_usesFallbackWhenDistanceZeroWithSteps() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // 5000 steps, 0 distance
        MetricsRepository.MonthlyAnalytics analytics = new MetricsRepository.MonthlyAnalytics(
                5000L, 0.0, 0L, 0.0, 0.0, null);
        fragment.bindMonthlyAnalyticsForTest(analytics);

        String summaryDist = fragment.getTextSummaryDistance().getText().toString();
        // 5000 steps * 70.55cm = 3527.5m = 3.53 km
        assertFalse("Distance should not be 0.00 km when 5000 steps walked", summaryDist.contains("0.00"));
        assertTrue("Distance should reflect calculated km", summaryDist.contains("3.53"));
    }

    @Test
    public void testPersonalBestFormatting_greaterThan10k_lessThan10k_andNull() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // >= 10,000 steps uses %.1fk format
        DailyMetricsEntity pbHigh = new DailyMetricsEntity("2026-11-01", 12500L, 8000.0, 3600L, 300.0, 10000L, 1L);
        fragment.bindMonthlyAnalyticsForTest(new MetricsRepository.MonthlyAnalytics(12500L, 8000.0, 3600L, 300.0, 7.5, pbHigh));
        assertEquals("12.5k", fragment.getTextPersonalBestValue().getText().toString());

        // < 10,000 steps uses formatted steps (e.g. 8,420)
        DailyMetricsEntity pbLow = new DailyMetricsEntity("2026-11-01", 8420L, 6000.0, 3000L, 250.0, 10000L, 1L);
        fragment.bindMonthlyAnalyticsForTest(new MetricsRepository.MonthlyAnalytics(8420L, 6000.0, 3000L, 250.0, 8.0, pbLow));
        assertEquals("8,420", fragment.getTextPersonalBestValue().getText().toString());

        // Null / 0 steps uses empty placeholder '—'
        fragment.bindMonthlyAnalyticsForTest(new MetricsRepository.MonthlyAnalytics(0L, 0.0, 0L, 0.0, 0.0, null));
        assertEquals(activity.getString(R.string.history_empty_placeholder), fragment.getTextPersonalBestValue().getText().toString());
    }

    @Test
    public void testGoalAchievementRate_futureMonthShowsPlaceholder() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // All days in future -> elapsedDays == 0 -> should show '—'
        LocalDate futureDate = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        CalendarDayModel futureDay = new CalendarDayModel(futureDate, 1, false, false, false, true, 0L, 10000L);
        fragment.updateGoalAchievementRateForTest(Collections.singletonList(futureDay));
        assertEquals(activity.getString(R.string.history_empty_placeholder), fragment.getTextSummaryGoalRate().getText().toString());

        // Past day reached goal -> 1/1 = 100%
        LocalDate pastDate = LocalDate.now().minusDays(1);
        CalendarDayModel pastDay = new CalendarDayModel(pastDate, pastDate.getDayOfMonth(), false, false, true, true, 11000L, 10000L);
        fragment.updateGoalAchievementRateForTest(Collections.singletonList(pastDay));
        assertEquals("100%", fragment.getTextSummaryGoalRate().getText().toString());
    }

    @Test
    public void testMonthlyAveragePace_imperialUnitFormatting() {
        settingsRepository.setUnitSystem(SettingsRepository.UnitSystem.IMPERIAL);

        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // 1609.344m (1 mile) in 600 seconds (10 min) -> pace = 10'00" /mi
        MetricsRepository.MonthlyAnalytics analytics = new MetricsRepository.MonthlyAnalytics(
                2000L, 1609.344, 600L, 100.0, 6.21, null);
        fragment.bindMonthlyAnalyticsForTest(analytics);

        assertEquals("10'00\"", fragment.getTextPaceValue().getText().toString());
        assertEquals(" min/mi", fragment.getTextPaceUnit().getText().toString());
    }

    @Test
    public void testOnResume_executesWithoutCrash() {
        MainActivity activity = launchHistoryTab();
        HistoryFragment fragment = (HistoryFragment) activity.getFragmentByTagCompat(MainActivity.TAG_HISTORY);
        assertNotNull(fragment);

        // Resume fragment
        fragment.onResume();
        assertNotNull(fragment.getView());
    }
}
