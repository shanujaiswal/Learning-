/*
 * NetworkingDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, Retrofit, Gson/OkHttp,
 * WorkManager, and a generated R class) to compile and run. The classes below are meant to be
 * dropped into app/src/main/java/<your/package>/ as SEPARATE .java files (ApiService,
 * RetrofitClient, User, NetworkingDemoActivity, SyncService, DataSyncWorker), plus the INTERNET
 * permission and Gradle dependencies noted at the bottom of this file.
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/06 Networking and Background Work in Android.md
 *
 * Covers:
 *     1. A Retrofit interface (@GET/@POST endpoints) + a singleton Retrofit client setup
 *     2. A note on AsyncTask's deprecation, with ExecutorService (and a coroutines mention) as
 *        the modern alternative
 *     3. A simple started Service class skeleton
 *     4. A WorkManager Worker class for deferrable, guaranteed background work
 */

package com.example.networkingdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class NetworkingDemoActivity extends AppCompatActivity {

    private static final String TAG = "NetworkingDemoActivity";

    // A single background-thread pool reused for the lifetime of the app process -- the modern
    // replacement for AsyncTask (see the note below). Not shut down in onDestroy() here because
    // it is meant to be a long-lived, app-wide pool in a real app (e.g. held in an Application
    // subclass or a DI singleton); shown at Activity scope only for illustrative simplicity.
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_networking_demo);

        demonstrateRetrofitCall();
        demonstrateModernBackgroundThreadAlternativeToAsyncTask();
        demonstrateStartingAService();
        demonstrateSchedulingWorkManagerWork();
    }

    /**
     * Retrofit: a type-safe HTTP client built from an annotated interface (ApiService, defined
     * further down). Retrofit generates the actual networking implementation at runtime via a
     * dynamic proxy, using OkHttp underneath and Gson to (de)serialize JSON <-> Java objects.
     */
    private void demonstrateRetrofitCall() {
        ApiService apiService = RetrofitClient.getInstance().create(ApiService.class);

        // Call<T> represents one preparable-but-not-yet-executed HTTP request. enqueue() runs it
        // asynchronously on an OkHttp background thread pool and delivers the result back via
        // the Callback -- onResponse()/onFailure() are posted back onto the main thread by
        // Retrofit automatically, so it is always safe to touch Views directly inside them.
        Call<List<User>> call = apiService.getUsers();
        call.enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body();
                    Log.d(TAG, "Retrofit -- fetched " + users.size() + " users");
                    for (User user : users) {
                        Log.d(TAG, "Retrofit -- user: " + user.name + " <" + user.email + ">");
                    }
                } else {
                    Log.e(TAG, "Retrofit -- server responded with error code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                // Network-level failure -- no connectivity, timeout, DNS failure, etc. (as opposed
                // to onResponse() with a non-2xx code, which means the server WAS reached).
                Log.e(TAG, "Retrofit -- network call failed", t);
            }
        });

        // POSTing a new resource, following the same enqueue()/Callback pattern.
        Call<User> createCall = apiService.createUser(new User("Grace Hopper", "grace@example.com"));
        createCall.enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                Log.d(TAG, "Retrofit -- created user, server assigned id: "
                        + (response.body() != null ? response.body().id : "?"));
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                Log.e(TAG, "Retrofit -- create user call failed", t);
            }
        });
    }

    /**
     * AsyncTask deprecation note: android.os.AsyncTask was deprecated in API level 30 (Android 11).
     * It suffered from well-known problems -- it leaked the enclosing Activity/Fragment when used
     * as a non-static inner class, its behavior around configuration changes was awkward (the
     * callback could fire after the Activity was destroyed), and its default executor's behavior
     * around concurrent vs. serial execution changed confusingly across Android versions.
     *
     * Modern alternatives:
     *   - Plain Java: java.util.concurrent.ExecutorService (shown below) + posting results back
     *     to the main thread via a Handler tied to Looper.getMainLooper(), or via runOnUiThread().
     *   - Kotlin: coroutines (viewModelScope.launch { withContext(Dispatchers.IO) { ... } }) are
     *     the officially recommended approach on Kotlin projects -- not applicable here since this
     *     file is Java, but worth knowing if the project later adds Kotlin modules.
     *   - Jetpack libraries (Retrofit, Room, WorkManager) already manage their own background
     *     threading internally, as seen elsewhere in this file -- often you don't need to manage
     *     an ExecutorService by hand at all.
     */
    private void demonstrateModernBackgroundThreadAlternativeToAsyncTask() {
        backgroundExecutor.execute(() -> {
            // Simulate some blocking work that must not run on the main thread (e.g. parsing a
            // large file, a synchronous network call, heavy computation).
            Log.d(TAG, "ExecutorService -- running background work on thread: "
                    + Thread.currentThread().getName());

            int result = 2 + 2; // stand-in for real work

            // Hop back onto the main thread to safely update UI -- runOnUiThread() is an
            // AppCompatActivity/Activity convenience that posts a Runnable to the main Looper.
            runOnUiThread(() -> Log.d(TAG, "ExecutorService -- background result delivered "
                    + "back on main thread: " + result));
        });
    }

    /** Starting the illustrative SyncService (defined further down) as a started Service. */
    private void demonstrateStartingAService() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        startService(serviceIntent);
        Log.d(TAG, "Requested start of SyncService");
    }

    /**
     * WorkManager: the recommended API for deferrable, GUARANTEED background work that should
     * still run even if the app process dies or the device reboots (WorkManager persists queued
     * work and reschedules it). Best fit for things like "upload this file eventually" or
     * "sync data periodically" -- NOT for work that must run immediately and visibly (use a
     * foreground Service for that instead).
     */
    private void demonstrateSchedulingWorkManagerWork() {
        OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(DataSyncWorker.class).build();
        WorkManager.getInstance(this).enqueue(syncWorkRequest);
        Log.d(TAG, "Enqueued DataSyncWorker via WorkManager");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // In a real app this executor would likely be app-scoped rather than Activity-scoped;
        // shutting it down here is shown only because it was created at Activity scope above.
        backgroundExecutor.shutdown();
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * User.java -- save as a SEPARATE file. Plain POJO Gson (de)serializes to/from JSON automatically
 * by matching field names (no annotations needed unless the JSON keys differ from field names).
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.networkingdemo;
 *
 * public class User {
 *     public long id;
 *     public String name;
 *     public String email;
 *
 *     public User(String name, String email) {
 *         this.name = name;
 *         this.email = email;
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * ApiService.java -- save as a SEPARATE file. Retrofit interface -- each method's annotation
 * declares the HTTP method + relative path; Retrofit builds and executes the actual request.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.networkingdemo;
 *
 * import java.util.List;
 * import retrofit2.Call;
 * import retrofit2.http.Body;
 * import retrofit2.http.GET;
 * import retrofit2.http.POST;
 * import retrofit2.http.Path;
 *
 * public interface ApiService {
 *
 *     @GET("users")
 *     Call<List<User>> getUsers();
 *
 *     @GET("users/{id}")
 *     Call<User> getUserById(@Path("id") long id);
 *
 *     @POST("users")
 *     Call<User> createUser(@Body User newUser);
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * RetrofitClient.java -- save as a SEPARATE file. A simple singleton so the whole app shares one
 * Retrofit instance (and thus one underlying OkHttp connection pool) instead of rebuilding it.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.networkingdemo;
 *
 * import retrofit2.Retrofit;
 * import retrofit2.converter.gson.GsonConverterFactory;
 *
 * public class RetrofitClient {
 *
 *     private static final String BASE_URL = "https://api.example.com/"; // must end with '/'
 *     private static Retrofit instance;
 *
 *     public static synchronized Retrofit getInstance() {
 *         if (instance == null) {
 *             instance = new Retrofit.Builder()
 *                     .baseUrl(BASE_URL)
 *                     .addConverterFactory(GsonConverterFactory.create()) // JSON <-> POJO
 *                     .build();
 *         }
 *         return instance;
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * SyncService.java -- save as a SEPARATE file. A minimal started Service skeleton -- runs on the
 * MAIN thread by default (a plain Service does NOT get a background thread for free; for real
 * work you would spawn one yourself, or prefer an IntentService/WorkManager/foreground Service
 * with its own thread). Shown only to illustrate the Service lifecycle shape.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.networkingdemo;
 *
 * import android.app.Service;
 * import android.content.Intent;
 * import android.os.IBinder;
 * import android.util.Log;
 * import androidx.annotation.Nullable;
 *
 * public class SyncService extends Service {
 *
 *     private static final String TAG = "SyncService";
 *
 *     @Override
 *     public void onCreate() {
 *         super.onCreate();
 *         Log.d(TAG, "onCreate() -- Service instance is being created");
 *     }
 *
 *     @Override
 *     public int onStartCommand(Intent intent, int flags, int startId) {
 *         Log.d(TAG, "onStartCommand() -- Service was started via startService()/startForegroundService()");
 *         // Do the work here (ideally off the main thread for anything non-trivial), then call
 *         // stopSelf() when done since a started Service otherwise keeps running indefinitely.
 *         stopSelf();
 *         // START_NOT_STICKY: if the system kills this Service to reclaim memory, do not
 *         // automatically recreate it -- appropriate for one-shot work like this sync example.
 *         return START_NOT_STICKY;
 *     }
 *
 *     @Nullable
 *     @Override
 *     public IBinder onBind(Intent intent) {
 *         // Returning null means this is a "started" Service only, not a "bound" one -- no
 *         // client can bind to it via bindService(); see the Theory chapter for the distinction.
 *         return null;
 *     }
 *
 *     @Override
 *     public void onDestroy() {
 *         super.onDestroy();
 *         Log.d(TAG, "onDestroy() -- Service is being torn down");
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * DataSyncWorker.java -- save as a SEPARATE file. WorkManager instantiates this class itself
 * (it must have a (Context, WorkerParameters) constructor) and calls doWork() on a background
 * thread it manages -- no manual threading needed inside a Worker.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.networkingdemo;
 *
 * import android.content.Context;
 * import android.util.Log;
 * import androidx.annotation.NonNull;
 * import androidx.work.Worker;
 * import androidx.work.WorkerParameters;
 *
 * public class DataSyncWorker extends Worker {
 *
 *     private static final String TAG = "DataSyncWorker";
 *
 *     public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
 *         super(context, params);
 *     }
 *
 *     @NonNull
 *     @Override
 *     public Result doWork() {
 *         Log.d(TAG, "doWork() -- running guaranteed background sync work");
 *         try {
 *             // Simulated real work -- e.g. an upload, a database sync, etc.
 *             Thread.sleep(500);
 *             Log.d(TAG, "doWork() -- sync completed successfully");
 *             return Result.success();
 *         } catch (InterruptedException e) {
 *             Thread.currentThread().interrupt();
 *             Log.e(TAG, "doWork() -- interrupted, will retry", e);
 *             return Result.retry(); // WorkManager will reschedule this Worker with backoff
 *         }
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * AndroidManifest.xml requirements:
 *
 * <uses-permission android:name="android.permission.INTERNET" />
 * <service android:name=".SyncService" android:exported="false" />
 *
 * app/build.gradle (Module) dependencies needed:
 *
 * dependencies {
 *     implementation("com.squareup.retrofit2:retrofit:2.11.0")
 *     implementation("com.squareup.retrofit2:converter-gson:2.11.0")
 *     implementation("androidx.work:work-runtime:2.9.0")
 * }
 */
