package com.pulse.tracker.ui.water;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.R;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.data.repository.WaterRepository;
import com.pulse.tracker.domain.WaterIntakeCalculator;
import com.pulse.tracker.receiver.WaterReminderManager;
import com.pulse.tracker.ui.common.CircularProgressView;
import com.pulse.tracker.util.HapticHelper;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Screen presenting daily water intake tracking, evidence-based scientific hydration calculator,
 * and interval reminder controls adhering to the Pulse Bento dark design system.
 */
public class WaterFragment extends Fragment {

    private CircularProgressView progressRing;
    private TextView textCurrent;
    private TextView textTarget;
    private TextView textRemaining;

    // Breakdown Bento Card views
    private TextView textBreakdownBaseSub;
    private TextView textBreakdownBaseVal;
    private TextView textBreakdownBsaSub;
    private TextView textBreakdownBsaVal;
    private TextView textBreakdownAgeSub;
    private TextView textBreakdownAgeVal;
    private TextView textBreakdownStepsSub;
    private TextView textBreakdownStepsVal;
    private TextView textBreakdownTotalVal;

    // Reminder toggle view
    private MaterialCardView btnReminderToggle;
    private ImageView iconReminderToggle;

    private int currentIntakeMl = 0;
    private int calculatedTargetMl = WaterIntakeCalculator.DEFAULT_FALLBACK_TARGET_ML;
    private long todaySteps = 0L;

    @Nullable
    private ValueAnimator textNumberAnimator;
    @Nullable
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    @Nullable
    private SettingsRepository.OnSettingsChangeListener settingsListener;
    @Nullable
    private LiveData<DailyMetricsEntity> todayMetricsLive;

    public static WaterFragment newInstance() {
        return new WaterFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_water, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupListeners(view);
        setupDataObservers();

        loadWaterIntake();
        recalculateAndRender(false);
    }

    private void bindViews(@NonNull View view) {
        progressRing = view.findViewById(R.id.hero_water_progress_view);
        textCurrent = view.findViewById(R.id.text_water_current);
        textTarget = view.findViewById(R.id.text_water_target);
        textRemaining = view.findViewById(R.id.text_water_remaining);

        textBreakdownBaseSub = view.findViewById(R.id.text_breakdown_base_sub);
        textBreakdownBaseVal = view.findViewById(R.id.text_breakdown_base_val);
        textBreakdownBsaSub = view.findViewById(R.id.text_breakdown_bsa_sub);
        textBreakdownBsaVal = view.findViewById(R.id.text_breakdown_bsa_val);
        textBreakdownAgeSub = view.findViewById(R.id.text_breakdown_age_sub);
        textBreakdownAgeVal = view.findViewById(R.id.text_breakdown_age_val);
        textBreakdownStepsSub = view.findViewById(R.id.text_breakdown_steps_sub);
        textBreakdownStepsVal = view.findViewById(R.id.text_breakdown_steps_val);
        textBreakdownTotalVal = view.findViewById(R.id.text_breakdown_total_val);

        btnReminderToggle = view.findViewById(R.id.btn_water_reminder_toggle);
        iconReminderToggle = view.findViewById(R.id.icon_water_reminder_toggle);
    }

    private void setupListeners(@NonNull View view) {
        MaterialCardView btn250 = view.findViewById(R.id.btn_add_250);
        MaterialCardView btn500 = view.findViewById(R.id.btn_add_500);
        MaterialCardView btn750 = view.findViewById(R.id.btn_add_750);
        View btnReset = view.findViewById(R.id.btn_water_reset);

        if (btn250 != null) {
            btn250.setOnClickListener(v -> addWater(250));
        }
        if (btn500 != null) {
            btn500.setOnClickListener(v -> addWater(500));
        }
        if (btn750 != null) {
            btn750.setOnClickListener(v -> addWater(750));
        }
        if (btnReset != null) {
            btnReset.setOnClickListener(v -> resetWater());
        }

        if (btnReminderToggle != null) {
            btnReminderToggle.setOnClickListener(v -> toggleReminders());
        }
    }

