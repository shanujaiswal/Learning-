/*
 * JetpackDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The classes below are meant
 * to be dropped into app/src/main/java/<your/package>/ as SEPARATE .java files
 * (BootReceiver.java, CounterViewModel.java, JetpackDemoActivity.java respectively) in a
 * project that also declares matching manifest entries (sketched at the bottom of this
 * file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/12 Broadcast Receivers and Jetpack Architecture Components.md
 *
 * Covers:
 *     1. A BroadcastReceiver with STATIC (manifest) registration (BootReceiver, for the
 *        exempted BOOT_COMPLETED implicit broadcast) and DYNAMIC (runtime) registration
 *        (a battery-level receiver registered/unregistered in mirroring lifecycle callbacks)
 *     2. A ViewModel class surviving configuration changes (CounterViewModel), including the
 *        AndroidViewModel variant note and onCleared() for cancelling in-flight work
 *     3. A LiveData usage example observed from an Activity, with MutableLiveData exposed as
 *        read-only LiveData, setValue() vs postValue(), and a savedInstanceState == null
 *        guard against re-triggering an expensive load on every rotation
 */

package com.example.advancedcomponents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * BootReceiver -- STATIC (manifest) registration. Declaring a <receiver> in the manifest
 * means the OS can deliver this broadcast even if the app's process is NOT currently
 * running (the OS starts the process briefly just to deliver it). BOOT_COMPLETED is one of
 * the few implicit broadcasts still exempted for manifest-registered delivery on API 26+ --
 * most others require dynamic registration instead (see BatteryLevelReceiver usage below).
 */
class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // onReceive() runs on the MAIN thread and must return QUICKLY (a few seconds at
            // most) -- this is not a place to do real work, only to react briefly, e.g. by
            // enqueueing a WorkManager job.
            Log.d(TAG, "Device finished booting -- would enqueue a WorkManager sync job here");
        }
    }
}

/**
 * CounterViewModel -- a ViewModel holding UI-related data that needs to SURVIVE
 * configuration changes. When the OS destroys and recreates an Activity due to rotation,
 * this instance is RETAINED (not recreated), so in-memory state doesn't need to be
 * re-fetched or manually saved/restored via a Bundle.
 *
 * IMPORTANT: a ViewModel MUST NOT hold a reference to a View, Activity, Fragment, or any
 * other UI-lifecycle-tied object -- doing so leaks it for as long as the ViewModel is
 * retained (which can outlive the specific Activity instance that created it). If a
 * Context is genuinely needed (e.g. for Application-scoped resources), extend
 * AndroidViewModel instead, which is handed the Application context specifically because
 * it never gets destroyed/recreated:
 *
 *     public class CounterViewModel extends AndroidViewModel {
 *         public CounterViewModel(Application application) { super(application); }
 *     }
 */
class CounterViewModel extends ViewModel {

    // MutableLiveData is the read-write variant, kept private/internal -- only a read-only
    // LiveData supertype is exposed publicly, so external observers can read but never push
    // new values themselves (the standard "encapsulated mutability" pattern).
    private final MutableLiveData<Integer> counter = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> loadedItems = new MutableLiveData<>();

    private boolean hasLoadedOnce = false;

    public LiveData<Integer> getCounter() {
        return counter;
    }

    public LiveData<List<String>> getLoadedItems() {
        return loadedItems;
    }

    /** Called from a click handler on the main thread -- setValue() is required here. */
    public void increment() {
        Integer current = counter.getValue();
        counter.setValue((current == null ? 0 : current) + 1); // must be called from the main thread
    }

    /**
     * Simulates a one-time expensive load (e.g. a network/database fetch delegated to a
     * Repository in real code). Tracks hasLoadedOnce internally so the ViewModel itself can
     * refuse a redundant reload, which is more robust than relying solely on the Activity's
     * savedInstanceState == null check.
     */
    public void loadItemsIfNeeded() {
        if (hasLoadedOnce) {
            return;
        }
        hasLoadedOnce = true;

        // In real code this delegates to a Repository which itself uses a background
        // thread/executor; a raw Thread is used here purely for illustration.
        new Thread(() -> {
            List<String> result = fetchItemsFromNetwork();
            // postValue() is thread-safe for calling off the main thread -- setValue() would
            // throw IllegalStateException if called here. Note: if postValue() is called
            // multiple times in rapid succession before the main thread processes them, only
            // the LAST value survives; intermediate values can be dropped.
            loadedItems.postValue(result);
        }).start();
    }

    private List<String> fetchItemsFromNetwork() {
        try {
            Thread.sleep(800); // simulated network latency
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        List<String> items = new ArrayList<>();
        items.add("Broadcast Receivers");
        items.add("ViewModel");
        items.add("LiveData");
        return items;
    }

    @Override
    protected void onCleared() {
        // Called when the ViewModel is finally destroyed for good (the Activity finishing
        // for good) -- NOT on every configuration change, which is precisely what makes this
        // the right place to cancel long-running work tied to this screen's real lifetime.
        super.onCleared();
        Log.d("CounterViewModel", "onCleared() -- cancel any in-flight work here");
    }
}

/**
 * JetpackDemoActivity -- ties together dynamic BroadcastReceiver registration, ViewModel
 * retrieval via ViewModelProvider, and LiveData observation.
 */
public class JetpackDemoActivity extends AppCompatActivity {

