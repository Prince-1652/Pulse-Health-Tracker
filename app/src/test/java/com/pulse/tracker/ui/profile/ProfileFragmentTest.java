package com.pulse.tracker.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.ui.common.RulerTapeView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProfileFragmentTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsRepository repo = SettingsRepository.getInstance(context);
        repo.setOnboardingCompleted(true);
        repo.setUserName("Tester");
        repo.setDailyStepTarget(10000L);
        repo.setHeightCm(175.0);
        repo.setWeightKg(70.0);
        repo.setAge(28);
        repo.setGender(SettingsRepository.Gender.MALE);
        repo.setUnitSystem(SettingsRepository.UnitSystem.METRIC);
        repo.setTrackingLatency(SettingsRepository.TrackingLatency.BATTERY_EFFICIENT);
        repo.clearManualStride();
    }

    private MainActivity launchProfileTab() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.KEY_ACTIVE_POSITION, MainActivity.POSITION_PROFILE);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent);
        MainActivity activity = controller.create().start().resume().visible().get();
        activity.selectTab(MainActivity.POSITION_PROFILE, false);
        return activity;
    }

    @Test
    public void testProfileFragment_initialHubBindingAndCategoryCards() {
        MainActivity activity = launchProfileTab();
        ProfileFragment fragment = (ProfileFragment) activity.getFragmentForPosition(MainActivity.POSITION_PROFILE);
        assertNotNull("ProfileFragment must be attached", fragment);

        View root = fragment.getView();
        assertNotNull("Root view must not be null", root);

        ViewFlipper flipper = root.findViewById(R.id.view_flipper_profile);
        assertNotNull("ViewFlipper must exist", flipper);
        assertEquals("Initial display must be Hub (page 0)", ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());
        assertEquals("Current page state must be PAGE_HUB", ProfileFragment.PAGE_HUB, fragment.getCurrentPage());

        // Verify User Profile Card & Edit Affordance
        EditText editName = root.findViewById(R.id.edit_profile_name);
        assertNotNull(editName);
        assertEquals("Tester", editName.getText().toString());

        View btnEditName = root.findViewById(R.id.btn_edit_profile_name);
        assertNotNull("Edit name button must exist", btnEditName);
        btnEditName.performClick();
        assertTrue("Edit name must be focused on edit button click", editName.hasFocus());

        // Verify All 5 Bento Category Cards Exist on Hub
        View cardBiometrics = root.findViewById(R.id.card_cat_biometrics);
        View cardGoals = root.findViewById(R.id.card_cat_goals);
        View cardCalibration = root.findViewById(R.id.card_cat_calibration);
        View cardPreferences = root.findViewById(R.id.card_cat_preferences);
        View cardAbout = root.findViewById(R.id.card_cat_about);

        assertNotNull("Biometrics category card exists", cardBiometrics);
        assertNotNull("Goals category card exists", cardGoals);
        assertNotNull("Calibration category card exists", cardCalibration);
        assertNotNull("Preferences category card exists", cardPreferences);
        assertNotNull("About category card exists", cardAbout);

        // Verify Category Subtitles
        TextView subBiometrics = root.findViewById(R.id.text_cat_biometrics_subtitle);
        TextView subGoals = root.findViewById(R.id.text_cat_goals_subtitle);
        TextView subCalibration = root.findViewById(R.id.text_cat_calibration_subtitle);
        TextView subPreferences = root.findViewById(R.id.text_cat_preferences_subtitle);
        TextView subAbout = root.findViewById(R.id.text_cat_about_subtitle);

        assertNotNull(subBiometrics);
        assertTrue("Biometrics subtitle contains height and weight", subBiometrics.getText().toString().contains("175 cm"));
        assertTrue("Biometrics subtitle contains weight", subBiometrics.getText().toString().contains("70"));

        assertNotNull(subGoals);
        assertTrue("Goals subtitle contains step count", subGoals.getText().toString().contains("10,000"));
        assertTrue("Goals subtitle contains hydration ml", subGoals.getText().toString().contains("ml"));

        assertNotNull(subCalibration);
        assertTrue("Calibration subtitle contains auto/manual stride", subCalibration.getText().toString().contains("Auto"));

        assertNotNull(subPreferences);
        assertTrue("Preferences subtitle contains Metric", subPreferences.getText().toString().contains("Metric"));

        assertNotNull(subAbout);
        assertTrue("About subtitle contains Pulse v1.0", subAbout.getText().toString().contains("Pulse v1.0"));
    }

    @Test
    public void testProfileFragment_navigationToDetailPagesAndBack() {
        MainActivity activity = launchProfileTab();
        ProfileFragment fragment = (ProfileFragment) activity.getFragmentForPosition(MainActivity.POSITION_PROFILE);
        View root = fragment.getView();
        ViewFlipper flipper = root.findViewById(R.id.view_flipper_profile);

        // 1. Biometrics Navigation
        root.findViewById(R.id.card_cat_biometrics).performClick();
        assertEquals(ProfileFragment.PAGE_BIOMETRICS, flipper.getDisplayedChild());
        assertEquals(ProfileFragment.PAGE_BIOMETRICS, fragment.getCurrentPage());

        // Precision controls inside Biometrics page
        RulerTapeView rulerHeight = root.findViewById(R.id.ruler_profile_height);
        RulerTapeView rulerWeight = root.findViewById(R.id.ruler_profile_weight);
        assertNotNull(rulerHeight);
        assertNotNull(rulerWeight);

        // Header back click
        root.findViewById(R.id.btn_back_biometrics).performClick();
        assertEquals(ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());
        assertEquals(ProfileFragment.PAGE_HUB, fragment.getCurrentPage());

        // 2. Goals & Targets Navigation
        root.findViewById(R.id.card_cat_goals).performClick();
        assertEquals(ProfileFragment.PAGE_GOALS, flipper.getDisplayedChild());
        assertEquals(ProfileFragment.PAGE_GOALS, fragment.getCurrentPage());

        Slider sliderGoal = root.findViewById(R.id.slider_profile_goal);
        assertNotNull(sliderGoal);
        assertEquals(50000f, sliderGoal.getValueTo(), 0.01f);
        View cardHydration = root.findViewById(R.id.card_hydration_guidance);
        assertNotNull("Hydration clinical guidance card must exist", cardHydration);

        // System back navigation
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertEquals(ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());
        assertEquals(ProfileFragment.PAGE_HUB, fragment.getCurrentPage());

        // 3. Calibration Navigation
        root.findViewById(R.id.card_cat_calibration).performClick();
        assertEquals(ProfileFragment.PAGE_CALIBRATION, flipper.getDisplayedChild());
        root.findViewById(R.id.btn_back_calibration).performClick();
        assertEquals(ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());

        // 4. Preferences Navigation
        root.findViewById(R.id.card_cat_preferences).performClick();
        assertEquals(ProfileFragment.PAGE_PREFERENCES, flipper.getDisplayedChild());
        root.findViewById(R.id.btn_back_preferences).performClick();
        assertEquals(ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());

        // 5. About Navigation
        root.findViewById(R.id.card_cat_about).performClick();
        assertEquals(ProfileFragment.PAGE_ABOUT, flipper.getDisplayedChild());
        root.findViewById(R.id.btn_back_about).performClick();
        assertEquals(ProfileFragment.PAGE_HUB, flipper.getDisplayedChild());
    }
}
