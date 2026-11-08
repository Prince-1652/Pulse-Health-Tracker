package com.pulse.tracker.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pulse.tracker.R;
import com.pulse.tracker.util.HapticHelper;

/**
 * Custom measuring tape / horizontal ruler slider view with mechanical haptic ticks,
 * momentum scrolling, auto-snapping, and precision center indicator needle.
 */
public class RulerTapeView extends View {

    public interface OnValueChangeListener {
        void onValueChanged(float value);
        void onValueConfirmed(float value);
    }

    private float minValue = 50f;
    private float maxValue = 250f;
    private float currentValue = 170f;
    private float step = 1.0f;

    private int indicatorColor = 0xFF7CB5FF;
    private float tickSpacingPx;
    private float majorTickHeight;
    private float mediumTickHeight;
    private float minorTickHeight;

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mediumTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path needleHeadPath = new Path();

    private GestureDetector gestureDetector;
    private OverScroller scroller;
    private ValueAnimator snapAnimator;
    private boolean isFlinging = false;

    private OnValueChangeListener listener;
    private int lastReportedTick = -1;
    private Vibrator vibrator;
    private long lastHapticTimestamp = 0L;

    public RulerTapeView(Context context) {
        super(context);
        init(context, null);
    }

    public RulerTapeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RulerTapeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setHapticFeedbackEnabled(true);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        float density = getResources().getDisplayMetrics().density;
        tickSpacingPx = 13f * density;
        majorTickHeight = 30f * density;
        mediumTickHeight = 20f * density;
        minorTickHeight = 12f * density;

        // Base / minor ticks (bright visible slate-silver)
        tickPaint.setColor(0xFF8A8A96);
        tickPaint.setStrokeWidth(1.6f * density);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        // Medium ticks (every 5 units - bright high-contrast silver)
        mediumTickPaint.setColor(0xFFD0D0D8);
        mediumTickPaint.setStrokeWidth(2.2f * density);
        mediumTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // Major ticks (every 10 units - pure high-contrast white)
        majorTickPaint.setColor(0xFFFFFFFF);
        majorTickPaint.setStrokeWidth(2.8f * density);
        majorTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // Baseline (crisp visible separator line)
        baseLinePaint.setColor(0xFF50505A);
        baseLinePaint.setStrokeWidth(1.8f * density);

