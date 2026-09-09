/*
 * NotificationDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The classes below are meant
 * to be dropped into app/src/main/java/<your/package>/ as SEPARATE .java files
 * (NotificationApp.java, MessageReplyReceiver.java, DownloadForegroundService.java,
 * NotificationDemoActivity.java respectively) in a project that also declares matching
 * manifest entries and permissions (sketched at the bottom of this file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/10 Android Notifications.md
 *
 * Covers:
 *     1. NotificationChannel creation (API 26+), created early in Application.onCreate()
 *     2. NotificationCompat.Builder usage with an inline-reply action backed by RemoteInput
 *        and a FLAG_MUTABLE PendingIntent, plus a simple non-input "Mark as read" action
 *     3. A foreground service notification example (startForeground() within onStartCommand(),
 *        an ongoing/non-dismissable notification, and the manifest foregroundServiceType
 *        requirement on API 34+)
 */

package com.example.advancedcomponents;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

/**
 * NotificationApp -- a custom Application subclass. Channels are created EARLY (here, in
 * Application.onCreate()) so they exist well before the first notification is ever posted,
 * regardless of which screen the user first reaches. Requires
 * android:name=".NotificationApp" on the <application> tag in the manifest.
 */
class NotificationApp extends Application {

    static final String CHANNEL_MESSAGES = "messages_channel";
    static final String CHANNEL_DOWNLOADS = "downloads_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        // NotificationChannel doesn't exist below API 26 -- NotificationCompat.Builder
        // gracefully ignores the channel ID argument on those versions and falls back to the
        // priority/sound/vibration set directly on the builder, which is why we guard channel
        // creation itself but still build notifications via NotificationCompat unconditionally.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = getSystemService(NotificationManager.class);

        // Pick the LOWEST importance that still serves the purpose -- HIGH is reserved for
        // genuinely time-sensitive content (an incoming message), not routine updates.
        NotificationChannel messagesChannel = new NotificationChannel(
                CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH);
        messagesChannel.setDescription("Notifications for new incoming messages");
        messagesChannel.enableLights(true);
        messagesChannel.enableVibration(true);
        manager.createNotificationChannel(messagesChannel);

        // Downloads/ongoing work channel -- lower importance is appropriate since it's not
        // urgent enough to warrant a heads-up popup + sound every time progress updates.
        NotificationChannel downloadsChannel = new NotificationChannel(
                CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW);
        downloadsChannel.setDescription("Shows ongoing and completed downloads");
        manager.createNotificationChannel(downloadsChannel);

        // Channel creation is idempotent -- calling createNotificationChannel() again with the
        // same ID does nothing if it already exists, and does NOT let you change
        // importance/sound after the user has seen it (those become user-owned in Settings).
    }
}

/**
 * NotificationDemoActivity -- posts a rich notification with actions when a button is tapped.
 */
public class NotificationDemoActivity extends AppCompatActivity {

