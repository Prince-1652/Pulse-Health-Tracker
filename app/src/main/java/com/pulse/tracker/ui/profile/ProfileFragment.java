package com.pulse.tracker.ui.profile;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

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
import com.pulse.tracker.ui.common.RulerTapeView;
import com.pulse.tracker.util.HapticHelper;

import java.util.Locale;

/**
 * Profile screen destination implementing the Bento squircle modular layout
 * for personal biometrics, stride calibration, tracking latency, and daily target preferences.
 * Features precision measuring tape rulers with haptic ticks for Height and Weight,
 * magnetic snap target setter with haptic feedback, auto-scrolling on input focus,
 * and ViewPager2 swipe protection during numeric or slider manipulation.
 */
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;

    private NestedScrollView profileScrollView;

    // Bento Card 0: Identity / Name
    private View cardProfileIdentity;
    private EditText editProfileName;

    // Bento Card 1: Biometrics
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

    // Bento Card 2: Calibration & Tracking
    private View cardProfileCalibration;
    private EditText editStride;
    private TextView textStrideUnit;
    private MaterialButton btnResetStride;
    private MaterialButtonToggleGroup toggleGroupLatency;
    private MaterialButton btnLatencyBattery;
    private MaterialButton btnLatencyFaster;

    // Bento Card 3: Goals & Preferences
    private View cardProfileGoals;
    private EditText editGoal;
    private Slider sliderGoal;
    private MaterialButtonToggleGroup toggleGroupUnits;
    private MaterialButton btnUnitMetric;
    private MaterialButton btnUnitImperial;

    private SettingsRepository.UnitSystem currentUnitSystem = SettingsRepository.UnitSystem.METRIC;
    private double currentHeightCm = SettingsRepository.DEFAULT_HEIGHT_CM;
    private double currentWeightKg = SettingsRepository.DEFAULT_WEIGHT_KG;
    private double currentStrideCm = SettingsRepository.DEFAULT_HEIGHT_CM * SettingsRepository.STRIDE_HEIGHT_RATIO;
    private boolean currentIsManualStride = false;
    private long currentGoal = SettingsRepository.DEFAULT_STEP_GOAL;

    private boolean isProgrammaticUpdate = false;
    private boolean isSliderDragging = false;
    private boolean isHeightRulerDragging = false;
    private boolean isWeightRulerDragging = false;
    private int lastReportedGoalTick = -1;
    private long lastGoalHapticTime = 0L;
    private Vibrator targetVibrator;

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
        setupActionListeners(view);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        observeViewModel();
    }

    private void bindViews(@NonNull View root) {
        profileScrollView = root.findViewById(R.id.profile_scroll_view);

        // Card 0: Identity
        cardProfileIdentity = root.findViewById(R.id.card_profile_identity);
        editProfileName = root.findViewById(R.id.edit_profile_name);

        // Card 1: Biometrics
        cardProfileBiometrics = root.findViewById(R.id.card_profile_biometrics);
        textHeightValue = root.findViewById(R.id.text_profile_height_value);
        textHeightUnit = root.findViewById(R.id.text_profile_height_unit);
        rulerHeight = root.findViewById(R.id.ruler_profile_height);
        if (rulerHeight != null) {
            rulerHeight.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.pulse_sky_blue)); // Sky blue accent
        }

        textWeightValue = root.findViewById(R.id.text_profile_weight_value);
        textWeightUnit = root.findViewById(R.id.text_profile_weight_unit);
        rulerWeight = root.findViewById(R.id.ruler_profile_weight);
        if (rulerWeight != null) {
            rulerWeight.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.pulse_orange)); // Orange/salmon accent
        }

        cardProfileAge = root.findViewById(R.id.card_profile_age);
        editAge = root.findViewById(R.id.edit_profile_age);
        toggleGroupGender = root.findViewById(R.id.toggle_group_gender);
        btnGenderMale = root.findViewById(R.id.btn_gender_male);
        btnGenderFemale = root.findViewById(R.id.btn_gender_female);
        btnGenderUnspecified = root.findViewById(R.id.btn_gender_unspecified);

        // Card 2: Calibration & Tracking
        cardProfileCalibration = root.findViewById(R.id.card_profile_calibration);
        editStride = root.findViewById(R.id.edit_profile_stride);
        textStrideUnit = root.findViewById(R.id.text_profile_stride_unit);
        btnResetStride = root.findViewById(R.id.btn_reset_stride);
        toggleGroupLatency = root.findViewById(R.id.toggle_group_latency);
        btnLatencyBattery = root.findViewById(R.id.btn_latency_battery);
        btnLatencyFaster = root.findViewById(R.id.btn_latency_faster);

        // Card 3: Goals & Preferences
        cardProfileGoals = root.findViewById(R.id.card_profile_goals);
        editGoal = root.findViewById(R.id.edit_profile_goal);
        sliderGoal = root.findViewById(R.id.slider_profile_goal);
        toggleGroupUnits = root.findViewById(R.id.toggle_group_units);
        btnUnitMetric = root.findViewById(R.id.btn_unit_metric);
        btnUnitImperial = root.findViewById(R.id.btn_unit_imperial);

        if (getContext() != null) {
            targetVibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void setupActionListeners(@NonNull View root) {
        if (profileScrollView != null) {
            profileScrollView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View currentFocus = getActivity() != null ? getActivity().getCurrentFocus() : null;
                    if (currentFocus != null) {
                        currentFocus.clearFocus();
                        hideKeyboard(currentFocus);
                    }
                }
                return false;
            });
        }

        // Display Name listener + auto scroll
        if (editProfileName != null) {
            setupFocusAutoScroll(editProfileName, cardProfileIdentity);
            editProfileName.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    commitName();
                    v.clearFocus();
                    hideKeyboard(v);
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
                    if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
                        textHeightValue.setText(String.format(Locale.getDefault(), "%.0f", value));
                    } else {
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
                    if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
                        textWeightValue.setText(String.format(Locale.getDefault(), "%.0f", value));
                    } else {
                        textWeightValue.setText(String.format(Locale.getDefault(), "%.1f", value));
                    }
                }

                @Override
                public void onValueConfirmed(float value) {
                    isWeightRulerDragging = false;
                    viewModel.saveWeight(value, currentUnitSystem);
                }
            });
        }

        // Age card click -> focus editAge and show keyboard
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

        // Daily step goal slider with magnetic sticky ticks and ViewPager2 swipe protection
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
                    goalSteps = Math.max(500L, Math.min(100000L, goalSteps));
                    currentGoal = goalSteps;
                    slider.setValue((float) goalSteps);
                    if (editGoal != null && !editGoal.hasFocus()) {
                        editGoal.setText(String.format(Locale.getDefault(), "%,d", goalSteps));
                    }
                    viewModel.saveDailyStepGoal(goalSteps);
                    triggerMagneticTick(slider, true, true); // Sharp distinct lock-in accent
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
        if (profileScrollView == null) return;
        profileScrollView.postDelayed(() -> {
            if (!isAdded() || getContext() == null) return;
            int[] targetLoc = new int[2];
            target.getLocationOnScreen(targetLoc);
            int[] scrollLoc = new int[2];
            profileScrollView.getLocationOnScreen(scrollLoc);
            int relativeY = targetLoc[1] - scrollLoc[1] + profileScrollView.getScrollY();
            float density = getResources().getDisplayMetrics().density;
            int offset = Math.max(0, relativeY - (int) (24f * density));
            profileScrollView.smoothScrollTo(0, offset);
        }, 250);
    }

    private void triggerMagneticTick(@NonNull View sourceView, boolean isMilestone) {
        triggerMagneticTick(sourceView, isMilestone, false);
    }

    private void triggerMagneticTick(@NonNull View sourceView, boolean isMilestone, boolean force) {
        if (isMilestone) {
            HapticHelper.performMilestone(getContext());
        } else {
            HapticHelper.performTick(getContext(), force);
        }
    }

    private void observeViewModel() {
        // User Name observation
        viewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name == null || editProfileName == null) return;
            if (!editProfileName.hasFocus()) {
                isProgrammaticUpdate = true;
                editProfileName.setText(name);
                isProgrammaticUpdate = false;
            }
        });

        // Unit System observation
        viewModel.getUnitSystem().observe(getViewLifecycleOwner(), unit -> {
            if (unit == null) return;
            this.currentUnitSystem = unit;
            updateUnitSystemViews(unit);
        });

        // Height observation
        viewModel.getHeightCm().observe(getViewLifecycleOwner(), height -> {
            if (height == null) return;
            this.currentHeightCm = height;
            updateHeightDisplay(height);
        });

        // Weight observation
        viewModel.getWeightKg().observe(getViewLifecycleOwner(), weight -> {
            if (weight == null) return;
            this.currentWeightKg = weight;
            updateWeightDisplay(weight);
        });

        // Age observation
        viewModel.getAge().observe(getViewLifecycleOwner(), age -> {
            if (age == null || editAge == null) return;
            if (!editAge.hasFocus()) {
                editAge.setText(String.valueOf(age));
            }
        });

        // Gender observation
        viewModel.getGender().observe(getViewLifecycleOwner(), gender -> {
            if (gender == null || toggleGroupGender == null) return;
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
        });

        // Stride observation
        viewModel.getStrideLengthCm().observe(getViewLifecycleOwner(), stride -> {
            if (stride == null) return;
            this.currentStrideCm = stride;
            updateStrideDisplay(stride);
        });

        // Manual stride flag observation
        viewModel.getIsManualStride().observe(getViewLifecycleOwner(), isManual -> {
            this.currentIsManualStride = Boolean.TRUE.equals(isManual);
            if (btnResetStride != null) {
                btnResetStride.setVisibility(currentIsManualStride ? View.VISIBLE : View.GONE);
            }
        });

        // Tracking latency observation
        viewModel.getTrackingLatency().observe(getViewLifecycleOwner(), latency -> {
            if (latency == null || toggleGroupLatency == null) return;
            int targetId = (latency == SettingsRepository.TrackingLatency.FASTER_UPDATES)
                    ? R.id.btn_latency_faster
                    : R.id.btn_latency_battery;
            if (toggleGroupLatency.getCheckedButtonId() != targetId) {
                isProgrammaticUpdate = true;
                toggleGroupLatency.check(targetId);
                isProgrammaticUpdate = false;
            }
        });

        // Daily step goal observation
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
        });
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
