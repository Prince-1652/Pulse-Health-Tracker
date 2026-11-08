package com.pulse.tracker.ui.history;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.pulse.tracker.data.local.entity.DailyMetricsEntity;
import com.pulse.tracker.data.repository.MetricsRepository;
import com.pulse.tracker.data.repository.SettingsRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ViewModel managing activity history state, monthly calendar grid generation with Room metrics,
 * selected date inspection, and monthly analytics aggregation.
 */
public class HistoryViewModel extends AndroidViewModel {

    private final MetricsRepository metricsRepository;
    private final SettingsRepository settingsRepository;
    private final SettingsRepository.OnSettingsChangeListener settingsChangeListener;

    private final MutableLiveData<YearMonth> selectedMonth = new MutableLiveData<>();
    private final MutableLiveData<LocalDate> selectedDate = new MutableLiveData<>();
    private final MediatorLiveData<List<CalendarDayModel>> calendarDays = new MediatorLiveData<>();
    private final MediatorLiveData<DailyMetricsEntity> selectedDateMetrics = new MediatorLiveData<>();
    private final MutableLiveData<MetricsRepository.MonthlyAnalytics> monthlyAnalytics = new MutableLiveData<>();
    private final MutableLiveData<SettingsRepository.UnitSystem> unitSystem = new MutableLiveData<>();

    private LiveData<List<DailyMetricsEntity>> currentMonthRoomSource = null;
    private LiveData<DailyMetricsEntity> currentDateRoomSource = null;
    private List<DailyMetricsEntity> cachedMonthEntities = new ArrayList<>();

    public HistoryViewModel(@NonNull Application application) {
        this(application, MetricsRepository.getInstance(application), SettingsRepository.getInstance(application));
    }

    public HistoryViewModel(@NonNull Application application,
                            @NonNull MetricsRepository metricsRepository,
                            @NonNull SettingsRepository settingsRepository) {
        super(application);
        this.metricsRepository = Objects.requireNonNull(metricsRepository);
        this.settingsRepository = Objects.requireNonNull(settingsRepository);

        setLiveValue(unitSystem, settingsRepository.getUnitSystem());

        settingsChangeListener = key -> {
            if (SettingsRepository.KEY_UNIT_SYSTEM.equals(key)) {
                postLiveValue(unitSystem, settingsRepository.getUnitSystem());
            } else if (SettingsRepository.KEY_DAILY_STEP_TARGET.equals(key)) {
                rebuildCalendarDays();
                refreshSelectedDateMetrics();
            }
        };
        settingsRepository.registerListener(settingsChangeListener);

        // When selected month changes, refresh Room sources and analytics
        calendarDays.addSource(selectedMonth, month -> {
            if (month != null) {
                onSelectedMonthChanged(month);
            }
        });

        // When selected date changes, re-evaluate selection state in grid and observe date metrics
        calendarDays.addSource(selectedDate, date -> {
            rebuildCalendarDays();
            if (date != null) {
                observeSelectedDateMetrics(date);
            }
        });

        // Default initial month and date
        YearMonth currentYearMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        setLiveValue(selectedMonth, currentYearMonth);
        setLiveValue(selectedDate, today);
    }

    private void onSelectedMonthChanged(@NonNull YearMonth month) {
        // Adjust selected date if it is outside this month
        LocalDate currentSelected = selectedDate.getValue();
        if (currentSelected == null || !YearMonth.from(currentSelected).equals(month)) {
            LocalDate today = LocalDate.now();
            if (YearMonth.from(today).equals(month)) {
                setLiveValue(selectedDate, today);
            } else {
                setLiveValue(selectedDate, month.atDay(1));
            }
        }

        // Switch Room LiveData query for the month
        if (currentMonthRoomSource != null) {
            calendarDays.removeSource(currentMonthRoomSource);
        }
        String monthPattern = month.toString(); // "yyyy-MM"
        currentMonthRoomSource = metricsRepository.getMonthMetricsLive(monthPattern);
        calendarDays.addSource(currentMonthRoomSource, entities -> {
            cachedMonthEntities = (entities != null) ? entities : new ArrayList<>();
            rebuildCalendarDays();
            refreshMonthlyAnalytics(month);
        });

        // Compute initial monthly analytics synchronously or via callback
        refreshMonthlyAnalytics(month);
    }

    private void observeSelectedDateMetrics(@NonNull LocalDate date) {
        if (currentDateRoomSource != null) {
            selectedDateMetrics.removeSource(currentDateRoomSource);
        }
        String dateStr = date.toString();
        currentDateRoomSource = metricsRepository.getTodayMetricsLive(dateStr);
        selectedDateMetrics.addSource(currentDateRoomSource, entity -> {
            long defaultGoal = settingsRepository.getDailyStepTarget();
            DailyMetricsEntity safeEntity = (entity != null)
                    ? entity
                    : DailyMetricsEntity.empty(dateStr, defaultGoal);
            setLiveValue(selectedDateMetrics, safeEntity);
        });
    }

    private void refreshSelectedDateMetrics() {
        LocalDate date = selectedDate.getValue();
        if (date != null) {
            observeSelectedDateMetrics(date);
        }
    }