    private static final String TAG = "NotificationDemoActivity";
    private static final int NOTIFICATION_ID_MESSAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_demo);

        Button postNotificationButton = findViewById(R.id.buttonPostNotification);
        postNotificationButton.setOnClickListener(v -> showMessageNotificationWithActions("Ada", "Are you free to review the PR?"));

        Button startForegroundButton = findViewById(R.id.buttonStartForegroundService);
        startForegroundButton.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, DownloadForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        });
    }

    /**
     * Builds and posts a notification with BOTH a content tap target (deep-links to the
     * relevant conversation, not just the launcher Activity) AND two actions: an inline-reply
     * action backed by RemoteInput (requires FLAG_MUTABLE), and a simple "Mark as read"
     * action (a plain FLAG_IMMUTABLE PendingIntent is fine since it carries no RemoteInput).
     */
    private void showMessageNotificationWithActions(String sender, String message) {
        // POST_NOTIFICATIONS is a dangerous runtime permission as of API 33 -- always check
        // before calling notify(), or the call silently does nothing on newer devices.
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted -- skipping notify()");
            return;
        }

        // Content intent -- deep-links to a specific conversation screen, not a generic
        // launcher Activity, so tapping the notification opens the RELEVANT screen.
        Intent contentIntent = new Intent(this, NotificationDemoActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE);

        // Inline-reply action: MUST use FLAG_MUTABLE since the system needs to attach the
        // typed text into the Intent's extras before firing it -- one of the few legitimate
        // exceptions to the "always prefer FLAG_IMMUTABLE" rule.
        Intent replyIntent = new Intent(this, MessageReplyReceiver.class);
        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                this, 0, replyIntent, PendingIntent.FLAG_MUTABLE);

        RemoteInput remoteInput = new RemoteInput.Builder("KEY_REPLY_TEXT")
                .setLabel("Reply")
                .build();

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_reply, "Reply", replyPendingIntent)
                .addRemoteInput(remoteInput) // enables inline text reply directly in the shade
                .build();

        // Simple action with no inline input -- a normal (immutable) PendingIntent targeting
        // a BroadcastReceiver is appropriate here.
        Intent markReadIntent = new Intent(this, MessageReplyReceiver.class);
        markReadIntent.putExtra("MARK_READ_ONLY", true);
        PendingIntent markReadPendingIntent = PendingIntent.getBroadcast(
                this, 1, markReadIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action markReadAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_check, "Mark as read", markReadPendingIntent)
                .build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationApp.CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification) // REQUIRED -- simple opaque/alpha silhouette, not a full-color icon
                .setContentTitle(sender)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // expanded view shows full text
                .setPriority(NotificationCompat.PRIORITY_HIGH) // ignored on API 26+ (channel importance wins), used pre-26
                .setContentIntent(contentPendingIntent)
                .addAction(replyAction)
                .addAction(markReadAction)
                .setAutoCancel(true); // dismiss once tapped -- appropriate for a one-shot alert

        // Reusing the same NOTIFICATION_ID updates the existing notification in place;
        // a unique ID per conversation would let multiple message notifications stack.
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_MESSAGE, builder.build());
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * MessageReplyReceiver.java -- save as a SEPARATE file. Target of both the reply action and
 * the "Mark as read" action above.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.content.BroadcastReceiver;
 * import android.content.Context;
 * import android.content.Intent;
 * import android.os.Bundle;
 * import android.util.Log;
 * import androidx.core.app.RemoteInput;
 *
 * public class MessageReplyReceiver extends BroadcastReceiver {
 *     @Override
 *     public void onReceive(Context context, Intent intent) {
 *         if (intent.getBooleanExtra("MARK_READ_ONLY", false)) {
 *             Log.d("MessageReplyReceiver", "Marked conversation as read");
 *             return;
 *         }
 *         Bundle results = RemoteInput.getResultsFromIntent(intent);
 *         if (results != null) {
 *             CharSequence replyText = results.getCharSequence("KEY_REPLY_TEXT");
 *             Log.d("MessageReplyReceiver", "User replied inline: " + replyText);
 *             // In real code: send the reply via a Repository/network call here.
 *         }
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * DownloadForegroundService.java -- save as a SEPARATE file. A foreground service performing
 * work the user is actively aware of; MUST display a persistent, non-dismissable notification
 * for as long as it runs.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.app.Notification;
 * import android.app.Service;
 * import android.content.Intent;
 * import android.os.IBinder;
 * import androidx.annotation.Nullable;
 * import androidx.core.app.NotificationCompat;
 *
 * public class DownloadForegroundService extends Service {
 *     private static final int FOREGROUND_ID = 2001;
 *
 *     @Override
 *     public int onStartCommand(Intent intent, int flags, int startId) {
 *         Notification notification = new NotificationCompat.Builder(this, NotificationApp.CHANNEL_DOWNLOADS)
 *                 .setSmallIcon(R.drawable.ic_download)
 *                 .setContentTitle("Downloading file")
 *                 .setContentText("0% complete")
 *                 .setOngoing(true)   // not swipe-dismissable while work is in progress
 *                 .setProgress(100, 0, false)
 *                 .build();
 *
 *         // startForeground() must be called within a few seconds of the service starting --
 *         // failing to promote a started service to foreground promptly throws
 *         // ForegroundServiceDidNotStartInTimeException on recent API levels.
 *         startForeground(FOREGROUND_ID, notification);
 *
 *         new Thread(() -> {
 *             simulateDownloadWithProgressUpdates();
 *             // Once the qualifying work finishes: stop showing as foreground, then stop the
 *             // service entirely -- a dangling foreground notification after work has actually
 *             // stopped is a common source of user complaints.
 *             stopForeground(Service.STOP_FOREGROUND_REMOVE);
 *             stopSelf();
 *         }).start();
 *
 *         return START_NOT_STICKY;
 *     }
 *
 *     private void simulateDownloadWithProgressUpdates() {
 *         // Real code would update the notification's setProgress() periodically via
 *         // NotificationManagerCompat.notify() with the SAME FOREGROUND_ID.
 *     }
 *
 *     @Nullable
 *     @Override
 *     public IBinder onBind(Intent intent) { return null; }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * Matching AndroidManifest.xml entries:
 *
 * <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 * <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 *
 * <application android:name=".NotificationApp">
 *     <activity android:name=".NotificationDemoActivity" android:exported="true">
 *         <intent-filter>
 *             <action android:name="android.intent.action.MAIN" />
 *             <category android:name="android.intent.category.LAUNCHER" />
 *         </intent-filter>
 *     </activity>
 *
 *     <receiver android:name=".MessageReplyReceiver" android:exported="false" />
 *
 *     <!-- As of API 34, foregroundServiceType is REQUIRED for every foreground service,
 *          along with the corresponding FOREGROUND_SERVICE_* runtime permission above. -->
 *     <service
 *         android:name=".DownloadForegroundService"
 *         android:foregroundServiceType="dataSync"
 *         android:exported="false" />
 * </application>
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_notification_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <Button android:id="@+id/buttonPostNotification"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Post message notification (with actions)" />
 *     <Button android:id="@+id/buttonStartForegroundService"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Start foreground download service" />
 * </LinearLayout>
 */
