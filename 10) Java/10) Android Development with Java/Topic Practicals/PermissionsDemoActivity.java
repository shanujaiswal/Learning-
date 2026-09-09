/*
 * PermissionsDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The PermissionsDemoActivity
 * class below is meant to be dropped into app/src/main/java/<your/package>/ as its own
 * .java file in a project that also declares the relevant <uses-permission> entries in
 * AndroidManifest.xml (sketched at the bottom of this file in a comment), with a matching
 * minimal layout.
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/08 Android Permissions System.md
 *
 * Covers:
 *     1. Runtime permission request for a single dangerous permission (CAMERA) using
 *        ActivityResultContracts.RequestPermission(), registered unconditionally in a
 *        field initializer (never inside a click handler)
 *     2. shouldShowRequestPermissionRationale() usage to distinguish "never asked yet" /
 *        "asked once, denied" from "permanently denied", including a Settings redirect
 *     3. Checking/requesting MULTIPLE permissions at once (CAMERA + RECORD_AUDIO) via
 *        ActivityResultContracts.RequestMultiplePermissions(), iterating the resulting
 *        Map<String, Boolean> individually rather than assuming all-or-nothing
 *     4. POST_NOTIFICATIONS handling for API 33+
 */

package com.example.advancedcomponents;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Map;

/**
 * PermissionsDemoActivity -- demonstrates the modern, type-safe way to request dangerous
 * runtime permissions, both singly and in a batch, plus the rationale/permanent-denial
 * handling every production app needs.
 */
public class PermissionsDemoActivity extends AppCompatActivity {

    private static final String TAG = "PermissionsDemoActivity";
    private static final String PREFS_NAME = "permission_prefs";
    private static final String KEY_CAMERA_ASKED_BEFORE = "camera_asked_before";

    // Used to distinguish "permanently denied" from "never asked" -- shouldShowRequestPermissionRationale()
    // returns false in BOTH cases, so it cannot be used alone to tell them apart; we track our
    // own "have we asked before" flag in SharedPreferences to fill that gap.
    private SharedPreferences prefs;

