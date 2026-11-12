package com.pulse.tracker.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.pulse.tracker.R;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Custom circular progress ring adhering to Pulse Bento UI system.
 * Renders neutral background track, animated progress arc, and centered percentage display
 * with full accessibility support.
 */
public class CircularProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stepTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint percentTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();
    private final RectF pillBounds = new RectF();

    private float progress = 0.0f;
    private float animatedProgress = 0.0f;
    private int max = 10000;
    private long currentSteps = 0L;
    private String subtitle = "of 10,000 target";
    private boolean centerPercentageOnly = true;

    @Nullable
    private ValueAnimator progressAnimator;

    public CircularProgressView(Context context) {
        this(context, null);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        float defaultStrokeWidth = dpToPx(16f); // Thick, modern progress ring per UI/UX redesign
        float defaultStepTextSize = spToPx(34f);
        float defaultSubtitleTextSize = spToPx(13f);
        float defaultPercentTextSize = spToPx(24f); // Bold 24sp dark text inside ring

        int defaultTrackColor = 0x26000000; // Semi-translucent dark green/black
        int defaultProgressColor = 0xFF121212; // High contrast dark
        int defaultStepTextColor = 0xFF121212;
        int defaultSubtitleTextColor = 0xFF333333;
        int defaultPercentTextColor = 0xFF121212;
        int defaultPercentPillColor = 0x26000000;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CircularProgressView, defStyleAttr, 0);
            defaultTrackColor = a.getColor(R.styleable.CircularProgressView_cpv_trackColor, defaultTrackColor);
            defaultProgressColor = a.getColor(R.styleable.CircularProgressView_cpv_progressColor, defaultProgressColor);
            defaultStrokeWidth = a.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, defaultStrokeWidth);
            defaultStepTextColor = a.getColor(R.styleable.CircularProgressView_cpv_stepTextColor, defaultStepTextColor);
            defaultSubtitleTextColor = a.getColor(R.styleable.CircularProgressView_cpv_subtitleTextColor, defaultSubtitleTextColor);
            defaultPercentTextColor = a.getColor(R.styleable.CircularProgressView_cpv_percentTextColor, defaultPercentTextColor);
            defaultPercentPillColor = a.getColor(R.styleable.CircularProgressView_cpv_percentPillColor, defaultPercentPillColor);
            defaultPercentTextSize = a.getDimension(R.styleable.CircularProgressView_cpv_percentTextSize, defaultPercentTextSize);
            defaultStepTextSize = a.getDimension(R.styleable.CircularProgressView_cpv_stepTextSize, defaultStepTextSize);
            defaultSubtitleTextSize = a.getDimension(R.styleable.CircularProgressView_cpv_subtitleTextSize, defaultSubtitleTextSize);
            progress = a.getFloat(R.styleable.CircularProgressView_cpv_progress, 0.0f);
            animatedProgress = progress;
            max = a.getInt(R.styleable.CircularProgressView_cpv_max, 10000);
            currentSteps = a.getInt(R.styleable.CircularProgressView_cpv_currentSteps, 0);
            String customSubtitle = a.getString(R.styleable.CircularProgressView_cpv_subtitle);
            if (customSubtitle != null) {
                subtitle = customSubtitle;
            }
            centerPercentageOnly = a.getBoolean(R.styleable.CircularProgressView_cpv_centerPercentageOnly, true);
            a.recycle();
        }

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(defaultTrackColor);
        trackPaint.setStrokeWidth(defaultStrokeWidth);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(defaultProgressColor);
        progressPaint.setStrokeWidth(defaultStrokeWidth);

        stepTextPaint.setColor(defaultStepTextColor);
        stepTextPaint.setTextSize(defaultStepTextSize);
        stepTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        stepTextPaint.setTextAlign(Paint.Align.CENTER);

        subtitleTextPaint.setColor(defaultSubtitleTextColor);
        subtitleTextPaint.setTextSize(defaultSubtitleTextSize);
        subtitleTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        subtitleTextPaint.setTextAlign(Paint.Align.CENTER);

        percentTextPaint.setColor(defaultPercentTextColor);
        percentTextPaint.setTextSize(defaultPercentTextSize);
        percentTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        percentTextPaint.setTextAlign(Paint.Align.CENTER);

        percentPillPaint.setColor(defaultPercentPillColor);
        percentPillPaint.setStyle(Paint.Style.FILL);

        updateAccessibilityDescription();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultDimension = (int) dpToPx(140f);
        int desiredWidth = defaultDimension + getPaddingLeft() + getPaddingRight();
        int desiredHeight = defaultDimension + getPaddingTop() + getPaddingBottom();

        int resolvedWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);

        int side = Math.min(resolvedWidth, resolvedHeight);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float paddingLeft = getPaddingLeft();
        float paddingRight = getPaddingRight();
        float paddingTop = getPaddingTop();
        float paddingBottom = getPaddingBottom();

        float contentWidth = w - paddingLeft - paddingRight;
        float contentHeight = h - paddingTop - paddingBottom;

        float maxStroke = Math.max(trackPaint.getStrokeWidth(), progressPaint.getStrokeWidth());
        float diameter = Math.min(contentWidth, contentHeight) - maxStroke;

        float cx = paddingLeft + contentWidth / 2f;
        float cy = paddingTop + contentHeight / 2f;

        arcBounds.set(
                cx - diameter / 2f,
                cy - diameter / 2f,
                cx + diameter / 2f,
                cy + diameter / 2f
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (arcBounds.isEmpty()) {
            return;
        }

        // 1. Draw background neutral track (full circle ring)
        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint);

        // 2. Draw foreground progress arc (clamped to 100% / 360 degrees visually)
        float clampedProgress = Math.min(1.0f, Math.max(0.0f, animatedProgress));
        float sweepAngle = clampedProgress * 360f;
        if (sweepAngle > 0.5f) {
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);
        }

        float centerX = arcBounds.centerX();
        float centerY = arcBounds.centerY();

        int displayPercent = max > 0 ? (int) Math.round(animatedProgress * 100f) : 0;
        displayPercent = Math.max(0, Math.min(999, displayPercent));
        String percentText = displayPercent + "%";

        if (centerPercentageOnly) {
            // Draw ONLY the percentage text (e.g. "85%") in the exact center of the ring, centered vertically and horizontally using FontMetrics
            Paint.FontMetrics pctFm = percentTextPaint.getFontMetrics();
            float pctBaseline = centerY - (pctFm.ascent + pctFm.descent) / 2f;
            canvas.drawText(percentText, centerX, pctBaseline, percentTextPaint);
        } else {
            // Legacy multi-line block (step count, subtitle, and pill)
            String stepText = NumberFormat.getIntegerInstance(Locale.getDefault()).format(currentSteps);

            Paint.FontMetrics stepFm = stepTextPaint.getFontMetrics();
            Paint.FontMetrics subFm = subtitleTextPaint.getFontMetrics();
            Paint.FontMetrics pctFm = percentTextPaint.getFontMetrics();

            float stepHeight = stepFm.descent - stepFm.ascent;
            boolean hasSubtitle = (subtitle != null && !subtitle.isEmpty());
            float subHeight = hasSubtitle ? (subFm.descent - subFm.ascent) : 0f;

            float pillHeight = dpToPx(22f);
            float pillPaddingH = dpToPx(10f);
            float textWidth = percentTextPaint.measureText(percentText);
            float pillWidth = textWidth + pillPaddingH * 2f;
            float pillRadius = pillHeight / 2f;

            float spacing1 = hasSubtitle ? dpToPx(4f) : 0f;
            float spacing2 = dpToPx(8f);

            float totalBlockHeight = stepHeight + spacing1 + subHeight + spacing2 + pillHeight;

            float stepBaseline = centerY - (totalBlockHeight / 2f) - stepFm.ascent;
            canvas.drawText(stepText, centerX, stepBaseline, stepTextPaint);

            float nextY = stepBaseline + stepFm.descent;
            if (hasSubtitle) {
                float subBaseline = nextY + spacing1 - subFm.ascent;
                canvas.drawText(subtitle, centerX, subBaseline, subtitleTextPaint);
                nextY = subBaseline + subFm.descent;
            }

            float pillTop = nextY + spacing2;
            float pillCenterY = pillTop + pillHeight / 2f;
            float pctBaseline = pillCenterY - (pctFm.ascent + pctFm.descent) / 2f;

            pillBounds.set(
                    centerX - pillWidth / 2f,
                    pillTop,
                    centerX + pillWidth / 2f,
                    pillTop + pillHeight
            );
            canvas.drawRoundRect(pillBounds, pillRadius, pillRadius, percentPillPaint);
            canvas.drawText(percentText, centerX, pctBaseline, percentTextPaint);
        }
    }

    /**
     * Animate progress smoothly with FastOutSlowInInterpolator over durationMs from current progress.
     */
    public void animateProgress(float targetProgress, long durationMs) {
        animateProgress(this.animatedProgress, targetProgress, durationMs);
    }

    /**
     * Animate progress smoothly with FastOutSlowInInterpolator over durationMs from specified start progress.
     */
    public void animateProgress(float fromProgress, float targetProgress, long durationMs) {
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }

        this.progress = targetProgress;
        this.animatedProgress = fromProgress;

        if (durationMs <= 0) {
            this.animatedProgress = targetProgress;
            invalidate();
            updateAccessibilityDescription();
            return;
        }

        progressAnimator = ValueAnimator.ofFloat(fromProgress, targetProgress);
        progressAnimator.setDuration(durationMs);
        progressAnimator.setInterpolator(new FastOutSlowInInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            animatedProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
        updateAccessibilityDescription();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        this.animatedProgress = progress;
        this.currentSteps = Math.max(0, Math.round(progress * max));
        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }
        invalidate();
        updateAccessibilityDescription();
    }

    public float getProgress() {
        return progress;
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        updateProgressRatio();
        if (progressAnimator == null || !progressAnimator.isRunning()) {
            this.animatedProgress = this.progress;
        }
        invalidate();
        updateAccessibilityDescription();
    }

    public int getMax() {
        return max;
    }

    public void setCurrentSteps(long steps) {
        this.currentSteps = Math.max(0, steps);
        updateProgressRatio();
        if (progressAnimator == null || !progressAnimator.isRunning()) {
            this.animatedProgress = this.progress;
        }
        invalidate();
        updateAccessibilityDescription();
    }

    public long getCurrentSteps() {
        return currentSteps;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        invalidate();
        updateAccessibilityDescription();
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setCenterPercentageOnly(boolean centerPercentageOnly) {
        this.centerPercentageOnly = centerPercentageOnly;
        invalidate();
    }

    public boolean isCenterPercentageOnly() {
        return centerPercentageOnly;
    }

    public void setPercentTextSize(float sizePx) {
        percentTextPaint.setTextSize(sizePx);
        invalidate();
    }

    public void setStepTextColor(@ColorInt int color) {
        stepTextPaint.setColor(color);
        invalidate();
    }

    public void setSubtitleTextColor(@ColorInt int color) {
        subtitleTextPaint.setColor(color);
        invalidate();
    }

    public void setPercentTextColor(@ColorInt int color) {
        percentTextPaint.setColor(color);
        invalidate();
    }

    public void setPercentPillColor(@ColorInt int color) {
        percentPillPaint.setColor(color);
        invalidate();
    }

    public void setTrackColor(@ColorInt int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    public void setProgressColor(@ColorInt int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    public void setStrokeWidth(float strokeWidthPx) {
        trackPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeWidth(strokeWidthPx);
        requestLayout();
        invalidate();
    }

    private void updateProgressRatio() {
        if (max > 0) {
            this.progress = (float) currentSteps / max;
        } else {
            this.progress = 0f;
        }
    }

    private void updateAccessibilityDescription() {
        String formattedSteps = NumberFormat.getIntegerInstance(Locale.getDefault()).format(currentSteps);
        String formattedMax = NumberFormat.getIntegerInstance(Locale.getDefault()).format(max);
        int percent = (int) Math.round(Math.min(100f, Math.max(0f, progress * 100f)));

        String description = getContext().getString(
                R.string.today_hero_a11y_format,
                formattedSteps,
                formattedMax,
                percent
        );
        setContentDescription(description);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(CircularProgressView.class.getName());
        info.setContentDescription(getContentDescription());
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
