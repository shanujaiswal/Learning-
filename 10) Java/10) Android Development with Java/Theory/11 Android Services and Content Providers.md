# What a Service Is

--> A **Service** is an app component that performs work in the BACKGROUND, with no user interface of its own, and (critically) is NOT tied to any particular Activity's lifecycle -- it keeps running even if the Activity that started it is destroyed, which is exactly why it exists: for work that should outlive a single screen (playing music, syncing data, tracking location).
--> A Service still runs on the app's MAIN thread by default -- it does NOT automatically get a background thread just by being a Service; any actual long-running work performed inside its callbacks must still be offloaded (a thread, an executor, coroutines/RxJava on the Kotlin side) or it will block the UI exactly like doing the same work in an Activity would.
--> There are two fundamentally different ways to use a Service, and understanding the difference is the crux of this topic.

# Started Services

--> A Service is **started** by calling `startService()` (or, on modern Android, `startForegroundService()` for work the user should be aware of -- see the Notifications file) -- once started, it runs INDEFINITELY in the background until it explicitly stops itself (`stopSelf()`) or is stopped externally (`stopService()`), regardless of whether the component that started it is still around.
--> A started Service has NO return channel back to its caller by default -- it's fire-and-forget; if the caller needs a result, it must be delivered some other way (a broadcast, a callback registered via binding, updating a shared database the caller observes, etc.).

```java
public class SyncService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Runs on the main thread -- offload real work:
        new Thread(() -> {
            performDataSync();
            stopSelf();   // stop this Service instance once the work completes
        }).start();

        return START_NOT_STICKY;   // don't automatically restart with a null Intent if killed
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }   // not bindable
}
```

```java
// Starting it:
Intent intent = new Intent(this, SyncService.class);
startService(intent);
```

--> **`onStartCommand()`'s return value** tells the OS what to do if it kills the process to reclaim memory while the Service is running:

| Return Value | Behavior on Restart |
|---|---|
| `START_NOT_STICKY` | Don't recreate the Service at all -- appropriate for work that's fine to simply not finish (e.g. triggered by a now-stale event). |
| `START_STICKY` | Recreate the Service and call `onStartCommand()` again with a `null` Intent -- appropriate for services that should just keep running (e.g. music playback) without needing to know the original starting Intent's data. |
| `START_REDELIVER_INTENT` | Recreate the Service AND redeliver the LAST Intent it was started with -- appropriate when the original Intent's data is needed to resume the work correctly (e.g. an in-progress upload). |

--> Since API 26 (Android 8), plain background `startService()` calls are heavily restricted while the app itself is backgrounded -- calling `startService()` from a backgrounded app can throw `IllegalStateException`; `startForegroundService()` (which requires promoting to a foreground service with a notification within seconds, see the Notifications file) is the modern way to start meaningful background work reliably.

# Bound Services

--> A Service is **bound** when a client calls `bindService()` -- this establishes a client-server connection where the client gets a live `IBinder` interface to call methods on the Service DIRECTLY (in-process) or across processes (via AIDL, out of scope here) -- a bound Service exists only as long as at least one client is bound to it, and is destroyed automatically once the last client unbinds.

```java
public class CounterService extends Service {
    private final IBinder binder = new LocalBinder();
    private int counter = 0;

    public class LocalBinder extends Binder {
        CounterService getService() { return CounterService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    public int incrementAndGet() { return ++counter; }
}
```

```java
private CounterService counterService;
private boolean isBound = false;

private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        CounterService.LocalBinder localBinder = (CounterService.LocalBinder) service;
        counterService = localBinder.getService();
        isBound = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) { isBound = false; }
};

@Override
protected void onStart() {
    super.onStart();
    Intent intent = new Intent(this, CounterService.class);
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
}

@Override
protected void onStop() {
    super.onStop();
    if (isBound) { unbindService(connection); isBound = false; }
}
```

