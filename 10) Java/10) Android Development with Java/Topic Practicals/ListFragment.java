/*
 * ListFragment.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, the Navigation
 * component, and a generated R class / Safe Args / NavController setup from res/ resources
 * and a nav_graph.xml) to compile and run. The classes below are meant to be dropped into
 * app/src/main/java/<your/package>/ as SEPARATE .java files (ListFragment.java,
 * DetailFragment.java, SharedViewModel.java, FragmentsDemoActivity.java), alongside a
 * nav_graph.xml and matching layouts (sketched at the bottom of this file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/04 Fragments and Navigation.md
 *
 * Covers:
 *     1. A Fragment subclass with its own lifecycle methods (onAttach/onCreateView/onViewCreated/
 *        onStart/onResume/onPause/onStop/onDestroyView/onDetach) with Log statements
 *     2. A classic FragmentManager transaction (replace + addToBackStack) as the manual alternative
 *     3. Navigation Component NavController usage (navigate() with an action id from nav_graph.xml)
 *     4. Fragment-to-fragment communication via a shared ViewModel, AND via an interface callback
 */

package com.example.fragmentsdemo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

/**
 * ListFragment -- the "first" Fragment: shows a list-like screen with a button that navigates
 * to DetailFragment, both via the Navigation component and (commented) via a manual
 * FragmentManager transaction.
 */
public class ListFragment extends Fragment {

    private static final String TAG = "ListFragment";

    // Communication approach 1: shared ViewModel, scoped to the shared host Activity so both
    // fragments see the SAME instance (see setupSharedViewModel below).
    private SharedViewModel sharedViewModel;

    // Communication approach 2: an interface callback the hosting Activity implements --
    // set in onAttach(), cleared in onDetach() to avoid leaking a reference to a dead Activity.
    public interface OnItemSelectedListener {
        void onItemSelected(String itemName);
    }
    @Nullable
    private OnItemSelectedListener callbackListener;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach() -- Fragment is now associated with its hosting Activity/Context");

        // Wire up the interface-callback approach: the host Activity MUST implement the
        // listener interface, otherwise this throws ClassCastException -- a deliberate fail-fast
        // so a misconfigured host is caught immediately during development.
        if (context instanceof OnItemSelectedListener) {
            callbackListener = (OnItemSelectedListener) context;
        } else {
            throw new ClassCastException(context + " must implement OnItemSelectedListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView() -- inflating this Fragment's own layout, "
                + "independent of the hosting Activity's layout");
        // 'false' for attachToRoot -- the FragmentManager/NavController attaches this View
        // hierarchy to the container itself; attaching it ourselves here would double-attach it.
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated() -- the returned View from onCreateView() now exists; "
                + "safe to call findViewById() on it from here on");

        setupSharedViewModel();

        TextView itemText = view.findViewById(R.id.textItemName);
        Button selectButton = view.findViewById(R.id.buttonSelectItem);
        Button navigateButton = view.findViewById(R.id.buttonNavigateToDetail);

        selectButton.setOnClickListener(v -> {
            String chosenItem = "Item #42";
            itemText.setText("Selected: " + chosenItem);

            // Approach 1: push the selection into the shared ViewModel -- DetailFragment
            // (or any other Fragment sharing the same ViewModel scope) observes this and updates.
            sharedViewModel.selectItem(chosenItem);

            // Approach 2: also notify the hosting Activity directly via the interface callback.
            if (callbackListener != null) {
                callbackListener.onItemSelected(chosenItem);
            }
        });