    // --- Single-permission launcher -------------------------------------------------------
    // MUST be registered unconditionally before the Activity reaches STARTED (a field
    // initializer, or an unconditional call in onCreate()) -- registering it conditionally
    // (e.g. inside a click handler) throws IllegalStateException.
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "CAMERA permission granted");
                    startCameraFeature();
                } else {
                    Log.d(TAG, "CAMERA permission denied");
                    handleCameraPermissionDenied();
                }
            });

    // --- Multiple-permissions launcher -----------------------------------------------------
    private final ActivityResultLauncher<String[]> multiPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                // The callback receives a Map<String, Boolean> -- always check EACH permission
                // individually rather than assuming all-or-nothing, since the user can grant
                // some and deny others in the same batch dialog.
                boolean cameraGranted = Boolean.TRUE.equals(results.get(Manifest.permission.CAMERA));
                boolean audioGranted = Boolean.TRUE.equals(results.get(Manifest.permission.RECORD_AUDIO));

                for (Map.Entry<String, Boolean> entry : results.entrySet()) {
                    Log.d(TAG, entry.getKey() + " -> " + entry.getValue());
                }

                if (cameraGranted && audioGranted) {
                    startVideoRecordingFeature();
                } else {
                    Toast.makeText(this, "Camera and microphone are both required for video recording",
                            Toast.LENGTH_LONG).show();
                }
            });

    // --- POST_NOTIFICATIONS launcher (API 33+) ---------------------------------------------
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Notifications will not be shown", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions_demo);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Button cameraButton = findViewById(R.id.buttonRequestCamera);
        cameraButton.setOnClickListener(v -> checkAndRequestCameraPermission());

        Button multiButton = findViewById(R.id.buttonRequestMultiple);
        multiButton.setOnClickListener(v -> checkAndRequestVideoPermissions());

        // Request POST_NOTIFICATIONS in context, right when it's actually needed -- here we
        // do it eagerly at launch purely for demonstration; a real app should request it just
        // before the first notification it actually wants to show.
        requestNotificationPermissionIfNeeded();
    }

    /**
     * The full modern pattern: check current grant state, and if not granted, decide whether
     * to show an explanatory rationale UI first (when the user has denied once but not
     * permanently) before firing the system dialog.
     */
    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraFeature();
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // User denied once before (but not permanently) -- show an explanation UI,
            // THEN request again after they acknowledge it. A bare system dialog with no
            // context is the single biggest driver of "Deny".
            showRationaleThenRequest();
        } else {
            // Either the very first request ever (nothing to explain yet), OR a permanent
            // "Don't ask again" denial -- shouldShowRequestPermissionRationale() returns
            // false in BOTH cases, so we consult our own "asked before" flag to tell them apart.
            boolean askedBefore = prefs.getBoolean(KEY_CAMERA_ASKED_BEFORE, false);
            if (askedBefore) {
                // We asked before and got neither a grant nor a rationale-eligible denial ->
                // this is a permanent denial; the system dialog would auto-deny silently, so
                // send the user to app Settings instead.
                offerAppSettingsRedirect();
            } else {
                prefs.edit().putBoolean(KEY_CAMERA_ASKED_BEFORE, true).apply();
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        }
    }

    private void showRationaleThenRequest() {
        // In a real app this would be an AlertDialog explaining WHY the camera is needed;
        // simplified here to a Toast plus an immediate re-request for illustration.
        Toast.makeText(this, "Camera access is needed to scan documents", Toast.LENGTH_SHORT).show();
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void handleCameraPermissionDenied() {
        // Design the app to DEGRADE GRACEFULLY -- disable just the dependent feature with an
        // explanatory UI state, rather than crashing or blocking the entire app.
        Toast.makeText(this, "Camera feature disabled until permission is granted", Toast.LENGTH_SHORT).show();
    }

    /**
     * Sends the user to the app's own detail screen in system Settings -- the only way to
     * recover from a permanent "Don't ask again" denial, since the runtime dialog itself
     * will no longer appear.
     */
    private void offerAppSettingsRedirect() {
        Toast.makeText(this, "Camera permission permanently denied -- opening Settings", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    /**
     * Checking multiple permissions example: only launches the batch request for whichever
     * of CAMERA/RECORD_AUDIO are NOT already granted, rather than blindly re-requesting both
     * every time (already-granted permissions don't need to be in the request array, though
     * including them is harmless -- the system just reports them as already granted).
     */
    private void checkAndRequestVideoPermissions() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        if (cameraGranted && audioGranted) {
            startVideoRecordingFeature();
            return;
        }

        multiPermissionLauncher.launch(new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        });
    }

    /**
     * POST_NOTIFICATIONS became a dangerous, runtime-requested permission for the first time
     * on API 33 -- apps that "just worked" for notifications on older devices silently stop
     * showing any notification at all until this is granted, so it must be checked explicitly
     * on top of the standard CAMERA/RECORD_AUDIO flow above.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return; // permission doesn't exist before API 33 -- nothing to request
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void startCameraFeature() {
        Log.d(TAG, "Starting camera feature (permission already confirmed granted)");
    }

    private void startVideoRecordingFeature() {
        Log.d(TAG, "Starting video recording feature (camera + microphone both confirmed granted)");
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * Matching AndroidManifest.xml <uses-permission> declarations (declaring a dangerous permission
 * in the manifest is necessary but NOT sufficient -- the runtime request above is also required):
 *
 * <uses-permission android:name="android.permission.CAMERA" />
 * <uses-permission android:name="android.permission.RECORD_AUDIO" />
 * <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 *
 * <activity android:name=".PermissionsDemoActivity" android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.MAIN" />
 *         <category android:name="android.intent.category.LAUNCHER" />
 *     </intent-filter>
 * </activity>
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_permissions_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <Button android:id="@+id/buttonRequestCamera"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Request camera permission" />
 *     <Button android:id="@+id/buttonRequestMultiple"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Request camera + microphone" />
 * </LinearLayout>
 */
