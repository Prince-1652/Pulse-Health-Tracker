package com.pulse.tracker.ui.history;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Model representing a single cell in the 7-column monthly calendar grid.
 * Tracks calendar date, in-month state, today marker, selection state, and goal achievement snapshot.
 */
public class CalendarDayModel {

    @Nullable
    private final LocalDate date;
    private final int dayNumber;
    private final boolean isToday;
    private final boolean isSelected;
    private final boolean isGoalReached;
    private final boolean isInMonth;
    private final long steps;
    private final long stepGoal;

    public CalendarDayModel(@Nullable LocalDate date,
                            int dayNumber,
                            boolean isToday,
                            boolean isSelected,
                            boolean isGoalReached,
                            boolean isInMonth,
                            long steps,
                            long stepGoal) {
        this.date = date;
        this.dayNumber = dayNumber;
        this.isToday = isToday;
        this.isSelected = isSelected;
        this.isGoalReached = isGoalReached;
        this.isInMonth = isInMonth;
        this.steps = steps;
        this.stepGoal = stepGoal;
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public boolean isToday() {
        return isToday;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public boolean isGoalReached() {
        return isGoalReached;
    }

    public boolean isInMonth() {
        return isInMonth;
    }

    public long getSteps() {
        return steps;
    }

    public long getStepGoal() {
        return stepGoal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarDayModel that = (CalendarDayModel) o;
        return dayNumber == that.dayNumber &&
                isToday == that.isToday &&
                isSelected == that.isSelected &&
                isGoalReached == that.isGoalReached &&
                isInMonth == that.isInMonth &&
                steps == that.steps &&
                stepGoal == that.stepGoal &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, dayNumber, isToday, isSelected, isGoalReached, isInMonth, steps, stepGoal);
    }

    @Override
    public String toString() {
        return "CalendarDayModel{" +
                "date=" + date +
                ", dayNumber=" + dayNumber +
                ", isToday=" + isToday +
                ", isSelected=" + isSelected +
                ", isGoalReached=" + isGoalReached +
                ", isInMonth=" + isInMonth +
                ", steps=" + steps +
                ", stepGoal=" + stepGoal +
                '}';
    }
}

