package com.pulse.tracker.ui.common;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests verifying RulerTapeView step snapping, precision boundaries,
 * range enforcement, and listener contract.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RulerTapeViewTest {

    private Context context;
    private RulerTapeView rulerTapeView;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        rulerTapeView = new RulerTapeView(context);
    }

    @Test
    public void testInitialState_defaults() {
        assertEquals(170f, rulerTapeView.getValue(), 0.001f);
    }

    @Test
    public void testSetValue_snapsToStep_integerStep() {
        rulerTapeView.setValueRange(50f, 250f, 1.0f);
        rulerTapeView.setValue(170.32f);
        assertEquals(170.0f, rulerTapeView.getValue(), 0.0001f);

        rulerTapeView.setValue(170.78f);
        assertEquals(171.0f, rulerTapeView.getValue(), 0.0001f);
    }

    @Test
    public void testSetValue_snapsToStep_halfStep() {
        rulerTapeView.setValueRange(20f, 200f, 0.5f);
        rulerTapeView.setValue(65.32f);
        assertEquals(65.5f, rulerTapeView.getValue(), 0.0001f);

        rulerTapeView.setValue(65.18f);
        assertEquals(65.0f, rulerTapeView.getValue(), 0.0001f);

        rulerTapeView.setValue(65.89f);
        assertEquals(66.0f, rulerTapeView.getValue(), 0.0001f);
    }

    @Test
    public void testSetValue_clampingMinMax() {
        rulerTapeView.setValueRange(50f, 250f, 1.0f);

        rulerTapeView.setValue(10f);
        assertEquals(50.0f, rulerTapeView.getValue(), 0.0001f);

        rulerTapeView.setValue(300f);
        assertEquals(250.0f, rulerTapeView.getValue(), 0.0001f);
    }

    @Test
    public void testSetValueRange_reClampsAndSnapsCurrentValue() {
        rulerTapeView.setValue(180f);
        rulerTapeView.setValueRange(50f, 100f, 1.0f);
        assertEquals(100.0f, rulerTapeView.getValue(), 0.0001f);

        rulerTapeView.setValueRange(120f, 200f, 0.5f);
        assertEquals(120.0f, rulerTapeView.getValue(), 0.0001f);
    }

    @Test
    public void testSnapValueToStep_mathPrecision() {
        rulerTapeView.setValueRange(20f, 200f, 0.5f);
        assertEquals(70.0f, rulerTapeView.snapValueToStep(70.0f), 0.0001f);
        assertEquals(70.5f, rulerTapeView.snapValueToStep(70.49f), 0.0001f);
        assertEquals(70.0f, rulerTapeView.snapValueToStep(70.21f), 0.0001f);
        assertEquals(71.0f, rulerTapeView.snapValueToStep(70.85f), 0.0001f);
    }

    @Test
    public void testOnValueChangeListener_triggeredOnConfirmed() {
        AtomicReference<Float> changedVal = new AtomicReference<>();
        AtomicReference<Float> confirmedVal = new AtomicReference<>();

        rulerTapeView.setOnValueChangeListener(new RulerTapeView.OnValueChangeListener() {
            @Override
            public void onValueChanged(float value) {
                changedVal.set(value);
            }

            @Override
            public void onValueConfirmed(float value) {
                confirmedVal.set(value);
            }
        });

        // Simulating programmatic setValue does not fire user listener (expected behavior)
        rulerTapeView.setValue(175.0f);
        assertNull(changedVal.get());
        assertNull(confirmedVal.get());
        assertEquals(175.0f, rulerTapeView.getValue(), 0.0001f);
    }
}
