package com.pulse.tracker;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.ui.history.HistoryFragment;
import com.pulse.tracker.ui.home.TodayFragment;
import com.pulse.tracker.ui.onboarding.OnboardingActivity;
import com.pulse.tracker.ui.profile.ProfileFragment;
import com.pulse.tracker.ui.water.WaterFragment;

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
 * Unit tests verifying MainActivity 4-tab navigation (Steps, Water, History, Profile),
 * active label visibility vs inactive GONE state, configuration change preservation,
 * and onboarding gating.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    private Context context;
    private SettingsRepository settingsRepository;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        settingsRepository = SettingsRepository.getInstance(context);
        settingsRepository.setOnboardingCompleted(true);
    }

    @Test
    public void testOnboardingGating_whenNotCompleted_launchesOnboardingActivity() {
        settingsRepository.setOnboardingCompleted(false);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().get();

        assertTrue("MainActivity should finish when onboarding is incomplete", activity.isFinishing());
    }

    @Test
    public void testInitialLaunch_showsTodayTabAndIndicator() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        View tabToday = activity.findViewById(R.id.nav_tab_today);
        View indicatorToday = activity.findViewById(R.id.indicator_tab_today);
        View indicatorWater = activity.findViewById(R.id.indicator_tab_water);
        View indicatorHistory = activity.findViewById(R.id.indicator_tab_history);
        View indicatorProfile = activity.findViewById(R.id.indicator_tab_profile);

        assertNotNull(tabToday);
        assertEquals(View.VISIBLE, indicatorToday.getVisibility());
        assertEquals(View.INVISIBLE, indicatorWater.getVisibility());
        assertEquals(View.INVISIBLE, indicatorHistory.getVisibility());
        assertEquals(View.INVISIBLE, indicatorProfile.getVisibility());

        Fragment currentFrag = activity.getFragmentByTagCompat("tag_today");
        assertNotNull(currentFrag);
        assertTrue(currentFrag instanceof TodayFragment);

        android.widget.TextView labelToday = activity.findViewById(R.id.label_tab_today);
        android.widget.TextView labelWater = activity.findViewById(R.id.label_tab_water);
        android.widget.TextView labelHistory = activity.findViewById(R.id.label_tab_history);
        android.widget.TextView labelProfile = activity.findViewById(R.id.label_tab_profile);

        assertNotNull(labelToday);
        assertEquals("Steps", labelToday.getText().toString());
        assertEquals(View.VISIBLE, labelToday.getVisibility());
        assertEquals(View.GONE, labelWater.getVisibility());
        assertEquals(View.GONE, labelHistory.getVisibility());
        assertEquals(View.GONE, labelProfile.getVisibility());
    }

    @Test
    public void testTabNavigation_switchesAcrossAllFourTabs() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        View tabToday = activity.findViewById(R.id.nav_tab_today);
        View tabWater = activity.findViewById(R.id.nav_tab_water);
        View tabHistory = activity.findViewById(R.id.nav_tab_history);
        View tabProfile = activity.findViewById(R.id.nav_tab_profile);

        View indicatorToday = activity.findViewById(R.id.indicator_tab_today);
        View indicatorWater = activity.findViewById(R.id.indicator_tab_water);
        View indicatorHistory = activity.findViewById(R.id.indicator_tab_history);
        View indicatorProfile = activity.findViewById(R.id.indicator_tab_profile);

        android.widget.TextView labelToday = activity.findViewById(R.id.label_tab_today);
        android.widget.TextView labelWater = activity.findViewById(R.id.label_tab_water);
        android.widget.TextView labelHistory = activity.findViewById(R.id.label_tab_history);
        android.widget.TextView labelProfile = activity.findViewById(R.id.label_tab_profile);

        // 1. Switch to Water (pos 1)
        tabWater.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        assertEquals(View.INVISIBLE, indicatorToday.getVisibility());
        assertEquals(View.VISIBLE, indicatorWater.getVisibility());
        assertEquals(View.INVISIBLE, indicatorHistory.getVisibility());
        assertEquals(View.INVISIBLE, indicatorProfile.getVisibility());

        assertEquals(View.GONE, labelToday.getVisibility());
        assertEquals(View.VISIBLE, labelWater.getVisibility());
        assertEquals(View.GONE, labelHistory.getVisibility());
        assertEquals(View.GONE, labelProfile.getVisibility());

        Fragment waterFrag = activity.getFragmentByTagCompat("tag_water");
        assertNotNull(waterFrag);
        assertTrue(waterFrag instanceof WaterFragment);

        // 2. Switch to History (pos 2)
        tabHistory.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        assertEquals(View.INVISIBLE, indicatorToday.getVisibility());
        assertEquals(View.INVISIBLE, indicatorWater.getVisibility());
        assertEquals(View.VISIBLE, indicatorHistory.getVisibility());
        assertEquals(View.INVISIBLE, indicatorProfile.getVisibility());

        assertEquals(View.GONE, labelToday.getVisibility());
        assertEquals(View.GONE, labelWater.getVisibility());
        assertEquals(View.VISIBLE, labelHistory.getVisibility());
        assertEquals(View.GONE, labelProfile.getVisibility());

        Fragment historyFrag = activity.getFragmentByTagCompat("tag_history");
        assertNotNull(historyFrag);
        assertTrue(historyFrag instanceof HistoryFragment);

        // 3. Switch to Profile (pos 3)
        tabProfile.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        assertEquals(View.INVISIBLE, indicatorToday.getVisibility());
        assertEquals(View.INVISIBLE, indicatorWater.getVisibility());
        assertEquals(View.INVISIBLE, indicatorHistory.getVisibility());
        assertEquals(View.VISIBLE, indicatorProfile.getVisibility());

        assertEquals(View.GONE, labelToday.getVisibility());
        assertEquals(View.GONE, labelWater.getVisibility());
        assertEquals(View.GONE, labelHistory.getVisibility());
        assertEquals(View.VISIBLE, labelProfile.getVisibility());

        Fragment profileFrag = activity.getFragmentByTagCompat("tag_profile");
        assertNotNull(profileFrag);
        assertTrue(profileFrag instanceof ProfileFragment);
    }

    @Test
    public void testConfigurationChange_preservesActiveTabState() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        // Switch to Water tab
        View tabWater = activity.findViewById(R.id.nav_tab_water);
        tabWater.performClick();
        activity.getSupportFragmentManager().executePendingTransactions();

        // Simulate configuration change / rotation
        Bundle savedState = new Bundle();
        controller.saveInstanceState(savedState);
        controller.pause().stop().destroy();

        // Recreate activity with saved state
        ActivityController<MainActivity> recreatedController = Robolectric.buildActivity(MainActivity.class);
        MainActivity recreatedActivity = recreatedController.create(savedState).start().resume().visible().get();
        recreatedActivity.getSupportFragmentManager().executePendingTransactions();

        View recreatedIndicatorToday = recreatedActivity.findViewById(R.id.indicator_tab_today);
        View recreatedIndicatorWater = recreatedActivity.findViewById(R.id.indicator_tab_water);
        View recreatedIndicatorHistory = recreatedActivity.findViewById(R.id.indicator_tab_history);
        View recreatedIndicatorProfile = recreatedActivity.findViewById(R.id.indicator_tab_profile);

        assertEquals("Water indicator should remain active after rotation",
                View.VISIBLE, recreatedIndicatorWater.getVisibility());
        assertEquals(View.INVISIBLE, recreatedIndicatorToday.getVisibility());
        assertEquals(View.INVISIBLE, recreatedIndicatorHistory.getVisibility());
        assertEquals(View.INVISIBLE, recreatedIndicatorProfile.getVisibility());

        Fragment activeFrag = recreatedActivity.getFragmentByTagCompat("tag_water");
        assertNotNull(activeFrag);
        assertTrue(activeFrag instanceof WaterFragment);
    }

    @Test
    public void testPagerAdapter_containsFourDestinations() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        MainActivity.MainPagerAdapter adapter = new MainActivity.MainPagerAdapter(activity);
        assertEquals(4, adapter.getItemCount());
        assertTrue(adapter.createFragment(0) instanceof TodayFragment);
        assertTrue(adapter.createFragment(1) instanceof WaterFragment);
        assertTrue(adapter.createFragment(2) instanceof HistoryFragment);
        assertTrue(adapter.createFragment(3) instanceof ProfileFragment);
    }

    @Test
    public void testBottomNavSizing_andDynamicWeights() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class);
        MainActivity activity = controller.create().start().resume().visible().get();

        // 1. Verify Dock height is 58dp
        float dockHeight = activity.getResources().getDimension(R.dimen.bottom_navigation_height);
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals(58f, dockHeight / density, 0.5f);

        // 2. Verify Icon size is 24dp
        float iconSize = activity.getResources().getDimension(R.dimen.nav_icon_size);
        assertEquals(24f, iconSize / density, 0.5f);

        // 3. Verify Label text size is 13sp
        float labelSizeSp = activity.getResources().getDimension(R.dimen.nav_label_text_size) / activity.getResources().getDisplayMetrics().scaledDensity;
        assertEquals(13f, labelSizeSp, 0.5f);

        // 4. Verify Active Tab (Today) has expanded weight (1.65) and inactive tabs have weight 1.0
        View tabToday = activity.findViewById(R.id.nav_tab_today);
        View tabWater = activity.findViewById(R.id.nav_tab_water);
        View tabHistory = activity.findViewById(R.id.nav_tab_history);
        View tabProfile = activity.findViewById(R.id.nav_tab_profile);

        android.widget.LinearLayout.LayoutParams lpToday = (android.widget.LinearLayout.LayoutParams) tabToday.getLayoutParams();
        android.widget.LinearLayout.LayoutParams lpWater = (android.widget.LinearLayout.LayoutParams) tabWater.getLayoutParams();
        assertEquals(1.65f, lpToday.weight, 0.05f);
        assertEquals(1.0f, lpWater.weight, 0.05f);

        // 5. Switch to Water tab and verify weight changes dynamically
        tabWater.performClick();
        lpToday = (android.widget.LinearLayout.LayoutParams) tabToday.getLayoutParams();
        lpWater = (android.widget.LinearLayout.LayoutParams) tabWater.getLayoutParams();
        assertEquals(1.0f, lpToday.weight, 0.05f);
        assertEquals(1.65f, lpWater.weight, 0.05f);
    }
}
