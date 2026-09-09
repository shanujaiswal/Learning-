/*
 * LifecycleDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The two Activity classes
 * below are meant to be dropped into app/src/main/java/<your/package>/ as SEPARATE
 * .java files (LifecycleDemoActivity.java and SecondActivity.java respectively) in a
 * project that also declares both activities in AndroidManifest.xml (sketched at the
 * bottom of this file in a comment), with matching minimal layouts.
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/02 Activities and Lifecycle.md
 *
 * Covers:
 *     1. Full Activity lifecycle method overrides (onCreate/onStart/onResume/onPause/
 *        onStop/onDestroy, plus onRestart) with Log statements showing the call order
 *     2. An explicit Intent (naming the target Activity class directly)
 *     3. An implicit Intent (asking the system to find a component that can handle an action)
 *     4. Passing data to another Activity via Bundle/putExtra, and reading it back via getStringExtra
 */

package com.example.lifecycledemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * LifecycleDemoActivity -- the "first" Activity. It overrides every lifecycle callback so that
 * running the app and watching `adb logcat -s LifecycleDemoActivity` shows the exact call order:
 *
 *   Launch:                onCreate -> onStart -> onResume
 *   Press Home / backgrounded: onPause -> onStop
 *   Return to app:          onRestart -> onStart -> onResume
 *   Press Back / finish():  onPause -> onStop -> onDestroy
 *
 * Rotating the device (a configuration change) destroys and recreates the Activity entirely:
 *   onPause -> onStop -> onSaveInstanceState -> onDestroy -> onCreate -> onStart -> onResume
 */
public class LifecycleDemoActivity extends AppCompatActivity {

    private static final String TAG = "LifecycleDemoActivity";

    // Key used both to SAVE a value into the Bundle and to READ it back -- keeping it as a
    // named constant (instead of a repeated string literal) avoids typos between the two sites.
    private static final String KEY_COUNTER = "counter_value";

