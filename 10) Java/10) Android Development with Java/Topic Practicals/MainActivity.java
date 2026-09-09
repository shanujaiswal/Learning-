/*
 * MainActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. Drop the class below
 * into app/src/main/java/<your/package>/MainActivity.java in a new "Empty Views
 * Activity" project, and ensure a matching res/layout/activity_main.xml exists
 * (a minimal one is sketched at the bottom of this file in a comment).
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/01 Android Fundamentals and Project Structure.md
 *
 * Covers:
 *     1. A minimal Activity showing onCreate() + setContentView() + the generated R class
 *     2. Reading values out of resources (strings.xml, colors.xml) via R
 *     3. Logging via Log.d -- the standard way to inspect behavior via `adb logcat`
 *     4. Checking the app's own manifest-declared metadata at runtime (versionName, packageName)
 */

package com.example.fundamentalsdemo;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Tag used consistently for this class's log lines -- makes filtering `adb logcat` output easy,
    // e.g. `adb logcat -s MainActivity`
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView inflates res/layout/activity_main.xml into this Activity's View hierarchy.
        // R.layout.activity_main is a compile-time-generated constant -- it only exists once the
        // corresponding XML file is present in res/layout/ and the project has been built at least once.
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate() called -- Activity is being created for the first time or recreated "
                + "after a configuration change (savedInstanceState == null means a fresh launch)");

        // findViewById looks up a View declared in the inflated XML by its android:id.
        // (See Topic Practicals file 03 for the modern ViewBinding alternative.)
        TextView titleText = findViewById(R.id.textTitle);

        // Reading a resource string via the generated R class instead of hardcoding text in Java --
        // this is the standard pattern: strings live in res/values/strings.xml, Java code only
        // references them by generated ID, which keeps localization/translation centralized.
        String appName = getString(R.string.app_name);
        titleText.setText("Welcome to: " + appName);

        // Reading a color resource similarly -- res/values/colors.xml
        int accentColor = getColor(R.color.purple_500);
        titleText.setTextColor(accentColor);

        demonstratePackageMetadata();
    }

    /**
     * Reads back metadata that was DECLARED in AndroidManifest.xml / build.gradle at build time --
     * useful for showing an "About" screen with the current app version, or for debugging which
     * build a user actually has installed.
     */
    private void demonstratePackageMetadata() {
        try {
            PackageManager packageManager = getPackageManager();
            String packageName = getPackageName();   // matches applicationId from app/build.gradle
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);

            Log.d(TAG, "Package name (applicationId): " + packageName);
            Log.d(TAG, "versionName (from build.gradle defaultConfig): " + packageInfo.versionName);
            Log.d(TAG, "versionCode (from build.gradle defaultConfig): " + packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            // Extremely unlikely for an app to fail to find its OWN package info, but PackageManager
            // APIs are checked-exception-heavy by convention -- always handle it defensively.
            Log.e(TAG, "Could not read own package info", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called -- not guaranteed to run on abrupt process death, "
                + "see the Activities and Lifecycle chapter for why");
    }
}

/*
 * Minimal matching layout -- save as res/layout/activity_main.xml in the same project:
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:orientation="vertical"
 *     android:padding="24dp"
 *     android:gravity="center">
 *
 *     <TextView
 *         android:id="@+id/textTitle"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:textSize="20sp" />
 *
 * </LinearLayout>
 *
 * And ensure res/values/strings.xml contains:
 *     <string name="app_name">Fundamentals Demo</string>
 *
 * And res/values/colors.xml contains (default in most templates):
 *     <color name="purple_500">#FF6200EE</color>
 */
