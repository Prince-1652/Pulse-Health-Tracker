package com.pulse.tracker.ui.water;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.data.repository.WaterRepository;
import com.pulse.tracker.ui.common.CircularProgressView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WaterFragmentTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsRepository.getInstance(context).setOnboardingCompleted(true);
        WaterRepository.resetTodayWater(context);
        WaterRepository.setRemindersEnabled(context, true);
    }

    @Test
    public void testWaterFragment_viewBindingAndQuickAdd() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_WATER);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent);
        MainActivity activity = controller.create().start().resume().visible().get();

        activity.selectTab(MainActivity.POSITION_WATER, false);

        WaterFragment fragment = (WaterFragment) activity.getFragmentForPosition(MainActivity.POSITION_WATER);
        assertNotNull("WaterFragment should be attached", fragment);

        View root = fragment.getView();
        assertNotNull("Root view should exist", root);

        CircularProgressView ring = root.findViewById(R.id.hero_water_progress_view);
        assertNotNull("Hero ring should exist", ring);

        TextView textCurrent = root.findViewById(R.id.text_water_current);
        assertNotNull("Current intake text should exist", textCurrent);
        assertEquals("0", textCurrent.getText().toString().trim());

        TextView textTarget = root.findViewById(R.id.text_water_target);
        assertNotNull("Target text should exist", textTarget);
        assertTrue("Target should have ml unit", textTarget.getText().toString().contains("ml"));

        MaterialCardView breakdownCard = root.findViewById(R.id.card_water_breakdown);
        assertNotNull("Breakdown bento card should exist", breakdownCard);

        // Test Quick Add +250 ml
        MaterialCardView btn250 = root.findViewById(R.id.btn_add_250);
        assertNotNull("Quick add 250 button should exist", btn250);
        btn250.performClick();
        assertEquals(250, WaterRepository.getTodayIntakeMl(context));

        // Test Quick Add +500 ml
        MaterialCardView btn500 = root.findViewById(R.id.btn_add_500);
        assertNotNull("Quick add 500 button should exist", btn500);
        btn500.performClick();
        assertEquals(750, WaterRepository.getTodayIntakeMl(context));

        // Test Quick Add +750 ml
        MaterialCardView btn750 = root.findViewById(R.id.btn_add_750);
        assertNotNull("Quick add 750 button should exist", btn750);
        btn750.performClick();
        assertEquals(1500, WaterRepository.getTodayIntakeMl(context));

        // Test Reset
        View btnReset = root.findViewById(R.id.btn_water_reset);
        assertNotNull("Reset button should exist", btnReset);
        btnReset.performClick();
        assertEquals(0, WaterRepository.getTodayIntakeMl(context));
    }

    @Test
    public void testWaterFragment_reminderToggle() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_WATER);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent);
        MainActivity activity = controller.create().start().resume().visible().get();

        activity.selectTab(MainActivity.POSITION_WATER, false);

        WaterFragment fragment = (WaterFragment) activity.getFragmentForPosition(MainActivity.POSITION_WATER);
        assertNotNull(fragment);

        View root = fragment.getView();
        assertNotNull(root);

        MaterialCardView btnToggle = root.findViewById(R.id.btn_water_reminder_toggle);
        assertNotNull("Reminder toggle button should exist", btnToggle);

        assertTrue(WaterRepository.isRemindersEnabled(context));
        btnToggle.performClick();
        assertTrue(!WaterRepository.isRemindersEnabled(context));
        btnToggle.performClick();
        assertTrue(WaterRepository.isRemindersEnabled(context));
    }
}
