# What a Broadcast Is

--> A **broadcast** is a system-wide message that the OS (or any app) publishes and that any number of interested components can RECEIVE -- unlike an Intent that starts a single specific Activity/Service, a broadcast is inherently one-to-many (or one-to-zero, if nothing is listening), used for announcing events rather than requesting a specific action from a specific component.
--> A **`BroadcastReceiver`** is the component that receives these -- its `onReceive(Context, Intent)` callback runs on the MAIN thread and must return QUICKLY (roughly a few seconds at most before the OS considers it unresponsive and may kill the process) -- it is not a place to do real work, only to react briefly (e.g. kick off a `WorkManager` job, post a notification, update a small piece of state).

```java
public class NetworkChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isConnected = cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
        // React briefly -- e.g. enqueue a WorkManager sync job -- do NOT block here.
    }
}
```

# Static (Manifest) Registration

--> Declaring a `<receiver>` in the manifest means the OS can deliver a broadcast to it even if the app's process is NOT currently running -- the OS starts the process briefly just to deliver the broadcast.

```xml
<receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

--> **Since API 26 (Android 8), most IMPLICIT broadcasts can no longer be received via a manifest-declared receiver at all** -- this was a deliberate change to curb apps waking themselves (and draining battery) in response to system-wide events they weren't actively using -- a fixed list of broadcasts is exempted (e.g. `BOOT_COMPLETED`, and a handful of others tied to legitimate always-on use cases); everything else requires dynamic registration while the app is actually running, or a `JobScheduler`/`WorkManager` constraint that achieves the same goal (e.g. "run when network becomes available") without a background wake-up.
--> `android:exported` must be explicitly declared for any receiver with an `<intent-filter>` on API 31+, exactly as with Activities/Services (see the Intents file).

# Dynamic (Runtime) Registration

--> A receiver registered in CODE only receives broadcasts while the registering component is alive, and must be explicitly UNREGISTERED to avoid leaking it -- this is now the required approach for most system broadcasts, and is also simply the right choice whenever a receiver's relevance is tied to a screen being visible.

```java
private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        updateBatteryUi(level);
    }
};

@Override
protected void onStart() {
    super.onStart();
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
}

@Override
protected void onStop() {
    super.onStop();
    unregisterReceiver(batteryReceiver);
}
```

--> **`ContextCompat.registerReceiver()`** (AndroidX) is the modern way to register dynamically -- since API 33, plain `registerReceiver()` REQUIRES explicitly specifying `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`, and `ContextCompat`'s version smooths this over across API levels; use `RECEIVER_NOT_EXPORTED` unless the receiver genuinely needs to receive broadcasts sent by OTHER apps.
--> Always unregister in the mirroring lifecycle callback (`onStart`/`onStop`, or `onResume`/`onPause`) -- forgetting to unregister causes a leaked receiver reference, and re-registering the SAME instance without unregistering first throws `IllegalArgumentException: Receiver already registered`.

# Common System Broadcasts

| Action | Fires When |
|---|---|
| `Intent.ACTION_BOOT_COMPLETED` | Device finishes booting (requires `RECEIVE_BOOT_COMPLETED` permission; one of the few exempted implicit broadcasts still deliverable via manifest registration). |
| `ConnectivityManager.CONNECTIVITY_ACTION` | Network connectivity changes (deprecated for manifest use; prefer `NetworkCallback` via `ConnectivityManager` for active monitoring). |
| `Intent.ACTION_BATTERY_CHANGED` | Battery level/state changes (a "sticky" broadcast -- the last one is cached and delivered immediately to a new dynamic registrant). |
| `Intent.ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` | Device plugged in / unplugged from power. |
| `Intent.ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` | Screen turns on/off (implicit-only, dynamic registration required). |
| `Intent.ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` | An app is installed/uninstalled on the device. |

# Sending Custom Broadcasts

--> An app can broadcast its OWN custom events, either for other apps to hear (a public/system-wide broadcast) or purely to communicate BETWEEN its own components without a direct reference to each other.

```java
Intent intent = new Intent("com.example.app.ACTION_DATA_SYNCED");
intent.putExtra("RECORD_COUNT", 42);
context.sendBroadcast(intent);
```

--> **`LocalBroadcastManager`** (the historical way to send broadcasts confined to your OWN app's process, without the overhead/exposure of a system-wide broadcast) is now DEPRECATED -- for in-app component communication, prefer a `ViewModel` shared via `LiveData`/observer pattern, an event bus library, or Kotlin Flow, since those don't involve the Intent/broadcast machinery at all and are more directly typed.
--> Custom broadcasts sent app-wide via `sendBroadcast()` are receivable by any app with a matching dynamically-registered receiver -- use `sendBroadcast(intent, permission)` (requiring the receiver to hold a specific permission) if the broadcast carries sensitive data and shouldn't be readable by arbitrary apps.

# Jetpack Architecture Components -- Why They Exist

--> Handling configuration changes (see the Activities and Lifecycle file) by manually saving/restoring every piece of UI state in `onSaveInstanceState()` becomes unwieldy as screens grow more complex, and mixing network/database calls directly into an Activity/Fragment tightly couples business logic to a component the OS can destroy at any time -- **Jetpack Architecture Components** (`ViewModel`, `LiveData`, `Room`, `WorkManager`, `Navigation`, `DataBinding`, `Lifecycle`) were introduced as an official, opinionated set of libraries addressing exactly these recurring pain points.

# ViewModel -- Surviving Configuration Changes

--> A **`ViewModel`** is a class specifically designed to hold and manage UI-related data that needs to SURVIVE configuration changes -- when the OS destroys and recreates an Activity due to rotation, the `ViewModel` instance associated with it is RETAINED (not recreated), so in-memory state (a loaded list, a network-fetched result) doesn't need to be re-fetched or manually saved/restored via a Bundle.

```java
public class UserViewModel extends ViewModel {
    private final MutableLiveData<List<User>> users = new MutableLiveData<>();

