package com.pulse.tracker.ui.history;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.R;
import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;
import com.pulse.tracker.domain.CalorieCalculator;
import com.pulse.tracker.domain.DistanceCalculator;
import com.pulse.tracker.util.UnitFormatter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * History screen destination featuring month navigation, 7-column calendar grid,
 * selected day detail inspection Bento card, 3-column crisp white summary Bento card,
 * and monthly analytics bento cards.
 */
public class HistoryFragment extends Fragment {

    private HistoryViewModel viewModel;

    private TextView textSelectedMonth;
    private ImageButton btnPrevMonth;
    private ImageButton btnNextMonth;
    private RecyclerView recyclerCalendarDays;
    private CalendarDayAdapter calendarAdapter;

    // Selected Day Detail Card
    private TextView textSelectedDayDate;
    private MaterialCardView badgeSelectedDayGoal;
    private TextView textSelectedDayGoalStatus;
    private TextView textSelectedDaySteps;
    private TextView textSelectedDayDistance;
    private TextView textSelectedDayDistanceUnit;
    private TextView textSelectedDayActiveTime;
    private TextView textSelectedDayActiveTimeUnit;
    private TextView textSelectedDayCalories;
    private TextView textSelectedDayCaloriesUnit;

    // 3-Column Crisp White Summary Bento Card (Reference 3 Statistics Screen Style)
    private TextView textSummaryDistance;
    private TextView textSummaryGoalRate;
    private TextView textSummaryGoalRateLabel;
    private TextView textPersonalBestValue;
    private TextView textPersonalBestSubtitle;

    // Bento Monthly Total & Average Pace Cards
    private TextView textMonthlyTotalValue;
    private TextView textPaceValue;
    private TextView textPaceUnit;

    private SettingsRepository.UnitSystem currentUnitSystem = SettingsRepository.UnitSystem.METRIC;

    public HistoryFragment() {
        // Required empty public constructor
    }

    public static HistoryFragment newInstance() {
        return new HistoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.history_scroll_view).post(() -> {
            View sv = view.findViewById(R.id.history_scroll_view);
            if (sv != null) sv.scrollTo(0, 0);
        });

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        // Month Selector
        textSelectedMonth = view.findViewById(R.id.text_selected_month);
        btnPrevMonth = view.findViewById(R.id.btn_prev_month);
        btnNextMonth = view.findViewById(R.id.btn_next_month);

        btnPrevMonth.setOnClickListener(v -> viewModel.previousMonth());
        btnNextMonth.setOnClickListener(v -> viewModel.nextMonth());

        // Calendar Grid
        recyclerCalendarDays = view.findViewById(R.id.recycler_calendar_days);
        recyclerCalendarDays.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        calendarAdapter = new CalendarDayAdapter();
        calendarAdapter.setOnDayClickListener(dayModel -> {
            if (dayModel.getDate() != null) {
                viewModel.selectDate(dayModel.getDate());
            }
        });
        recyclerCalendarDays.setAdapter(calendarAdapter);

        // Selected Day Detail Card
        textSelectedDayDate = view.findViewById(R.id.text_selected_day_date);
        badgeSelectedDayGoal = view.findViewById(R.id.badge_selected_day_goal);
        textSelectedDayGoalStatus = view.findViewById(R.id.text_selected_day_goal_status);
        textSelectedDaySteps = view.findViewById(R.id.text_selected_day_steps);
        textSelectedDayDistance = view.findViewById(R.id.text_selected_day_distance);
        textSelectedDayDistanceUnit = view.findViewById(R.id.text_selected_day_distance_unit);
        textSelectedDayActiveTime = view.findViewById(R.id.text_selected_day_active_time);
        textSelectedDayActiveTimeUnit = view.findViewById(R.id.text_selected_day_active_time_unit);
        textSelectedDayCalories = view.findViewById(R.id.text_selected_day_calories);
        textSelectedDayCaloriesUnit = view.findViewById(R.id.text_selected_day_calories_unit);

