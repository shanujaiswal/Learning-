# What a Notification Is

--> A **notification** is a message displayed OUTSIDE your app's UI -- in the status bar, the notification shade, on the lock screen, or as a heads-up banner -- used to inform the user of something relevant even while your app isn't in the foreground (a new message, a download finishing, an ongoing process).
--> Built via `NotificationCompat.Builder` (AndroidX's backward-compatible builder) and posted through `NotificationManagerCompat`, which internally delegates to the platform's `NotificationManager`.

# NotificationChannel -- Required on Android 8+ (API 26)

--> Since API 26, EVERY notification MUST belong to a **`NotificationChannel`** -- channels group notifications by TYPE/PURPOSE (e.g. "Messages", "Downloads", "Promotions"), and the user controls importance, sound, vibration, and visibility PER CHANNEL from system Settings, rather than for the app as a whole -- this hands users fine-grained control instead of the old all-or-nothing "block this app's notifications entirely."
--> A channel must be created BEFORE you can post a notification to it, and channel creation is idempotent -- calling `createNotificationChannel()` again with the same ID does nothing if it already exists (and, importantly, does NOT let you change importance/sound after the user has seen it -- most channel properties become user-owned once created and are only editable by the user in Settings).

```java
private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel(
                "messages_channel",                       // unique, stable channel ID
                "Messages",                                // user-visible name
                NotificationManager.IMPORTANCE_HIGH);      // controls heads-up/sound behavior
        channel.setDescription("Notifications for new incoming messages");
        channel.enableLights(true);
        channel.enableVibration(true);

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
```

--> Create channels EARLY -- typically in `Application.onCreate()` -- so they exist well before the first notification is ever posted, regardless of which screen the user first reaches.
--> **Importance levels** (`IMPORTANCE_HIGH/DEFAULT/LOW/MIN/NONE`) map roughly to: HIGH = heads-up popup + sound, DEFAULT = sound but no heads-up, LOW = no sound, MIN = no sound and hidden from the status bar (shows only when the shade is pulled down) -- pick the LOWEST importance that still serves the notification's purpose, since overly aggressive channels are a common reason users disable notifications for an app entirely.
--> On API levels below 26, `NotificationChannel` doesn't exist at all -- `NotificationCompat.Builder` gracefully ignores the channel ID argument on those versions and instead uses the priority/sound/vibration set directly on the builder, which is why building via `NotificationCompat` (not the raw `Notification.Builder`) is important for supporting older devices from one code path.

# Building and Posting a Basic Notification

```java
private void showMessageNotification(String sender, String message) {
    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    PendingIntent contentIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_notification)         // REQUIRED -- must be a simple, opaque/alpha-only icon
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)     // ignored on API 26+, used pre-26
            .setContentIntent(contentIntent)
            .setAutoCancel(true);                              // dismiss when tapped

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
        return;   // API 33+ requires this runtime permission -- see the Permissions file
    }
    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
}
```

--> **`setSmallIcon()`** is the ONLY truly mandatory field -- without it, `notify()` throws; the icon must be a simple white-on-transparent silhouette (the system tints it), NOT a full-color app icon.
--> Each posted notification needs an **ID** -- reusing the same ID with `notify()` UPDATES the existing notification in place (useful for progress bars); using a unique ID per notification lets multiple notifications coexist and stack.
--> `setAutoCancel(true)` dismisses the notification automatically once tapped -- without it, the notification stays in the shade after being opened, which is usually not what's wanted for a one-shot alert (though it IS appropriate for persistent/ongoing notifications).
--> `POST_NOTIFICATIONS` is a dangerous runtime permission as of API 33 (see the Permissions System file) -- always check/request it before calling `notify()`, or the call silently does nothing (or the OS throws, depending on target SDK) on newer devices.

# Expanded/Rich Notification Styles

```java
builder.setStyle(new NotificationCompat.BigTextStyle()
        .bigText("This is a much longer message body that would otherwise be truncated " +
                 "in the default single-line collapsed notification view."));

builder.setStyle(new NotificationCompat.BigPictureStyle()
        .bigPicture(bitmap)
        .bigLargeIcon((Bitmap) null));   // clear the large icon once expanded, a common polish touch

builder.setStyle(new NotificationCompat.InboxStyle()
        .addLine("Message 1")
        .addLine("Message 2")
        .setSummaryText("+3 more"));
```

--> Styles only change the EXPANDED appearance (when the user pulls the notification open) -- the collapsed view still uses `setContentTitle()`/`setContentText()`.

# Notification Actions

--> Notifications can carry up to a few directly-tappable **action buttons**, each backed by its own `PendingIntent`, letting the user respond WITHOUT opening the app.

```java
Intent replyIntent = new Intent(this, ReplyReceiver.class);
PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
        this, 0, replyIntent, PendingIntent.FLAG_MUTABLE);   // MUTABLE required for RemoteInput

RemoteInput remoteInput = new RemoteInput.Builder("KEY_REPLY_TEXT")
        .setLabel("Reply")
        .build();

NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
        R.drawable.ic_reply, "Reply", replyPendingIntent)
        .addRemoteInput(remoteInput)      // enables inline text reply directly in the notification shade
        .build();

builder.addAction(replyAction);
```

```java
// Inside ReplyReceiver.onReceive():
Bundle results = RemoteInput.getResultsFromIntent(intent);
if (results != null) {
    CharSequence replyText = results.getCharSequence("KEY_REPLY_TEXT");
    sendReply(replyText.toString());
}
```

--> Actions requiring `RemoteInput` (inline reply) MUST use `FLAG_MUTABLE` on their `PendingIntent`, since the system needs to attach the typed text into the Intent's extras before firing it -- this is one of the few legitimate exceptions to the "always prefer `FLAG_IMMUTABLE`" rule from the Intents file.
--> A simple action with no inline input (e.g. "Mark as read", "Snooze") uses a normal `PendingIntent` (immutable is fine) targeting a `BroadcastReceiver`, `Activity`, or `Service` as appropriate.

# Foreground Service Notifications

--> A **foreground service** is a `Service` performing work the user is actively aware of (music playback, a location-tracking run, a file upload) -- unlike a normal background service, it is EXEMPT from many background-execution limits, but in exchange it MUST display a persistent, non-dismissable notification for as long as it runs, so the user always knows it's active.

```java
public class MusicPlaybackService extends Service {
    private static final int FOREGROUND_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "playback_channel")
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle("Now Playing")
                .setContentText("Song Title -- Artist")
                .setOngoing(true)                 // not swipe-dismissable
                .build();

        startForeground(FOREGROUND_ID, notification);
        // ... begin playback / long-running work ...
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
```

```xml
<service
    android:name=".MusicPlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
```

--> `startForeground()` must be called within a few seconds of the service starting (the exact grace window has tightened across releases) -- failing to promote a started service to foreground promptly throws `ForegroundServiceDidNotStartInTimeException` on recent API levels.
--> As of API 34 (Android 14), declaring `android:foregroundServiceType` in the manifest is REQUIRED for every foreground service, and the corresponding runtime permission (e.g. `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_LOCATION`) must also be declared -- omitting the type is a manifest/runtime error, part of Google's ongoing effort to make background execution more accountable.
--> Call `stopForeground()` (with a flag indicating whether to also remove the notification) once the qualifying work finishes, and `stopSelf()`/`onDestroy()` to end the service entirely -- a foreground notification left dangling after the underlying work has actually stopped is a common source of user complaints ("why does this app say it's still playing music").

# Notification Grouping and Summaries

```java
builder.setGroup("MESSAGES_GROUP");   // applied to each individual message notification

Notification summaryNotification = new NotificationCompat.Builder(this, "messages_channel")
        .setSmallIcon(R.drawable.ic_notification)
        .setStyle(new NotificationCompat.InboxStyle().setSummaryText("3 new messages"))
        .setGroup("MESSAGES_GROUP")
        .setGroupSummary(true)          // this one represents the collapsed "bundle" view
        .build();
```

--> Grouping bundles multiple related notifications together in the shade under one collapsible summary -- important for apps that can post many notifications in quick succession (chat apps, email).

# Common Gotchas

--> **Forgetting to create the channel before posting** on API 26+ -- `notify()` either silently fails or throws, depending on exact version/manufacturer skin, since the notification has nowhere valid to attach.
--> **Trying to change channel importance/sound programmatically after creation** -- once a channel exists, the OS treats those settings as user-owned; your code changing them again has NO EFFECT (the user must change them manually in Settings) -- if truly new behavior is needed, create a NEW channel with a different ID (and accept the old one becomes orphaned/unused).
--> **Using `FLAG_IMMUTABLE` on an action PendingIntent that needs `RemoteInput`** -- silently breaks inline reply; the system can't attach the typed text.
--> **Missing `POST_NOTIFICATIONS` permission handling on API 33+** -- notifications silently stop appearing for users on new installs until the app requests and is granted this permission.
--> **A colorful/full app-icon-style small icon** -- the system either rejects it or renders it as a solid white blob, since `setSmallIcon()` expects a simple alpha silhouette, not a photographic/multi-color image.
--> **Long-running work in a plain (non-foreground) `Service` triggering the system to kill it** -- background execution limits (Doze, App Standby, background service restrictions since API 26) mean genuinely long or user-visible ongoing work needs to be a foreground service (or `WorkManager` for deferrable work), not a plain started service.

# Best Practices

--> Create one channel PER logical notification category, not one giant channel for the whole app -- this is what gives users meaningful granular control and is exactly what channels are FOR.
--> Choose the lowest importance that still achieves the notification's purpose -- reserve `IMPORTANCE_HIGH` for genuinely time-sensitive content (an incoming call/message), not routine updates.
--> Always provide a `setContentIntent()` that deep-links to the RELEVANT screen for that notification's content, not just the app's launcher Activity -- tapping a message notification should open that conversation, not a generic home screen.
--> Test the full notification lifecycle: permission denial, tapping, dismissing, and (for foreground services) killing the app process while the service claims to be running, to confirm the notification is torn down correctly.
