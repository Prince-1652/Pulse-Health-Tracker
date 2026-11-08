package com.pulse.tracker.ui;

import com.pulse.tracker.ui.history.CalendarDayAdapter;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.*;

/**
 * Unit tests verifying calendar calculations and item model behavior.
 */
public class CalendarDayAdapterTest {

    @Test
    public void testDayItemCreation() {
        CalendarDayAdapter.DayItem item = new CalendarDayAdapter.DayItem(15, false, true, false);
        assertEquals(15, item.dayNumber);
        assertFalse(item.isToday);
        assertTrue(item.isGoalReached);
        assertFalse(item.isSelected);
    }

    @Test
    public void testPaddingCellIdentification() {
        CalendarDayAdapter.DayItem paddingCell = new CalendarDayAdapter.DayItem(0, false, false, false);
        assertEquals(0, paddingCell.dayNumber);
    }

    @Test
    public void testMonthDaysCalculation() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.FEBRUARY, 1);
        int daysInFeb2026 = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        assertEquals(28, daysInFeb2026);

        cal.set(2024, Calendar.FEBRUARY, 1);
        int daysInFeb2024LeapYear = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        assertEquals(29, daysInFeb2024LeapYear);
    }
}