        // 3-Column Summary Card
        textSummaryDistance = view.findViewById(R.id.text_summary_monthly_distance);
        textSummaryGoalRate = view.findViewById(R.id.text_summary_goal_rate);
        textSummaryGoalRateLabel = view.findViewById(R.id.text_summary_goal_rate_label);
        textPersonalBestValue = view.findViewById(R.id.text_history_personal_best_value);
        textPersonalBestSubtitle = view.findViewById(R.id.text_history_personal_best_subtitle);

        // Monthly Total & Pace Cards
        textMonthlyTotalValue = view.findViewById(R.id.text_history_monthly_total_value);
        textPaceValue = view.findViewById(R.id.text_history_pace_value);
        textPaceUnit = view.findViewById(R.id.text_history_pace_unit);

        // Observe ViewModel LiveData
        viewModel.getUnitSystem().observe(getViewLifecycleOwner(), unitSystem -> {
            currentUnitSystem = unitSystem;
            DailyMetricsEntity currentEntity = viewModel.getSelectedDateMetrics().getValue();
            if (currentEntity != null) {
                bindSelectedDateMetrics(currentEntity);
            }
            MetricsRepository.MonthlyAnalytics currentAnalytics = viewModel.getMonthlyAnalytics().getValue();
            if (currentAnalytics != null) {
                bindMonthlyAnalytics(currentAnalytics);
            }
        });