--> A Service can be BOTH started and bound simultaneously (e.g. a music player: started so it survives no clients being bound, AND bound so the UI can query/control playback directly) -- in that case it's only fully destroyed once it has been explicitly stopped AND has no remaining bound clients.
--> Always pair `bindService()`/`unbindService()` symmetrically in matching lifecycle callbacks (e.g. `onStart()`/`onStop()`) -- calling `unbindService()` without a prior successful bind, or leaking a bind that's never released, both cause crashes or resource leaks.

# IntentService and JobIntentService -- Historical Context

--> **`IntentService`** (now deprecated) was a convenience Service subclass that automatically ran each incoming Intent's work on a single background WORKER THREAD (a serial queue -- one Intent processed at a time), and automatically called `stopSelf()` once the queue was empty -- it existed specifically to remove the "remember to spin up your own thread and stop yourself" boilerplate from a started Service.
--> **`JobIntentService`** was introduced as a transitional replacement that worked the same way but ALSO decided, based on API level, whether to run as a plain background Service (older devices) or schedule the work via `JobScheduler` (API 26+, respecting Doze/background execution limits) -- it bridged the gap while apps migrated toward the modern background-work restrictions.
--> Both are now considered LEGACY -- current guidance is **`WorkManager`** (covered conceptually in the Networking and Background Work file in the Android Development with Java folder) for deferrable/guaranteed background work, and a plain foreground Service (with `startForegroundService()`) for work the user needs to see happening right now -- new code should not reach for `IntentService`/`JobIntentService`.

# Content Providers -- Sharing Data Between Apps

