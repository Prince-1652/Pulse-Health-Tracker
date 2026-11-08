package com.pulse.tracker.ui.onboarding;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
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

import static org.junit.Assert.*;

/**
 * Unit tests verifying OnboardingActivity 7-step view hierarchy,
 * dedicated screens for Identity, Gender/Age, Height, Weight, Goal, Hydration, Permissions,
 * component styling matching Profile, and swipe lockout controls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OnboardingActivityTest {

    private Context context;
    private SettingsRepository settingsRepository;

    @Before
    public void setUp() {
        Context appContext = ApplicationProvider.getApplicationContext();
        context = new ContextThemeWrapper(appContext, R.style.Theme_Pulse);
        settingsRepository = SettingsRepository.getInstance(appContext);
        settingsRepository.setOnboardingCompleted(false);
    }

    @Test
    public void testActivityCreation_bindsViewPagerAnd7IndicatorDots() {
        ActivityController<OnboardingActivity> controller = Robolectric.buildActivity(OnboardingActivity.class);
        OnboardingActivity activity = controller.create().start().resume().visible().get();

        assertNotNull("Activity should launch", activity);
        ViewPager2 viewPager = activity.findViewById(R.id.view_pager_onboarding);
        assertNotNull("ViewPager2 should exist", viewPager);
        assertEquals("Initial page should be 0", 0, viewPager.getCurrentItem());
        assertNotNull(viewPager.getAdapter());
        assertEquals("Total onboarding steps should be 7", 7, viewPager.getAdapter().getItemCount());

        assertNotNull(activity.findViewById(R.id.indicator_dot_0));
        assertNotNull(activity.findViewById(R.id.indicator_dot_1));
        assertNotNull(activity.findViewById(R.id.indicator_dot_2));
        assertNotNull(activity.findViewById(R.id.indicator_dot_3));
        assertNotNull(activity.findViewById(R.id.indicator_dot_4));
        assertNotNull(activity.findViewById(R.id.indicator_dot_5));
        assertNotNull(activity.findViewById(R.id.indicator_dot_6));
    }

    @Test
    public void testLayoutInflation_page1WelcomeAndIdentity() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page1 = inflater.inflate(R.layout.layout_onboarding_page1, null, false);
        assertNotNull("Page 1 should inflate", page1);

        EditText inputName = page1.findViewById(R.id.input_onboarding_name);
        assertNotNull("Display Name input should exist", inputName);

        MaterialCardView cardIdentity = page1.findViewById(R.id.card_onboarding_identity);
        assertNotNull("Identity Bento Card should exist", cardIdentity);
        assertEquals(0, cardIdentity.getStrokeWidth());

        MaterialCardView cardUnits = page1.findViewById(R.id.card_onboarding_units);
        assertNotNull("Units Bento Card should exist", cardUnits);
        assertEquals(0, cardUnits.getStrokeWidth());

        MaterialButtonToggleGroup toggleUnits = page1.findViewById(R.id.toggle_onboarding_units);
        assertNotNull("Units toggle group should exist", toggleUnits);

        MaterialCardView cardExplanation = page1.findViewById(R.id.card_unit_explanation);
        assertNotNull("Unit explanation card should exist", cardExplanation);
        assertEquals(0, cardExplanation.getStrokeWidth());

        TextView textName = page1.findViewById(R.id.text_unit_system_name);
        assertNotNull("Unit system name text should exist", textName);

        TextView textExample = page1.findViewById(R.id.text_unit_system_example);
        assertNotNull("Unit system example text should exist", textExample);
    }

    @Test
    public void testLayoutInflation_page2GenderAndAge() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page2 = inflater.inflate(R.layout.layout_onboarding_page2, null, false);
        assertNotNull("Page 2 should inflate", page2);

        MaterialCardView cardGender = page2.findViewById(R.id.card_onboarding_gender);
        assertNotNull("Gender card should exist", cardGender);
        assertEquals(0, cardGender.getStrokeWidth());

        MaterialButtonToggleGroup toggleGender = page2.findViewById(R.id.toggle_onboarding_gender);
        assertNotNull("Gender ToggleGroup should exist", toggleGender);

        EditText inputAge = page2.findViewById(R.id.input_onboarding_age);
        assertNotNull("Age input should exist", inputAge);
        assertNotNull(page2.findViewById(R.id.btn_age_minus));
        assertNotNull(page2.findViewById(R.id.btn_age_plus));
    }

    @Test
    public void testLayoutInflation_page3HeightCalibration() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page3 = inflater.inflate(R.layout.layout_onboarding_page3, null, false);
        assertNotNull("Page 3 should inflate", page3);

        MaterialCardView cardHeight = page3.findViewById(R.id.card_onboarding_height);
        assertNotNull("Height Bento Card should exist", cardHeight);
        assertEquals(0, cardHeight.getStrokeWidth());

        RulerTapeView rulerHeight = page3.findViewById(R.id.ruler_onboarding_height);
        assertNotNull("Height RulerTapeView should exist", rulerHeight);

        TextView textHeightValue = page3.findViewById(R.id.text_onboarding_height_value);
        assertNotNull("Height value display should exist", textHeightValue);

        TextView textStridePreview = page3.findViewById(R.id.text_onboarding_stride_preview);
        assertNotNull("Stride preview pill should exist", textStridePreview);
    }

    @Test
    public void testLayoutInflation_page4WeightCalibration() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page4 = inflater.inflate(R.layout.layout_onboarding_page4, null, false);
        assertNotNull("Page 4 should inflate", page4);

        MaterialCardView cardWeight = page4.findViewById(R.id.card_onboarding_weight);
        assertNotNull("Weight Bento Card should exist", cardWeight);
        assertEquals(0, cardWeight.getStrokeWidth());

        RulerTapeView rulerWeight = page4.findViewById(R.id.ruler_onboarding_weight);
        assertNotNull("Weight RulerTapeView should exist", rulerWeight);

        TextView textWeightValue = page4.findViewById(R.id.text_onboarding_weight_value);
        assertNotNull("Weight value display should exist", textWeightValue);

        TextView textBmrPreview = page4.findViewById(R.id.text_onboarding_bmr_preview);
        assertNotNull("BMR preview pill should exist", textBmrPreview);
    }

    @Test
    public void testLayoutInflation_page5DailyStepGoal() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page5 = inflater.inflate(R.layout.layout_onboarding_page5, null, false);
        assertNotNull("Page 5 should inflate", page5);

        MaterialCardView cardGoals = page5.findViewById(R.id.card_onboarding_goals);
        assertNotNull("Goals Bento Card should exist", cardGoals);
        assertEquals(0, cardGoals.getStrokeWidth());

        Slider sliderGoal = page5.findViewById(R.id.slider_onboarding_goal);
        assertNotNull("Daily target Slider should exist", sliderGoal);

        TextView textGoalValue = page5.findViewById(R.id.text_onboarding_goal_value);
        assertNotNull("Goal value display should exist", textGoalValue);

        assertNotNull(page5.findViewById(R.id.btn_preset_6k));
        assertNotNull(page5.findViewById(R.id.btn_preset_8k));
        assertNotNull(page5.findViewById(R.id.btn_preset_10k));
        assertNotNull(page5.findViewById(R.id.btn_preset_12k));
    }

    @Test
    public void testLayoutInflation_page6HydrationTarget() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page6 = inflater.inflate(R.layout.layout_onboarding_page6, null, false);
        assertNotNull("Page 6 should inflate", page6);

        MaterialCardView cardHero = page6.findViewById(R.id.card_onboarding_hydration_hero);
        assertNotNull("Hydration Hero card should exist", cardHero);
        assertEquals(0, cardHero.getStrokeWidth());

        TextView tvHydrationHero = page6.findViewById(R.id.tv_onboarding_hydration_hero);
        assertNotNull("Hydration Hero text should exist", tvHydrationHero);

        assertNotNull(page6.findViewById(R.id.tv_hydration_base));
        assertNotNull(page6.findViewById(R.id.tv_hydration_activity));
        assertNotNull(page6.findViewById(R.id.tv_hydration_bsa_age));
    }

    @Test
    public void testLayoutInflation_page7PermissionsAndPrivacy() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View page7 = inflater.inflate(R.layout.layout_onboarding_page7, null, false);
        assertNotNull("Page 7 should inflate", page7);

        MaterialCardView cardSensor = page7.findViewById(R.id.card_feature_sensor);
        assertNotNull("Feature Sensor card should exist", cardSensor);
        assertEquals(0, cardSensor.getStrokeWidth());

        MaterialCardView cardService = page7.findViewById(R.id.card_feature_service);
        assertNotNull("Feature Service card should exist", cardService);
        assertEquals(0, cardService.getStrokeWidth());

        MaterialCardView cardPrivacy = page7.findViewById(R.id.card_feature_privacy);
        assertNotNull("Feature Privacy card should exist", cardPrivacy);
        assertEquals(0, cardPrivacy.getStrokeWidth());
    }

    @Test
    public void testSwipeToggleHelper() {
        ActivityController<OnboardingActivity> controller = Robolectric.buildActivity(OnboardingActivity.class);
        OnboardingActivity activity = controller.create().start().resume().visible().get();

        ViewPager2 viewPager = activity.findViewById(R.id.view_pager_onboarding);
        assertTrue(viewPager.isUserInputEnabled());

        activity.setSwipeEnabled(false);
        assertFalse(viewPager.isUserInputEnabled());

        activity.setSwipeEnabled(true);
        assertTrue(viewPager.isUserInputEnabled());
    }

    @Test
    public void testContinuousIndicatorScrollProgress() {
        ActivityController<OnboardingActivity> controller = Robolectric.buildActivity(OnboardingActivity.class);
        OnboardingActivity activity = controller.create().start().resume().visible().get();

        View dot0 = activity.findViewById(R.id.indicator_dot_0);
        View dot1 = activity.findViewById(R.id.indicator_dot_1);
        View dot2 = activity.findViewById(R.id.indicator_dot_2);

        float density = activity.getResources().getDisplayMetrics().density;
        int minWidth = Math.round(8 * density);
        int maxWidth = Math.round(24 * density);

        // At rest page 0
        activity.updateIndicatorScrollProgress(0, 0f);
        assertEquals(maxWidth, dot0.getLayoutParams().width);
        assertEquals(minWidth, dot1.getLayoutParams().width);
        assertEquals(minWidth, dot2.getLayoutParams().width);

        // Halfway scroll between page 0 and page 1
        activity.updateIndicatorScrollProgress(0, 0.5f);
        int halfWidth = minWidth + Math.round((maxWidth - minWidth) * 0.5f);
        assertEquals(halfWidth, dot0.getLayoutParams().width);
        assertEquals(halfWidth, dot1.getLayoutParams().width);
        assertEquals(minWidth, dot2.getLayoutParams().width);

        // Settled on page 1
        activity.updateIndicatorScrollProgress(1, 0f);
        assertEquals(minWidth, dot0.getLayoutParams().width);
        assertEquals(maxWidth, dot1.getLayoutParams().width);
        assertEquals(minWidth, dot2.getLayoutParams().width);
    }
}