    private void setupDataObservers() {
        Context context = getContext();
        if (context == null) return;

        // 1. SharedPreferences listener (syncs with notification "+250 ml" quick action)
        SharedPreferences waterPrefs = context.getApplicationContext()
                .getSharedPreferences(WaterRepository.PREFS_NAME, Context.MODE_PRIVATE);
        prefsListener = (sharedPreferences, key) -> {
            if (WaterRepository.KEY_WATER_INTAKE.equals(key)) {
                int stored = WaterRepository.getTodayIntakeMl(context);
                if (stored != currentIntakeMl && isAdded()) {
                    int old = currentIntakeMl;
                    currentIntakeMl = stored;
                    animateNumberChange(old, currentIntakeMl);
                    updateProgressRing(true);
                    updateStatusTexts();
                }
            } else if (WaterRepository.KEY_REMINDERS_ENABLED.equals(key)) {
                updateReminderVisuals();
            }
        };
        waterPrefs.registerOnSharedPreferenceChangeListener(prefsListener);

        // 2. SettingsRepository listener (recalculates if biometrics change in Profile)
        SettingsRepository settings = SettingsRepository.getInstance(context);
        settingsListener = key -> {
            if (SettingsRepository.KEY_HEIGHT_CM.equals(key) ||
                    SettingsRepository.KEY_WEIGHT_KG.equals(key) ||
                    SettingsRepository.KEY_AGE.equals(key) ||
                    SettingsRepository.KEY_GENDER.equals(key)) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> recalculateAndRender(true));
                }
            }
        };
        settings.registerListener(settingsListener);

        // 3. LiveData observer on today's steps in Room
        MetricsRepository metricsRepo = MetricsRepository.getInstance(context);
        String today = WaterRepository.getTodayDateString();
        todayMetricsLive = metricsRepo.getTodayMetricsLive(today);
        todayMetricsLive.observe(getViewLifecycleOwner(), entity -> {
            long newSteps = (entity != null) ? entity.getSteps() : 0L;
            if (newSteps != todaySteps) {
                todaySteps = newSteps;
                recalculateAndRender(true);
            }
        });
    }

    private void loadWaterIntake() {
        if (getContext() == null) return;
        currentIntakeMl = WaterRepository.getTodayIntakeMl(getContext());
    }

    private void recalculateAndRender(boolean animateProgress) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        SettingsRepository settings = SettingsRepository.getInstance(context);
        double height = settings.getHeightCm();
        double weight = settings.getWeightKg();
        int age = settings.getAge();
        SettingsRepository.Gender gender = settings.getGender();

        WaterIntakeCalculator.Breakdown breakdown =
                WaterIntakeCalculator.calculateBreakdown(weight, height, age, gender, todaySteps);

        calculatedTargetMl = breakdown.getTargetMl();

        // 1. Hero text & target
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
        if (textCurrent != null) {
            textCurrent.setText(nf.format(currentIntakeMl));
        }
        if (textTarget != null) {
            textTarget.setText(getString(R.string.water_target_format, nf.format(calculatedTargetMl)));
        }

        // 2. Circular progress ring
        updateProgressRing(animateProgress);

        // 3. Status and remaining
        updateStatusTexts();

        // 4. Breakdown card values
        renderBreakdownCard(breakdown, weight, age, gender, todaySteps);

        // 5. Reminder toggle state
        updateReminderVisuals();
    }

    private void renderBreakdownCard(@NonNull WaterIntakeCalculator.Breakdown breakdown,
                                    double weight, int age,
                                    SettingsRepository.Gender gender, long steps) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());

        // Base
        String genderStr = (gender == SettingsRepository.Gender.MALE) ? "Male" :
                (gender == SettingsRepository.Gender.FEMALE) ? "Female" : "Unspecified";
        double multiplier = WaterIntakeCalculator.getGenderMultiplier(gender);
        if (textBreakdownBaseSub != null) {
            textBreakdownBaseSub.setText(String.format(Locale.getDefault(), "%.1f kg • %s (%.0f ml/kg)", weight, genderStr, multiplier));
        }
        if (textBreakdownBaseVal != null) {
            textBreakdownBaseVal.setText(String.format(Locale.getDefault(), "%s ml", nf.format(Math.round(breakdown.getBaseMl()))));
        }

        // BSA
        if (textBreakdownBsaSub != null) {
            textBreakdownBsaSub.setText(String.format(Locale.getDefault(), "BSA %.2f m² (DuBois baseline 1.73)", breakdown.getBsa()));
        }
        if (textBreakdownBsaVal != null) {
            int bsaAdj = (int) Math.round(breakdown.getBsaAdjustmentMl());
            String sign = (bsaAdj > 0) ? "+" : "";
            textBreakdownBsaVal.setText(String.format(Locale.getDefault(), "%s%s ml", sign, nf.format(bsaAdj)));
        }

        // Age
        if (textBreakdownAgeSub != null) {
            if (age < 30) {
                textBreakdownAgeSub.setText(String.format(Locale.getDefault(), "Age %d (< 30 yrs: +5%%)", age));
            } else if (age > 55) {
                textBreakdownAgeSub.setText(String.format(Locale.getDefault(), "Age %d (> 55 yrs: -5%%)", age));
            } else {
                textBreakdownAgeSub.setText(String.format(Locale.getDefault(), "Age %d (30–55 yrs: baseline)", age));
            }
        }
        if (textBreakdownAgeVal != null) {
            int ageAdj = (int) Math.round(breakdown.getAgeAdjustmentMl());
            String sign = (ageAdj > 0) ? "+" : "";
            textBreakdownAgeVal.setText(String.format(Locale.getDefault(), "%s%s ml", sign, nf.format(ageAdj)));
        }

        // Steps
        if (textBreakdownStepsSub != null) {
            if (steps > WaterIntakeCalculator.SEDENTARY_STEP_THRESHOLD) {
                textBreakdownStepsSub.setText(String.format(Locale.getDefault(), "%,d steps (+50 ml / 1k > 3k)", steps));
            } else {
                textBreakdownStepsSub.setText(String.format(Locale.getDefault(), "%,d steps (Sedentary baseline)", steps));
            }
        }
        if (textBreakdownStepsVal != null) {
            int stepAdj = (int) Math.round(breakdown.getStepCompensationMl());
            String sign = (stepAdj > 0) ? "+" : "";
            textBreakdownStepsVal.setText(String.format(Locale.getDefault(), "%s%s ml", sign, nf.format(stepAdj)));
        }

        // Total
        if (textBreakdownTotalVal != null) {
            textBreakdownTotalVal.setText(String.format(Locale.getDefault(), "%s ml", nf.format(breakdown.getTargetMl())));
        }
    }

    private void updateProgressRing(boolean animate) {
        if (progressRing == null) return;
        progressRing.setMax(Math.max(1, calculatedTargetMl));
        progressRing.setCurrentSteps(currentIntakeMl);

        float ratio = calculatedTargetMl > 0 ? (float) currentIntakeMl / calculatedTargetMl : 0f;
        if (animate) {
            progressRing.animateProgress(ratio, 400L);
        } else {
            progressRing.setProgress(ratio);
        }
    }

    private void updateStatusTexts() {
        if (!isAdded()) return;
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());

        if (textRemaining != null) {
            int remaining = Math.max(0, calculatedTargetMl - currentIntakeMl);
            if (remaining > 0) {
                textRemaining.setText(getString(R.string.water_remaining_format, nf.format(remaining)));
            } else {
                textRemaining.setText(R.string.water_goal_reached);
            }
        }
    }

    private void updateReminderVisuals() {
        Context context = getContext();
        if (context == null) return;

        boolean enabled = WaterRepository.isRemindersEnabled(context);

        if (btnReminderToggle != null) {
            btnReminderToggle.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                    enabled ? 0xFFFFFFFF : 0xFF222222));
        }

        if (iconReminderToggle != null) {
            iconReminderToggle.setImageResource(enabled ? R.drawable.ic_notification : R.drawable.ic_notification_off);
            iconReminderToggle.setImageTintList(android.content.res.ColorStateList.valueOf(
                    enabled ? 0xFF121212 : 0xFF8E8E93));
        }
    }

    private void addWater(int amountMl) {
        Context context = getContext();
        if (context == null) return;

        HapticHelper.performTick(context, true);

        int oldVal = currentIntakeMl;
        currentIntakeMl = WaterRepository.addWaterIntake(context, amountMl);

        animateNumberChange(oldVal, currentIntakeMl);
        updateProgressRing(true);
        updateStatusTexts();
    }

    private void resetWater() {
        Context context = getContext();
        if (context == null) return;

        HapticHelper.performTick(context, true);

        int oldVal = currentIntakeMl;
        WaterRepository.resetTodayWater(context);
        currentIntakeMl = 0;

        animateNumberChange(oldVal, 0);
        updateProgressRing(true);
        updateStatusTexts();
    }

    private void toggleReminders() {
        Context context = getContext();
        if (context == null) return;

        HapticHelper.performTick(context, true);

        boolean current = WaterRepository.isRemindersEnabled(context);
        boolean updated = !current;
        WaterRepository.setRemindersEnabled(context, updated);

        if (updated) {
            WaterReminderManager.scheduleNextReminder(context);
        } else {
            WaterReminderManager.cancelReminders(context);
            WaterReminderManager.cancelNotification(context);
        }

        updateReminderVisuals();
    }

    private void animateNumberChange(int from, int to) {
        if (textNumberAnimator != null && textNumberAnimator.isRunning()) {
            textNumberAnimator.cancel();
        }

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.getDefault());
        textNumberAnimator = ValueAnimator.ofInt(from, to);
        textNumberAnimator.setDuration(350);
        textNumberAnimator.setInterpolator(new DecelerateInterpolator());
        textNumberAnimator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            if (textCurrent != null) {
                textCurrent.setText(nf.format(val));
            }
        });
        textNumberAnimator.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (textNumberAnimator != null) {
            textNumberAnimator.cancel();
            textNumberAnimator = null;
        }

        Context context = getContext();
        if (context != null) {
            if (prefsListener != null) {
                SharedPreferences waterPrefs = context.getApplicationContext()
                        .getSharedPreferences(WaterRepository.PREFS_NAME, Context.MODE_PRIVATE);
                waterPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
                prefsListener = null;
            }
            if (settingsListener != null) {
                SettingsRepository.getInstance(context).unregisterListener(settingsListener);
                settingsListener = null;
            }
        }
    }
}