        viewModel.getSelectedMonth().observe(getViewLifecycleOwner(), this::bindSelectedMonth);
        viewModel.getCalendarDays().observe(getViewLifecycleOwner(), days -> {
            calendarAdapter.setDays(days);
            updateGoalAchievementRate(days);
        });
        viewModel.getSelectedDateMetrics().observe(getViewLifecycleOwner(), this::bindSelectedDateMetrics);
        viewModel.getMonthlyAnalytics().observe(getViewLifecycleOwner(), this::bindMonthlyAnalytics);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.handleMidnightRollover();
            DailyMetricsEntity currentEntity = viewModel.getSelectedDateMetrics().getValue();
            if (currentEntity != null) {
                bindSelectedDateMetrics(currentEntity);
            }
            MetricsRepository.MonthlyAnalytics currentAnalytics = viewModel.getMonthlyAnalytics().getValue();
            if (currentAnalytics != null) {
                bindMonthlyAnalytics(currentAnalytics);
            }
            List<CalendarDayModel> days = viewModel.getCalendarDays().getValue();
            if (days != null) {
                updateGoalAchievementRate(days);
            }
            YearMonth currentMonth = viewModel.getSelectedMonth().getValue();
            if (currentMonth != null) {
                bindSelectedMonth(currentMonth);
            }
        }
    }

    private void bindSelectedMonth(@NonNull YearMonth month) {
        String formatted = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()));
        textSelectedMonth.setText(formatted);
    }

    private void updateGoalAchievementRate(@Nullable List<CalendarDayModel> days) {
        if (textSummaryGoalRate == null) return;
        if (days == null || days.isEmpty()) {
            textSummaryGoalRate.setText(R.string.history_empty_placeholder);
            if (textSummaryGoalRateLabel != null) {
                textSummaryGoalRateLabel.setText(R.string.history_summary_goal_rate_label);
            }
            return;
        }

        LocalDate today = LocalDate.now();
        int elapsedDays = 0;
        int goalReachedCount = 0;

        for (CalendarDayModel day : days) {
            if (day.isInMonth() && day.getDate() != null && !day.getDate().isAfter(today)) {
                elapsedDays++;
                if (day.isGoalReached()) {
                    goalReachedCount++;
                }
            }
        }

        if (elapsedDays > 0) {
            int ratePercent = (int) Math.round((goalReachedCount * 100.0) / elapsedDays);
            textSummaryGoalRate.setText(String.format(Locale.getDefault(), "%d%%", ratePercent));
            if (textSummaryGoalRateLabel != null) {
                textSummaryGoalRateLabel.setText(String.format(Locale.getDefault(), "%d/%d Days", goalReachedCount, elapsedDays));
            }
        } else {
            textSummaryGoalRate.setText(R.string.history_empty_placeholder);
            if (textSummaryGoalRateLabel != null) {
                textSummaryGoalRateLabel.setText(R.string.history_summary_goal_rate_label);
            }
        }
    }

    private void bindSelectedDateMetrics(@NonNull DailyMetricsEntity entity) {
        Context context = getContext();
        if (context == null) return;

        LocalDate date;
        try {
            date = LocalDate.parse(entity.getDate());
        } catch (Exception e) {
            date = LocalDate.now();
        }

        // Header date formatted: "Wednesday, Nov 4" or "Today, Nov 7"
        if (date.equals(LocalDate.now())) {
            textSelectedDayDate.setText(String.format("Today, %s",
                    date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))));
        } else {
            textSelectedDayDate.setText(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())));
        }

        // Steps value
        textSelectedDaySteps.setText(UnitFormatter.formatSteps(entity.getSteps()));

        // Goal status pill
        long steps = entity.getSteps();
        long goal = entity.getStepGoal() > 0 ? entity.getStepGoal() : 10000L;
        if (steps >= goal && steps > 0) {
            badgeSelectedDayGoal.setCardBackgroundColor(ContextCompat.getColor(context, R.color.pulse_green));
            textSelectedDayGoalStatus.setTextColor(ContextCompat.getColor(context, R.color.pulse_on_green));
            textSelectedDayGoalStatus.setText(R.string.history_selected_day_goal_met);
        } else {
            badgeSelectedDayGoal.setCardBackgroundColor(ContextCompat.getColor(context, R.color.pulse_surface_alt));
            textSelectedDayGoalStatus.setTextColor(ContextCompat.getColor(context, R.color.pulse_text_secondary));
            textSelectedDayGoalStatus.setText(getString(R.string.history_selected_day_goal_format,
                    UnitFormatter.formatSteps(goal)));
        }

        // Settings for biometrics and stride fallbacks
        SettingsRepository settings = SettingsRepository.getInstance(context);

        // Sub-metrics: Distance, Active Time, Calories formatted with baseline-aligned values & units
        // Detail Card Zero Fallback: If distanceMeters <= 0.0 but steps > 0, calculate distance from stride length fallback
        double distanceMeters = entity.getDistanceMeters();
        if (distanceMeters <= 0.0 && steps > 0) {
            double strideCm = (settings != null) ? settings.getStrideLengthCm() : 70.0;
            if (strideCm <= 0.0) {
                strideCm = 70.0;
            }
            distanceMeters = DistanceCalculator.calculateDistanceMeters(steps, strideCm);
        }

        String formattedDist = UnitFormatter.formatDistance(distanceMeters, currentUnitSystem);
        String[] distParts = formattedDist.split(" ", 2);
        textSelectedDayDistance.setText(distParts[0]);
        if (textSelectedDayDistanceUnit != null) {
            textSelectedDayDistanceUnit.setText(distParts.length > 1 ? (" " + distParts[1]) : " km");
        }

        // Detail Card Zero Fallback: If activeSeconds <= 0 but steps > 0, compute fallback active seconds (Math.max(1, Math.round(steps * 0.55)))
        long seconds = entity.getActiveSeconds();
        if (seconds <= 0 && steps > 0) {
            seconds = Math.max(1L, Math.round(steps * 0.55));
        }

        if (seconds <= 0) {
            textSelectedDayActiveTime.setText("0");
            if (textSelectedDayActiveTimeUnit != null) textSelectedDayActiveTimeUnit.setText(" min");
        } else if (seconds < 60) {
            textSelectedDayActiveTime.setText("<1");
            if (textSelectedDayActiveTimeUnit != null) textSelectedDayActiveTimeUnit.setText(" min");
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (hours > 0) {
                textSelectedDayActiveTime.setText(hours + "h " + minutes);
                if (textSelectedDayActiveTimeUnit != null) textSelectedDayActiveTimeUnit.setText("m");
            } else {
                textSelectedDayActiveTime.setText(String.valueOf(minutes));
                if (textSelectedDayActiveTimeUnit != null) textSelectedDayActiveTimeUnit.setText(" min");
            }
        }

        // Detail Card Zero Fallback: If activeCaloriesKcal <= 0.0 but steps > 0, compute fallback step calories via CalorieCalculator.calculateStepCalories(steps, weight, stride)
        double kcal = entity.getActiveCaloriesKcal();
        if ((kcal <= 0.0 || Double.isNaN(kcal) || Double.isInfinite(kcal)) && steps > 0) {
            double weight = (settings != null) ? settings.getWeightKg() : SettingsRepository.DEFAULT_WEIGHT_KG;
            double stride = (settings != null) ? settings.getStrideLengthCm() : 70.0;
            kcal = CalorieCalculator.calculateStepCalories(steps, weight, stride);
        }

        if (kcal <= 0.0 || Double.isNaN(kcal) || Double.isInfinite(kcal)) {
            textSelectedDayCalories.setText("0");
            if (textSelectedDayCaloriesUnit != null) textSelectedDayCaloriesUnit.setText(" kcal");
        } else {
            long roundedKcal = Math.round(kcal);
            textSelectedDayCalories.setText(java.text.NumberFormat.getIntegerInstance().format(roundedKcal));
            if (textSelectedDayCaloriesUnit != null) textSelectedDayCaloriesUnit.setText(" kcal");
        }
    }

    private void bindMonthlyAnalytics(@NonNull MetricsRepository.MonthlyAnalytics analytics) {
        Context context = getContext();
        if (context == null) return;

        // 1. Monthly Total steps
        if (textMonthlyTotalValue != null) {
            textMonthlyTotalValue.setText(UnitFormatter.formatSteps(analytics.getTotalSteps()));
        }

        // 2. 3-Column Summary Card: Col 1 Distance walked with fallback
        double totalDistance = analytics.getTotalDistanceMeters();
        if (totalDistance <= 0.0 && analytics.getTotalSteps() > 0) {
            SettingsRepository settings = SettingsRepository.getInstance(context);
            double strideCm = (settings != null) ? settings.getStrideLengthCm() : 70.0;
            if (strideCm <= 0.0) {
                strideCm = 70.0;
            }
            totalDistance = DistanceCalculator.calculateDistanceMeters(analytics.getTotalSteps(), strideCm);
        }

        if (textSummaryDistance != null) {
            textSummaryDistance.setText(UnitFormatter.formatDistance(totalDistance, currentUnitSystem));
        }

        // 3. 3-Column Summary Card: Col 3 Personal Best
        DailyMetricsEntity pb = analytics.getPersonalBest();
        if (pb != null && pb.getSteps() > 0) {
            long pbSteps = pb.getSteps();
            if (pbSteps >= 10000) {
                if (textPersonalBestValue != null) {
                    textPersonalBestValue.setText(String.format(Locale.getDefault(), "%.1fk", pbSteps / 1000.0));
                }
            } else {
                if (textPersonalBestValue != null) {
                    textPersonalBestValue.setText(UnitFormatter.formatSteps(pbSteps));
                }
            }
            if (textPersonalBestSubtitle != null) {
                textPersonalBestSubtitle.setText(R.string.history_summary_pb_label);
            }
        } else {
            if (textPersonalBestValue != null) {
                textPersonalBestValue.setText(R.string.history_empty_placeholder);
            }
            if (textPersonalBestSubtitle != null) {
                textPersonalBestSubtitle.setText(R.string.history_summary_pb_label);
            }
        }

        // 4. Average Pace
        long totalSeconds = analytics.getTotalActiveSeconds();

        // If totalDistance > 0.0 but totalSeconds == 0, estimate active seconds from totalSteps so pace does not display empty "--" when user actually walked.
        if (totalDistance > 0.0 && totalSeconds <= 0 && analytics.getTotalSteps() > 0) {
            totalSeconds = Math.max(1L, Math.round(analytics.getTotalSteps() * 0.55));
        }

        if (totalDistance > 0.0 && totalSeconds > 0) {
            double distanceKm = DistanceCalculator.metersToKilometers(totalDistance);
            if (distanceKm > 0.0001) {
                double paceSecondsPerKm = totalSeconds / distanceKm;
                // Clamp pace to reasonable bounds [2:00/km, 30:00/km] (120 to 1800 seconds per km)
                if (paceSecondsPerKm < 120.0) {
                    paceSecondsPerKm = 120.0;
                } else if (paceSecondsPerKm > 1800.0) {
                    paceSecondsPerKm = 1800.0;
                }

                double paceSeconds = (currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL)
                        ? paceSecondsPerKm * (DistanceCalculator.METERS_PER_MILE / DistanceCalculator.METERS_PER_KILOMETER)
                        : paceSecondsPerKm;

                long paceMin = (long) (paceSeconds / 60);
                long paceSec = (long) (paceSeconds % 60);

                if (textPaceValue != null) {
                    textPaceValue.setText(String.format(Locale.getDefault(), "%d'%02d\"", paceMin, paceSec));
                }
                if (textPaceUnit != null) {
                    textPaceUnit.setText(currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL
                            ? " " + getString(R.string.history_avg_pace_unit_mi)
                            : " " + getString(R.string.history_avg_pace_unit));
                }
            } else {
                setEmptyPace();
            }
        } else {
            setEmptyPace();
        }
    }

    private void setEmptyPace() {
        if (textPaceValue != null) {
            textPaceValue.setText(R.string.history_empty_placeholder);
        }
        if (textPaceUnit != null) {
            textPaceUnit.setText(currentUnitSystem == SettingsRepository.UnitSystem.IMPERIAL
                    ? " " + getString(R.string.history_avg_pace_unit_mi)
                    : " " + getString(R.string.history_avg_pace_unit));
        }
    }

    @VisibleForTesting
    void bindSelectedDateMetricsForTest(@NonNull DailyMetricsEntity entity) {
        bindSelectedDateMetrics(entity);
    }

    @VisibleForTesting
    void bindMonthlyAnalyticsForTest(@NonNull MetricsRepository.MonthlyAnalytics analytics) {
        bindMonthlyAnalytics(analytics);
    }

    @VisibleForTesting
    void updateGoalAchievementRateForTest(@Nullable List<CalendarDayModel> days) {
        updateGoalAchievementRate(days);
    }

    @VisibleForTesting
    TextView getTextSummaryDistance() {
        return textSummaryDistance;
    }

    @VisibleForTesting
    TextView getTextSummaryGoalRate() {
        return textSummaryGoalRate;
    }

    @VisibleForTesting
    TextView getTextPersonalBestValue() {
        return textPersonalBestValue;
    }

    @VisibleForTesting
    TextView getTextSelectedDayDistance() {
        return textSelectedDayDistance;
    }

    @VisibleForTesting
    TextView getTextSelectedDayActiveTime() {
        return textSelectedDayActiveTime;
    }

    @VisibleForTesting
    TextView getTextSelectedDayCalories() {
        return textSelectedDayCalories;
    }

    @VisibleForTesting
    TextView getTextPaceValue() {
        return textPaceValue;
    }

    @VisibleForTesting
    TextView getTextPaceUnit() {
        return textPaceUnit;
    }
}