    public void refreshMonthlyAnalytics(@NonNull YearMonth month) {
        String monthPrefix = month.toString();
        metricsRepository.calculateMonthlyAnalytics(monthPrefix, analytics -> {
            setLiveValue(monthlyAnalytics, analytics);
        });
    }

    /**
     * Builds the complete 7-column calendar grid aligning Monday through Sunday.
     */
    private void rebuildCalendarDays() {
        YearMonth month = selectedMonth.getValue();
        if (month == null) {
            return;
        }

        LocalDate selected = selectedDate.getValue();
        LocalDate today = LocalDate.now();
        long defaultGoal = settingsRepository.getDailyStepTarget();

        Map<String, DailyMetricsEntity> entityMap = new HashMap<>();
        if (cachedMonthEntities != null) {
            for (DailyMetricsEntity entity : cachedMonthEntities) {
                entityMap.put(entity.getDate(), entity);
            }
        }

        LocalDate firstDay = month.atDay(1);
        // Monday-first offset: Monday (1) -> 0 ... Sunday (7) -> 6
        int firstDow = firstDay.getDayOfWeek().getValue();
        int leadingOffset = firstDow - 1;

        List<CalendarDayModel> days = new ArrayList<>();

        // Leading padding days from previous month
        YearMonth prevMonth = month.minusMonths(1);
        int prevMonthLength = prevMonth.lengthOfMonth();
        for (int i = leadingOffset - 1; i >= 0; i--) {
            int dayNum = prevMonthLength - i;
            LocalDate date = prevMonth.atDay(dayNum);
            boolean isToday = date.equals(today);
            boolean isSelected = date.equals(selected);
            days.add(new CalendarDayModel(date, dayNum, isToday, isSelected, false, false, 0L, defaultGoal));
        }

        // Current month days
        int daysInMonth = month.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            DailyMetricsEntity entity = entityMap.get(date.toString());
            long steps = (entity != null) ? entity.getSteps() : 0L;
            long goal = (entity != null && entity.getStepGoal() > 0) ? entity.getStepGoal() : defaultGoal;
            boolean isToday = date.equals(today);
            boolean isSelected = date.equals(selected);
            boolean isGoalReached = (steps >= goal && steps > 0);

            days.add(new CalendarDayModel(date, day, isToday, isSelected, isGoalReached, true, steps, goal));
        }

        // Trailing padding days from next month to complete the week
        int remainder = days.size() % 7;
        int trailingDays = (remainder == 0) ? 0 : 7 - remainder;
        YearMonth nextMonth = month.plusMonths(1);
        for (int d = 1; d <= trailingDays; d++) {
            LocalDate date = nextMonth.atDay(d);
            boolean isToday = date.equals(today);
            boolean isSelected = date.equals(selected);
            days.add(new CalendarDayModel(date, d, isToday, isSelected, false, false, 0L, defaultGoal));
        }

        setLiveValue(calendarDays, days);
    }

    public void previousMonth() {
        YearMonth current = selectedMonth.getValue();
        if (current == null) {
            current = YearMonth.now();
        }
        setSelectedMonth(current.minusMonths(1));
    }

    public void nextMonth() {
        YearMonth current = selectedMonth.getValue();
        if (current == null) {
            current = YearMonth.now();
        }
        setSelectedMonth(current.plusMonths(1));
    }

    public void setSelectedMonth(@NonNull YearMonth month) {
        Objects.requireNonNull(month);
        setLiveValue(selectedMonth, month);
    }

    public void selectDate(@NonNull LocalDate date) {
        Objects.requireNonNull(date);
        YearMonth monthOfDate = YearMonth.from(date);
        YearMonth currentMonth = selectedMonth.getValue();
        if (currentMonth != null && !currentMonth.equals(monthOfDate)) {
            setLiveValue(selectedMonth, monthOfDate);
        }
        setLiveValue(selectedDate, date);
    }

    public void setSelectedDate(@NonNull LocalDate date) {
        selectDate(date);
    }

    @NonNull
    public LiveData<YearMonth> getSelectedMonth() {
        return selectedMonth;
    }

    @NonNull
    public LiveData<LocalDate> getSelectedDate() {
        return selectedDate;
    }

    @NonNull
    public LiveData<List<CalendarDayModel>> getCalendarDays() {
        return calendarDays;
    }

    @NonNull
    public LiveData<DailyMetricsEntity> getSelectedDateMetrics() {
        return selectedDateMetrics;
    }

    @NonNull
    public LiveData<MetricsRepository.MonthlyAnalytics> getMonthlyAnalytics() {
        return monthlyAnalytics;
    }

    @NonNull
    public LiveData<SettingsRepository.UnitSystem> getUnitSystem() {
        return unitSystem;
    }

    private <T> void setLiveValue(MutableLiveData<T> liveData, @Nullable T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }

    private <T> void postLiveValue(MutableLiveData<T> liveData, @Nullable T value) {
        liveData.postValue(value);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        settingsRepository.unregisterListener(settingsChangeListener);
    }
}

