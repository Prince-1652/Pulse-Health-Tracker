package com.pulse.tracker.ui.profile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.data.repository.WaterRepository;
import com.pulse.tracker.domain.WaterIntakeCalculator;
import com.pulse.tracker.ui.common.RulerTapeView;
import com.pulse.tracker.util.HapticHelper;

import java.util.Locale;

/**
 * Profile and settings destination implementing a categorized Bento settings hub
 * and dedicated detail pages for biometrics, targets, sensor calibration,
 * measurement units, and offline database privacy.
 *
 * Features precision measuring tape rulers with haptic ticks for Height and Weight,
 * magnetic snap step target slider with tactile feedback, auto-scrolling on input focus,
 * and ViewPager2 swipe protection during manipulation.
 */
public class ProfileFragment extends Fragment {

    public static final int PAGE_HUB = 0;
    public static final int PAGE_BIOMETRICS = 1;
    public static final int PAGE_GOALS = 2;
    public static final int PAGE_CALIBRATION = 3;
    public static final int PAGE_PREFERENCES = 4;
    public static final int PAGE_ABOUT = 5;

    public static final long MAX_STEP_GOAL = 50000L;

    private static final String KEY_CURRENT_PAGE = "key_profile_current_page";

    private ProfileViewModel viewModel;

    private ViewFlipper viewFlipper;
    private int currentPage = PAGE_HUB;
    private OnBackPressedCallback onBackPressedCallback;

    // Scroll containers
    private NestedScrollView profileScrollView;
    private NestedScrollView scrollPageBiometrics;
    private NestedScrollView scrollPageGoals;
    private NestedScrollView scrollPageCalibration;
    private NestedScrollView scrollPagePreferences;
    private NestedScrollView scrollPageAbout;

    // Hub Bento: Profile Identity Card
    private View cardProfileIdentity;
    private EditText editProfileName;
    private ImageView btnEditProfileName;
    private boolean isEditingName = false;

    // Hub Bento: Category Cards & Subtitles
    private View cardCatBiometrics;
    private TextView textCatBiometricsSubtitle;

    private View cardCatGoals;
    private TextView textCatGoalsSubtitle;

    private View cardCatCalibration;
    private TextView textCatCalibrationSubtitle;

    private View cardCatPreferences;
    private TextView textCatPreferencesSubtitle;

    private View cardCatAbout;
    private TextView textCatAboutSubtitle;

    // Detail Back Buttons
    private View btnBackBiometrics;
    private View btnBackGoals;
    private View btnBackCalibration;
    private View btnBackPreferences;
    private View btnBackAbout;

    // Detail Page 1: Biometrics
    private View cardProfileBiometrics;
    private TextView textHeightValue;
    private TextView textHeightUnit;
    private RulerTapeView rulerHeight;

    private TextView textWeightValue;
    private TextView textWeightUnit;
    private RulerTapeView rulerWeight;

    private View cardProfileAge;
    private EditText editAge;
    private MaterialButtonToggleGroup toggleGroupGender;
    private MaterialButton btnGenderMale;
    private MaterialButton btnGenderFemale;
    private MaterialButton btnGenderUnspecified;

    // Detail Page 2: Goals & Targets
    private View cardProfileGoals;
    private EditText editGoal;
    private Slider sliderGoal;
    private TextView textWaterGoalValue;

    // Detail Page 3: Tracking & Calibration
    private View cardProfileCalibration;
    private EditText editStride;
    private TextView textStrideUnit;
    private MaterialButton btnResetStride;
    private MaterialButtonToggleGroup toggleGroupLatency;
    private MaterialButton btnLatencyBattery;
    private MaterialButton btnLatencyFaster;

    // Detail Page 4: Preferences & Units
    private MaterialButtonToggleGroup toggleGroupUnits;
    private MaterialButton btnUnitMetric;
    private MaterialButton btnUnitImperial;

    // State Variables
    private SettingsRepository.UnitSystem currentUnitSystem = SettingsRepository.UnitSystem.METRIC;
    private double currentHeightCm = SettingsRepository.DEFAULT_HEIGHT_CM;
    private double currentWeightKg = SettingsRepository.DEFAULT_WEIGHT_KG;
    private int currentAge = SettingsRepository.DEFAULT_AGE;
    private SettingsRepository.Gender currentGender = SettingsRepository.Gender.UNSPECIFIED;
    private double currentStrideCm = SettingsRepository.DEFAULT_HEIGHT_CM * SettingsRepository.STRIDE_HEIGHT_RATIO;
    private boolean currentIsManualStride = false;
    private long currentGoal = SettingsRepository.DEFAULT_STEP_GOAL;
    private SettingsRepository.TrackingLatency currentLatency = SettingsRepository.TrackingLatency.BATTERY_EFFICIENT;

