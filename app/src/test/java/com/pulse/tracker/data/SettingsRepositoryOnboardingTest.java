package com.pulse.tracker.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.pulse.tracker.data.repository.SettingsRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Unit tests verifying SettingsRepository onboarding state persistence
 * and change listener behavior.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsRepositoryOnboardingTest {

    private SettingsRepository repository;
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        sharedPreferences = context.getSharedPreferences("test_pulse_onboarding_prefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        repository = new SettingsRepository(sharedPreferences);
    }

    @Test
    public void testOnboardingCompleted_defaultIsFalse() {
        assertFalse("New install should default to onboarding not completed", repository.hasCompletedOnboarding());
    }

    @Test
    public void testSetOnboardingCompleted_persistsTrueAndFalse() {
        repository.setOnboardingCompleted(true);
        assertTrue(repository.hasCompletedOnboarding());

        repository.setOnboardingCompleted(false);
        assertFalse(repository.hasCompletedOnboarding());
    }

    @Test
    public void testTrackingEnabled_persistsState() {
        assertFalse(repository.isTrackingEnabled());

        repository.setTrackingEnabled(true);
        assertTrue(repository.isTrackingEnabled());

        repository.setTrackingEnabled(false);
        assertFalse(repository.isTrackingEnabled());
    }

    @Test
    public void testOnboardingCompleted_notifiesRegisteredListener() {
        AtomicBoolean notified = new AtomicBoolean(false);
        SettingsRepository.OnSettingsChangeListener listener = key -> {
            if (SettingsRepository.KEY_ONBOARDING_COMPLETED.equals(key)) {
                notified.set(true);
            }
        };

        repository.registerListener(listener);
        repository.setOnboardingCompleted(true);

        assertTrue("Settings change listener should be notified of onboarding completion change", notified.get());
        repository.unregisterListener(listener);
    }
}

