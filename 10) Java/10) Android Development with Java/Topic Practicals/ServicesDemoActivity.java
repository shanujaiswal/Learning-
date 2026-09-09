/*
 * ServicesDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The classes below are meant
 * to be dropped into app/src/main/java/<your/package>/ as SEPARATE .java files
 * (SyncStartedService.java, CounterBoundService.java, NotesProvider.java,
 * NotesDbHelper.java, ServicesDemoActivity.java respectively) in a project that also
 * declares matching manifest entries (sketched at the bottom of this file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/11 Android Services and Content Providers.md
 *
 * Covers:
 *     1. A Started Service (offloading work off the main thread, onStartCommand() return
 *        value semantics, stopSelf())
 *     2. A Bound Service (LocalBinder pattern, ServiceConnection, symmetric bind/unbind
 *        in mirroring lifecycle callbacks)
 *     3. A simple ContentProvider implementation with UriMatcher and full CRUD methods
 *        (query/insert/update/delete/getType)
 *     4. ContentResolver client usage (query with try-with-resources Cursor, insert,
 *        and registerContentObserver for reacting to changes)
 */

package com.example.advancedcomponents;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.app.Service;
import android.database.ContentObserver;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * SyncStartedService -- a Started Service: fire-and-forget, runs indefinitely until it stops
 * itself, independent of whichever component started it.
 */
class SyncStartedService extends Service {

    private static final String TAG = "SyncStartedService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A Service still runs on the app's MAIN thread by default -- it does NOT
        // automatically get a background thread just by being a Service. Any actual
        // long-running work must be explicitly offloaded.
        new Thread(() -> {
            Log.d(TAG, "Sync started on background thread");
            performDataSync();
            Log.d(TAG, "Sync finished -- stopping service");
            stopSelf(); // stop this Service instance once the work completes
        }).start();

        // START_NOT_STICKY -- if the OS kills the process to reclaim memory mid-sync, don't
        // automatically restart with a null Intent; appropriate since a stale sync trigger
        // is fine to simply not finish (contrast with START_REDELIVER_INTENT for an
        // in-progress upload that needs its original Intent data to resume correctly).
        return START_NOT_STICKY;
    }

    private void performDataSync() {
        try {
            Thread.sleep(1500); // simulated network/database work
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // not bindable -- this Service is started-only
    }
}

/**
 * CounterBoundService -- a Bound Service: exists only as long as at least one client is
 * bound, and is destroyed automatically once the last client unbinds. Exposes direct
 * in-process method calls via a LocalBinder.
 */
class CounterBoundService extends Service {

    private final IBinder binder = new LocalBinder();
    private int counter = 0;

    /** LocalBinder hands the bound client a direct reference to this Service instance. */
    public class LocalBinder extends Binder {
        CounterBoundService getService() {
            return CounterBoundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public int incrementAndGet() {
        return ++counter;
    }

    public int getCurrentCount() {
        return counter;
    }
}

/**
 * NotesDbHelper -- minimal SQLiteOpenHelper backing NotesProvider below.
 */
class NotesDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notes.db";
    private static final int DATABASE_VERSION = 1;

    NotesDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notes");
        onCreate(db);
    }
}

/**
 * NotesProvider -- a simple ContentProvider implementation exposing a "notes" table to other
 * apps (permissions allowing) and to this app's own components through the standard
 * Uri/ContentResolver interface, regardless of the underlying storage being SQLite.
 */
class NotesProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.advancedcomponents.notesprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/notes");

    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        uriMatcher.addURI(AUTHORITY, "notes", NOTES);
        uriMatcher.addURI(AUTHORITY, "notes/#", NOTE_ID); // '#' matches a numeric ID segment
    }

    private NotesDbHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new NotesDbHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                         @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        switch (uriMatcher.match(uri)) {
            case NOTES:
                return db.query("notes", projection, selection, selectionArgs, null, null, sortOrder);
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                return db.query("notes", projection, "_id=?", new String[]{id}, null, null, sortOrder);
            default:
                // exported providers can be called by ANY app -- treat all incoming Uris and
                // selections as untrusted input, exactly like a public API endpoint.
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (uriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Invalid URI for insert: " + uri);
        }
        long id = dbHelper.getWritableDatabase().insert("notes", null, values);
        Uri resultUri = ContentUris.withAppendedId(CONTENT_URI, id);
        // Notify any registered ContentObservers (e.g. the one set up in
        // ServicesDemoActivity below) that data at this Uri has changed.
        getContext().getContentResolver().notifyChange(resultUri, null);
        return resultUri;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                       @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;
        switch (uriMatcher.match(uri)) {
            case NOTES:
                count = db.update("notes", values, selection, selectionArgs);
                break;
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                count = db.update("notes", values, "_id=?", new String[]{id});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;
        switch (uriMatcher.match(uri)) {
            case NOTES:
                count = db.delete("notes", selection, selectionArgs);
                break;
            case NOTE_ID:
                String id = uri.getLastPathSegment();
                count = db.delete("notes", "_id=?", new String[]{id});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (count > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case NOTES:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".notes";
            case NOTE_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".notes";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }
}

/**
 * ServicesDemoActivity -- ties together the Started Service, the Bound Service, and
 * ContentResolver client usage against NotesProvider.
 */
public class ServicesDemoActivity extends AppCompatActivity {

    private static final String TAG = "ServicesDemoActivity";

    private CounterBoundService counterService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CounterBoundService.LocalBinder localBinder = (CounterBoundService.LocalBinder) service;
            counterService = localBinder.getService();
            isBound = true;
            Log.d(TAG, "Bound to CounterBoundService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services_demo);

        Button startServiceButton = findViewById(R.id.buttonStartService);
        startServiceButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SyncStartedService.class);
            startService(intent);
        });

        Button incrementButton = findViewById(R.id.buttonIncrementBound);
        incrementButton.setOnClickListener(v -> {
            if (isBound) {
                Log.d(TAG, "Bound counter is now: " + counterService.incrementAndGet());
            }
        });

        Button insertNoteButton = findViewById(R.id.buttonInsertNote);
        insertNoteButton.setOnClickListener(v -> insertNoteViaContentResolver());

        Button queryNotesButton = findViewById(R.id.buttonQueryNotes);
        queryNotesButton.setOnClickListener(v -> queryNotesViaContentResolver());

        // Observing changes -- reacting whenever the provider's data changes, from ANY caller
        // (this app's own insert() below, or in principle another app entirely).
        getContentResolver().registerContentObserver(NotesProvider.CONTENT_URI, true,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        Log.d(TAG, "Notes data changed -- would refresh the notes list here");
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, CounterBoundService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Always pair bindService()/unbindService() symmetrically in matching lifecycle
        // callbacks -- calling unbindService() without a prior successful bind, or leaking a
        // bind that's never released, both cause crashes or resource leaks.
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void insertNoteViaContentResolver() {
        ContentValues values = new ContentValues();
        values.put("title", "Shopping list");
        values.put("body", "Milk, eggs, bread");
        Uri newNoteUri = getContentResolver().insert(NotesProvider.CONTENT_URI, values);
        Log.d(TAG, "Inserted note at: " + newNoteUri);
    }

    private void queryNotesViaContentResolver() {
        // Cursor implements Closeable -- try-with-resources guarantees it's closed even if an
        // exception is thrown while iterating, avoiding a classic native-resource leak.
        try (Cursor cursor = getContentResolver().query(
                NotesProvider.CONTENT_URI,
                new String[]{"_id", "title", "body"},
                null, null, "title ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                    Log.d(TAG, "Note: " + title);
                }
            }
        }
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * Matching AndroidManifest.xml entries:
 *
 * <application>
 *     <activity android:name=".ServicesDemoActivity" android:exported="true">
 *         <intent-filter>
 *             <action android:name="android.intent.action.MAIN" />
 *             <category android:name="android.intent.category.LAUNCHER" />
 *         </intent-filter>
 *     </activity>
 *
 *     <service android:name=".SyncStartedService" android:exported="false" />
 *     <service android:name=".CounterBoundService" android:exported="false" />
 *
 *     <!-- Scope the provider with read/write permissions (see the Permissions System file
 *          for declaring custom signature-level permissions) if it must remain exported;
 *          set exported="false" if it's purely for internal use within this app. -->
 *     <provider
 *         android:name=".NotesProvider"
 *         android:authorities="com.example.advancedcomponents.notesprovider"
 *         android:exported="true"
 *         android:readPermission="com.example.advancedcomponents.permission.READ_NOTES"
 *         android:writePermission="com.example.advancedcomponents.permission.WRITE_NOTES" />
 * </application>
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_services_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <Button android:id="@+id/buttonStartService"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Start sync service" />
 *     <Button android:id="@+id/buttonIncrementBound"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Increment bound counter" />
 *     <Button android:id="@+id/buttonInsertNote"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Insert note via ContentResolver" />
 *     <Button android:id="@+id/buttonQueryNotes"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Query notes via ContentResolver" />
 * </LinearLayout>
 */