    public LiveData<List<User>> getUsers() { return users; }

    public void loadUsers() {
        // In real code, this delegates to a Repository which itself uses a background thread
        new Thread(() -> {
            List<User> result = fetchUsersFromNetwork();
            users.postValue(result);   // postValue() is thread-safe for calling off the main thread
        }).start();
    }

    @Override
    protected void onCleared() {
        // Called when the ViewModel is finally destroyed for good (not just a config change) --
        // cancel any in-flight work here.
    }
}
```

```java
public class UserListActivity extends AppCompatActivity {
    private UserViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        viewModel = new ViewModelProvider(this).get(UserViewModel.class);
        viewModel.getUsers().observe(this, users -> adapter.submitList(users));

        if (savedInstanceState == null) {   // avoid re-triggering the fetch on every rotation
            viewModel.loadUsers();
        }
    }
}
```

--> A `ViewModel` is retrieved (never constructed directly with `new`) via `ViewModelProvider`, which is scoped to a `ViewModelStoreOwner` (an Activity, a Fragment, or a Navigation graph) -- requesting the SAME class from the SAME owner returns the SAME instance across configuration changes, which is the entire mechanism that makes state survive rotation without any manual Bundle work.
--> A `ViewModel` MUST NOT hold a reference to a `View`, `Activity`, `Fragment`, or any other object tied to the UI lifecycle -- doing so leaks that UI object for as long as the ViewModel is retained (which can outlive the specific Activity instance that created it, across a config change), and defeats the very problem ViewModel exists to solve; if a ViewModel genuinely needs a `Context` (e.g. for `Application`-scoped resources), use `AndroidViewModel`, which is handed the `Application` context specifically because it, unlike an Activity, never gets destroyed/recreated.
--> `onCleared()` fires only when the ViewModel is PERMANENTLY going away (the Activity finishing for good, or the Fragment's view being permanently destroyed) -- not on every configuration change, which is precisely the distinction that makes it the right place to cancel long-running work tied to that screen's real lifetime.

# LiveData -- Lifecycle-Aware Observable Data

--> **`LiveData`** is an observable data holder that is LIFECYCLE-AWARE -- it only delivers updates to observers that are in an ACTIVE lifecycle state (`STARTED` or `RESUMED`), and automatically stops notifying (and cleans up the subscription) when the observing component is destroyed -- this eliminates a whole class of bugs where a callback fires into a destroyed Activity/Fragment and crashes with a `NullPointerException` or `IllegalStateException` trying to touch views that no longer exist.

```java
MutableLiveData<Integer> counter = new MutableLiveData<>(0);

// Observing -- tied to the LifecycleOwner passed in (usually 'this' in an Activity/Fragment)
counter.observe(this, value -> textView.setText(String.valueOf(value)));

