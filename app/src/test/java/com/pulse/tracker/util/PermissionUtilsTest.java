package com.pulse.tracker.util;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests verifying PermissionUtils sensor detection, multi-API permission resolution,
 * and App Settings routing.
 */
@RunWith(RobolectricTestRunner.class)
public class PermissionUtilsTest {

    private Context baseContext;

    @Before
    public void setUp() {
        baseContext = ApplicationProvider.getApplicationContext();
    }

    private Context createPermissionContext(final Set<String> grantedPermissions) {
        return new ContextWrapper(baseContext) {
            @Override
            public int checkPermission(String permission, int pid, int uid) {
                if (grantedPermissions != null && grantedPermissions.contains(permission)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                return PackageManager.PERMISSION_DENIED;
            }
        };
    }

    @Test
    public void testCreateAppSettingsIntent_producesCorrectUriAndAction() {
        Intent intent = PermissionUtils.createAppSettingsIntent(baseContext);

        assertNotNull(intent);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());

        Uri data = intent.getData();
        assertNotNull(data);
        assertEquals("package", data.getScheme());
        assertEquals(baseContext.getPackageName(), data.getSchemeSpecificPart());
    }

    @Test
    @Config(sdk = 28)
    public void testActivityRecognition_onApi28_returnsTrueWithoutPrompt() {
        // Prior to Android Q (API 29), activity recognition is not a runtime permission
        Context deniedContext = createPermissionContext(new HashSet<>());
        assertTrue("API 28 should not require runtime activity recognition permission",
                PermissionUtils.hasActivityRecognitionPermission(deniedContext));
    }

    @Test
    @Config(sdk = 34)
    public void testActivityRecognition_onApi34_reflectsPermissionState() {
        Set<String> perms = new HashSet<>();
        Context deniedContext = createPermissionContext(perms);
        assertFalse(PermissionUtils.hasActivityRecognitionPermission(deniedContext));

        perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        Context grantedContext = createPermissionContext(perms);
        assertTrue(PermissionUtils.hasActivityRecognitionPermission(grantedContext));
    }

    @Test
    @Config(sdk = 30)
    public void testNotificationPermission_onApi30_returnsTrueWithoutPrompt() {
        // Prior to Android 13 (API 33), notification permission is granted at install
        Context deniedContext = createPermissionContext(new HashSet<>());
        assertTrue("API 30 should not require runtime notification permission",
                PermissionUtils.hasNotificationPermission(deniedContext));
    }

    @Test
    @Config(sdk = 34)
    public void testNotificationPermission_onApi34_reflectsPermissionState() {
        Set<String> perms = new HashSet<>();
        Context deniedContext = createPermissionContext(perms);
        assertFalse(PermissionUtils.hasNotificationPermission(deniedContext));

        perms.add(Manifest.permission.POST_NOTIFICATIONS);
        Context grantedContext = createPermissionContext(perms);
        assertTrue(PermissionUtils.hasNotificationPermission(grantedContext));
    }

    @Test
    @Config(sdk = 34)
    public void testGetPermissionsToRequest_onApi34_includesBothWhenDenied() {
        Set<String> perms = new HashSet<>();
        Context deniedContext = createPermissionContext(perms);

        String[] permsToRequest = PermissionUtils.getPermissionsToRequest(deniedContext);
        assertEquals(2, permsToRequest.length);

        // Grant activity recognition
        perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        Context partialContext = createPermissionContext(perms);
        String[] remaining = PermissionUtils.getPermissionsToRequest(partialContext);
        assertEquals(1, remaining.length);
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, remaining[0]);
    }

    @Test
    @Config(sdk = 30)
    public void testGetPermissionsToRequest_onApi30_neverIncludesNotification() {
        Set<String> perms = new HashSet<>();
        Context deniedContext = createPermissionContext(perms);

        String[] permsToRequest = PermissionUtils.getPermissionsToRequest(deniedContext);
        assertEquals(1, permsToRequest.length);
        assertEquals(Manifest.permission.ACTIVITY_RECOGNITION, permsToRequest[0]);
    }

    @Test
    @Config(sdk = 28)
    public void testGetPermissionsToRequest_onApi28_returnsEmpty() {
        Context deniedContext = createPermissionContext(new HashSet<>());
        String[] perms = PermissionUtils.getPermissionsToRequest(deniedContext);
        assertEquals(0, perms.length);
    }

    @Test
    public void testStepCounterSensorAvailable_nullSensorManager_returnsFalse() {
        Context noSensorServiceContext = new ContextWrapper(baseContext) {
            @Override
            public Object getSystemService(String name) {
                if (Context.SENSOR_SERVICE.equals(name)) {
                    return null;
                }
                return super.getSystemService(name);
            }
        };

        assertFalse(PermissionUtils.isStepCounterSensorAvailable(noSensorServiceContext));
    }

    @Test
    public void testStepCounterSensorAvailable_checksDefaultSensor() {
        // Base context in Robolectric without step sensor returns false
        assertFalse(PermissionUtils.isStepCounterSensorAvailable(baseContext));
    }
}