--> A **`ContentProvider`** is the standard Android mechanism for exposing a structured set of data to OTHER apps (and, often, to your own app's own components) through a uniform, table-like interface -- regardless of whether the underlying storage is SQLite, a flat file, a network call, or an in-memory structure, callers interact with it identically via `ContentResolver` and `Uri`s.
--> It is the ONLY sanctioned way for one app's private database/files to be safely queried by another app -- direct file-path or database-file access across app sandboxes is not possible (and even within a single app, some teams use a ContentProvider internally just for its permission-scoping and consistent Uri-addressing benefits, e.g. exposing files to another app via `FileProvider`, a specialized ContentProvider used for sharing files through `content://` Uris rather than raw file paths).

```java
public class NotesProvider extends ContentProvider {
    public static final String AUTHORITY = "com.example.app.notesprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/notes");

    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        uriMatcher.addURI(AUTHORITY, "notes", NOTES);
        uriMatcher.addURI(AUTHORITY, "notes/#", NOTE_ID);   // '#' matches a numeric ID segment
    }

    private NotesDbHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new NotesDbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                         String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        switch (uriMatcher.match(uri)) {
            case NOTES:
                return db.query("notes", projection, selection, selectionArgs, null, null, sortOrder);
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                return db.query("notes", projection, "_id=?", new String[]{id}, null, null, sortOrder);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        long id = dbHelper.getWritableDatabase().insert("notes", null, values);
        Uri resultUri = ContentUris.withAppendedId(CONTENT_URI, id);
        getContext().getContentResolver().notifyChange(resultUri, null);   // notify any observers
        return resultUri;
    }

    // update(), delete(), getType() follow the same UriMatcher-driven pattern
}
```

```xml
<provider
    android:name=".NotesProvider"
    android:authorities="com.example.app.notesprovider"
    android:exported="true"
    android:readPermission="com.example.app.permission.READ_NOTES"
    android:writePermission="com.example.app.permission.WRITE_NOTES" />
```

--> A **`Uri`** identifies data through a `content://` scheme: `content://<authority>/<path>[/<id>]` -- the AUTHORITY uniquely names the provider (conventionally the package name), and the path/ID identify which table/row within it, analogous to a URL identifying a web resource.
--> **`UriMatcher`** is the standard helper for dispatching an incoming Uri to the right internal handling branch, since a single provider often exposes multiple logical "tables" (e.g. `/notes` for the whole collection, `/notes/5` for one row).
--> The six core methods to implement are `onCreate()`, `query()`, `insert()`, `update()`, `delete()`, and `getType()` (returns the MIME type for a given Uri) -- `query()` returns a `Cursor`, the same row-iteration abstraction used directly with SQLite (see the Data Persistence file in the Android Development with Java folder).

# ContentResolver -- the Client Side

--> Other components (in your own app OR another app, permissions allowing) never talk to a `ContentProvider` class directly -- they go through **`ContentResolver`** (`getContentResolver()`), which routes the call to whichever provider is registered for that Uri's authority, including across process boundaries transparently.

```java
// Querying
Cursor cursor = getContentResolver().query(
        NotesProvider.CONTENT_URI,
        new String[]{"_id", "title", "body"},
        null, null, "title ASC");
if (cursor != null) {
    while (cursor.moveToNext()) {
        String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
    }
    cursor.close();   // Cursors hold native resources -- always close them (or use try-with-resources)
}

// Inserting
ContentValues values = new ContentValues();
values.put("title", "Shopping list");
values.put("body", "Milk, eggs, bread");
Uri newNoteUri = getContentResolver().insert(NotesProvider.CONTENT_URI, values);

// Observing changes -- reacting when the provider's data changes
getContentResolver().registerContentObserver(NotesProvider.CONTENT_URI, true,
        new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) { refreshNotesList(); }
        });
```

--> `ContentResolver` is also how apps read PLATFORM-provided content -- contacts (`ContactsContract`), the media store (`MediaStore.Images`, `MediaStore.Video`), calendar events -- these are simply system-provided `ContentProvider`s exposed the exact same way, which is why the same query/insert/Uri pattern shows up throughout the Android SDK regardless of what data is involved.
--> Always `close()` a returned `Cursor` once done with it (or use try-with-resources, since `Cursor` implements `Closeable`) -- an unclosed cursor holds onto underlying database/native resources and is a classic resource leak.

# Common Gotchas

--> **Doing real work directly on a started Service's main-thread callback** -- a Service is not automatically backgrounded; `onStartCommand()`/`onHandleIntent()`-style methods still run on the main thread unless you explicitly offload.
--> **Forgetting to unbind a bound Service** -- leaks the `ServiceConnection` and keeps the Service artificially alive; always unbind in the mirroring lifecycle callback to whichever one you bound in.
--> **Choosing `START_STICKY` for one-shot work** -- causes the Service to restart itself with a `null` Intent after being killed, re-running `onStartCommand()` with no data to act on, which crashes or misbehaves if the code assumes the Intent is always non-null.
--> **Exposing a `ContentProvider` with `exported="true"` and no read/write permission** on data that shouldn't be public -- any installed app can then query it; scope providers with `readPermission`/`writePermission`, or set `exported="false"` if it's purely for internal use (`FileProvider`-style sharing still works via explicit Uri grants even when not broadly exported).
--> **Not closing `Cursor` objects** returned from `query()` -- accumulates leaked native resources over the app's lifetime.

# Best Practices

--> Reach for a started Service only for background work with no ongoing two-way interaction need; reach for a bound Service (or a bound+started hybrid) when a client needs to call methods on it directly and observe live state.
--> Prefer `WorkManager` over a hand-rolled started Service for anything deferrable (sync, upload, periodic work) -- it already handles retry, constraints (network/charging), and Doze-compliant scheduling that a raw Service does not give you for free.
--> Only build a full `ContentProvider` when data genuinely needs to be shared ACROSS apps (or you specifically want its Uri-based permission model) -- for data used only within your own app, a Room database/DAO accessed directly is simpler and has less boilerplate (see the Data Persistence file).
--> Always version and validate incoming `Uri`s/selections in a `ContentProvider`'s `query()`/`update()`/`delete()` -- since `exported` providers can be called by ANY app, treat all incoming parameters as untrusted input, exactly like a public API endpoint.
