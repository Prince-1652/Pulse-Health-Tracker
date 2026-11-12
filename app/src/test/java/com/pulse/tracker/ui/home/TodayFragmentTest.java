package com.pulse.tracker.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.ui.common.CircularProgressView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying TodayFragment UI components, CircularProgressView rendering,
 * Bento modular metric cards, and dynamic state banner transitions.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TodayFragmentTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsRepository.getInstance(context).setOnboardingCompleted(true);
    }

    @Test
    public void testCircularProgressView_progressAndAccessibilityDescription() {
        CircularProgressView progressView = new CircularProgressView(context);
        progressView.setMax(10000);
        progressView.setCurrentSteps(8500);

        assertEquals(10000, progressView.getMax());
        assertEquals(8500, progressView.getCurrentSteps());
        assertEquals(0.85f, progressView.getProgress(), 0.01f);
        assertTrue(progressView.isCenterPercentageOnly());

        CharSequence contentDesc = progressView.getContentDescription();
        assertNotNull(contentDesc);
        assertTrue("Description should include steps", contentDesc.toString().contains("8,500"));
        assertTrue("Description should include max", contentDesc.toString().contains("10,000"));
        assertTrue("Description should include percent", contentDesc.toString().contains("85"));

        // Test setProgress synchronizing currentSteps
        progressView.setProgress(0.6f);
        assertEquals(6000L, progressView.getCurrentSteps());

        // Test setMax keeping animatedProgress in sync
        progressView.setMax(20000);
        assertEquals(0.3f, progressView.getProgress(), 0.01f);
    }

    @Test
    public void testTodayFragment_rapidSuccessiveEmissions_smoothlyUpdatesWithoutStaleOverwrite() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        TodayFragment fragment = (TodayFragment) activity.getFragmentByTagCompat("tag_today");
        assertNotNull(fragment);
        View root = fragment.getView();
        assertNotNull(root);

        TextView textHeroSteps = root.findViewById(R.id.text_hero_steps);
        assertNotNull(textHeroSteps);

        // Rapid emissions: 0 steps, then immediately 8,420 steps
        fragment.updateMetrics(0L, 10000, 0.0, 0.0, 0L);
        fragment.updateMetrics(8420L, 10000, 6000.0, 350.0, 3000L);
        fragment.endEntranceAnimations();

        // Must display 8,420 and NEVER overwrite with stale 0
        assertEquals("8,420", textHeroSteps.getText().toString());
    }

    @Test
    public void testTodayFragment_bindsBentoCardsAndInitialViews() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        Fragment currentFrag = activity.getFragmentByTagCompat("tag_today");
        assertNotNull("TodayFragment should be attached", currentFrag);
        assertTrue(currentFrag instanceof TodayFragment);

        TodayFragment fragment = (TodayFragment) currentFrag;
        View root = fragment.getView();
        assertNotNull(root);

        // Bento Cards presence
        MaterialCardView heroCard = root.findViewById(R.id.card_hero_progress);
        MaterialCardView distanceCard = root.findViewById(R.id.card_metric_distance);
        MaterialCardView activeTimeCard = root.findViewById(R.id.card_metric_active_time);
        MaterialCardView caloriesCard = root.findViewById(R.id.card_metric_calories);
        MaterialCardView weeklyChartCard = root.findViewById(R.id.card_weekly_chart);

        assertNotNull(heroCard);
        assertNotNull(distanceCard);
        assertNotNull(activeTimeCard);
        assertNotNull(caloriesCard);
        assertNotNull(weeklyChartCard);

        // Test atomic metric update
        fragment.updateMetrics(7500L, 10000, 5400.0, 320.0, 2400L);
        fragment.endEntranceAnimations();

        TextView textCalories = root.findViewById(R.id.text_calories_value);
        TextView textActiveTime = root.findViewById(R.id.text_active_time_value);
        TextView textDistance = root.findViewById(R.id.text_distance_value);
        TextView textHeroSteps = root.findViewById(R.id.text_hero_steps);

        assertNotNull(textHeroSteps);
        assertEquals("7,500", textHeroSteps.getText().toString());
        assertEquals("320", textCalories.getText().toString());
        assertEquals("40", textActiveTime.getText().toString()); // 2400s / 60 = 40 min
        assertEquals("5.40", textDistance.getText().toString());

        // Test fallback active minutes and calories when steps recorded but active metrics are 0
        fragment.updateMetrics(150L, 10000, 110.0, 0.0, 10L);
        fragment.endEntranceAnimations();
        assertEquals("1", textActiveTime.getText().toString()); // steps >= 100 gives 1 min fallback
        org.junit.Assert.assertNotEquals("0", textCalories.getText().toString()); // fallback step calories > 0
    }

    @Test
    public void testTodayFragment_emptyGraph_rendersGhostBarsAndPlaceholder() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        TodayFragment fragment = (TodayFragment) activity.getFragmentByTagCompat("tag_today");
        assertNotNull(fragment);
        View root = fragment.getView();
        assertNotNull(root);

        com.github.mikephil.charting.charts.BarChart chart = root.findViewById(R.id.weekly_bar_chart);
        View placeholder = root.findViewById(R.id.chart_empty_placeholder);

        assertNotNull(chart);
        assertNotNull(placeholder);
        assertEquals(View.VISIBLE, chart.getVisibility());
    }

    @Test
    public void testTodayFragment_topHeaderBar_andActionButtons() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        TodayFragment fragment = (TodayFragment) activity.getFragmentByTagCompat("tag_today");
        assertNotNull(fragment);
        View root = fragment.getView();
        assertNotNull(root);

        TextView textGreeting = root.findViewById(R.id.text_greeting_title);
        TextView textSubtitle = root.findViewById(R.id.text_greeting_subtitle);
        View btnNotificationToggle = root.findViewById(R.id.btn_notification_toggle);

        assertNotNull(textGreeting);
        assertEquals("Hi Prince!", textGreeting.getText().toString());
        assertNotNull(textSubtitle);
        assertEquals("Welcome back", textSubtitle.getText().toString());
        assertNotNull(btnNotificationToggle);

        // Verify switch to Profile tab via bottom nav
        View tabProfile = activity.findViewById(R.id.nav_tab_profile);
        assertNotNull(tabProfile);
        tabProfile.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        View indicatorProfile = activity.findViewById(R.id.indicator_tab_profile);
        assertNotNull(indicatorProfile);
        assertEquals(View.VISIBLE, indicatorProfile.getVisibility());

        // Switch back to today tab and test notification toggle
        View tabToday = activity.findViewById(R.id.nav_tab_today);
        tabToday.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        SettingsRepository settings = SettingsRepository.getInstance(context);
        assertTrue("Notification should be enabled by default", settings.isNotificationEnabled());

        // Click toggle button -> disables notification
        btnNotificationToggle.performClick();
        org.junit.Assert.assertFalse("Notification should be disabled after toggle", settings.isNotificationEnabled());

        // Click toggle button again -> enables notification
        btnNotificationToggle.performClick();
        assertTrue("Notification should be enabled after second toggle", settings.isNotificationEnabled());
    }
}