        navigateButton.setOnClickListener(v -> navigateToDetailFragment(view));
    }

    private void setupSharedViewModel() {
        // requireActivity() as the ViewModelStoreOwner (instead of 'this' Fragment) is what makes
        // the ViewModel SHARED -- every Fragment hosted by the same Activity that also requests
        // SharedViewModel.class via requireActivity() gets back the exact same instance.
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    /**
     * Navigation Component approach (preferred in modern apps): NavController resolves an
     * "action" declared in nav_graph.xml (e.g. action_listFragment_to_detailFragment) and
     * handles the FragmentTransaction, back stack, and optional animations/args for you.
     */
    private void navigateToDetailFragment(View anchorView) {
        NavController navController = Navigation.findNavController(anchorView);

        // Passing arguments via a Bundle -- with Safe Args (a Gradle plugin) this would instead
        // be a generated, type-safe ListFragmentDirections.actionListFragmentToDetailFragment(...)
        // call; shown here in plain Bundle form to stay dependency-free.
        Bundle args = new Bundle();
        args.putString("itemName", "Item #42");

        Log.d(TAG, "Navigating to DetailFragment via NavController");
        navController.navigate(R.id.action_listFragment_to_detailFragment, args);
    }

    /*
     * MANUAL FragmentManager alternative to the NavController call above (shown commented,
     * for comparison -- this is what NavController does under the hood, and is still valid for
     * simple apps that don't need a full nav graph):
     *
     * DetailFragment detailFragment = new DetailFragment();
     * requireActivity().getSupportFragmentManager()
     *         .beginTransaction()
     *         .replace(R.id.fragmentContainer, detailFragment)   // swap out this container's Fragment
     *         .addToBackStack("list_to_detail")                  // makes the Back button return here
     *         .commit();
     */

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart() -- Fragment's View is now visible");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() -- Fragment is now interactive");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() -- Fragment is losing foreground focus");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() -- Fragment's View is no longer visible");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // IMPORTANT: the Fragment object itself can outlive its View (e.g. when placed on the
        // back stack) -- always null out any View references here to avoid leaking them.
        Log.d(TAG, "onDestroyView() -- this Fragment's View hierarchy is being torn down, "
                + "but the Fragment instance may still be reused later (e.g. from the back stack)");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach() -- Fragment is no longer associated with its hosting Activity");
        callbackListener = null; // avoid leaking a reference to a dead/detached Activity
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * SharedViewModel.java -- save as a SEPARATE file. Holds state that both ListFragment and
 * DetailFragment observe, scoped to their shared host Activity (see setupSharedViewModel above).
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.fragmentsdemo;
 *
 * import androidx.lifecycle.LiveData;
 * import androidx.lifecycle.MutableLiveData;
 * import androidx.lifecycle.ViewModel;
 *
 * public class SharedViewModel extends ViewModel {
 *
 *     private final MutableLiveData<String> selectedItem = new MutableLiveData<>();
 *
 *     public LiveData<String> getSelectedItem() {
 *         return selectedItem; // exposed as read-only LiveData to outside observers
 *     }
 *
 *     public void selectItem(String itemName) {
 *         selectedItem.setValue(itemName);
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * DetailFragment.java -- save as a SEPARATE file. Observes the SharedViewModel set by
 * ListFragment, and also reads the Bundle args passed via the NavController.navigate() call.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.fragmentsdemo;
 *
 * import android.os.Bundle;
 * import android.view.LayoutInflater;
 * import android.view.View;
 * import android.view.ViewGroup;
 * import android.widget.TextView;
 * import androidx.annotation.NonNull;
 * import androidx.annotation.Nullable;
 * import androidx.fragment.app.Fragment;
 * import androidx.lifecycle.ViewModelProvider;
 *
 * public class DetailFragment extends Fragment {
 *
 *     private SharedViewModel sharedViewModel;
 *
 *     @Nullable
 *     @Override
 *     public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
 *                               @Nullable Bundle savedInstanceState) {
 *         return inflater.inflate(R.layout.fragment_detail, container, false);
 *     }
 *
 *     @Override
 *     public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
 *         super.onViewCreated(view, savedInstanceState);
 *         TextView detailText = view.findViewById(R.id.textDetail);
 *
 *         // Reading the navigation argument passed via NavController.navigate(actionId, bundle).
 *         if (getArguments() != null) {
 *             String itemNameFromNav = getArguments().getString("itemName", "(none)");
 *             detailText.setText("Nav arg itemName: " + itemNameFromNav);
 *         }
 *
 *         // Observing the SAME SharedViewModel instance ListFragment wrote to -- requireActivity()
 *         // is again the key to getting the shared, Activity-scoped instance.
 *         sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
 *         sharedViewModel.getSelectedItem().observe(getViewLifecycleOwner(), itemName ->
 *                 detailText.setText(detailText.getText() + "\nShared VM itemName: " + itemName));
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * FragmentsDemoActivity.java -- the host Activity. Must implement ListFragment's callback
 * interface, and hosts a NavHostFragment (declared in its layout) for the Navigation component.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.fragmentsdemo;
 *
 * import android.os.Bundle;
 * import android.util.Log;
 * import android.widget.Toast;
 * import androidx.appcompat.app.AppCompatActivity;
 *
 * public class FragmentsDemoActivity extends AppCompatActivity
 *         implements ListFragment.OnItemSelectedListener {
 *
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_fragments_demo); // must contain a NavHostFragment
 *     }
 *
 *     @Override
 *     public void onItemSelected(String itemName) {
 *         Log.d("FragmentsDemoActivity", "Callback received selection: " + itemName);
 *         Toast.makeText(this, "Host Activity heard: " + itemName, Toast.LENGTH_SHORT).show();
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/navigation/nav_graph.xml:
 *
 * <navigation xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:app="http://schemas.android.com/apk/res-auto"
 *     android:id="@+id/nav_graph"
 *     app:startDestination="@id/listFragment">
 *
 *     <fragment android:id="@+id/listFragment" android:name="com.example.fragmentsdemo.ListFragment"
 *         android:label="List">
 *         <action android:id="@+id/action_listFragment_to_detailFragment"
 *             app:destination="@id/detailFragment" />
 *     </fragment>
 *
 *     <fragment android:id="@+id/detailFragment" android:name="com.example.fragmentsdemo.DetailFragment"
 *         android:label="Detail" />
 *
 * </navigation>
 *
 * Minimal res/layout/activity_fragments_demo.xml (hosts the nav graph):
 *
 * <androidx.fragment.app.FragmentContainerView
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:app="http://schemas.android.com/apk/res-auto"
 *     android:id="@+id/navHostFragment"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:name="androidx.navigation.fragment.NavHostFragment"
 *     app:navGraph="@navigation/nav_graph"
 *     app:defaultNavHost="true" />
 *
 * app/build.gradle (Module) dependencies needed:
 *     implementation("androidx.navigation:navigation-fragment:2.7.7")
 *     implementation("androidx.navigation:navigation-ui:2.7.7")
 *     implementation("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
 */