// Updating
counter.setValue(counter.getValue() + 1);   // MUST be called from the main thread
counter.postValue(counter.getValue() + 1);  // safe to call from a background thread
```

--> **`MutableLiveData`** is the read-write variant used internally (typically inside a `ViewModel`); expose the read-only `LiveData` supertype from a public getter so external observers can read but not push new values -- this is the standard "encapsulated mutability" pattern for a `ViewModel`'s exposed state.
--> `setValue()` must be called on the main thread and throws if called elsewhere; `postValue()` is safe from a background thread and schedules the update onto the main thread (note: if `postValue()` is called multiple times in rapid succession before the main thread processes them, only the LAST value survives -- intermediate values can be dropped).
--> LiveData is considered somewhat supplanted by **Kotlin `Flow`/`StateFlow`** in newer Kotlin-first codebases, but remains fully supported and is still the natural fit for a Java-based Android app, since Flow is a Kotlin coroutines-based API without a first-class Java equivalent.

# The Broader Jetpack Architecture Ecosystem

--> Beyond `ViewModel`/`LiveData`, several other Architecture Components address related recurring problems -- each is covered in more depth in its own dedicated context, but a working map of the ecosystem is useful:

| Component | Problem It Solves |
|---|---|
| **Room** | A type-safe SQLite abstraction (compile-time-checked queries, auto-generated boilerplate) -- see the Data Persistence file for the underlying SQLite concepts it builds on. |
| **WorkManager** | Guaranteed, constraint-aware deferrable background work (survives app restarts and device reboots) -- the modern replacement for most started-Service-based background scheduling. |
| **Navigation Component** | A graph-based, type-safe way to manage Fragment/Activity destinations and the back stack declaratively, instead of manual `FragmentTransaction` calls. |
| **Data Binding / View Binding** | Generates typed accessors for layout XML views, removing `findViewById()` boilerplate (View Binding) and optionally binding layout XML attributes directly to observable data (Data Binding). |
| **Paging** | Loads and displays large data sets (from a database or network) a page at a time inside a `RecyclerView`, integrating with `LiveData`/Flow for automatic UI updates as pages load. |
| **Lifecycle** | The foundational library underneath ViewModel/LiveData -- `Lifecycle`, `LifecycleOwner`, and `LifecycleObserver` let any class react to lifecycle events without manually overriding Activity/Fragment callbacks. |

--> These components are designed to be used TOGETHER, and largely define what "idiomatic modern Android architecture" means in practice: a `ViewModel` exposes `LiveData` derived from a Repository, the Repository combines a `Room` database (local cache) with network calls, `WorkManager` handles anything that needs to run reliably in the background, and the UI layer (Activities/Fragments, or Compose) simply observes and renders whatever the `ViewModel` publishes -- this is commonly referred to as the app's "single source of truth" pattern, where the UI never mutates data directly but only reacts to state flowing down from the ViewModel/Repository layer.

# Common Gotchas

--> **Long-running work inside `onReceive()`** -- the callback runs on the main thread with a strict time budget; the OS may declare the receiver unresponsive (an ANR-equivalent for receivers) if it blocks too long -- delegate real work to `WorkManager` or a foreground service instead.
--> **Forgetting that most implicit broadcasts can't be manifest-registered on API 26+** -- code that worked on an older test device silently receives nothing on a modern one; check whether the specific broadcast is in the exempted list, and switch to dynamic registration (or an alternative like `WorkManager` constraints) if not.
--> **Leaking a dynamically registered receiver** by never calling `unregisterReceiver()`, or double-registering the same instance.
--> **Holding a `View`/`Activity`/`Context` reference inside a `ViewModel`** -- leaks the UI object across the ViewModel's longer lifespan; use `AndroidViewModel` + `Application` context if a Context is genuinely needed.
--> **Calling `setValue()` on a `LiveData` from a background thread** -- throws `IllegalStateException`; use `postValue()` instead from off the main thread.
--> **Re-triggering an expensive `ViewModel` load unconditionally in `onCreate()`** -- since `onCreate()` runs again after every configuration change, an unconditional fetch call re-runs the network/database call on every rotation even though the ViewModel (and its already-loaded data) survived; guard the initial trigger with a `savedInstanceState == null` check (or, more robustly, have the ViewModel itself track whether it has already loaded).

# Best Practices

--> Prefer dynamic registration and modern alternatives (`WorkManager`, `ConnectivityManager.NetworkCallback`) over manifest-registered broadcast receivers wherever possible, both because most implicit broadcasts no longer support it and because it avoids unnecessary process wake-ups.
--> Keep `ViewModel`s free of any Android UI framework references beyond `AndroidViewModel`'s `Application` -- this is also what makes ViewModels straightforward to unit-test without an emulator/instrumentation.
--> Expose `LiveData` (not `MutableLiveData`) from a `ViewModel`'s public API, keeping the mutable setter private to enforce a single place where state changes originate.
--> Treat this file's components as the entry point, not the destination -- `Room`, `WorkManager`, `Navigation`, and Paging each warrant their own deep dive once a real project's needs call for them.
