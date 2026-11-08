package com.pulse.tracker.receiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying BootReceiver respects user tracking preference and ignores irrelevant broadcasts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BootReceiverTest {

    private Context baseContext;
    private SettingsRepository settingsRepository;
    private BootReceiver receiver;
    private List<Intent> startedServices;
    private Context trackingContext;

    @Before
    public void setUp() {
        baseContext = ApplicationProvider.getApplicationContext();
        settingsRepository = SettingsRepository.getInstance(baseContext);
        receiver = new BootReceiver();
        startedServices = new ArrayList<>();

        trackingContext = new ContextWrapper(baseContext) {
            @Override
            public ComponentName startForegroundService(Intent service) {
                startedServices.add(service);
                return service.getComponent();
            }

            @Override
            public ComponentName startService(Intent service) {
                startedServices.add(service);
                return service.getComponent();
            }
        };
    }

    @Test
    public void onReceive_whenTrackingDisabled_doesNotStartService() {
        settingsRepository.setTrackingEnabled(false);

        Intent bootIntent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(trackingContext, bootIntent);

        assertTrue("No service should be started when tracking is disabled", startedServices.isEmpty());
    }

    @Test
    public void onReceive_whenNonBootAction_doesNotStartService() {
        settingsRepository.setTrackingEnabled(true);

        Intent otherIntent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        receiver.onReceive(trackingContext, otherIntent);

        assertTrue("No service should be started for non-boot action", startedServices.isEmpty());
    }

    @Test
    public void onReceive_whenNullIntent_doesNotCrash() {
        receiver.onReceive(trackingContext, null);
        assertTrue(startedServices.isEmpty());
    }
}
