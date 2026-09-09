/*
 * IntentDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The classes below are meant
 * to be dropped into app/src/main/java/<your/package>/ as SEPARATE .java files
 * (IntentDemoActivity.java, ShareTargetActivity.java, DeepLinkActivity.java,
 * ReminderReceiver.java respectively) in a project that also declares the relevant
 * manifest entries (sketched at the bottom of this file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/07 Android Intents and Intent Filters In Depth.md
 *
 * Covers:
 *     1. Explicit intents beyond basics -- targeting a component in ANOTHER app via
 *        ComponentName (package + class name), plus a plain in-app explicit intent
 *     2. Implicit intents with resolveActivity() defensive checks and Intent.createChooser()
 *     3. Intent-filter manifest declarations (shown as comments at the bottom) for a
 *        share-target Activity and a deep-link Activity, including <queries> for API 30+
 *        package visibility and android:autoVerify App Links
 *     4. PendingIntent creation with the four factory methods, FLAG_IMMUTABLE vs
 *        FLAG_MUTABLE, and FLAG_UPDATE_CURRENT semantics
 *     5. Extras best practices -- namespaced typed extra key constants, defaulted getters,
 *        and a Parcelable custom object extra using the API 33+ type-safe overload
 */

package com.example.advancedcomponents;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * IntentDemoActivity -- the entry point Activity for this practical. Wires up a handful of
 * buttons, each demonstrating a distinct Intent pattern covered in the theory chapter.
 */
public class IntentDemoActivity extends AppCompatActivity {

    private static final String TAG = "IntentDemoActivity";

    // --- Namespaced extra key constants -------------------------------------------------
    // Best practice: define extra keys as public static final fields on the class that OWNS
    // (reads) them, namespaced with the package name to avoid collisions with extras another
    // app or component might set on the same Intent object.
    public static final String EXTRA_USER_ID =
            "com.example.advancedcomponents.EXTRA_USER_ID";
    public static final String EXTRA_USER_PROFILE =
            "com.example.advancedcomponents.EXTRA_USER_PROFILE";

