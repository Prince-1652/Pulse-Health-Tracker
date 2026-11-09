package com.pulse.tracker.ui.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.MainActivity;
import com.pulse.tracker.R;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.DistanceCalculator;
import com.pulse.tracker.service.StepNotificationManager;
import com.pulse.tracker.service.StepTrackingService;
import com.pulse.tracker.ui.common.CircularProgressView;
import com.pulse.tracker.ui.common.RoundedBarChartRenderer;
import com.pulse.tracker.util.PermissionUtils;
import com.pulse.tracker.util.UnitFormatter;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Today/Home screen destination hosting the hero circular progress visualization,
 * Bento modular metric cards (Distance, Calories, Active Minutes), 7-day activity chart,
 * and dynamic state banners.
 */
public class TodayFragment extends Fragment {

    private static final String TAG = "TodayFragment";

    private HomeViewModel viewModel;

    // Top Header & Actions
    private TextView textGreetingTitle;
    private MaterialCardView btnNotificationToggle;
    private ImageView iconNotificationToggle;

    // Hero & Header
    private CircularProgressView heroProgressView;
    private TextView textHeroSteps;
    private TextView textHeroGoal;

    // Dynamic State Banners
    private View cardStatePermission;
    private MaterialButton btnGrantPermission;
    private View cardStateSensorUnsupported;
    private View cardStatePaused;
    private MaterialButton btnResumeTracking;

    // Bento Metrics
    private TextView textDistanceValue;
    private TextView textDistanceUnit;
    private TextView textCaloriesValue;
    private TextView textCaloriesUnit;
    private TextView textActiveTimeValue;
    private TextView textActiveTimeUnit;

    // 7-Day Chart
    private BarChart weeklyBarChart;
    private View chartEmptyPlaceholder;

    private boolean initialEntranceAnimated = false;
    private boolean chartEntranceAnimated = false;
    private ValueAnimator metricsCountAnimator;
    private long currentSteps = 0L;
    private long dailyGoal = 10000L;
    private SettingsRepository.UnitSystem currentUnitSystem = SettingsRepository.UnitSystem.METRIC;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    public TodayFragment() {
        // Required empty public constructor
    }

