# Why Background Work Is a First-Class Android Concern

--> Android enforces that ALL potentially slow operations (network calls, disk I/O, database queries, heavy computation) must happen OFF the main (UI) thread -- the main thread is responsible for drawing frames and responding to input, and blocking it for more than a short window (historically cited as ~5 seconds for input dispatch) triggers an **ANR (Application Not Responding)** dialog, letting the user force-quit the app.
--> Over the years Android has offered (and deprecated) several different concurrency primitives -- understanding WHY each was deprecated is as useful as knowing the current recommended tool, since a lot of existing code and tutorials still reference the older ones.

# The Network Permission and Basic HTTP Concepts

--> Any network access requires the `INTERNET` permission in the manifest (a normal, not dangerous, permission -- granted automatically at install, no runtime prompt needed):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

--> At the lowest level, Android apps can use `HttpURLConnection` (built into the JDK) directly for HTTP requests -- functional, but verbose: manual stream handling, manual JSON parsing, manual error/retry handling, and it's easy to leak connections or forget to close streams.

```java
// Low-level approach -- shown for understanding, rarely written by hand in real projects
URL url = new URL("https://api.example.com/users");
HttpURLConnection connection = (HttpURLConnection) url.openConnection();
try {
    connection.setRequestMethod("GET");
    int responseCode = connection.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        // manually parse response.toString() as JSON here
    }
} finally {
    connection.disconnect();
}
```

# Retrofit -- The Standard HTTP Client Library

--> **Retrofit** (by Square) is the de facto standard library for HTTP networking in Android/Java -- it turns an HTTP API into a Java INTERFACE, using annotations to describe each endpoint, and generates the implementation (including request building, execution, and response parsing) at runtime.

```java
// 1) Define the API as an interface
public interface ApiService {
    @GET("users/{id}")
    Call<User> getUser(@Path("id") int userId);

    @GET("users")
    Call<List<User>> getAllUsers();

    @POST("users")
    Call<User> createUser(@Body User newUser);
}
```

```java
// 2) Build a Retrofit instance (typically once, as a singleton)
Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .addConverterFactory(GsonConverterFactory.create())   // auto JSON <-> Java object mapping
        .build();

ApiService api = retrofit.create(ApiService.class);
```

```java
// 3) Make an asynchronous call -- runs off the main thread automatically, delivers result via callback
api.getUser(42).enqueue(new Callback<User>() {
    @Override
    public void onResponse(Call<User> call, Response<User> response) {
        if (response.isSuccessful()) {
            User user = response.body();
            // IMPORTANT: this callback fires on the MAIN thread -- safe to update UI directly here
        } else {
            // handle non-2xx HTTP response (404, 500, etc.)
        }
    }

    @Override
    public void onFailure(Call<User> call, Throwable t) {
        // handle network failure (no connectivity, timeout, DNS failure, etc.)
    }
});
```

--> **`enqueue()` vs `execute()`** -- `enqueue()` runs the request asynchronously on a background thread pool and delivers the result via callback on the main thread; `execute()` runs SYNCHRONOUSLY on whatever thread calls it -- calling `execute()` directly on the main thread would block it and is effectively never correct; it's meant to be called from code that's already off the main thread.
--> **Converter factories** -- Retrofit doesn't parse JSON itself; a converter factory (commonly Gson, Moshi, or kotlinx.serialization) plugs in the actual serialization logic, translating between JSON and Java model classes automatically based on field names.
--> **OkHttp** -- Retrofit is built on top of OkHttp (also by Square), which handles the actual HTTP transport, connection pooling, and supports **Interceptors** for cross-cutting concerns like adding auth headers to every request or logging requests/responses.

```java
OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(chain -> {
            Request original = chain.request();
            Request authorized = original.newBuilder()
                    .header("Authorization", "Bearer " + getAuthToken())
                    .build();
            return chain.proceed(authorized);
        })
        .build();

Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build();
```

# AsyncTask -- Deprecated, But Important to Recognize

--> **`AsyncTask`** was, for many years, the standard built-in way to run background work and publish results to the UI thread -- it is now **DEPRECATED as of API 30** and REMOVED as a recommended pattern; a huge amount of older tutorials and legacy code still use it, so recognizing it is important even though it shouldn't be used in new code.

```java
// Historical pattern -- shown for RECOGNITION, not recommended for new code
private class FetchDataTask extends AsyncTask<String, Integer, String> {
    @Override
    protected String doInBackground(String... urls) {
        // runs on a background thread
        return performNetworkCall(urls[0]);
    }

    @Override
    protected void onPostExecute(String result) {
        // runs on the main thread -- safe to update UI
        textView.setText(result);
    }
}
// Usage: new FetchDataTask().execute("https://api.example.com/data");
```

--> **Why it was deprecated** -- `AsyncTask` had confusing lifecycle-coupling behavior (tasks kept running even after the hosting Activity was destroyed, risking leaks and crashes updating a dead UI), inconsistent execution behavior that changed across API levels (serial vs parallel execution defaults changed between versions, surprising developers), and no built-in cancellation-on-configuration-change handling.
--> **Modern replacements** -- for Java-based Android code, the practical replacements are: `java.util.concurrent.Executors` (a plain thread pool) combined with posting results back to the main thread via a `Handler`, or `ListenableFuture`/`Executors` + explicit lifecycle-aware callback dispatch; Retrofit's own `enqueue()` (shown above) already avoids the problem entirely for network calls specifically; Kotlin-based projects use coroutines, which aren't directly available in plain Java.

```java
// A simple modern Executor + Handler replacement pattern
private final ExecutorService executor = Executors.newSingleThreadExecutor();
private final Handler mainHandler = new Handler(Looper.getMainLooper());

private void loadDataInBackground() {
    executor.execute(() -> {
        String result = performExpensiveWork();          // runs on background thread
        mainHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) {        // guard against updating a dead Activity
                textView.setText(result);                    // runs back on the main thread
            }
        });
    });
}
```