    // Simple in-memory state that we round-trip through onSaveInstanceState/onCreate so the
    // counter survives a rotation instead of resetting to 0.
    private int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lifecycle_demo);

        Log.d(TAG, "onCreate() -- Activity object is being constructed. "
                + "savedInstanceState == null means this is a fresh launch, not a recreation.");

        // Restore state saved by onSaveInstanceState() before a configuration-change destroy.
        if (savedInstanceState != null) {
            counter = savedInstanceState.getInt(KEY_COUNTER, 0);
            Log.d(TAG, "Restored counter from savedInstanceState: " + counter);
        }

        TextView counterText = findViewById(R.id.textCounter);
        counterText.setText("Counter: " + counter);

        Button incrementButton = findViewById(R.id.buttonIncrement);
        incrementButton.setOnClickListener(v -> {
            counter++;
            counterText.setText("Counter: " + counter);
        });

        Button explicitIntentButton = findViewById(R.id.buttonExplicitIntent);
        explicitIntentButton.setOnClickListener(v -> launchSecondActivityExplicitly());

        Button implicitIntentButton = findViewById(R.id.buttonImplicitIntent);
        implicitIntentButton.setOnClickListener(v -> launchWebPageImplicitly());
    }

    /**
     * Explicit Intent: names the exact component (SecondActivity.class) that should handle it.
     * This is the normal way to navigate WITHIN your own app, because you know exactly which
     * Activity you want to start -- there is no ambiguity for the system to resolve.
     */
    private void launchSecondActivityExplicitly() {
        Intent intent = new Intent(this, SecondActivity.class);

        // Passing data to the next Activity via putExtra -- key/value pairs stored in the
        // Intent's underlying Bundle. Keys are conventionally namespaced to avoid collisions
        // with extras from other apps/components, e.g. "com.example.lifecycledemo.EXTRA_NAME".
        intent.putExtra(SecondActivity.EXTRA_USER_NAME, "Ada Lovelace");
        intent.putExtra(SecondActivity.EXTRA_COUNTER_VALUE, counter);

        Log.d(TAG, "Starting SecondActivity explicitly with extras: "
                + "name=Ada Lovelace, counter=" + counter);
        startActivity(intent);
    }

    /**
     * Implicit Intent: describes an ACTION and DATA, and lets Android's Intent resolution
     * system find any installed app (could be Chrome, another browser, etc.) capable of
     * handling it. We don't know or care which component actually opens -- that's the point.
     */
    private void launchWebPageImplicitly() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com"));

        // Defensive check: resolveActivity() confirms at least one app on the device can handle
        // this implicit Intent BEFORE calling startActivity(), which would otherwise crash with
        // an ActivityNotFoundException on a device with no browser installed.
        if (intent.resolveActivity(getPackageManager()) != null) {
            Log.d(TAG, "Starting implicit ACTION_VIEW Intent for a URL");
            startActivity(intent);
        } else {
            Log.e(TAG, "No app found to handle ACTION_VIEW for a web URL");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() -- Activity is becoming visible to the user (not yet interactive)");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() -- Activity is now in the foreground and interactive; "
                + "this is where you'd resume camera previews, sensors, animations, etc.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() -- Activity is losing foreground focus (e.g. a dialog appeared, "
                + "or the user is navigating away); this MUST be fast -- release camera/sensors here");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() -- Activity is no longer visible at all; good place to stop "
                + "heavier work like animations or unregistering broadcast receivers");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart() -- Activity is coming back from a fully stopped state "
                + "(user returned to it); onStart() will be called again right after this");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() -- final cleanup before the Activity object is discarded; "
                + "isFinishing() tells you whether this is a real finish() vs a config-change recreate");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Called before the Activity is destroyed due to a configuration change (rotation) or
        // when the system may need to reclaim memory -- NOT called on a normal user-initiated
        // finish() (e.g. pressing Back), since there'd be nothing to restore to in that case.
        outState.putInt(KEY_COUNTER, counter);
        Log.d(TAG, "onSaveInstanceState() -- saving counter=" + counter + " ahead of a possible recreate");
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * SecondActivity.java -- save as a SEPARATE file in the same package. This is the target of the
 * explicit Intent above; it reads back the extras that were placed into the Bundle.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.lifecycledemo;
 *
 * import android.os.Bundle;
 * import android.util.Log;
 * import android.widget.TextView;
 * import androidx.appcompat.app.AppCompatActivity;
 *
 * public class SecondActivity extends AppCompatActivity {
 *
 *     private static final String TAG = "SecondActivity";
 *
 *     // Namespaced extra keys -- shared constants so the sending and receiving side can never
 *     // disagree on the exact key string used.
 *     public static final String EXTRA_USER_NAME = "com.example.lifecycledemo.EXTRA_USER_NAME";
 *     public static final String EXTRA_COUNTER_VALUE = "com.example.lifecycledemo.EXTRA_COUNTER_VALUE";
 *
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_second);
 *
 *         // Reading the extras back out of the Intent that started this Activity.
 *         // getStringExtra/getIntExtra return a safe default (null / 0) if the key is absent,
 *         // so this Activity should never crash even if launched without those extras.
 *         String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
 *         int counterValue = getIntent().getIntExtra(EXTRA_COUNTER_VALUE, 0);
 *
 *         Log.d(TAG, "Received extras -- name: " + userName + ", counter: " + counterValue);
 *
 *         TextView infoText = findViewById(R.id.textInfo);
 *         infoText.setText("Hello, " + userName + "! Counter was: " + counterValue);
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * Matching AndroidManifest.xml <application> entries (both activities must be declared):
 *
 * <activity android:name=".LifecycleDemoActivity" android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.MAIN" />
 *         <category android:name="android.intent.category.LAUNCHER" />
 *     </intent-filter>
 * </activity>
 * <activity android:name=".SecondActivity" android:exported="false" />
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_lifecycle_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <TextView android:id="@+id/textCounter"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" />
 *     <Button android:id="@+id/buttonIncrement"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Increment" />
 *     <Button android:id="@+id/buttonExplicitIntent"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Open SecondActivity (explicit)" />
 *     <Button android:id="@+id/buttonImplicitIntent"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Open a web page (implicit)" />
 * </LinearLayout>
 *
 * Minimal res/layout/activity_second.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <TextView android:id="@+id/textInfo"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" />
 * </LinearLayout>
 */
