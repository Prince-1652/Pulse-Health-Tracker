package com.pulse.tracker.ui.common;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.BarBuffer;
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * Custom BarChartRenderer for MPAndroidChart that draws rounded top caps on each bar
 * using Path.addRoundRect() with top-left and top-right radii and flat bottom corners.
 */
public class RoundedBarChartRenderer extends BarChartRenderer {

    private final Path barPath = new Path();
    private final RectF barRect = new RectF();
    private float cornerRadius;

    public RoundedBarChartRenderer(BarDataProvider chart, ChartAnimator animator, ViewPortHandler viewPortHandler) {
        this(chart, animator, viewPortHandler, 8f);
    }

    public RoundedBarChartRenderer(BarDataProvider chart, ChartAnimator animator, ViewPortHandler viewPortHandler, float radiusDp) {
        super(chart, animator, viewPortHandler);
        this.cornerRadius = Utils.convertDpToPixel(radiusDp);
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
    }

    public float getCornerRadius() {
        return cornerRadius;
    }

    @Override
    protected void drawDataSet(Canvas c, IBarDataSet dataSet, int index) {
        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());

        mBarBorderPaint.setColor(dataSet.getBarBorderColor());
        mBarBorderPaint.setStrokeWidth(Utils.convertDpToPixel(dataSet.getBarBorderWidth()));

        final boolean drawBorder = dataSet.getBarBorderWidth() > 0.0f;

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        // Initialize and fill the bar buffer
        BarBuffer buffer = mBarBuffers[index];
        buffer.setPhases(phaseX, phaseY);
        buffer.setDataSet(index);
        buffer.setInverted(mChart.isInverted(dataSet.getAxisDependency()));
        buffer.setBarWidth(mChart.getBarData().getBarWidth());

        buffer.feed(dataSet);

        trans.pointValuesToPixel(buffer.buffer);

        final boolean isSingleColor = dataSet.getColors().size() == 1;

        if (isSingleColor) {
            mRenderPaint.setColor(dataSet.getColor());
        }

        for (int j = 0, pos = 0; j < buffer.size(); j += 4, pos++) {
            float left = buffer.buffer[j];
            float top = buffer.buffer[j + 1];
            float right = buffer.buffer[j + 2];
            float bottom = buffer.buffer[j + 3];

            if (!mViewPortHandler.isInBoundsLeft(right)) {
                continue;
            }

            if (!mViewPortHandler.isInBoundsRight(left)) {
                break;
            }

            if (!isSingleColor) {
                mRenderPaint.setColor(dataSet.getColor(pos));
            }

            // In screen coordinates: top is smaller Y (higher on screen), bottom is larger Y (lower on screen)
            float actualTop = Math.min(top, bottom);
            float actualBottom = Math.max(top, bottom);

            float width = right - left;
            float height = actualBottom - actualTop;

            if (height <= 0f) {
                continue;
            }

            // Cap the corner radius to half the bar width or the bar height
            float radius = Math.min(cornerRadius, Math.min(width / 2f, height));

            barRect.set(left, actualTop, right, actualBottom);
            barPath.reset();

            // Rounded top caps (top-left, top-right rounded; bottom-left, bottom-right flat)
            float[] radii = new float[]{
                    radius, radius, // Top-left
                    radius, radius, // Top-right
                    0f, 0f,         // Bottom-right
                    0f, 0f          // Bottom-left
            };

            barPath.addRoundRect(barRect, radii, Path.Direction.CW);
            c.drawPath(barPath, mRenderPaint);

            if (drawBorder) {
                c.drawPath(barPath, mBarBorderPaint);
            }
        }
    }
}

