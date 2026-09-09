# What an Activity Is

--> An **Activity** is a single, focused screen with a user interface -- e.g. a login screen, a settings screen, a list screen -- it is the fundamental building block of Android's UI layer, roughly analogous to a "window" or "screen" in other UI frameworks, though a modern app increasingly hosts multiple Fragments inside just ONE Activity (covered in the Fragments file) rather than one Activity per screen.
--> Every Activity subclasses `android.app.Activity` (in practice, almost always `AppCompatActivity` from AndroidX, which backports newer UI features/behaviors to older API levels).
--> Critically, an Activity's constructor is NEVER called directly by your code -- the Android OS instantiates it via reflection when needed, which is exactly why initialization logic belongs in lifecycle CALLBACK methods, not the constructor.

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);   // inflate the XML layout for this screen
    }
}
```

# The Activity Lifecycle

--> The OS moves every Activity through a well-defined sequence of states, and calls a specific callback method as each transition happens -- your job is to do the right work (allocate resources, save state, release resources) in the right callback, because you never control WHEN these transitions happen, only HOW you respond to them.

```text
                 App launched
                      |
                      v
                 onCreate()  <-- one-time setup: inflate layout, init variables, restore saved state
                      |
                      v
                  onStart()  <-- Activity becoming visible
                      |
                      v
                 onResume()  <-- Activity in the foreground, interactive  <-----+
                      |                                                         |
                 [Activity running, user interacting]                          |
                      |                                                         |
                      v                                                         |
                  onPause()  <-- losing focus (e.g. dialog popped, ---> onResume() (returning)
                      |            or new Activity partially covers this one)
                      v
                  onStop()   <-- no longer visible (e.g. user pressed Home,   ---> onRestart() -> onStart()
                      |            navigated to another full-screen Activity)      (returning)
                      v
                onDestroy()  <-- Activity being finished or the OS is
                                  reclaiming its memory
```

--> **`onCreate(Bundle savedInstanceState)`** -- called exactly once when the Activity is first created -- this is where you call `setContentView()`, find/bind views, set up click listeners, and restore any previously saved instance state.
--> **`onStart()`** -- the Activity is about to become visible to the user (but may not yet be interactive).
--> **`onResume()`** -- the Activity is now in the foreground and the user can interact with it -- this is where you'd start things that must only run while actively visible AND focused, like a camera preview or resuming an animation.
--> **`onPause()`** -- called when the Activity is losing focus but MAY still be partially visible (e.g. a semi-transparent dialog is now on top) -- must be fast, since the next Activity won't resume until this returns; a classic place to pause video playback or release a camera resource that only one app can hold at a time.
--> **`onStop()`** -- the Activity is no longer visible at all -- a good place to stop expensive updates (e.g. unregister a location listener) that don't need to run when off-screen.
--> **`onRestart()`** -- called only when coming back to a STOPPED Activity, right before `onStart()` fires again.
--> **`onDestroy()`** -- final cleanup before the Activity object is discarded -- either because it (or the whole task) is finishing, or because the OS destroyed it due to a configuration change or memory pressure.

--> **Not every callback is guaranteed to run** -- if the system needs to kill the app's process abruptly to reclaim memory, `onPause()`/`onStop()` may run but `onDestroy()` is NOT guaranteed to be called -- which is exactly why state that MUST be saved belongs in `onSaveInstanceState()` / `onPause()`, not `onDestroy()`.

# Configuration Changes and Activity Recreation

--> By default, a **configuration change** (screen rotation, language change, keyboard availability change, dark/light theme switch) causes the OS to completely DESTROY and RECREATE the current Activity -- this catches almost every beginner off guard the first time they rotate a device mid-test and watch their in-memory state vanish.

```java
@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("counter_value", counter);      // survives rotation-triggered recreation
    outState.putString("user_input", editText.getText().toString());
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (savedInstanceState != null) {
        counter = savedInstanceState.getInt("counter_value", 0);   // restore after recreation
    }
}
```

--> `onSaveInstanceState()` is called BEFORE the Activity is destroyed due to a configuration change (or being backgrounded and later reclaimed) -- it's meant for small amounts of transient UI state (scroll position, a typed-but-unsaved value, a selected tab), not for persisting large or permanent data (that belongs in a database or file, covered in the Data Persistence file).
--> The `Bundle` passed to `onCreate()` and `onRestoreInstanceState()` is `null` on a fresh launch and non-null when recreating after a configuration change or process death -- checking for `null` is the standard way to distinguish "fresh start" from "recreated."

# Intents -- Requesting an Action, Explicit or Implicit

--> An **Intent** is a messaging object used to request an action from another app component -- most commonly, to start a new Activity, but also to start a Service or deliver a broadcast. There are two flavors:

```java
// EXPLICIT intent -- names the exact target component by class -- used for navigation WITHIN your own app
Intent explicitIntent = new Intent(MainActivity.this, DetailActivity.class);
startActivity(explicitIntent);

// IMPLICIT intent -- describes an ACTION to perform, letting the OS pick a component (possibly from
// another installed app) that has registered to handle it
Intent implicitIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com"));
startActivity(implicitIntent);   // OS shows a browser (or a chooser if multiple apps can handle it)