    // Distinct PendingIntent request codes -- PendingIntent "equality" for FLAG_UPDATE_CURRENT
    // purposes is based on request code + the wrapped Intent's action/data/categories/component,
    // NOT its extras, so logically independent pending intents need distinct request codes.
    private static final int REQUEST_CODE_REMINDER = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intent_demo);

        Button explicitOtherAppButton = findViewById(R.id.buttonExplicitOtherApp);
        explicitOtherAppButton.setOnClickListener(v -> startExplicitIntentInAnotherApp());

        Button implicitShareButton = findViewById(R.id.buttonShareWithChooser);
        implicitShareButton.setOnClickListener(v -> shareTextWithChooser());

        Button implicitMapButton = findViewById(R.id.buttonOpenMap);
        implicitMapButton.setOnClickListener(v -> openLocationImplicitly());

        Button parcelableButton = findViewById(R.id.buttonSendParcelable);
        parcelableButton.setOnClickListener(v -> sendTypedParcelableExtra());

        Button pendingIntentButton = findViewById(R.id.buttonSchedulePendingIntent);
        pendingIntentButton.setOnClickListener(v -> buildReminderPendingIntent());

        // Reading extras back defensively -- NEVER assume an extra is present, since ANY
        // component that is exported can be launched by any other app with an Intent
        // missing expected extras entirely.
        int userId = getIntent().getIntExtra(EXTRA_USER_ID, -1);
        if (userId != -1) {
            Log.d(TAG, "Launched with userId extra: " + userId);
        }
    }

    /**
     * Explicit Intent targeting a component in ANOTHER installed app, via ComponentName
     * (package name + fully-qualified class name) rather than Class -- this is how you reach
     * a component you don't own, provided it is exported="true" and you know its names.
     *
     * On API 30+ this also requires a <queries> declaration naming the target package
     * (see the manifest sketch at the bottom of this file), or resolveActivity()/startActivity()
     * will behave as if the target app is not installed at all (package-visibility filtering).
     */
    private void startExplicitIntentInAnotherApp() {
        ComponentName component = new ComponentName(
                "com.example.otherapp",
                "com.example.otherapp.ui.ShareActivity");

        Intent intent = new Intent();
        intent.setComponent(component);
        // Explicit intents can still carry action/data/extras -- "explicit" only means a
        // component is set; the target can read getAction()/getData()/extras exactly as with
        // an implicit intent, useful when one Activity handles several modes from different callers.
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, "Shared from AdvancedComponents demo");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "Target app/component not found or not visible", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Implicit Intent forced through a chooser -- recommended for share-style actions so the
     * user isn't silently locked into a default handler they didn't consciously pick.
     */
    private void shareTextWithChooser() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Check out this Android Intents practical!");

        Intent chooserIntent = Intent.createChooser(sendIntent, "Share via");
        if (chooserIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooserIntent);
        } else {
            Toast.makeText(this, "No app found to handle sharing", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Implicit Intent for a geo Uri -- always defensively check resolution is possible before
     * calling startActivity(), since a zero-match implicit intent throws ActivityNotFoundException.
     */
    private void openLocationImplicitly() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Eiffel+Tower"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No app found to handle this request", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Typed extras best practice: pass a small Parcelable object rather than serializing a
     * large object graph. Keep extras small -- the whole Bundle travels through Binder IPC
     * with a strict size limit -- pass an ID and re-fetch the full object on the receiving
     * side for anything large; a small value object like UserProfile below is fine directly.
     */
    private void sendTypedParcelableExtra() {
        UserProfile profile = new UserProfile(42, "Ada Lovelace");

        Intent intent = new Intent(this, ProfileDetailActivity.class);
        intent.putExtra(EXTRA_USER_PROFILE, profile);
        startActivity(intent);
    }

    /**
     * PendingIntent creation -- wraps a regular Intent together with the permission and
     * identity to fire it LATER, as if this app fired it, even from a different process
     * (e.g. AlarmManager, or the System UI process for a notification tap).
     */
    private void buildReminderPendingIntent() {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("REMINDER_TEXT", "Time to review Advanced Android Components!");

        // FLAG_IMMUTABLE is the safe default: the receiving system component cannot modify the
        // wrapped Intent's extras before firing it. FLAG_UPDATE_CURRENT ensures that if a
        // PendingIntent with this same request code already exists, its extras are replaced
        // rather than silently ignored (a very common bug when updating a scheduled reminder).
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, REQUEST_CODE_REMINDER, intent, flags);

        Log.d(TAG, "Built PendingIntent (would normally be handed to AlarmManager.setExact(...)): "
                + pendingIntent);
        Toast.makeText(this, "PendingIntent created for reminder broadcast", Toast.LENGTH_SHORT).show();

        // Example of how this would actually be scheduled (commented -- requires SCHEDULE_EXACT_ALARM
        // on API 31+ and is out of scope for this practical):
        //
        // AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        // long triggerAtMillis = System.currentTimeMillis() + 60_000;
        // alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * UserProfile.java -- save as a SEPARATE file. A minimal hand-written Parcelable used as a
 * strongly-typed custom object extra (the recommended alternative to Serializable for Android
 * Intent/Bundle extras, since Parcelable is designed for the platform's IPC mechanism and is
 * far more efficient).
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.os.Parcel;
 * import android.os.Parcelable;
 *
 * public class UserProfile implements Parcelable {
 *     final int id;
 *     final String name;
 *
 *     UserProfile(int id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 *
 *     protected UserProfile(Parcel in) {
 *         id = in.readInt();
 *         name = in.readString();
 *     }
 *
 *     @Override
 *     public void writeToParcel(Parcel dest, int flags) {
 *         dest.writeInt(id);
 *         dest.writeString(name);
 *     }
 *
 *     @Override
 *     public int describeContents() { return 0; }
 *
 *     public static final Creator<UserProfile> CREATOR = new Creator<UserProfile>() {
 *         @Override
 *         public UserProfile createFromParcel(Parcel in) { return new UserProfile(in); }
 *
 *         @Override
 *         public UserProfile[] newArray(int size) { return new UserProfile[size]; }
 *     };
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * ProfileDetailActivity.java -- save as a SEPARATE file. Reads the Parcelable extra back using
 * the API 33+ type-safe overload where available, falling back to the deprecated raw overload
 * on older API levels.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.os.Build;
 * import android.os.Bundle;
 * import androidx.appcompat.app.AppCompatActivity;
 *
 * public class ProfileDetailActivity extends AppCompatActivity {
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_profile_detail);
 *
 *         UserProfile profile;
 *         if (Build.VERSION.SDK_INT >= 33) {
 *             // API 33+ type-safe retrieval -- avoids an unchecked-cast warning.
 *             profile = getIntent().getParcelableExtra(
 *                     IntentDemoActivity.EXTRA_USER_PROFILE, UserProfile.class);
 *         } else {
 *             // Pre-33 (deprecated but still functional)
 *             profile = getIntent().getParcelableExtra(IntentDemoActivity.EXTRA_USER_PROFILE);
 *         }
 *
 *         if (profile != null) {
 *             setTitle("Profile: " + profile.name);
 *         }
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * ReminderReceiver.java -- save as a SEPARATE file. The target of the PendingIntent built
 * above (PendingIntent.getBroadcast()).
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.content.BroadcastReceiver;
 * import android.content.Context;
 * import android.content.Intent;
 * import android.util.Log;
 *
 * public class ReminderReceiver extends BroadcastReceiver {
 *     @Override
 *     public void onReceive(Context context, Intent intent) {
 *         String text = intent.getStringExtra("REMINDER_TEXT");
 *         Log.d("ReminderReceiver", "Reminder fired: " + text);
 *         // In real code: post a notification here (see the Notifications practical).
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * ShareTargetActivity.java / DeepLinkActivity.java declarations and their intent filters,
 * PLUS the <queries> element required on API 30+ to explicitly target another app's component,
 * PLUS an Android App Link example with autoVerify -- all sketched here as comments since they
 * belong in AndroidManifest.xml, not a .java file:
 * ---------------------------------------------------------------------------------------------
 *
 * <manifest xmlns:android="http://schemas.android.com/apk/res/android">
 *
 *     <!-- Package-visibility declaration required on API 30+ to explicitly target a
 *          component in com.example.otherapp (see startExplicitIntentInAnotherApp() above). -->
 *     <queries>
 *         <package android:name="com.example.otherapp" />
 *         <intent>
 *             <action android:name="android.intent.action.SEND" />
 *             <data android:mimeType="text/plain" />
 *         </intent>
 *     </queries>
 *
 *     <application>
 *         <activity android:name=".IntentDemoActivity" android:exported="true">
 *             <intent-filter>
 *                 <action android:name="android.intent.action.MAIN" />
 *                 <category android:name="android.intent.category.LAUNCHER" />
 *             </intent-filter>
 *         </activity>
 *
 *         <activity android:name=".ProfileDetailActivity" android:exported="false" />
 *
 *         <!-- Share-target Activity: advertises willingness to handle ACTION_SEND for
 *              plain text, reachable via any app's share sheet (must declare CATEGORY_DEFAULT
 *              or plain startActivity()-fired implicit intents will never match it). -->
 *         <activity android:name=".ShareTargetActivity" android:exported="true">
 *             <intent-filter>
 *                 <action android:name="android.intent.action.SEND" />
 *                 <category android:name="android.intent.category.DEFAULT" />
 *                 <data android:mimeType="text/plain" />
 *             </intent-filter>
 *         </activity>
 *
 *         <!-- Deep-link Activity as a genuine Android App Link: autoVerify="true" plus a
 *              https scheme/host filter, backed by an assetlinks.json hosted at
 *              https://www.example.com/.well-known/assetlinks.json, skips the disambiguation
 *              dialog entirely once domain ownership is verified. -->
 *         <activity android:name=".DeepLinkActivity" android:exported="true">
 *             <intent-filter android:autoVerify="true">
 *                 <action android:name="android.intent.action.VIEW" />
 *                 <category android:name="android.intent.category.DEFAULT" />
 *                 <category android:name="android.intent.category.BROWSABLE" />
 *                 <data
 *                     android:scheme="https"
 *                     android:host="www.example.com"
 *                     android:pathPrefix="/product" />
 *             </intent-filter>
 *         </activity>
 *
 *         <receiver android:name=".ReminderReceiver" android:exported="false" />
 *
 *     </application>
 * </manifest>
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_intent_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <Button android:id="@+id/buttonExplicitOtherApp"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Explicit intent to another app" />
 *     <Button android:id="@+id/buttonShareWithChooser"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Share text (forced chooser)" />
 *     <Button android:id="@+id/buttonOpenMap"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Open location (implicit)" />
 *     <Button android:id="@+id/buttonSendParcelable"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Send typed Parcelable extra" />
 *     <Button android:id="@+id/buttonSchedulePendingIntent"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Build reminder PendingIntent" />
 * </LinearLayout>
 */
