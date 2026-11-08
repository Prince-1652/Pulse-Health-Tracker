package com.pulse.tracker.ui.history;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.pulse.tracker.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter rendering 7-column monthly calendar day cells with squircle cards.
 * Supports distinct visual states for in-month vs out-of-month days, today highlight,
 * selected date squircle highlight, and accessible goal-reached checkmark indicators.
 */
public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    /**
     * Legacy DayItem supporting existing unit tests while extending the canonical CalendarDayModel.
     */
    public static class DayItem extends CalendarDayModel {
        public final int dayNumber;
        public final boolean isToday;
        public final boolean isGoalReached;
        public boolean isSelected;

        public DayItem(int dayNumber, boolean isToday, boolean isGoalReached, boolean isSelected) {
            super(null, dayNumber, isToday, isSelected, isGoalReached, dayNumber > 0, 0, 0);
            this.dayNumber = dayNumber;
            this.isToday = isToday;
            this.isGoalReached = isGoalReached;
            this.isSelected = isSelected;
        }

        public DayItem(CalendarDayModel model) {
            super(model.getDate(), model.getDayNumber(), model.isToday(), model.isSelected(),
                    model.isGoalReached(), model.isInMonth(), model.getSteps(), model.getStepGoal());
            this.dayNumber = model.getDayNumber();
            this.isToday = model.isToday();
            this.isGoalReached = model.isGoalReached();
            this.isSelected = model.isSelected();
        }
    }

    public interface OnDayClickListener {
        void onDayClick(@NonNull CalendarDayModel dayModel);
    }

    private final List<CalendarDayModel> days = new ArrayList<>();
    private OnDayClickListener onDayClickListener;

    public void setOnDayClickListener(@Nullable OnDayClickListener listener) {
        this.onDayClickListener = listener;
    }

    public void setDays(@Nullable List<? extends CalendarDayModel> newDays) {
        days.clear();
        if (newDays != null) {
            days.addAll(newDays);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CalendarDayModel item = days.get(position);
        holder.bind(item, onDayClickListener);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardCell;
        private final TextView textDay;
        private final ImageView goalIndicator;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCell = itemView.findViewById(R.id.card_day_cell);
            textDay = itemView.findViewById(R.id.text_day_number);
            goalIndicator = itemView.findViewById(R.id.view_goal_indicator);
        }

        public void bind(@NonNull CalendarDayModel item, @Nullable OnDayClickListener listener) {
            Context context = itemView.getContext();

            if (item.getDayNumber() <= 0) {
                cardCell.setVisibility(View.INVISIBLE);
                itemView.setOnClickListener(null);
                itemView.setContentDescription(null);
                return;
            }

            cardCell.setVisibility(View.VISIBLE);
            textDay.setText(String.valueOf(item.getDayNumber()));

            if (!item.isInMonth()) {
                // Out-of-month padding day: clearly visible light grayish tone on green background
                textDay.setTextColor(0xFF556850);
                textDay.setTypeface(null, android.graphics.Typeface.NORMAL);
                cardCell.setCardBackgroundColor(0x00000000);
                cardCell.setStrokeWidth(0);
                goalIndicator.setVisibility(View.INVISIBLE);
                itemView.setContentDescription(item.getDate() != null ? item.getDate().toString() : String.valueOf(item.getDayNumber()));
                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDayClick(item);
                    }
                });
                return;
            }

            // In-month days on green card: evaluate Selected vs Today vs Default
            if (item.isSelected()) {
                // Solid OLED Black squircle (#121212) with bold white number
                cardCell.setCardBackgroundColor(0xFF121212);
                cardCell.setStrokeWidth(0);
                textDay.setTextColor(0xFFFFFFFF);
                textDay.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (item.isToday()) {
                // Today: Translucent dark tint and border
                int strokePx = Math.max(1, Math.round(1.5f * context.getResources().getDisplayMetrics().density));
                cardCell.setCardBackgroundColor(0x22121212);
                cardCell.setStrokeWidth(strokePx);
                cardCell.setStrokeColor(0x55121212);
                textDay.setTextColor(0xFF121212);
                textDay.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                cardCell.setCardBackgroundColor(0x00000000);
                cardCell.setStrokeWidth(0);
                textDay.setTextColor(0xFF121212);
                textDay.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            // Goal-reached indicator: crisp Phosphor checkmark icon
            if (item.isGoalReached()) {
                goalIndicator.setVisibility(View.VISIBLE);
                int tint = item.isSelected() ? ContextCompat.getColor(context, R.color.pulse_green) : 0xFF121212;
                ImageViewCompat.setImageTintList(goalIndicator, ColorStateList.valueOf(tint));
                goalIndicator.setContentDescription(context.getString(R.string.history_goal_achieved));
            } else {
                goalIndicator.setVisibility(View.INVISIBLE);
                goalIndicator.setContentDescription(null);
            }

            // Accessibility content description
            StringBuilder a11y = new StringBuilder();
            a11y.append(item.getDayNumber());
            if (item.isToday()) {
                a11y.append(", Today");
            }
            if (item.isSelected()) {
                a11y.append(", Selected");
            }
            if (item.isGoalReached()) {
                a11y.append(", ").append(context.getString(R.string.history_goal_achieved));
            }
            itemView.setContentDescription(a11y.toString());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDayClick(item);
                }
            });
        }
    }
}