# Services -- Running Work Without a UI

--> A **Service** is an Android component for performing work in the background WITHOUT a user interface -- unlike a background thread tied to an Activity, a Service can (depending on type) continue running even after the user leaves the Activity that started it.
--> **Started Service** -- launched via `startService()`/`ContextCompat.startForegroundService()`, runs independently until it stops itself (`stopSelf()`) or is stopped externally (`stopService()`) -- does NOT return a result directly to the caller.

```java
public class SyncService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // do the background work, e.g. on a new thread since Service callbacks still run on the main thread
        new Thread(() -> {
            performSync();
            stopSelf();   // stop the Service once work is done
        }).start();
        return START_NOT_STICKY;   // don't automatically restart if the system kills the process
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;   // null for a pure Started Service (not bindable)
    }
}
```

--> **Bound Service** -- launched via `bindService()`, provides a client-server interface (an `IBinder`) that other components can call methods on directly -- lives only as long as at least one client is bound to it, and is automatically destroyed when the last client unbinds.
--> **Foreground Service** -- a Started Service that must show a persistent, ongoing notification (visible to the user) while it runs -- required by the OS for genuinely long-running, user-visible background work (e.g. music playback, an active navigation session, an ongoing file upload/download) specifically so the user is always aware something is running and can stop it; unlike a regular Started Service, the OS is much less likely to kill a Foreground Service under memory pressure.
--> **Important restriction -- Service callbacks run on the main thread by default**, just like Activity callbacks -- a Service is NOT automatically a background thread; it's a component that survives independently of an Activity's lifecycle. Actual long-running work inside a Service still needs its own thread/Executor, exactly as shown above.

# WorkManager -- The Modern Answer for Deferrable Background Work

--> **WorkManager** (Jetpack) is the currently RECOMMENDED API for deferrable, guaranteed background work -- work that should run even if the app is closed or the device restarts, such as periodic data sync, uploading logs, or processing a queued task -- and it intelligently delegates to the best underlying mechanism available on the current API level (JobScheduler, a Firebase JobDispatcher-like approach, or an internal AlarmManager+BroadcastReceiver combo on very old devices), so you write one API regardless of OS version.

```java
public class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            performUpload();          // runs on a WorkManager-managed background thread automatically
            return Result.success();
        } catch (Exception e) {
            return Result.retry();     // WorkManager will reschedule with backoff
        }
    }
}
```

```java
// One-time work with constraints (e.g. only run when connected to WiFi and charging)
Constraints constraints = new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresCharging(true)
        .build();

OneTimeWorkRequest uploadRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build();

WorkManager.getInstance(context).enqueue(uploadRequest);

// Periodic work (minimum interval is 15 minutes, an OS-enforced floor)
PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(SyncWorker.class,
        15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build();

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "periodic_sync", ExistingPeriodicWorkPolicy.KEEP, syncRequest);
```

--> **Why WorkManager over a raw Service for this kind of work** -- it survives app process death AND device reboot (work is persisted and rescheduled automatically), supports declarative constraints (network type, charging state, storage availability) without writing that logic yourself, and provides built-in retry/backoff -- a plain Service gives you none of this for free.
--> **When to still use a Service directly** -- WorkManager is explicitly for DEFERRABLE work, not work that must start immediately and stay continuously, visibly running (e.g. active music playback or a live navigation session) -- that's still the domain of a Foreground Service.

# Choosing the Right Tool -- Quick Reference

| Need | Tool |
|---|---|
| Make an HTTP API call | Retrofit (+ OkHttp), off the main thread automatically via `enqueue()` |
| Short background computation tied to current screen | `ExecutorService` + `Handler` (or a `ViewModel`-scoped coroutine/RxJava equivalent) |
| Continuous, user-visible background task (music, ongoing upload) | Foreground `Service` |
| Deferrable work that must eventually run, survive restarts | `WorkManager` |
| Legacy code you're reading, not writing | `AsyncTask` (recognize it, don't add new usages) |

# Common Gotchas

--> **Leaking an Activity/View reference from a background callback** -- a network callback that outlives the Activity (e.g. the user navigated away before the response arrived) and then touches a destroyed Activity's Views can crash or silently do nothing useful -- always guard UI updates with lifecycle checks, or better, route results through a `ViewModel` + `LiveData` so the UI layer only reacts while actually observing.
--> **Forgetting the `INTERNET` permission** -- causes a `SecurityException` at the point of the actual network call, not at compile time, which can be confusing during first setup.
--> **Assuming a Service runs on a background thread** -- it doesn't, by default; forgetting to spin up your own thread/Executor inside a Service's callback blocks the main thread exactly like doing the same work directly in an Activity would.
--> **Using WorkManager for work that needs sub-15-minute periodic scheduling, or truly immediate/continuous execution** -- it's not designed for that; a `PeriodicWorkRequest` has an OS-enforced 15-minute floor, and true continuous execution needs a Foreground Service instead.

# Best Practices

--> Use Retrofit for essentially all HTTP networking in a Java Android project -- writing raw `HttpURLConnection` code by hand is rarely justified today.
--> Never write new code against `AsyncTask` -- recognize it when reading legacy code, but use Executors/Handler (or WorkManager/Services, depending on the work's nature) in anything new.
--> Route background work results through a `ViewModel` and observable data (`LiveData`) rather than directly manipulating Activity/Fragment Views from a background callback -- it naturally avoids the dead-UI-reference class of bugs.
--> Reach for WorkManager by default for anything that should survive the app closing or the device rebooting; reserve Foreground Services for genuinely continuous, user-visible work.