    private boolean isProgrammaticUpdate = false;
    private boolean isSliderDragging = false;
    private boolean isHeightRulerDragging = false;
    private boolean isWeightRulerDragging = false;
    private int lastReportedGoalTick = -1;

    public ProfileFragment() {
        // Required empty public constructor
    }

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupBackNavigation();
        setupActionListeners(view);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        observeViewModel();

        if (savedInstanceState != null) {
            int restoredPage = savedInstanceState.getInt(KEY_CURRENT_PAGE, PAGE_HUB);
            if (restoredPage != PAGE_HUB) {
                navigateToPage(restoredPage, false);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_PAGE, currentPage);
    }

    private void bindViews(@NonNull View root) {
        viewFlipper = root.findViewById(R.id.view_flipper_profile);

        // Scroll containers
        profileScrollView = root.findViewById(R.id.profile_scroll_view);
        scrollPageBiometrics = root.findViewById(R.id.scroll_page_biometrics);
        scrollPageGoals = root.findViewById(R.id.scroll_page_goals);
        scrollPageCalibration = root.findViewById(R.id.scroll_page_calibration);
        scrollPagePreferences = root.findViewById(R.id.scroll_page_preferences);
        scrollPageAbout = root.findViewById(R.id.scroll_page_about);

        // Hub: User Profile Identity
        cardProfileIdentity = root.findViewById(R.id.card_profile_identity);
        editProfileName = root.findViewById(R.id.edit_profile_name);
        btnEditProfileName = root.findViewById(R.id.btn_edit_profile_name);

        // Hub: Bento Categories
        cardCatBiometrics = root.findViewById(R.id.card_cat_biometrics);
        textCatBiometricsSubtitle = root.findViewById(R.id.text_cat_biometrics_subtitle);

        cardCatGoals = root.findViewById(R.id.card_cat_goals);
        textCatGoalsSubtitle = root.findViewById(R.id.text_cat_goals_subtitle);

        cardCatCalibration = root.findViewById(R.id.card_cat_calibration);
        textCatCalibrationSubtitle = root.findViewById(R.id.text_cat_calibration_subtitle);

        cardCatPreferences = root.findViewById(R.id.card_cat_preferences);
        textCatPreferencesSubtitle = root.findViewById(R.id.text_cat_preferences_subtitle);

        cardCatAbout = root.findViewById(R.id.card_cat_about);
        textCatAboutSubtitle = root.findViewById(R.id.text_cat_about_subtitle);

        // Detail Back Buttons
        btnBackBiometrics = root.findViewById(R.id.btn_back_biometrics);
        btnBackGoals = root.findViewById(R.id.btn_back_goals);
        btnBackCalibration = root.findViewById(R.id.btn_back_calibration);
        btnBackPreferences = root.findViewById(R.id.btn_back_preferences);
        btnBackAbout = root.findViewById(R.id.btn_back_about);

        // Detail Page 1: Biometrics
        cardProfileBiometrics = root.findViewById(R.id.card_profile_biometrics);
        textHeightValue = root.findViewById(R.id.text_profile_height_value);
        textHeightUnit = root.findViewById(R.id.text_profile_height_unit);
        rulerHeight = root.findViewById(R.id.ruler_profile_height);
        if (rulerHeight != null && getContext() != null) {
            rulerHeight.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.pulse_sky_blue));
        }

        textWeightValue = root.findViewById(R.id.text_profile_weight_value);
        textWeightUnit = root.findViewById(R.id.text_profile_weight_unit);
        rulerWeight = root.findViewById(R.id.ruler_profile_weight);
        if (rulerWeight != null && getContext() != null) {
            rulerWeight.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.pulse_orange));
        }

        cardProfileAge = root.findViewById(R.id.card_profile_age);
        editAge = root.findViewById(R.id.edit_profile_age);
        toggleGroupGender = root.findViewById(R.id.toggle_group_gender);
        btnGenderMale = root.findViewById(R.id.btn_gender_male);
        btnGenderFemale = root.findViewById(R.id.btn_gender_female);
        btnGenderUnspecified = root.findViewById(R.id.btn_gender_unspecified);

        // Detail Page 2: Goals & Targets
        cardProfileGoals = root.findViewById(R.id.card_profile_goals);
        editGoal = root.findViewById(R.id.edit_profile_goal);
        sliderGoal = root.findViewById(R.id.slider_profile_goal);
        textWaterGoalValue = root.findViewById(R.id.text_water_goal_value);

        // Detail Page 3: Tracking & Calibration
        cardProfileCalibration = root.findViewById(R.id.card_profile_calibration);
        editStride = root.findViewById(R.id.edit_profile_stride);
        textStrideUnit = root.findViewById(R.id.text_profile_stride_unit);
        btnResetStride = root.findViewById(R.id.btn_reset_stride);
        toggleGroupLatency = root.findViewById(R.id.toggle_group_latency);
        btnLatencyBattery = root.findViewById(R.id.btn_latency_battery);
        btnLatencyFaster = root.findViewById(R.id.btn_latency_faster);

        // Detail Page 4: Preferences & Units
        toggleGroupUnits = root.findViewById(R.id.toggle_group_units);
        btnUnitMetric = root.findViewById(R.id.btn_unit_metric);
        btnUnitImperial = root.findViewById(R.id.btn_unit_imperial);
    }

    private void setupBackNavigation() {
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                navigateToHub();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    public void navigateToPage(int pageIndex, boolean animate) {
        if (viewFlipper == null || pageIndex == currentPage) return;

        if (animate && getContext() != null) {
            if (pageIndex > currentPage) {
                viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
                viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
            } else {
                viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
                viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
            }
        } else {
            viewFlipper.setInAnimation(null);
            viewFlipper.setOutAnimation(null);
        }

        currentPage = pageIndex;
        viewFlipper.setDisplayedChild(pageIndex);

        if (onBackPressedCallback != null) {
            onBackPressedCallback.setEnabled(currentPage != PAGE_HUB);
        }

        // Reset scroll position of target page to top
        NestedScrollView targetScroll = getScrollViewForPage(pageIndex);
        if (targetScroll != null) {
            targetScroll.scrollTo(0, 0);
        }

        // Dismiss keyboard if open
        View currentFocus = getActivity() != null ? getActivity().getCurrentFocus() : null;
        if (currentFocus != null) {
            currentFocus.clearFocus();
            hideKeyboard(currentFocus);
        }
    }

    public void navigateToHub() {
        navigateToPage(PAGE_HUB, true);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    @Nullable
    private NestedScrollView getScrollViewForPage(int page) {
        switch (page) {
            case PAGE_HUB: return profileScrollView;
            case PAGE_BIOMETRICS: return scrollPageBiometrics;
            case PAGE_GOALS: return scrollPageGoals;
            case PAGE_CALIBRATION: return scrollPageCalibration;
            case PAGE_PREFERENCES: return scrollPagePreferences;
            case PAGE_ABOUT: return scrollPageAbout;
            default: return null;
        }
    }

    private void setupActionListeners(@NonNull View root) {
        // Touch outside to hide keyboard on all scroll containers
        View.OnTouchListener dismissKeyboardOnTouch = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View currentFocus = getActivity() != null ? getActivity().getCurrentFocus() : null;
                if (currentFocus != null) {
                    currentFocus.clearFocus();
                    hideKeyboard(currentFocus);
                }
            }
            return false;
        };

        if (profileScrollView != null) profileScrollView.setOnTouchListener(dismissKeyboardOnTouch);
        if (scrollPageBiometrics != null) scrollPageBiometrics.setOnTouchListener(dismissKeyboardOnTouch);
        if (scrollPageGoals != null) scrollPageGoals.setOnTouchListener(dismissKeyboardOnTouch);
        if (scrollPageCalibration != null) scrollPageCalibration.setOnTouchListener(dismissKeyboardOnTouch);
        if (scrollPagePreferences != null) scrollPagePreferences.setOnTouchListener(dismissKeyboardOnTouch);
        if (scrollPageAbout != null) scrollPageAbout.setOnTouchListener(dismissKeyboardOnTouch);

        // Hub Bento Navigation Clicks
        if (cardCatBiometrics != null) cardCatBiometrics.setOnClickListener(v -> navigateToPage(PAGE_BIOMETRICS, true));
        if (cardCatGoals != null) cardCatGoals.setOnClickListener(v -> navigateToPage(PAGE_GOALS, true));
        if (cardCatCalibration != null) cardCatCalibration.setOnClickListener(v -> navigateToPage(PAGE_CALIBRATION, true));
        if (cardCatPreferences != null) cardCatPreferences.setOnClickListener(v -> navigateToPage(PAGE_PREFERENCES, true));
        if (cardCatAbout != null) cardCatAbout.setOnClickListener(v -> navigateToPage(PAGE_ABOUT, true));

        // Detail Page Back Button Clicks
        if (btnBackBiometrics != null) btnBackBiometrics.setOnClickListener(v -> navigateToHub());
        if (btnBackGoals != null) btnBackGoals.setOnClickListener(v -> navigateToHub());
        if (btnBackCalibration != null) btnBackCalibration.setOnClickListener(v -> navigateToHub());
        if (btnBackPreferences != null) btnBackPreferences.setOnClickListener(v -> navigateToHub());
        if (btnBackAbout != null) btnBackAbout.setOnClickListener(v -> navigateToHub());

        // Display Name listener + edit affordance + auto scroll
        if (btnEditProfileName != null) {
            btnEditProfileName.setOnClickListener(v -> {
                if (editProfileName == null) return;
                HapticHelper.tick(getContext());
                if (isEditingName) {
                    commitName();
                    editProfileName.clearFocus();
                    hideKeyboard(editProfileName);
                    updateEditNameButtonVisuals(false);
                } else {
                    updateEditNameButtonVisuals(true);
                    editProfileName.requestFocus();
                    editProfileName.setSelection(editProfileName.getText() != null ? editProfileName.getText().length() : 0);
                    showKeyboard(editProfileName);
                }
            });
        }
        if (editProfileName != null) {
            setupFocusAutoScroll(editProfileName, cardProfileIdentity);
            editProfileName.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && isEditingName) {
                    commitName();
                    updateEditNameButtonVisuals(false);
                }
            });
            editProfileName.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    commitName();
                    v.clearFocus();
                    hideKeyboard(v);
                    updateEditNameButtonVisuals(false);
                    return true;
                }
                return false;
            });
            editProfileName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isProgrammaticUpdate) return;
                    if (s != null && s.toString().trim().length() > 0) {
                        viewModel.saveUserName(s.toString().trim());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Height Ruler Tape View listener
        if (rulerHeight != null) {
            rulerHeight.setOnValueChangeListener(new RulerTapeView.OnValueChangeListener() {
                @Override
                public void onValueChanged(float value) {
                    isHeightRulerDragging = true;
                    if (textHeightValue != null) {
                        textHeightValue.setText(String.format(Locale.getDefault(), "%.0f", value));
                    }
                }

                @Override
                public void onValueConfirmed(float value) {
                    isHeightRulerDragging = false;
                    viewModel.saveHeight(value, currentUnitSystem);
                }
            });
        }

        // Weight Ruler Tape View listener
        if (rulerWeight != null) {
            rulerWeight.setOnValueChangeListener(new RulerTapeView.OnValueChangeListener() {
                @Override
                public void onValueChanged(float value) {
                    isWeightRulerDragging = true;
                    if (textWeightValue != null) {
                        if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
                            textWeightValue.setText(String.format(Locale.getDefault(), "%.0f", value));
                        } else {
                            textWeightValue.setText(String.format(Locale.getDefault(), "%.1f", value));
                        }
                    }
                }

                @Override
                public void onValueConfirmed(float value) {
                    isWeightRulerDragging = false;
                    viewModel.saveWeight(value, currentUnitSystem);
                }
            });
        }

        // Age card click -> focus editAge and show soft keyboard
        if (cardProfileAge != null) {
            cardProfileAge.setOnClickListener(v -> {
                if (editAge != null) {
                    editAge.requestFocus();
                    showKeyboard(editAge);
                }
            });
        }

        // Age validation & commit + auto scroll
        if (editAge != null) {
            setupFocusAutoScroll(editAge, cardProfileAge != null ? cardProfileAge : editAge);
            editAge.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    commitAge();
                    v.clearFocus();
                    hideKeyboard(editAge);
                    return true;
                }
                return false;
            });
            editAge.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (editAge.hasFocus()) {
                        scrollToTarget(cardProfileAge != null ? cardProfileAge : editAge);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        // Gender toggle group
        if (toggleGroupGender != null) {
            toggleGroupGender.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isProgrammaticUpdate || !isChecked) {
                    return;
                }
                SettingsRepository.Gender selectedGender;
                if (checkedId == R.id.btn_gender_male) {
                    selectedGender = SettingsRepository.Gender.MALE;
                } else if (checkedId == R.id.btn_gender_female) {
                    selectedGender = SettingsRepository.Gender.FEMALE;
                } else {
                    selectedGender = SettingsRepository.Gender.UNSPECIFIED;
                }
                viewModel.saveGender(selectedGender);
            });
        }

        // Stride validation & commit + auto scroll
        if (editStride != null) {
            setupFocusAutoScroll(editStride, cardProfileCalibration != null ? cardProfileCalibration : editStride);
            editStride.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitStride();
                    v.clearFocus();
                    hideKeyboard(editStride);
                    return true;
                }
                return false;
            });
        }

        // Stride reset button
        if (btnResetStride != null) {
            btnResetStride.setOnClickListener(v -> {
                if (viewModel != null) {
                    viewModel.resetStrideToDefault();
                }
            });
        }

        // Tracking latency toggle group
        if (toggleGroupLatency != null) {
            toggleGroupLatency.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isProgrammaticUpdate || !isChecked) {
                    return;
                }
                SettingsRepository.TrackingLatency latency = (checkedId == R.id.btn_latency_faster)
                        ? SettingsRepository.TrackingLatency.FASTER_UPDATES
                        : SettingsRepository.TrackingLatency.BATTERY_EFFICIENT;
                viewModel.saveTrackingLatency(latency);
            });
        }

        // Daily step goal numeric input commit + auto scroll
        if (editGoal != null) {
            setupFocusAutoScroll(editGoal, cardProfileGoals != null ? cardProfileGoals : editGoal);
            editGoal.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitGoal();
                    v.clearFocus();
                    hideKeyboard(editGoal);
                    return true;
                }
                return false;
            });
        }

        // Daily step goal slider with magnetic ticks & ViewPager2 swipe protection
        if (sliderGoal != null) {
            sliderGoal.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    ViewParent p = v.getParent();
                    while (p != null) {
                        p.requestDisallowInterceptTouchEvent(true);
                        p = p.getParent();
                    }
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setSwipeEnabled(false);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    ViewParent p = v.getParent();
                    while (p != null) {
                        p.requestDisallowInterceptTouchEvent(false);
                        p = p.getParent();
                    }
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setSwipeEnabled(true);
                    }
                }
                return false;
            });

            sliderGoal.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    long goalSteps = Math.round(value / 500.0f) * 500L;
                    currentGoal = goalSteps;
                    if (editGoal != null && !editGoal.hasFocus()) {
                        editGoal.setText(String.format(Locale.getDefault(), "%,d", goalSteps));
                    }
                    int tick = (int) (goalSteps / 500L);
                    if (tick != lastReportedGoalTick) {
                        lastReportedGoalTick = tick;
                        boolean isMilestone = (goalSteps % 5000L == 0);
                        triggerMagneticTick(slider, isMilestone, false);
                    }
                }
            });

            sliderGoal.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    isSliderDragging = true;
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setSwipeEnabled(false);
                    }
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    isSliderDragging = false;
                    long goalSteps = Math.round(slider.getValue() / 500.0f) * 500L;
                    goalSteps = Math.max(500L, Math.min(MAX_STEP_GOAL, goalSteps));
                    currentGoal = goalSteps;
                    slider.setValue((float) goalSteps);
                    if (editGoal != null && !editGoal.hasFocus()) {
                        editGoal.setText(String.format(Locale.getDefault(), "%,d", goalSteps));
                    }
                    viewModel.saveDailyStepGoal(goalSteps);
                    triggerMagneticTick(slider, true, true);
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setSwipeEnabled(true);
                    }
                }
            });
        }

        // Unit system toggle group
        if (toggleGroupUnits != null) {
            toggleGroupUnits.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isProgrammaticUpdate || !isChecked) {
                    return;
                }
                SettingsRepository.UnitSystem unit = (checkedId == R.id.btn_unit_imperial)
                        ? SettingsRepository.UnitSystem.IMPERIAL
                        : SettingsRepository.UnitSystem.METRIC;
                viewModel.saveUnitSystem(unit);
            });
        }
    }

    private void setupFocusAutoScroll(@NonNull EditText editText, @NonNull View containerCard) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).setSwipeEnabled(false);
                }
                scrollToTarget(containerCard);
            } else {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).setSwipeEnabled(true);
                }
                if (v == editProfileName) commitName();
                else if (v == editAge) commitAge();
                else if (v == editStride) commitStride();
                else if (v == editGoal) commitGoal();
            }
        });
    }

    private void scrollToTarget(@NonNull View target) {
        NestedScrollView parentScrollView = findParentScrollView(target);
        if (parentScrollView == null) parentScrollView = profileScrollView;
        if (parentScrollView == null) return;

        final NestedScrollView scrollView = parentScrollView;
        scrollView.postDelayed(() -> {
            if (!isAdded() || getContext() == null) return;
            int[] targetLoc = new int[2];
            target.getLocationOnScreen(targetLoc);
            int[] scrollLoc = new int[2];
            scrollView.getLocationOnScreen(scrollLoc);
            int relativeY = targetLoc[1] - scrollLoc[1] + scrollView.getScrollY();
            float density = getResources().getDisplayMetrics().density;
            int offset = Math.max(0, relativeY - (int) (24f * density));
            scrollView.smoothScrollTo(0, offset);
        }, 250);
    }

    private NestedScrollView findParentScrollView(View view) {
        ViewParent p = view.getParent();
        while (p != null) {
            if (p instanceof NestedScrollView) {
                return (NestedScrollView) p;
            }
            p = p.getParent();
        }
        return null;
    }

    private void triggerMagneticTick(@NonNull View sourceView, boolean isMilestone, boolean force) {
        if (isMilestone) {
            HapticHelper.performMilestone(getContext());
        } else {
            HapticHelper.performTick(getContext(), force);
        }
    }

    private void observeViewModel() {
        // User Name
        viewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name == null || editProfileName == null) return;
            if (!editProfileName.hasFocus()) {
                isProgrammaticUpdate = true;
                editProfileName.setText(name);
                isProgrammaticUpdate = false;
            }
        });

        // Unit System
        viewModel.getUnitSystem().observe(getViewLifecycleOwner(), unit -> {
            if (unit == null) return;
            this.currentUnitSystem = unit;
            updateUnitSystemViews(unit);
            updateHubSubtitles();
        });

        // Height
        viewModel.getHeightCm().observe(getViewLifecycleOwner(), height -> {
            if (height == null) return;
            this.currentHeightCm = height;
            updateHeightDisplay(height);
            updateHubSubtitles();
        });

        // Weight
        viewModel.getWeightKg().observe(getViewLifecycleOwner(), weight -> {
            if (weight == null) return;
            this.currentWeightKg = weight;
            updateWeightDisplay(weight);
            updateHubSubtitles();
        });

        // Age
        viewModel.getAge().observe(getViewLifecycleOwner(), age -> {
            if (age == null) return;
            this.currentAge = age;
            if (editAge != null && !editAge.hasFocus()) {
                editAge.setText(String.valueOf(age));
            }
            updateHubSubtitles();
        });

        // Gender
        viewModel.getGender().observe(getViewLifecycleOwner(), gender -> {
            if (gender == null) return;
            this.currentGender = gender;
            if (toggleGroupGender != null) {
                int targetId;
                switch (gender) {
                    case MALE:
                        targetId = R.id.btn_gender_male;
                        break;
                    case FEMALE:
                        targetId = R.id.btn_gender_female;
                        break;
                    case UNSPECIFIED:
                    default:
                        targetId = R.id.btn_gender_unspecified;
                        break;
                }
                if (toggleGroupGender.getCheckedButtonId() != targetId) {
                    isProgrammaticUpdate = true;
                    toggleGroupGender.check(targetId);
                    isProgrammaticUpdate = false;
                }
            }
            updateHubSubtitles();
        });

        // Stride
        viewModel.getStrideLengthCm().observe(getViewLifecycleOwner(), stride -> {
            if (stride == null) return;
            this.currentStrideCm = stride;
            updateStrideDisplay(stride);
            updateHubSubtitles();
        });

        // Manual Stride Flag
        viewModel.getIsManualStride().observe(getViewLifecycleOwner(), isManual -> {
            this.currentIsManualStride = Boolean.TRUE.equals(isManual);
            if (btnResetStride != null) {
                btnResetStride.setVisibility(currentIsManualStride ? View.VISIBLE : View.GONE);
            }
            updateHubSubtitles();
        });

        // Tracking Latency
        viewModel.getTrackingLatency().observe(getViewLifecycleOwner(), latency -> {
            if (latency == null) return;
            this.currentLatency = latency;
            if (toggleGroupLatency != null) {
                int targetId = (latency == SettingsRepository.TrackingLatency.FASTER_UPDATES)
                        ? R.id.btn_latency_faster
                        : R.id.btn_latency_battery;
                if (toggleGroupLatency.getCheckedButtonId() != targetId) {
                    isProgrammaticUpdate = true;
                    toggleGroupLatency.check(targetId);
                    isProgrammaticUpdate = false;
                }
            }
            updateHubSubtitles();
        });

        // Daily Step Goal
        viewModel.getDailyStepGoal().observe(getViewLifecycleOwner(), goal -> {
            if (goal == null) return;
            this.currentGoal = goal;
            if (editGoal != null && !editGoal.hasFocus()) {
                editGoal.setText(String.format(Locale.getDefault(), "%,d", goal));
            }
            if (sliderGoal != null && !isSliderDragging) {
                float clamped = Math.min(sliderGoal.getValueTo(), Math.max(sliderGoal.getValueFrom(), (float) goal));
                float snapped = Math.round((clamped - sliderGoal.getValueFrom()) / sliderGoal.getStepSize()) * sliderGoal.getStepSize() + sliderGoal.getValueFrom();
                sliderGoal.setValue(snapped);
                lastReportedGoalTick = (int) (snapped / 500f);
            }
            updateHubSubtitles();
        });
    }

    private void updateHubSubtitles() {
        if (!isAdded() || getContext() == null) return;

        // 1. Biometrics & Physical
        if (textCatBiometricsSubtitle != null) {
            String heightStr = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                    ? String.format(Locale.getDefault(), "%.0f in", SettingsRepository.cmToInches(currentHeightCm))
                    : String.format(Locale.getDefault(), "%.0f cm", currentHeightCm);

            String weightStr = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                    ? String.format(Locale.getDefault(), "%.0f lb", SettingsRepository.kgToLbs(currentWeightKg))
                    : String.format(Locale.getDefault(), "%.1f kg", currentWeightKg);

            String genderStr;
            if (currentGender == SettingsRepository.Gender.MALE) {
                genderStr = getString(R.string.profile_gender_male);
            } else if (currentGender == SettingsRepository.Gender.FEMALE) {
                genderStr = getString(R.string.profile_gender_female);
            } else {
                genderStr = getString(R.string.profile_gender_unspecified);
            }

            textCatBiometricsSubtitle.setText(String.format("%s • %s • %s", heightStr, weightStr, genderStr));
        }

        // 2. Goals & Targets
        int waterTargetMl = WaterIntakeCalculator.calculateBreakdown(
                currentWeightKg, currentHeightCm, currentAge, currentGender, currentGoal
        ).getTargetMl();

        if (textCatGoalsSubtitle != null) {
            textCatGoalsSubtitle.setText(String.format(Locale.getDefault(), "%,d steps • %,d ml", currentGoal, waterTargetMl));
        }
        if (textWaterGoalValue != null) {
            textWaterGoalValue.setText(String.format(Locale.getDefault(), "%,d ml", waterTargetMl));
        }

        // 3. Tracking & Calibration
        if (textCatCalibrationSubtitle != null) {
            String strideStr;
            if (currentIsManualStride) {
                strideStr = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                        ? String.format(Locale.getDefault(), "Manual (%.1f in)", SettingsRepository.cmToInches(currentStrideCm))
                        : String.format(Locale.getDefault(), "Manual (%.1f cm)", currentStrideCm);
            } else {
                strideStr = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                        ? String.format(Locale.getDefault(), "Auto (%.0f in)", SettingsRepository.cmToInches(currentStrideCm))
                        : String.format(Locale.getDefault(), "Auto (%.0f cm)", currentStrideCm);
            }

            String latencyStr = (currentLatency == SettingsRepository.TrackingLatency.FASTER_UPDATES)
                    ? getString(R.string.profile_latency_faster)
                    : "Balanced";

            textCatCalibrationSubtitle.setText(String.format("%s • %s", strideStr, latencyStr));
        }

        // 4. Preferences & Units
        if (textCatPreferencesSubtitle != null) {
            String unitStr = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                    ? "Imperial (mi, lb)"
                    : "Metric (km, kg)";
            textCatPreferencesSubtitle.setText(unitStr);
        }

        // 5. About & Storage
        if (textCatAboutSubtitle != null) {
            textCatAboutSubtitle.setText("Pulse v1.0 • 100% Offline & Private");
        }
    }

    private void updateUnitSystemViews(@NonNull SettingsRepository.UnitSystem unit) {
        if (toggleGroupUnits != null) {
            isProgrammaticUpdate = true;
            int targetButtonId = (unit == SettingsRepository.UnitSystem.IMPERIAL)
                    ? R.id.btn_unit_imperial
                    : R.id.btn_unit_metric;
            if (toggleGroupUnits.getCheckedButtonId() != targetButtonId) {
                toggleGroupUnits.check(targetButtonId);
            }
            isProgrammaticUpdate = false;
        }

        boolean isImperial = (unit == SettingsRepository.UnitSystem.IMPERIAL);

        // Update Ruler Tape ranges and step sizes
        if (rulerHeight != null) {
            if (isImperial) {
                rulerHeight.setValueRange(20f, 100f, 1.0f);
            } else {
                rulerHeight.setValueRange(50f, 250f, 1.0f);
            }
        }
        if (textHeightUnit != null) {
            textHeightUnit.setText(isImperial ? " in" : " cm");
        }

        if (rulerWeight != null) {
            if (isImperial) {
                rulerWeight.setValueRange(44f, 450f, 1.0f);
            } else {
                rulerWeight.setValueRange(20f, 200f, 0.5f);
            }
        }
        if (textWeightUnit != null) {
            textWeightUnit.setText(isImperial ? " lb" : " kg");
        }

        if (textStrideUnit != null) {
            textStrideUnit.setText(isImperial ? " in" : " cm");
        }

        updateHeightDisplay(currentHeightCm);
        updateWeightDisplay(currentWeightKg);
        updateStrideDisplay(currentStrideCm);
    }

    private void updateHeightDisplay(double cm) {
        double displayVal = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                ? SettingsRepository.cmToInches(cm)
                : cm;

        float snappedVal = rulerHeight != null ? rulerHeight.snapValueToStep((float) displayVal) : (float) Math.round(displayVal);
        if (textHeightValue != null) {
            textHeightValue.setText(String.format(Locale.getDefault(), "%.0f", snappedVal));
        }
        if (rulerHeight != null && !isHeightRulerDragging) {
            rulerHeight.setValue(snappedVal);
        }
    }

    private void updateWeightDisplay(double kg) {
        double displayVal = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                ? SettingsRepository.kgToLbs(kg)
                : kg;

        float snappedVal = rulerWeight != null ? rulerWeight.snapValueToStep((float) displayVal) : (float) displayVal;
        if (textWeightValue != null) {
            if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
                textWeightValue.setText(String.format(Locale.getDefault(), "%.0f", snappedVal));
            } else {
                textWeightValue.setText(String.format(Locale.getDefault(), "%.1f", snappedVal));
            }
        }
        if (rulerWeight != null && !isWeightRulerDragging) {
            rulerWeight.setValue(snappedVal);
        }
    }

    private void updateStrideDisplay(double cm) {
        if (editStride != null && !editStride.hasFocus()) {
            if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
                double inches = SettingsRepository.cmToInches(cm);
                editStride.setText(String.format(Locale.getDefault(), "%.1f", inches));
            } else {
                editStride.setText(String.format(Locale.getDefault(), "%.1f", cm));
            }
        }
    }

    private void updateEditNameButtonVisuals(boolean editing) {
        if (btnEditProfileName == null || getContext() == null) return;
        this.isEditingName = editing;
        if (editing) {
            btnEditProfileName.setImageResource(R.drawable.ic_check);
            btnEditProfileName.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.pulse_green)
            ));
            btnEditProfileName.setContentDescription("Save display name");
        } else {
            btnEditProfileName.setImageResource(R.drawable.ic_edit);
            btnEditProfileName.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.pulse_text_secondary)
            ));
            btnEditProfileName.setContentDescription(getString(R.string.profile_edit_name_desc));
        }
    }

    // Commit actions on focus loss or editor completion

    private void commitName() {
        if (editProfileName == null) return;
        Editable editable = editProfileName.getText();
        String name = editable != null ? editable.toString().trim() : "";
        if (!name.isEmpty()) {
            viewModel.saveUserName(name);
        } else {
            editProfileName.setText(viewModel.getUserName().getValue());
        }
    }

    private void commitAge() {
        if (editAge == null) return;
        Editable editable = editAge.getText();
        if (editable == null || editable.toString().trim().isEmpty()) {
            Integer current = viewModel.getAge().getValue();
            editAge.setText(current != null ? String.valueOf(current) : "30");
            return;
        }

        try {
            int parsed = Integer.parseInt(editable.toString().trim());
            boolean success = viewModel.saveAge(parsed);
            if (!success) {
                Integer current = viewModel.getAge().getValue();
                editAge.setText(current != null ? String.valueOf(current) : "30");
            }
        } catch (NumberFormatException e) {
            Integer current = viewModel.getAge().getValue();
            editAge.setText(current != null ? String.valueOf(current) : "30");
        }
    }

    private void commitStride() {
        if (editStride == null) return;
        Editable editable = editStride.getText();
        if (editable == null || editable.toString().trim().isEmpty()) {
            updateStrideDisplay(currentStrideCm);
            return;
        }

        try {
            double parsed = Double.parseDouble(editable.toString().trim());
            boolean success = viewModel.saveStrideLength(parsed, currentUnitSystem);
            if (!success) {
                updateStrideDisplay(currentStrideCm);
            }
        } catch (NumberFormatException e) {
            updateStrideDisplay(currentStrideCm);
        }
    }

    private void commitGoal() {
        if (editGoal == null) return;
        Editable editable = editGoal.getText();
        if (editable == null || editable.toString().trim().isEmpty()) {
            editGoal.setText(String.format(Locale.getDefault(), "%,d", currentGoal));
            return;
        }

        try {
            String clean = editable.toString().replace(",", "").trim();
            long parsed = Long.parseLong(clean);
            boolean success = viewModel.saveDailyStepGoal(parsed);
            if (!success) {
                editGoal.setText(String.format(Locale.getDefault(), "%,d", currentGoal));
            } else {
                if (sliderGoal != null) {
                    float clamped = Math.min(sliderGoal.getValueTo(), Math.max(sliderGoal.getValueFrom(), (float) parsed));
                    float snapped = Math.round((clamped - sliderGoal.getValueFrom()) / sliderGoal.getStepSize()) * sliderGoal.getStepSize() + sliderGoal.getValueFrom();
                    sliderGoal.setValue(snapped);
                    lastReportedGoalTick = (int) (snapped / 500f);
                }
            }
        } catch (NumberFormatException e) {
            editGoal.setText(String.format(Locale.getDefault(), "%,d", currentGoal));
        }
    }

    private void showKeyboard(@NonNull View view) {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(@NonNull View view) {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSwipeEnabled(true);
        }
    }
}