    private static final String TAG = "JetpackDemoActivity";

    private CounterViewModel viewModel;

    // DYNAMIC registration: a receiver registered in CODE only receives broadcasts while the
    // registering component is alive, and must be explicitly unregistered to avoid leaking
    // it. This is now the required approach for most system broadcasts (ACTION_BATTERY_CHANGED
    // is implicit-only and cannot be manifest-registered on API 26+).
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            Log.d(TAG, "Battery level changed: " + level + "%");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jetpack_demo);

        // A ViewModel is retrieved (never constructed directly with 'new') via
        // ViewModelProvider, scoped to this Activity as the ViewModelStoreOwner -- requesting
        // the SAME class from the SAME owner returns the SAME instance across configuration
        // changes, which is the entire mechanism that makes state survive rotation.
        viewModel = new ViewModelProvider(this).get(CounterViewModel.class);

        TextView counterText = findViewById(R.id.textCounter);
        TextView itemsText = findViewById(R.id.textItems);

        // Observing -- tied to the LifecycleOwner passed in ('this'). LiveData only delivers
        // updates to observers in an ACTIVE lifecycle state (STARTED/RESUMED) and
        // automatically stops notifying (and cleans up) when the Activity is destroyed --
        // eliminating callbacks firing into a destroyed Activity and crashing.
        viewModel.getCounter().observe(this, count -> counterText.setText("Counter: " + count));

        viewModel.getLoadedItems().observe(this, items -> {
            if (items != null) {
                itemsText.setText(String.join(", ", items));
            }
        });

        Button incrementButton = findViewById(R.id.buttonIncrementViewModel);
        incrementButton.setOnClickListener(v -> viewModel.increment());

        // Guard the initial trigger with a savedInstanceState == null check -- onCreate() runs
        // again after every configuration change, so an unconditional call here would re-run
        // the network/database fetch on every rotation even though the ViewModel (and its
        // already-loaded data) survived. (CounterViewModel ALSO guards internally via
        // hasLoadedOnce, belt-and-suspenders.)
        if (savedInstanceState == null) {
            viewModel.loadItemsIfNeeded();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // ContextCompat.registerReceiver() is the modern way to register dynamically -- since
        // API 33, plain registerReceiver() requires explicitly specifying RECEIVER_EXPORTED
        // or RECEIVER_NOT_EXPORTED; use RECEIVER_NOT_EXPORTED unless this receiver genuinely
        // needs to receive broadcasts sent by OTHER apps.
        ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Always unregister in the mirroring lifecycle callback -- forgetting to unregister
        // leaks the receiver reference, and re-registering the SAME instance without
        // unregistering first throws IllegalArgumentException: Receiver already registered.
        unregisterReceiver(batteryReceiver);
    }

    /** Demonstrates sending a custom, app-defined broadcast. */
    private void sendCustomSyncCompleteBroadcast(int recordCount) {
        Intent intent = new Intent("com.example.advancedcomponents.ACTION_DATA_SYNCED");
        intent.putExtra("RECORD_COUNT", recordCount);
        // LocalBroadcastManager is deprecated for in-app-only communication; a ViewModel +
        // LiveData/observer pattern (as used above) is the modern replacement whenever the
        // communication is purely within this app's own components. sendBroadcast() here is
        // shown only for the case of a genuinely system-wide/inter-app custom broadcast.
        sendBroadcast(intent);
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * Matching AndroidManifest.xml entries:
 *
 * <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 *
 * <application>
 *     <activity android:name=".JetpackDemoActivity" android:exported="true">
 *         <intent-filter>
 *             <action android:name="android.intent.action.MAIN" />
 *             <category android:name="android.intent.category.LAUNCHER" />
 *         </intent-filter>
 *     </activity>
 *
 *     <!-- Static registration -- BOOT_COMPLETED is one of the few implicit broadcasts still
 *          exempted for manifest-registered delivery on API 26+; android:exported must be
 *          explicitly declared for any receiver with an intent-filter on API 31+. -->
 *     <receiver android:name=".BootReceiver" android:exported="true">
 *         <intent-filter>
 *             <action android:name="android.intent.action.BOOT_COMPLETED" />
 *         </intent-filter>
 *     </receiver>
 * </application>
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_jetpack_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="24dp">
 *     <TextView android:id="@+id/textCounter"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" />
 *     <Button android:id="@+id/buttonIncrementViewModel"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:text="Increment (survives rotation)" />
 *     <TextView android:id="@+id/textItems"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" />
 * </LinearLayout>
 */