Intent shareIntent = new Intent(Intent.ACTION_SEND);
shareIntent.setType("text/plain");
shareIntent.putExtra(Intent.EXTRA_TEXT, "Check this out!");
startActivity(Intent.createChooser(shareIntent, "Share via"));   // forces an app-picker dialog
```

--> **Explicit intents** are the default choice for navigating between screens you own, because they're unambiguous and don't depend on what else is installed.
--> **Implicit intents** are how Android apps interoperate -- e.g. asking "some app that can view a URL" or "some app that can share plain text" to handle a request, without your app needing to know or care which app that is.
--> **Intent filters** in the manifest (`<intent-filter>`) are how a component declares which implicit intents it's willing to handle -- matching is based on the combination of action, category, and data type declared.

# Passing Data Between Activities

--> Data travels between Activities as **extras** attached to the Intent, stored internally in a `Bundle` -- only certain types are supported directly: primitives, Strings, and objects that implement `Serializable` or (preferably, on Android) `Parcelable`.

```java
// Sending Activity
Intent intent = new Intent(MainActivity.this, DetailActivity.class);
intent.putExtra("USER_ID", 42);
intent.putExtra("USER_NAME", "Vanisha");
intent.putExtra("IS_PREMIUM", true);
startActivity(intent);

// Receiving Activity
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_detail);

    Intent intent = getIntent();
    int userId = intent.getIntExtra("USER_ID", -1);           // -1 default if key missing
    String userName = intent.getStringExtra("USER_NAME");
    boolean isPremium = intent.getBooleanExtra("IS_PREMIUM", false);
}
```

--> **`Bundle`** -- a key-value container optimized for fast inter-process transfer -- used both for Intent extras and for `onSaveInstanceState()`; keys are Strings, values must be one of a fixed set of supported types.
--> **`Parcelable` vs `Serializable`** for passing custom objects -- `Serializable` is standard Java reflection-based serialization (simple to implement, but slow and generates garbage via reflection); `Parcelable` is Android's own high-performance interface designed specifically for cross-process data transfer -- `Parcelable` is the officially recommended choice for Android, even though it requires more boilerplate (Android Studio can auto-generate it, and Kotlin's `@Parcelize` removes the boilerplate entirely on the Kotlin side).

```java
public class User implements Parcelable {
    String name;
    int age;

    protected User(Parcel in) {
        name = in.readString();
        age = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(age);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) { return new User(in); }
        @Override
        public User[] newArray(int size) { return new User[size]; }
    };
}
```

# Getting a Result Back From an Activity

--> Older Android code used `startActivityForResult()`/`onActivityResult()`, but this is now DEPRECATED in favor of the **Activity Result API**, which is type-safe and doesn't require overriding a single giant `onActivityResult()` shared across every possible request in the Activity.

```java
private ActivityResultLauncher<Intent> launcher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                String value = data != null ? data.getStringExtra("RESULT_KEY") : null;
                // handle the returned value
            }
        });

// Launching:
launcher.launch(new Intent(this, DetailActivity.class));

// In the launched Activity, before finishing:
Intent resultIntent = new Intent();
resultIntent.putExtra("RESULT_KEY", "some value");
setResult(Activity.RESULT_OK, resultIntent);
finish();
```

--> `registerForActivityResult()` MUST be called unconditionally during Activity/Fragment initialization (typically as a field initializer or in `onCreate`), never inside a click listener or conditional branch -- it registers a callback with the framework that must exist before the Activity reaches `STARTED`, or it throws an `IllegalStateException`.

# The Task Back Stack

--> Activities live inside a **task** -- a stack of Activities the user has navigated through -- pressing the system Back button pops the top Activity off this stack and returns to the one beneath it.
--> **Launch modes** (`android:launchMode` in the manifest, or flags on the Intent) control how a new Activity instance interacts with the existing stack:

| Launch Mode | Behavior |
|---|---|
| `standard` (default) | Always creates a brand-new instance, even if one already exists on top. |
| `singleTop` | Reuses the existing instance (calling `onNewIntent()`) only if it's already at the TOP of the stack. |
| `singleTask` | Reuses a single instance across the whole task, clearing anything above it if it already exists. |
| `singleInstance` | Like `singleTask`, but this Activity gets its OWN separate task entirely. |

--> `onNewIntent(Intent intent)` is called instead of `onCreate()` when an existing Activity instance is being reused (`singleTop`/`singleTask`) and a new Intent targets it -- remember to call `setIntent(intent)` inside it if later code relies on `getIntent()` returning the latest one.

# Common Gotchas

--> **Memory leaks from holding an Activity Context too long** -- storing a `static` reference to an Activity (or passing its Context into a long-lived singleton/listener) prevents the Activity from being garbage collected even after `onDestroy()`, since something still references it -- prefer `getApplicationContext()` for anything that must outlive the Activity, and always null out/unregister listeners in `onDestroy()`/`onStop()`.
--> **Doing heavy work in `onCreate()`/`onResume()`** -- these run on the MAIN (UI) thread; blocking work here causes visible jank or an ANR (Application Not Responding) dialog -- long-running work belongs off the main thread (see the Networking and Background Work file).
--> **Forgetting `super.onX()` calls** -- every lifecycle override should call the superclass implementation (usually first, except `onBackPressed`-style methods where order can matter) -- skipping it can silently break framework bookkeeping.
--> **Relying on `onDestroy()` for critical saves** -- as noted above, it's not guaranteed to run; use `onPause()`/`onStop()`/`onSaveInstanceState()` for anything that must reliably persist.

# Best Practices

--> Keep Activities thin -- delegate UI logic to Fragments/Views and business logic to separate classes (ViewModels, repositories) rather than piling everything into the Activity, which becomes unmanageable as the app grows.
--> Always provide sensible default values when reading Intent extras (`getIntExtra(key, defaultValue)`) -- never assume an extra is present, since any component (including a malicious one, for exported Activities) could start your Activity without it.
--> Use the Activity Result API for all new code -- `startActivityForResult` is deprecated and lacks the improved lifecycle-safety and composability of the newer API.
--> Test configuration changes explicitly (rotate the emulator, or enable "Don't keep activities" in Developer Options) early and often -- it surfaces state-loss bugs immediately instead of after a confusing bug report.