    public static TodayFragment newInstance() {
        return new TodayFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (viewModel != null) {
                        viewModel.refresh();
                    }
                }
        );

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Context context = getContext();
                    if (context == null) {
                        return;
                    }
                    if (isGranted) {
                        startNotificationUpdateService(context, true);
                    } else {
                        SettingsRepository.getInstance(context).setNotificationEnabled(false);
                        updateNotificationToggleVisuals();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupActionListeners();
        updateNotificationToggleVisuals();
        updateGreeting();

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        observeViewModel();
    }

    private void bindViews(@NonNull View root) {
        textGreetingTitle = root.findViewById(R.id.text_greeting_title);
        btnNotificationToggle = root.findViewById(R.id.btn_notification_toggle);
        iconNotificationToggle = root.findViewById(R.id.icon_notification_toggle);

        heroProgressView = root.findViewById(R.id.hero_progress_view);
        textHeroSteps = root.findViewById(R.id.text_hero_steps);
        textHeroGoal = root.findViewById(R.id.text_hero_goal);

        cardStatePermission = root.findViewById(R.id.card_state_permission);
        btnGrantPermission = root.findViewById(R.id.btn_grant_permission);
        cardStateSensorUnsupported = root.findViewById(R.id.card_state_sensor_unsupported);
        cardStatePaused = root.findViewById(R.id.card_state_paused);
        btnResumeTracking = root.findViewById(R.id.btn_resume_tracking);

        textDistanceValue = root.findViewById(R.id.text_distance_value);
        textDistanceUnit = root.findViewById(R.id.text_distance_unit);
        textCaloriesValue = root.findViewById(R.id.text_calories_value);
        textCaloriesUnit = root.findViewById(R.id.text_calories_unit);
        textActiveTimeValue = root.findViewById(R.id.text_active_time_value);
        textActiveTimeUnit = root.findViewById(R.id.text_active_time_unit);

        weeklyBarChart = root.findViewById(R.id.weekly_bar_chart);
        chartEmptyPlaceholder = root.findViewById(R.id.chart_empty_placeholder);
    }

    private void setupActionListeners() {
        if (btnNotificationToggle != null) {
            btnNotificationToggle.setOnClickListener(v -> toggleNotificationState());
        }

        btnGrantPermission.setOnClickListener(v -> {
            Context context = requireContext();
            String[] permsToRequest = PermissionUtils.getPermissionsToRequest(context);
            if (permsToRequest.length > 0) {
                permissionLauncher.launch(permsToRequest);
            } else {
                PermissionUtils.openAppSettings(requireActivity());
            }
        });

        btnResumeTracking.setOnClickListener(v -> {
            Context context = requireContext();
            SettingsRepository settings = SettingsRepository.getInstance(context);
            settings.setTrackingPaused(false);
            Intent resumeIntent = new Intent(context, StepTrackingService.class);
            resumeIntent.setAction(StepTrackingService.ACTION_RESUME);
            try {
                if (settings.isNotificationEnabled()) {
                    ContextCompat.startForegroundService(context, resumeIntent);
                } else {
                    context.startService(resumeIntent);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException starting service: " + e.getMessage());
            } catch (IllegalStateException e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e instanceof ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "ForegroundServiceStartNotAllowedException resuming service: " + e.getMessage());
                } else {
                    Log.w(TAG, "IllegalStateException resuming service: " + e.getMessage());
                }
                try {
                    context.startService(resumeIntent);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service: " + e.getMessage(), e);
            }
            if (viewModel != null) {
                viewModel.checkTrackingStatus();
            }
            updateNotificationToggleVisuals();
        });
    }

    private void toggleNotificationState() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        SettingsRepository settings = SettingsRepository.getInstance(context);
        boolean enabled = !settings.isNotificationEnabled();
        settings.setNotificationEnabled(enabled);
        updateNotificationToggleVisuals();

        if (!enabled) {
            try {
                NotificationManagerCompat.from(context).cancel(StepNotificationManager.NOTIFICATION_ID);
            } catch (Exception ignored) {
            }
        }

        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionUtils.hasNotificationPermission(context)) {
                if (notificationPermissionLauncher != null) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
                return;
            }
        }

        startNotificationUpdateService(context, enabled);
    }

    private void startNotificationUpdateService(@NonNull Context context, boolean enabled) {
        Intent updateIntent = new Intent(context, StepTrackingService.class);
        updateIntent.setAction(StepTrackingService.ACTION_UPDATE_NOTIFICATION);
        try {
            if (enabled) {
                ContextCompat.startForegroundService(context, updateIntent);
            } else {
                context.startService(updateIntent);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException starting StepTrackingService for notification update: " + e.getMessage());
        } catch (IllegalStateException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e instanceof ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "ForegroundServiceStartNotAllowedException starting StepTrackingService: " + e.getMessage());
            } else {
                Log.w(TAG, "IllegalStateException starting StepTrackingService: " + e.getMessage());
            }
            try {
                context.startService(updateIntent);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error starting StepTrackingService: " + e.getMessage(), e);
        }
    }

    private void updateNotificationToggleVisuals() {
        if (iconNotificationToggle == null || getContext() == null) {
            return;
        }

        SettingsRepository settings = SettingsRepository.getInstance(requireContext());
        boolean isNotificationEnabled = settings.isNotificationEnabled();

        if (isNotificationEnabled) {
            // White circle background just like bottom nav active pill, dark icon
            if (btnNotificationToggle != null) {
                btnNotificationToggle.setCardBackgroundColor(ColorStateList.valueOf(0xFFFFFFFF));
            }
            iconNotificationToggle.setImageResource(R.drawable.ic_notification);
            iconNotificationToggle.setImageTintList(ColorStateList.valueOf(0xFF121212));
        } else {
            // Disabled dark circle background, muted slash icon
            if (btnNotificationToggle != null) {
                btnNotificationToggle.setCardBackgroundColor(ColorStateList.valueOf(0xFF222222));
            }
            iconNotificationToggle.setImageResource(R.drawable.ic_notification_off);
            iconNotificationToggle.setImageTintList(ColorStateList.valueOf(0xFF8E8E93));
        }
    }

    private void observeViewModel() {
        // Observe user preferences
        viewModel.getDailyStepTarget().observe(getViewLifecycleOwner(), target -> {
            if (target != null && target > 0) {
                this.dailyGoal = target;
                if (heroProgressView != null) {
                    heroProgressView.setMax((int) dailyGoal);
                    String formattedGoal = UnitFormatter.formatSteps(dailyGoal);
                    heroProgressView.setSubtitle(getString(R.string.today_hero_subtitle_format, formattedGoal));
                    if (textHeroGoal != null) {
                        textHeroGoal.setText(formattedGoal);
                    }
                }
            }
        });

        viewModel.getUnitSystem().observe(getViewLifecycleOwner(), unitSystem -> {
            if (unitSystem != null) {
                this.currentUnitSystem = unitSystem;
                DailyMetricsEntity entity = viewModel.getTodayMetrics().getValue();
                double meters = entity != null ? entity.getDistanceMeters() : 0.0;
                updateDistanceDisplay(meters);
            }
        });

        // Observe tracking state transitions
        viewModel.getTrackingStatus().observe(getViewLifecycleOwner(), this::updateTrackingStatusView);

        // Observe today's Room metrics row
        viewModel.getTodayMetrics().observe(getViewLifecycleOwner(), entity -> {
            if (entity != null) {
                long goal = entity.getStepGoal() > 0 ? entity.getStepGoal() : dailyGoal;
                updateMetrics(entity.getSteps(), (int) goal, entity.getDistanceMeters(),
                        entity.getActiveCaloriesKcal(), entity.getActiveSeconds());
            } else {
                updateMetrics(0L, (int) dailyGoal, 0.0, 0.0, 0L);
            }
        });

        // Observe 7-day chronological metrics
        viewModel.getLast7DaysMetrics().observe(getViewLifecycleOwner(), this::updateWeeklyChart);
    }

    private void updateTrackingStatusView(@Nullable HomeViewModel.TrackingStatus status) {
        updateNotificationToggleVisuals();

        if (status == null) {
            status = HomeViewModel.TrackingStatus.ACTIVE;
        }

        switch (status) {
            case PAUSED:
                // User directive: Do NOT show resume block in UI
                if (cardStatePaused != null) cardStatePaused.setVisibility(View.GONE);
                if (cardStatePermission != null) cardStatePermission.setVisibility(View.GONE);
                if (cardStateSensorUnsupported != null) cardStateSensorUnsupported.setVisibility(View.GONE);
                break;

            case PERMISSION_MISSING:
                if (cardStatePermission != null) cardStatePermission.setVisibility(View.VISIBLE);
                if (cardStatePaused != null) cardStatePaused.setVisibility(View.GONE);
                if (cardStateSensorUnsupported != null) cardStateSensorUnsupported.setVisibility(View.GONE);
                break;

            case SENSOR_UNSUPPORTED:
                if (cardStateSensorUnsupported != null) cardStateSensorUnsupported.setVisibility(View.VISIBLE);
                if (cardStatePermission != null) cardStatePermission.setVisibility(View.GONE);
                if (cardStatePaused != null) cardStatePaused.setVisibility(View.GONE);
                break;

            case ACTIVE:
            default:
                if (cardStatePermission != null) cardStatePermission.setVisibility(View.GONE);
                if (cardStateSensorUnsupported != null) cardStateSensorUnsupported.setVisibility(View.GONE);
                if (cardStatePaused != null) cardStatePaused.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * Updates display metrics atomically from repository/service.
     */
    public void updateMetrics(long steps, int goal, double distanceMeters, double caloriesKcal, long activeSeconds) {
        this.currentSteps = steps;
        this.dailyGoal = goal > 0 ? goal : this.dailyGoal;
        String formattedGoal = UnitFormatter.formatSteps(dailyGoal);

        if (heroProgressView != null) {
            heroProgressView.setMax((int) dailyGoal);
            heroProgressView.setCurrentSteps(steps);
            heroProgressView.setSubtitle(getString(R.string.today_hero_subtitle_format, formattedGoal));
        }

        if (textHeroGoal != null) {
            textHeroGoal.setText(formattedGoal);
        }

        float targetProgress = dailyGoal > 0 ? (float) steps / dailyGoal : 0f;

        if (!initialEntranceAnimated && isAdded()) {
            initialEntranceAnimated = true;

            // 1. Smooth 60 FPS Ease-in-out ring fill from 0%
            if (heroProgressView != null) {
                heroProgressView.animateProgress(0f, targetProgress, 1100L);
            }

            // 2. Smooth 60 FPS Ease-in-out count-up animation for all numeric metrics
            if (metricsCountAnimator != null && metricsCountAnimator.isRunning()) {
                metricsCountAnimator.cancel();
            }

            final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault());
            metricsCountAnimator = ValueAnimator.ofFloat(0f, 1f);
            metricsCountAnimator.setDuration(1100L);
            metricsCountAnimator.setInterpolator(new FastOutSlowInInterpolator());
            metricsCountAnimator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                long animSteps = Math.round(steps * fraction);
                if (textHeroSteps != null) {
                    textHeroSteps.setText(numberFormat.format(animSteps));
                }
                if (textCaloriesValue != null) {
                    int animKcal = (int) Math.round(caloriesKcal * fraction);
                    textCaloriesValue.setText(String.valueOf(animKcal));
                }
                if (textDistanceValue != null) {
                    updateDistanceDisplay(distanceMeters * fraction);
                }
                if (textActiveTimeValue != null) {
                    long animSecs = Math.round(activeSeconds * fraction);
                    textActiveTimeValue.setText(String.valueOf(animSecs / 60L));
                }
            });
            metricsCountAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (textHeroSteps != null) {
                        textHeroSteps.setText(numberFormat.format(steps));
                    }
                    if (textCaloriesValue != null) {
                        textCaloriesValue.setText(String.valueOf((int) Math.round(Math.max(0.0, caloriesKcal))));
                    }
                    updateDistanceDisplay(distanceMeters);
                    if (textActiveTimeValue != null) {
                        textActiveTimeValue.setText(String.valueOf(Math.max(0, activeSeconds / 60L)));
                    }
                }
            });
            metricsCountAnimator.start();
        } else {
            if (heroProgressView != null) {
                heroProgressView.setProgress(targetProgress);
            }
            if (textHeroSteps != null) {
                textHeroSteps.setText(NumberFormat.getIntegerInstance(Locale.getDefault()).format(steps));
            }
            if (textCaloriesValue != null) {
                int roundedKcal = (int) Math.round(Math.max(0.0, caloriesKcal));
                textCaloriesValue.setText(String.valueOf(roundedKcal));
            }
            updateDistanceDisplay(distanceMeters);
            if (textActiveTimeValue != null) {
                long minutes = Math.max(0, activeSeconds / 60);
                textActiveTimeValue.setText(String.valueOf(minutes));
            }
        }

        if (textCaloriesUnit != null) {
            textCaloriesUnit.setText(" " + getString(R.string.calories_unit));
        }
        if (textActiveTimeUnit != null) {
            textActiveTimeUnit.setText(" " + getString(R.string.active_time_unit));
        }
    }

    private void updateDistanceDisplay(double meters) {
        if (textDistanceValue == null || textDistanceUnit == null) {
            return;
        }

        double safeMeters = Math.max(0.0, meters);
        if (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL) {
            double miles = DistanceCalculator.metersToMiles(safeMeters);
            textDistanceValue.setText(String.format(Locale.getDefault(), "%.2f", miles));
            textDistanceUnit.setText(" " + getString(R.string.distance_unit_mi));
        } else {
            double km = DistanceCalculator.metersToKilometers(safeMeters);
            textDistanceValue.setText(String.format(Locale.getDefault(), "%.2f", km));
            textDistanceUnit.setText(" " + getString(R.string.distance_unit_km));
        }
    }

    private void updateWeeklyChart(@Nullable List<DailyMetricsEntity> last7Days) {
        if (weeklyBarChart == null || chartEmptyPlaceholder == null) {
            return;
        }

        // Chart styling per Section 11.3 & Reference 3 Statistics Directives
        weeklyBarChart.getDescription().setEnabled(false);
        weeklyBarChart.getLegend().setEnabled(false);
        weeklyBarChart.setPinchZoom(false);
        weeklyBarChart.setScaleEnabled(false);
        weeklyBarChart.setDragEnabled(false);
        weeklyBarChart.setDoubleTapToZoomEnabled(false);
        weeklyBarChart.setHighlightPerTapEnabled(false);
        weeklyBarChart.setHighlightPerDragEnabled(false);
        weeklyBarChart.setFitBars(false);

        float density = getResources().getDisplayMetrics().density;
        float offsetSide = density * 4f;
        float offsetTop = density * 16f;
        float offsetBottom = density * 28f;
        weeklyBarChart.setViewPortOffsets(offsetSide, offsetTop, offsetSide, offsetBottom);

        weeklyBarChart.setRenderer(new RoundedBarChartRenderer(weeklyBarChart, weeklyBarChart.getAnimator(), weeklyBarChart.getViewPortHandler(), 6f));

        Context context = requireContext();
        int textPrimaryColor = ContextCompat.getColor(context, R.color.pulse_text_primary);
        int textSecondaryColor = ContextCompat.getColor(context, R.color.pulse_text_secondary);

        boolean allZero = true;
        long maxSteps = 0L;
        int peakIndex = -1;

        if (last7Days != null && !last7Days.isEmpty()) {
            for (int i = 0; i < last7Days.size(); i++) {
                long steps = last7Days.get(i).getSteps();
                if (steps > 0) {
                    allZero = false;
                }
                if (steps > maxSteps) {
                    maxSteps = steps;
                    peakIndex = i;
                }
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        String[] weekdayLabels = new String[7];

        if (allZero) {
            // Modern Empty Graph:
            // 7 subtle ghost/skeleton bars (translucent grey #2A2A2E, rounded top caps, height ~24dp)
            // with weekday labels below them, and clean modern center pill badge overlay
            weeklyBarChart.setVisibility(View.VISIBLE);
            chartEmptyPlaceholder.setVisibility(View.VISIBLE);

            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                LocalDate day = today.minusDays(6 - i);
                weekdayLabels[i] = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                entries.add(new BarEntry(i, 1200f)); // Fixed height ghost bar
                colors.add(0xFF2A2A2E); // Subtle ghost translucent grey
            }

            BarDataSet dataSet = new BarDataSet(entries, "GhostWeeklySteps");
            dataSet.setColors(colors);
            dataSet.setDrawValues(false);

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.52f);

            YAxis leftAxis = weeklyBarChart.getAxisLeft();
            leftAxis.removeAllLimitLines();
            leftAxis.setDrawGridLines(false);
            leftAxis.setDrawAxisLine(false);
            leftAxis.setDrawLabels(false);
            leftAxis.setAxisMinimum(0f);

            // Gray horizontal line where bars start
            LimitLine baseLine = new LimitLine(0f);
            baseLine.setLineColor(0x33FFFFFF);
            baseLine.setLineWidth(1.2f);
            leftAxis.addLimitLine(baseLine);

            // Gray horizontal line where target is actually set
            float goalSteps = dailyGoal > 0 ? (float) dailyGoal : 10000f;
            LimitLine targetLine = new LimitLine(goalSteps);
            targetLine.setLineColor(0x4DFFFFFF);
            targetLine.setLineWidth(1.2f);
            targetLine.enableDashedLine(12f, 8f, 0f);
            leftAxis.addLimitLine(targetLine);

            leftAxis.setDrawLimitLinesBehindData(true);
            leftAxis.setAxisMaximum(goalSteps * 1.25f);

            weeklyBarChart.getAxisRight().setEnabled(false);

            XAxis xAxis = weeklyBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(false);
            xAxis.setTextColor(textSecondaryColor);
            xAxis.setTextSize(12f);
            xAxis.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            xAxis.setGranularity(1f);
            xAxis.setGranularityEnabled(true);
            xAxis.setValueFormatter(new IndexAxisValueFormatter(weekdayLabels));
            xAxis.setYOffset(6f);
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(6.5f);

            weeklyBarChart.setData(barData);
            weeklyBarChart.invalidate();

            if (!chartEntranceAnimated) {
                weeklyBarChart.animateY(1100, Easing.EaseInOutCubic);
                chartEntranceAnimated = true;
            }
            return;
        }

        // When activity exists:
        weeklyBarChart.setVisibility(View.VISIBLE);
        chartEmptyPlaceholder.setVisibility(View.GONE);

        final int peakColor = ContextCompat.getColor(requireContext(), R.color.pulse_orange);
        final int regularBarColor = (peakColor & 0x00FFFFFF) | 0x66000000; // 40% opacity for previous weekday activity
        final int todayIndex = last7Days.size() - 1;

        for (int i = 0; i < last7Days.size(); i++) {
            DailyMetricsEntity item = last7Days.get(i);
            entries.add(new BarEntry(i, (float) item.getSteps()));

            if (i == todayIndex) {
                colors.add(peakColor); // Highlight today's bar
            } else {
                colors.add(regularBarColor);
            }

            try {
                LocalDate date = LocalDate.parse(item.getDate());
                weekdayLabels[i] = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            } catch (Exception e) {
                weekdayLabels[i] = item.getDate();
            }
        }

        // X-Axis: Adaptive weekday labels, spread evenly across exactly 7 days
        XAxis xAxis = weeklyBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSecondaryColor);
        xAxis.setTextSize(12f);
        xAxis.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(weekdayLabels));
        xAxis.setYOffset(6f);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(6.5f);

        // Left and Right Y-Axis: Grid and labels disabled, gray base and target lines
        YAxis leftAxis = weeklyBarChart.getAxisLeft();
        leftAxis.removeAllLimitLines();
        leftAxis.resetAxisMaximum();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawLabels(false);
        leftAxis.setAxisMinimum(0f);

        // Gray horizontal line where bars start (y = 0)
        LimitLine baseLine = new LimitLine(0f);
        baseLine.setLineColor(0x33FFFFFF);
        baseLine.setLineWidth(1.2f);
        leftAxis.addLimitLine(baseLine);

        // Gray horizontal line where target is actually set (y = dailyGoal)
        float goalSteps = dailyGoal > 0 ? (float) dailyGoal : 10000f;
        LimitLine targetLine = new LimitLine(goalSteps);
        targetLine.setLineColor(0x4DFFFFFF);
        targetLine.setLineWidth(1.2f);
        targetLine.enableDashedLine(12f, 8f, 0f);
        leftAxis.addLimitLine(targetLine);

        leftAxis.setDrawLimitLinesBehindData(true);
        float axisMax = Math.max(goalSteps * 1.20f, (float) maxSteps * 1.20f);
        leftAxis.setAxisMaximum(axisMax);

        weeklyBarChart.getAxisRight().setEnabled(false);

        // BarDataSet configuration
        BarDataSet dataSet = new BarDataSet(entries, "WeeklySteps");
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(textPrimaryColor); // Adaptive text for peak label
        dataSet.setValueTextSize(10f);
        dataSet.setValueTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        final long todaySteps = (todayIndex >= 0 && todayIndex < last7Days.size()) ? last7Days.get(todayIndex).getSteps() : 0L;
        dataSet.setValueFormatter((value, entry, dataSetIndex, viewPortHandler) -> {
            if (entry != null && (int) entry.getX() == todayIndex) {
                return NumberFormat.getIntegerInstance(Locale.getDefault()).format(todaySteps) + " \u25B2";
            }
            return "";
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.52f);

        weeklyBarChart.setData(barData);
        weeklyBarChart.invalidate();

        if (!chartEntranceAnimated) {
            weeklyBarChart.animateY(1100, Easing.EaseInOutCubic);
            chartEntranceAnimated = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationToggleVisuals();
        updateGreeting();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    public void refreshGreeting() {
        updateGreeting();
    }

    private void updateGreeting() {
        if (textGreetingTitle != null && getContext() != null) {
            String name = SettingsRepository.getInstance(requireContext()).getUserName();
            textGreetingTitle.setText("Hi " + name + "!");
        }
    }

    @Override
    public void onDestroyView() {
        if (metricsCountAnimator != null && metricsCountAnimator.isRunning()) {
            metricsCountAnimator.cancel();
        }
        super.onDestroyView();
    }

    @VisibleForTesting
    public void endEntranceAnimations() {
        if (metricsCountAnimator != null && metricsCountAnimator.isRunning()) {
            metricsCountAnimator.end();
        }
    }

    @VisibleForTesting
    public HomeViewModel getViewModel() {
        return viewModel;
    }
}