        // Numerical markings (bright legible labels)
        textPaint.setColor(0xFFE4E4EC);
        textPaint.setTextSize(12f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));

        // Center needle
        indicatorPaint.setColor(indicatorColor);
        indicatorPaint.setStrokeWidth(3.0f * density);
        indicatorPaint.setStrokeCap(Paint.Cap.ROUND);

        indicatorHeadPaint.setColor(indicatorColor);
        indicatorHeadPaint.setStyle(Paint.Style.FILL);

        scroller = new OverScroller(context);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                isFlinging = false;
                if (snapAnimator != null && snapAnimator.isRunning()) {
                    snapAnimator.cancel();
                }
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                float deltaVal = (distanceX / tickSpacingPx) * step;
                updateCurrentValue(currentValue + deltaVal, true);
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (snapAnimator != null && snapAnimator.isRunning()) {
                    snapAnimator.cancel();
                }
                int startX = Math.round(currentValue * tickSpacingPx / step);
                int minX = Math.round(minValue * tickSpacingPx / step);
                int maxX = Math.round(maxValue * tickSpacingPx / step);
                isFlinging = true;
                scroller.fling(startX, 0, (int) -velocityX, 0, minX, maxX, 0, 0);
                postInvalidateOnAnimation();
                return true;
            }
        });
    }

    public void setIndicatorColor(int color) {
        this.indicatorColor = color;
        this.indicatorPaint.setColor(color);
        this.indicatorHeadPaint.setColor(color);
        invalidate();
    }

    public void setValueRange(float min, float max, float step) {
        if (snapAnimator != null && snapAnimator.isRunning()) {
            snapAnimator.cancel();
        }
        isFlinging = false;
        scroller.forceFinished(true);
        this.minValue = min;
        this.maxValue = max;
        this.step = Math.max(0.1f, step);
        if (currentValue < min) currentValue = min;
        if (currentValue > max) currentValue = max;
        currentValue = snapValueToStep(currentValue);
        lastReportedTick = Math.round(currentValue / this.step);
        invalidate();
    }

    public void setValue(float value) {
        if (snapAnimator != null && snapAnimator.isRunning()) {
            snapAnimator.cancel();
        }
        isFlinging = false;
        scroller.forceFinished(true);
        float clamped = Math.max(minValue, Math.min(maxValue, value));
        float snapped = snapValueToStep(clamped);
        if (Math.abs(currentValue - snapped) > 0.0001f) {
            currentValue = snapped;
            lastReportedTick = Math.round(currentValue / step);
            invalidate();
        }
    }

    public float getValue() {
        return currentValue;
    }

    public void setOnValueChangeListener(@Nullable OnValueChangeListener listener) {
        this.listener = listener;
    }

    public float snapValueToStep(float value) {
        long steps = Math.round((double) value / (double) step);
        double snapped = steps * (double) step;
        if (step >= 1.0f && Math.floor(step) == step) {
            snapped = Math.round(snapped);
        } else {
            snapped = Math.round(snapped * 100.0) / 100.0;
        }
        float target = (float) snapped;
        return Math.max(minValue, Math.min(maxValue, target));
    }

    private void updateCurrentValue(float newValue, boolean fromUser) {
        float clamped = Math.max(minValue, Math.min(maxValue, newValue));
        if (Math.abs(currentValue - clamped) > 0.0001f) {
            currentValue = clamped;

            int currentTick = Math.round(currentValue / step);
            if (fromUser && currentTick != lastReportedTick) {
                lastReportedTick = currentTick;
                triggerHapticTick();
                if (listener != null) {
                    listener.onValueChanged(currentValue);
                }
            }
            invalidate();
        }
    }

    private void triggerHapticTick() {
        triggerHapticTick(false);
    }

    private void triggerHapticTick(boolean force) {
        HapticHelper.performTick(getContext(), force);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            android.view.ViewParent parent = getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true);
                parent = parent.getParent();
            }
            disableParentViewPagerSwipe(false);
        }

        gestureDetector.onTouchEvent(event);

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            android.view.ViewParent parent = getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false);
                parent = parent.getParent();
            }
            disableParentViewPagerSwipe(true);
            if (!isFlinging && scroller.isFinished()) {
                snapToNearestStep();
            }
        }

        return true;
    }

    private void disableParentViewPagerSwipe(boolean enableSwipe) {
        Context ctx = getContext();
        if (ctx instanceof com.pulse.tracker.MainActivity) {
            ((com.pulse.tracker.MainActivity) ctx).setSwipeEnabled(enableSwipe);
        } else if (ctx instanceof com.pulse.tracker.ui.onboarding.OnboardingActivity) {
            ((com.pulse.tracker.ui.onboarding.OnboardingActivity) ctx).setSwipeEnabled(enableSwipe);
        } else if (ctx instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) ctx).getBaseContext();
            if (base instanceof com.pulse.tracker.MainActivity) {
                ((com.pulse.tracker.MainActivity) base).setSwipeEnabled(enableSwipe);
            } else if (base instanceof com.pulse.tracker.ui.onboarding.OnboardingActivity) {
                ((com.pulse.tracker.ui.onboarding.OnboardingActivity) base).setSwipeEnabled(enableSwipe);
            }
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()) {
            float val = (scroller.getCurrX() * step) / tickSpacingPx;
            updateCurrentValue(val, true);
            postInvalidateOnAnimation();
        } else if (isFlinging) {
            isFlinging = false;
            scroller.forceFinished(true);
            snapToNearestStep();
        }
    }

    private void snapToNearestStep() {
        final float target = snapValueToStep(currentValue);

        if (snapAnimator != null && snapAnimator.isRunning()) {
            snapAnimator.cancel();
        }

        if (Math.abs(currentValue - target) < 0.0001f) {
            currentValue = target;
            lastReportedTick = Math.round(target / step);
            invalidate();
            HapticHelper.performMilestone(getContext());
            if (listener != null) {
                listener.onValueChanged(target);
                listener.onValueConfirmed(target);
            }
            return;
        }

        snapAnimator = ValueAnimator.ofFloat(currentValue, target);
        snapAnimator.setDuration(120);
        snapAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        snapAnimator.addUpdateListener(anim -> {
            currentValue = (float) anim.getAnimatedValue();
            invalidate();
        });
        snapAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean isCanceled = false;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                isCanceled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (isCanceled) return;
                currentValue = target;
                lastReportedTick = Math.round(target / step);
                HapticHelper.performMilestone(getContext());
                if (listener != null) {
                    listener.onValueChanged(target);
                    listener.onValueConfirmed(target);
                }
                invalidate();
            }
        });
        snapAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerX = width / 2.0f;
        float density = getResources().getDisplayMetrics().density;
        float baseLineY = height - (16f * density);

        // 1. Draw horizontal ruler baseline
        canvas.drawLine(0, baseLineY, width, baseLineY, baseLinePaint);

        // 2. Compute range of visible ticks
        float visibleUnits = (centerX / tickSpacingPx) * step;
        int minTickIdx = Math.round(minValue / step);
        int maxTickIdx = Math.round(maxValue / step);

        int startTickIdx = (int) Math.floor((currentValue - visibleUnits) / step) - 1;
        int endTickIdx = (int) Math.ceil((currentValue + visibleUnits) / step) + 1;

        startTickIdx = Math.max(minTickIdx, startTickIdx);
        endTickIdx = Math.min(maxTickIdx, endTickIdx);

        // 3. Draw tick marks & numbers with high contrast
        for (int i = startTickIdx; i <= endTickIdx; i++) {
            float tickVal = (float) (Math.round(i * (double) step * 100.0) / 100.0);
            float x = centerX + ((tickVal - currentValue) / step) * tickSpacingPx;

            boolean isWholeNumber = Math.abs(tickVal - Math.round(tickVal)) < 0.001f;
            int wholeVal = Math.round(tickVal);

            boolean isMajor = isWholeNumber && (wholeVal % 10 == 0);
            boolean isMedium = isWholeNumber && (wholeVal % 5 == 0 && !isMajor);

            float tickH = isMajor ? majorTickHeight : (isMedium ? mediumTickHeight : minorTickHeight);
            Paint paint = isMajor ? majorTickPaint : (isMedium ? mediumTickPaint : tickPaint);

            canvas.drawLine(x, baseLineY, x, baseLineY - tickH, paint);

            // Major numeric label
            if (isMajor) {
                canvas.drawText(String.valueOf(wholeVal), x, baseLineY + (13f * density), textPaint);
            }
        }

        // 4. Center indicator needle (sharp precision pointer with top inverted triangle)
        float needleTop = baseLineY - majorTickHeight - (6f * density);
        canvas.drawLine(centerX, baseLineY + (4f * density), centerX, needleTop, indicatorPaint);

        // Needle top pointer head (triangle pointing down)
        needleHeadPath.reset();
        needleHeadPath.moveTo(centerX - (4f * density), needleTop - (6f * density));
        needleHeadPath.lineTo(centerX + (4f * density), needleTop - (6f * density));
        needleHeadPath.lineTo(centerX, needleTop + (2f * density));
        needleHeadPath.close();
        canvas.drawPath(needleHeadPath, indicatorHeadPaint);
    }
}
