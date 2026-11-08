package com.pulse.tracker.ui.onboarding;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.CalorieCalculator;
import com.pulse.tracker.domain.DistanceCalculator;
import com.pulse.tracker.domain.WaterIntakeCalculator;
import com.pulse.tracker.ui.common.RulerTapeView;
import com.pulse.tracker.util.HapticHelper;
import com.pulse.tracker.util.PermissionUtils;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Modern 7-step onboarding wizard introducing Pulse with matching dark OLED Bento theme,
 * dedicated screens for Identity, Gender/Age, Height, Weight, Daily Target, Hydration,
 * and Permissions with Apple-level 60fps animations, fluid page indicator dots,
 * and zero overlapping on the continue button.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int TOTAL_PAGES = 7;

    private SettingsRepository settingsRepository;

    private View rootLayout;
    private ViewPager2 viewPager;
    private View bottomDock;

    private View[] indicatorDots;
    private View btnPrimary;
    private TextView textPrimary;
    private MaterialButton btnSecondary;

    // View references: Page 0 (Identity & Units)
    private EditText inputName;
    private MaterialButtonToggleGroup toggleUnits;
    private TextView textUnitSystemName;
    private TextView textUnitSystemExample;

    // View references: Page 1 (Gender & Age)
    private MaterialButtonToggleGroup toggleGender;
    private EditText inputAge;
    private View btnAgeMinus;
    private View btnAgePlus;

    // View references: Page 2 (Height Calibration)
    private TextView textHeightValue;
    private TextView textHeightUnit;
    private TextView textStridePreview;
    private RulerTapeView rulerHeight;

    // View references: Page 3 (Weight Calibration)
    private TextView textWeightValue;
    private TextView textWeightUnit;
    private TextView textBmrPreview;
    private RulerTapeView rulerWeight;

    // View references: Page 4 (Daily Step Goal)
    private TextView textGoalValue;
    private TextView textGoalEquivalent;
    private Slider sliderGoal;
    private MaterialButton btnPreset6k;
    private MaterialButton btnPreset8k;
    private MaterialButton btnPreset10k;
    private MaterialButton btnPreset12k;

    // View references: Page 5 (Hydration Target & Breakdown)
    private TextView tvHydrationHero;
    private TextView tvHydrationBase;
    private TextView tvHydrationActivity;
    private TextView tvHydrationBsaAge;

    // View references: Page 6 (Permissions & Privacy)
    private MaterialCardView statusCard;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDesc;

    // Biometric state values
    private String currentName = SettingsRepository.DEFAULT_USER_NAME;
    private SettingsRepository.UnitSystem currentUnitSystem = SettingsRepository.UnitSystem.METRIC;
    private SettingsRepository.Gender currentGender = SettingsRepository.Gender.MALE;
    private int currentAge = SettingsRepository.DEFAULT_AGE;
    private double currentHeightCm = SettingsRepository.DEFAULT_HEIGHT_CM;
    private double currentWeightKg = SettingsRepository.DEFAULT_WEIGHT_KG;
    private long currentStepGoal = SettingsRepository.DEFAULT_STEP_GOAL;

    private boolean isHeightRulerDragging = false;
    private boolean isWeightRulerDragging = false;
    private boolean isSliderDragging = false;
    private int lastReportedGoalTick = -1;
    private int activePagePosition = 0;

    private boolean hasStepSensor = true;
    private boolean awaitingSettingsReturn = false;
    private boolean hasRequestedPermissions = false;

    private static final String KEY_HAS_REQUESTED_PERMISSIONS = "key_has_requested_permissions";

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::handlePermissionResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            hasRequestedPermissions = savedInstanceState.getBoolean(KEY_HAS_REQUESTED_PERMISSIONS, false);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_onboarding);

        settingsRepository = SettingsRepository.getInstance(this);
        loadInitialDefaults();

        bindViews();
        setupWindowInsets();
        setupViewPager();
        checkHardwareCapabilities();
        setupControls();
    }

    private void loadInitialDefaults() {
        currentName = settingsRepository.getUserName();
        currentUnitSystem = settingsRepository.getUnitSystem();
        currentGender = settingsRepository.getGender();
        currentAge = settingsRepository.getAge();
        currentHeightCm = settingsRepository.getHeightCm();
        currentWeightKg = settingsRepository.getWeightKg();
        currentStepGoal = settingsRepository.getDailyStepTarget();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HAS_REQUESTED_PERMISSIONS, hasRequestedPermissions);
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.root_onboarding);
        viewPager = findViewById(R.id.view_pager_onboarding);
        bottomDock = findViewById(R.id.bottom_controls_dock);

        indicatorDots = new View[]{
                findViewById(R.id.indicator_dot_0),
                findViewById(R.id.indicator_dot_1),
                findViewById(R.id.indicator_dot_2),
                findViewById(R.id.indicator_dot_3),
                findViewById(R.id.indicator_dot_4),
                findViewById(R.id.indicator_dot_5),
                findViewById(R.id.indicator_dot_6)
        };

        btnPrimary = findViewById(R.id.btn_primary_action);
        textPrimary = findViewById(R.id.text_primary_action);
        btnSecondary = findViewById(R.id.btn_secondary_action);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            if (bottomDock != null) {
                bottomDock.setPadding(
                        bottomDock.getPaddingLeft(),
                        bottomDock.getPaddingTop(),
                        bottomDock.getPaddingRight(),
                        systemBars.bottom + getResources().getDimensionPixelSize(R.dimen.space_24)
                );
            }
            return windowInsets;
        });
    }

    public void setSwipeEnabled(boolean enabled) {
        if (viewPager != null) {
            viewPager.setUserInputEnabled(enabled);
        }
    }

    private void setupViewPager() {
        viewPager.setAdapter(new OnboardingPagerAdapter());
        viewPager.setOffscreenPageLimit(1);

        // Apple-grade fluid PageTransformer: full containment, zero adjacent peeking
        viewPager.setPageTransformer((page, position) -> {
            if (position <= -1f || position >= 1f) {
                page.setAlpha(0f);
                page.setTranslationX(0f);
            } else if (position == 0f) {
                page.setAlpha(1f);
                page.setScaleX(1f);
                page.setScaleY(1f);
                page.setTranslationX(0f);
            } else {
                float absPos = Math.abs(position);
                page.setAlpha(1f - absPos);
                float scale = 0.96f + 0.04f * (1f - absPos);
                page.setScaleX(scale);
                page.setScaleY(scale);
                page.setTranslationX(0f);
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                updateIndicatorScrollProgress(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                activePagePosition = position;
                updateIndicatorScrollProgress(position, 0f);
                updateStepVisuals(position);
                handlePageEntered(position);
            }
        });
    }

    private void handlePageEntered(int position) {
        switch (position) {
            case 2: // Height
                updateHeightViews();
                break;
            case 3: // Weight
                updateWeightViews();
                break;
            case 4: // Step Goal
                updateGoalViews();
                break;
            case 5: // Hydration
                updateHydrationViewsWithAnimation();
                break;
            case 6: // Permissions
                updatePermissionActionButtons();
                break;
        }
    }

    void updateIndicatorScrollProgress(int position, float positionOffset) {
        if (indicatorDots == null || indicatorDots.length == 0) return;
        float density = getResources().getDisplayMetrics().density;
        int minWidth = Math.round(8 * density);
        int maxWidth = Math.round(24 * density);
        int delta = maxWidth - minWidth;

        int clampedPos = Math.max(0, Math.min(position, indicatorDots.length - 1));

        for (int i = 0; i < indicatorDots.length; i++) {
            View dot = indicatorDots[i];
            if (dot == null) continue;
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            if (lp == null) continue;

            int targetWidth;
            boolean isActive;
            if (i == clampedPos) {
                targetWidth = minWidth + Math.round(delta * (1f - positionOffset));
                isActive = positionOffset <= 0.5f;
            } else if (i == clampedPos + 1) {
                targetWidth = minWidth + Math.round(delta * positionOffset);
                isActive = positionOffset > 0.5f;
            } else {
                targetWidth = minWidth;
                isActive = false;
            }

            if (lp.width != targetWidth) {
                lp.width = targetWidth;
                dot.setLayoutParams(lp);
            }
            dot.setBackgroundResource(isActive ? R.drawable.bg_indicator_active : R.drawable.bg_indicator_inactive);
        }
    }

    void animateStepIndicators(int oldPos, int newPos) {
        updateIndicatorScrollProgress(newPos, 0f);
    }

    private void updateStepVisuals(int position) {
        if (position < TOTAL_PAGES - 1) {
            textPrimary.setText(R.string.onboarding_btn_continue);
            btnSecondary.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            btnSecondary.setText(R.string.onboarding_btn_back);
        } else {
            // Last Page: Permissions
            updatePermissionActionButtons();
        }
    }

    private void updatePermissionActionButtons() {
        if (!hasStepSensor) {
            textPrimary.setText(R.string.onboarding_btn_continue);
            btnSecondary.setVisibility(View.GONE);
            return;
        }

        boolean hasPermission = PermissionUtils.hasActivityRecognitionPermission(this);
        if (hasPermission || !hasRequestedPermissions) {
            textPrimary.setText(R.string.onboarding_btn_get_started);
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(R.string.onboarding_btn_back);
            if (statusCard != null) statusCard.setVisibility(View.GONE);
            return;
        }

        boolean permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                PermissionUtils.isPermissionPermanentlyDenied(this, Manifest.permission.ACTIVITY_RECOGNITION);

        if (statusCard != null) statusCard.setVisibility(View.VISIBLE);
        if (statusTitle != null && statusDesc != null) {
            if (permanentlyDenied) {
                statusTitle.setText(R.string.onboarding_status_blocked_title);
                statusDesc.setText(R.string.onboarding_status_blocked_desc);
                textPrimary.setText(R.string.onboarding_btn_settings);
            } else {
                statusTitle.setText(R.string.onboarding_status_denied_title);
                statusDesc.setText(R.string.onboarding_status_denied_desc);
                textPrimary.setText(R.string.onboarding_btn_retry);
            }
        }
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(R.string.onboarding_btn_continue);
    }

    private void checkHardwareCapabilities() {
        hasStepSensor = PermissionUtils.isStepCounterSensorAvailable(this);
    }

    private void setupControls() {
        btnPrimary.setOnClickListener(v -> handlePrimaryAction());
        btnSecondary.setOnClickListener(v -> handleSecondaryAction());

        // Apple fluid press scale feedback
        btnPrimary.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200)
                        .setInterpolator(new MainActivity.SpringInterpolator(4.2f, 14f)).start();
            }
            return false;
        });
    }

    private void handlePrimaryAction() {
        int currentPos = viewPager.getCurrentItem();
        HapticHelper.performTick(this);

        if (currentPos == 0) {
            // Save Step 1 (Identity & Units)
            if (inputName != null) {
                String entered = inputName.getText().toString().trim();
                currentName = entered.isEmpty() ? SettingsRepository.DEFAULT_USER_NAME : entered;
                settingsRepository.setUserName(currentName);
            }
            settingsRepository.setUnitSystem(currentUnitSystem);
            viewPager.setCurrentItem(1, true);
        } else if (currentPos < TOTAL_PAGES - 1) {
            saveBiometricsFromCurrentState();
            viewPager.setCurrentItem(currentPos + 1, true);
        } else {
            // Last step: permissions / launch
            executePermissionOrLaunch();
        }
    }

    private void handleSecondaryAction() {
        int currentPos = viewPager.getCurrentItem();
        HapticHelper.performTick(this);

        if (currentPos > 0 && currentPos < TOTAL_PAGES - 1) {
            viewPager.setCurrentItem(currentPos - 1, true);
        } else if (currentPos == TOTAL_PAGES - 1) {
            if (hasRequestedPermissions && !PermissionUtils.hasActivityRecognitionPermission(this)) {
                // User chooses to continue without tracking
                completeOnboardingAndLaunch(false);
            } else {
                viewPager.setCurrentItem(currentPos - 1, true);
            }
        }
    }

    private void saveBiometricsFromCurrentState() {
        if (inputName != null) {
            String entered = inputName.getText().toString().trim();
            currentName = entered.isEmpty() ? SettingsRepository.DEFAULT_USER_NAME : entered;
            settingsRepository.setUserName(currentName);
        }

        if (inputAge != null) {
            try {
                int age = Integer.parseInt(inputAge.getText().toString().trim());
                if (age >= SettingsRepository.MIN_AGE && age <= SettingsRepository.MAX_AGE) {
                    currentAge = age;
                }
            } catch (Exception ignored) {}
        }

        settingsRepository.setUnitSystem(currentUnitSystem);
        settingsRepository.setGender(currentGender);
        settingsRepository.setAge(currentAge);
        settingsRepository.setHeightCm(currentHeightCm);
        settingsRepository.setWeightKg(currentWeightKg);
        settingsRepository.setDailyStepTarget(currentStepGoal);
    }

    private void executePermissionOrLaunch() {
        if (!hasStepSensor) {
            completeOnboardingAndLaunch(false);
            return;
        }

        if (PermissionUtils.hasActivityRecognitionPermission(this)) {
            completeOnboardingAndLaunch(true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                PermissionUtils.isPermissionPermanentlyDenied(this, Manifest.permission.ACTIVITY_RECOGNITION) &&
                statusCard != null && statusCard.getVisibility() == View.VISIBLE) {
            awaitingSettingsReturn = true;
            PermissionUtils.openAppSettings(this);
            return;
        }

        String[] permissionsToRequest = PermissionUtils.getPermissionsToRequest(this);
        if (permissionsToRequest.length > 0) {
            hasRequestedPermissions = true;
            permissionLauncher.launch(permissionsToRequest);
        } else {
            completeOnboardingAndLaunch(true);
        }
    }

    private void handlePermissionResult(@NonNull Map<String, Boolean> results) {
        boolean activityGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Boolean granted = results.get(Manifest.permission.ACTIVITY_RECOGNITION);
            activityGranted = granted != null && granted;
        }

        if (activityGranted) {
            completeOnboardingAndLaunch(true);
        } else {
            updatePermissionActionButtons();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingSettingsReturn) {
            awaitingSettingsReturn = false;
            if (PermissionUtils.hasActivityRecognitionPermission(this)) {
                completeOnboardingAndLaunch(true);
            } else {
                updatePermissionActionButtons();
            }
        }
    }

    private void completeOnboardingAndLaunch(boolean trackingEnabled) {
        saveBiometricsFromCurrentState();
        settingsRepository.setOnboardingCompleted(true);
        settingsRepository.setTrackingEnabled(trackingEnabled);

        if (trackingEnabled && PermissionUtils.hasActivityRecognitionPermission(this)) {
            try {
                Intent serviceIntent = new Intent(this, com.pulse.tracker.service.StepTrackingService.class);
                serviceIntent.setAction(com.pulse.tracker.service.StepTrackingService.ACTION_START);
                if (settingsRepository.isNotificationEnabled()) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                android.util.Log.e("OnboardingActivity", "Failed to start StepTrackingService: " + e.getMessage());
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // --- Dynamic Display Synchronizers ---

    void updateUnitExplanationViews() {
        if (textUnitSystemName == null || textUnitSystemExample == null) return;
        if (currentUnitSystem == SettingsRepository.UnitSystem.METRIC) {
            textUnitSystemName.setText(R.string.unit_metric_name);
            textUnitSystemExample.setText(R.string.unit_metric_example);
        } else {
            textUnitSystemName.setText(R.string.unit_imperial_name);
            textUnitSystemExample.setText(R.string.unit_imperial_example);
        }
    }

    private void updateHeightViews() {
        boolean isImperial = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL);

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

        double displayHeight = isImperial ? SettingsRepository.cmToInches(currentHeightCm) : currentHeightCm;
        float snappedHeight = rulerHeight != null ? rulerHeight.snapValueToStep((float) displayHeight) : (float) Math.round(displayHeight);

        if (textHeightValue != null) {
            textHeightValue.setText(String.format(Locale.getDefault(), "%.0f", snappedHeight));
        }
        if (rulerHeight != null && !isHeightRulerDragging) {
            rulerHeight.setValue(snappedHeight);
        }

        if (textStridePreview != null) {
            double strideCm = currentHeightCm * SettingsRepository.STRIDE_HEIGHT_RATIO;
            if (isImperial) {
                double strideIn = SettingsRepository.cmToInches(strideCm);
                textStridePreview.setText(String.format(Locale.getDefault(), "Estimated stride: %.1f in", strideIn));
            } else {
                textStridePreview.setText(String.format(Locale.getDefault(), "Estimated stride: %.1f cm", strideCm));
            }
        }
    }

    private void updateWeightViews() {
        boolean isImperial = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL);

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

        double displayWeight = isImperial ? SettingsRepository.kgToLbs(currentWeightKg) : currentWeightKg;
        float snappedWeight = rulerWeight != null ? rulerWeight.snapValueToStep((float) displayWeight) : (float) displayWeight;

        if (textWeightValue != null) {
            textWeightValue.setText(String.format(Locale.getDefault(), isImperial ? "%.0f" : "%.1f", snappedWeight));
        }
        if (rulerWeight != null && !isWeightRulerDragging) {
            rulerWeight.setValue(snappedWeight);
        }

        if (textBmrPreview != null) {
            double bmr = CalorieCalculator.calculateBmr(currentWeightKg, currentHeightCm, currentAge, currentGender);
            if (bmr > 0) {
                textBmrPreview.setText(String.format(Locale.getDefault(), "Estimated resting BMR: ~%,.0f kcal/day", bmr));
            } else {
                textBmrPreview.setText("Metabolic baseline active");
            }
        }
    }

    private void updateGoalViews() {
        if (textGoalValue != null) {
            textGoalValue.setText(String.format(Locale.getDefault(), "%,d", currentStepGoal));
        }
        if (sliderGoal != null && !isSliderDragging) {
            float clamped = Math.min(sliderGoal.getValueTo(), Math.max(sliderGoal.getValueFrom(), (float) currentStepGoal));
            sliderGoal.setValue(clamped);
            lastReportedGoalTick = (int) (clamped / 500f);
        }
        updateGoalEquivalent();
    }

    private void updateGoalEquivalent() {
        if (textGoalEquivalent == null) return;
        double strideCm = DistanceCalculator.calculateDefaultStrideCm(currentHeightCm);
        double meters = DistanceCalculator.calculateDistanceMeters(currentStepGoal, strideCm);
        long activeMinutes = Math.round(currentStepGoal / 100.0); // ~100 steps/min walking cadence

        if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
            double miles = DistanceCalculator.metersToMiles(meters);
            textGoalEquivalent.setText(String.format(Locale.getDefault(), "~%.1f mi • ~%d active min", miles, activeMinutes));
        } else {
            double km = DistanceCalculator.metersToKilometers(meters);
            textGoalEquivalent.setText(String.format(Locale.getDefault(), "~%.1f km • ~%d active min", km, activeMinutes));
        }
    }

    private void updateHydrationViewsWithAnimation() {
        WaterIntakeCalculator.Breakdown breakdown =
                WaterIntakeCalculator.calculateBreakdown(currentWeightKg, currentHeightCm, currentAge, currentGender, currentStepGoal);
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());

        if (tvHydrationBase != null) {
            tvHydrationBase.setText(String.format(Locale.getDefault(), "%s ml", nf.format(Math.round(breakdown.getBaseMl()))));
        }
        if (tvHydrationActivity != null) {
            tvHydrationActivity.setText(String.format(Locale.getDefault(), "+%s ml", nf.format(Math.round(breakdown.getStepCompensationMl()))));
        }
        if (tvHydrationBsaAge != null) {
            double adj = breakdown.getBsaAdjustmentMl() + breakdown.getAgeAdjustmentMl();
            String prefix = adj >= 0 ? "+" : "";
            tvHydrationBsaAge.setText(String.format(Locale.getDefault(), "%s%s ml", prefix, nf.format(Math.round(adj))));
        }

        int targetMl = breakdown.getTargetMl();
        if (tvHydrationHero != null) {
            ValueAnimator counterAnim = ValueAnimator.ofInt(0, targetMl);
            counterAnim.setDuration(450);
            counterAnim.setInterpolator(new DecelerateInterpolator());
            counterAnim.addUpdateListener(anim -> {
                int val = (int) anim.getAnimatedValue();
                tvHydrationHero.setText(String.format(Locale.getDefault(), "%s ml / day", nf.format(val)));
            });
            counterAnim.start();
        }
    }

    // --- Pager Adapter ---
    private class OnboardingPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return TOTAL_PAGES;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view;
            switch (viewType) {
                case 0:
                    view = inflater.inflate(R.layout.layout_onboarding_page1, parent, false);
                    bindPage1Views(view);
                    break;
                case 1:
                    view = inflater.inflate(R.layout.layout_onboarding_page2, parent, false);
                    bindPage2Views(view);
                    break;
                case 2:
                    view = inflater.inflate(R.layout.layout_onboarding_page3, parent, false);
                    bindPage3Views(view);
                    break;
                case 3:
                    view = inflater.inflate(R.layout.layout_onboarding_page4, parent, false);
                    bindPage4Views(view);
                    break;
                case 4:
                    view = inflater.inflate(R.layout.layout_onboarding_page5, parent, false);
                    bindPage5Views(view);
                    break;
                case 5:
                    view = inflater.inflate(R.layout.layout_onboarding_page6, parent, false);
                    bindPage6Views(view);
                    break;
                case 6:
                default:
                    view = inflater.inflate(R.layout.layout_onboarding_page7, parent, false);
                    bindPage7Views(view);
                    break;
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}

        private void bindPage1Views(@NonNull View view) {
            inputName = view.findViewById(R.id.input_onboarding_name);
            toggleUnits = view.findViewById(R.id.toggle_onboarding_units);
            textUnitSystemName = view.findViewById(R.id.text_unit_system_name);
            textUnitSystemExample = view.findViewById(R.id.text_unit_system_example);

            if (inputName != null) {
                inputName.setText(currentName);
                inputName.addTextChangedListener(new SimpleWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        currentName = s.toString().trim();
                    }
                });
            }

            updateUnitExplanationViews();

            if (toggleUnits != null) {
                int checkId = (currentUnitSystem == SettingsRepository.UnitSystem.METRIC)
                        ? R.id.btn_onboarding_unit_metric : R.id.btn_onboarding_unit_imperial;
                toggleUnits.check(checkId);
                toggleUnits.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (isChecked) {
                        HapticHelper.performTick(OnboardingActivity.this);
                        currentUnitSystem = (checkedId == R.id.btn_onboarding_unit_metric)
                                ? SettingsRepository.UnitSystem.METRIC : SettingsRepository.UnitSystem.IMPERIAL;
                        updateUnitExplanationViews();
                        updateHeightViews();
                        updateWeightViews();
                        updateGoalEquivalent();
                    }
                });
            }
        }

        private void bindPage2Views(@NonNull View view) {
            toggleGender = view.findViewById(R.id.toggle_onboarding_gender);
            if (toggleGender != null) {
                int checkId = (currentGender == SettingsRepository.Gender.MALE) ? R.id.btn_onboarding_gender_male :
                        (currentGender == SettingsRepository.Gender.FEMALE) ? R.id.btn_onboarding_gender_female : R.id.btn_onboarding_gender_other;
                toggleGender.check(checkId);
                toggleGender.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (isChecked) {
                        HapticHelper.performTick(OnboardingActivity.this);
                        if (checkedId == R.id.btn_onboarding_gender_male) currentGender = SettingsRepository.Gender.MALE;
                        else if (checkedId == R.id.btn_onboarding_gender_female) currentGender = SettingsRepository.Gender.FEMALE;
                        else currentGender = SettingsRepository.Gender.UNSPECIFIED;
                        updateWeightViews();
                    }
                });
            }

            inputAge = view.findViewById(R.id.input_onboarding_age);
            btnAgeMinus = view.findViewById(R.id.btn_age_minus);
            btnAgePlus = view.findViewById(R.id.btn_age_plus);

            if (inputAge != null) {
                inputAge.setText(String.valueOf(currentAge));
                inputAge.addTextChangedListener(new SimpleWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        try {
                            int age = Integer.parseInt(s.toString().trim());
                            if (age >= SettingsRepository.MIN_AGE && age <= SettingsRepository.MAX_AGE) {
                                currentAge = age;
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }

            if (btnAgeMinus != null) {
                btnAgeMinus.setOnClickListener(v -> {
                    if (currentAge > SettingsRepository.MIN_AGE) {
                        currentAge--;
                        if (inputAge != null) inputAge.setText(String.valueOf(currentAge));
                        HapticHelper.performTick(OnboardingActivity.this);
                    }
                });
            }

            if (btnAgePlus != null) {
                btnAgePlus.setOnClickListener(v -> {
                    if (currentAge < SettingsRepository.MAX_AGE) {
                        currentAge++;
                        if (inputAge != null) inputAge.setText(String.valueOf(currentAge));
                        HapticHelper.performTick(OnboardingActivity.this);
                    }
                });
            }
        }

        private void bindPage3Views(@NonNull View view) {
            textHeightValue = view.findViewById(R.id.text_onboarding_height_value);
            textHeightUnit = view.findViewById(R.id.text_onboarding_height_unit);
            textStridePreview = view.findViewById(R.id.text_onboarding_stride_preview);
            rulerHeight = view.findViewById(R.id.ruler_onboarding_height);

            if (rulerHeight != null) {
                rulerHeight.setIndicatorColor(ContextCompat.getColor(OnboardingActivity.this, R.color.pulse_sky_blue));
                rulerHeight.setOnValueChangeListener(new RulerTapeView.OnValueChangeListener() {
                    @Override
                    public void onValueChanged(float value) {
                        isHeightRulerDragging = true;
                        if (textHeightValue != null) {
                            textHeightValue.setText(String.format(Locale.getDefault(), "%.0f", value));
                        }
                        currentHeightCm = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                                ? SettingsRepository.inchesToCm(value) : value;
                        updateStridePreviewOnly();
                    }

                    @Override
                    public void onValueConfirmed(float value) {
                        isHeightRulerDragging = false;
                        currentHeightCm = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                                ? SettingsRepository.inchesToCm(value) : value;
                        updateStridePreviewOnly();
                    }
                });
            }
            updateHeightViews();
        }

        private void updateStridePreviewOnly() {
            if (textStridePreview != null) {
                boolean isImperial = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL);
                double strideCm = currentHeightCm * SettingsRepository.STRIDE_HEIGHT_RATIO;
                if (isImperial) {
                    double strideIn = SettingsRepository.cmToInches(strideCm);
                    textStridePreview.setText(String.format(Locale.getDefault(), "Estimated stride: %.1f in", strideIn));
                } else {
                    textStridePreview.setText(String.format(Locale.getDefault(), "Estimated stride: %.1f cm", strideCm));
                }
            }
        }

        private void bindPage4Views(@NonNull View view) {
            textWeightValue = view.findViewById(R.id.text_onboarding_weight_value);
            textWeightUnit = view.findViewById(R.id.text_onboarding_weight_unit);
            textBmrPreview = view.findViewById(R.id.text_onboarding_bmr_preview);
            rulerWeight = view.findViewById(R.id.ruler_onboarding_weight);

            if (rulerWeight != null) {
                rulerWeight.setIndicatorColor(ContextCompat.getColor(OnboardingActivity.this, R.color.pulse_orange));
                rulerWeight.setOnValueChangeListener(new RulerTapeView.OnValueChangeListener() {
                    @Override
                    public void onValueChanged(float value) {
                        isWeightRulerDragging = true;
                        if (textWeightValue != null) {
                            textWeightValue.setText(String.format(Locale.getDefault(),
                                    currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL ? "%.0f" : "%.1f", value));
                        }
                        currentWeightKg = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                                ? SettingsRepository.lbsToKg(value) : value;
                        updateBmrPreviewOnly();
                    }

                    @Override
                    public void onValueConfirmed(float value) {
                        isWeightRulerDragging = false;
                        currentWeightKg = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                                ? SettingsRepository.lbsToKg(value) : value;
                        updateBmrPreviewOnly();
                    }
                });
            }
            updateWeightViews();
        }

        private void updateBmrPreviewOnly() {
            if (textBmrPreview != null) {
                double bmr = CalorieCalculator.calculateBmr(currentWeightKg, currentHeightCm, currentAge, currentGender);
                if (bmr > 0) {
                    textBmrPreview.setText(String.format(Locale.getDefault(), "Estimated resting BMR: ~%,.0f kcal/day", bmr));
                }
            }
        }

        private void bindPage5Views(@NonNull View view) {
            textGoalValue = view.findViewById(R.id.text_onboarding_goal_value);
            textGoalEquivalent = view.findViewById(R.id.text_onboarding_goal_equivalent);
            sliderGoal = view.findViewById(R.id.slider_onboarding_goal);
            btnPreset6k = view.findViewById(R.id.btn_preset_6k);
            btnPreset8k = view.findViewById(R.id.btn_preset_8k);
            btnPreset10k = view.findViewById(R.id.btn_preset_10k);
            btnPreset12k = view.findViewById(R.id.btn_preset_12k);

            if (sliderGoal != null) {
                sliderGoal.setOnTouchListener((v, event) -> {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                        ViewParent p = v.getParent();
                        while (p != null) {
                            p.requestDisallowInterceptTouchEvent(true);
                            p = p.getParent();
                        }
                        setSwipeEnabled(false);
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        ViewParent p = v.getParent();
                        while (p != null) {
                            p.requestDisallowInterceptTouchEvent(false);
                            p = p.getParent();
                        }
                        setSwipeEnabled(true);
                    }
                    return false;
                });

                sliderGoal.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        long goalSteps = Math.round(value / 500.0f) * 500L;
                        goalSteps = Math.max(500L, Math.min(100000L, goalSteps));
                        currentStepGoal = goalSteps;
                        if (textGoalValue != null) {
                            textGoalValue.setText(String.format(Locale.getDefault(), "%,d", goalSteps));
                        }
                        int tick = (int) (goalSteps / 500L);
                        if (tick != lastReportedGoalTick) {
                            lastReportedGoalTick = tick;
                            boolean isMilestone = (goalSteps % 5000L == 0);
                            HapticHelper.performTick(OnboardingActivity.this, isMilestone);
                        }
                        updateGoalEquivalent();
                    }
                });

                sliderGoal.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                    @Override
                    public void onStartTrackingTouch(@NonNull Slider slider) {
                        isSliderDragging = true;
                        setSwipeEnabled(false);
                    }

                    @Override
                    public void onStopTrackingTouch(@NonNull Slider slider) {
                        isSliderDragging = false;
                        long goalSteps = Math.round(slider.getValue() / 500.0f) * 500L;
                        goalSteps = Math.max(500L, Math.min(100000L, goalSteps));
                        currentStepGoal = goalSteps;
                        slider.setValue((float) goalSteps);
                        if (textGoalValue != null) {
                            textGoalValue.setText(String.format(Locale.getDefault(), "%,d", goalSteps));
                        }
                        HapticHelper.performTick(OnboardingActivity.this, true);
                        setSwipeEnabled(true);
                        updateGoalEquivalent();
                    }
                });
            }

            if (btnPreset6k != null) btnPreset6k.setOnClickListener(v -> setGoalPreset(6000L));
            if (btnPreset8k != null) btnPreset8k.setOnClickListener(v -> setGoalPreset(8000L));
            if (btnPreset10k != null) btnPreset10k.setOnClickListener(v -> setGoalPreset(10000L));
            if (btnPreset12k != null) btnPreset12k.setOnClickListener(v -> setGoalPreset(12000L));

            updateGoalViews();
        }

        private void setGoalPreset(long goal) {
            currentStepGoal = goal;
            if (sliderGoal != null) sliderGoal.setValue((float) goal);
            if (textGoalValue != null) {
                textGoalValue.setText(String.format(Locale.getDefault(), "%,d", goal));
            }
            HapticHelper.performTick(OnboardingActivity.this, true);
            updateGoalEquivalent();
        }

        private void bindPage6Views(@NonNull View view) {
            tvHydrationHero = view.findViewById(R.id.tv_onboarding_hydration_hero);
            tvHydrationBase = view.findViewById(R.id.tv_hydration_base);
            tvHydrationActivity = view.findViewById(R.id.tv_hydration_activity);
            tvHydrationBsaAge = view.findViewById(R.id.tv_hydration_bsa_age);
            updateHydrationViewsWithAnimation();
        }

        private void bindPage7Views(@NonNull View view) {
            statusCard = view.findViewById(R.id.card_onboarding_status);
            statusIcon = view.findViewById(R.id.img_status_icon);
            statusTitle = view.findViewById(R.id.tv_status_title);
            statusDesc = view.findViewById(R.id.tv_status_desc);
            updatePermissionActionButtons();
        }
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void afterTextChanged(Editable s) {}
    }
}
