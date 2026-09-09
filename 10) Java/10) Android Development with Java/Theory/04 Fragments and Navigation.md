# What a Fragment Is and Why It Exists

--> A **Fragment** represents a reusable portion of UI and behavior that must live INSIDE a hosting Activity -- it has its own layout and its own lifecycle, but that lifecycle is always driven by (and nested within) its host Activity's lifecycle.
--> Fragments were introduced to support flexible, multi-pane UIs (e.g. a list+detail layout on a tablet, collapsing to two separate screens on a phone) and have since become the standard way to structure a SINGLE-Activity app -- the modern recommended architecture is largely "one Activity, many Fragments," with the Activity acting mostly as a thin shell / navigation host.
--> A Fragment subclasses `androidx.fragment.app.Fragment` (always use the AndroidX version, not the ancient deprecated platform `android.app.Fragment`, which is removed entirely from recent SDKs).

```java
public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView nameText = view.findViewById(R.id.textName);
        nameText.setText("Loaded in onViewCreated");
    }
}
```

# The Fragment Lifecycle

--> A Fragment's lifecycle largely mirrors an Activity's, but has EXTRA states around View creation/destruction, because a Fragment's underlying View hierarchy can be destroyed and recreated independently of the Fragment object itself (e.g. when it's placed on a back stack and temporarily has no view).

```text
onAttach()        <-- Fragment is now associated with its host Activity/Context
    |
onCreate()        <-- Fragment-level init (NOT view-related -- no layout exists yet)
    |
onCreateView()     <-- inflate and RETURN this Fragment's View hierarchy
    |
onViewCreated()    <-- the returned View now exists -- safe to findViewById here
    |
onStart()
    |
onResume()          <-- Fragment visible and interactive
    |
   ... (running) ...
    |
onPause()
    |
onStop()
    |
onDestroyView()     <-- the View hierarchy is being destroyed (Fragment object may persist,
    |                    e.g. still on the back stack) -- release View references here
onDestroy()          <-- Fragment-level teardown
    |
onDetach()           <-- Fragment no longer associated with its host
```

--> **Why `onCreateView`/`onViewCreated` are split from `onCreate`/`onDestroy`** -- a Fragment can be pushed onto a back stack, have its VIEW torn down while the Fragment object itself stays alive in memory (to be recreated later when the user navigates back) -- so View setup/teardown logic must be scoped to `onCreateView`/`onDestroyView`, not the outer `onCreate`/`onDestroy`, or you risk holding stale View references.
--> **A common leak** -- storing a View reference (e.g. via ViewBinding) in a field that's only cleared in `onDestroy()` rather than `onDestroyView()` leaks the entire View hierarchy for however long the Fragment instance survives on the back stack.

```java
public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;   // ViewBinding for this Fragment

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // IMPORTANT: release the View reference to avoid leaking it
    }
}
```

# Adding Fragments With the FragmentManager

--> Each Activity (and each Fragment, for nested Fragments) owns a **`FragmentManager`**, responsible for adding, replacing, and removing Fragments, and for maintaining the Fragment back stack -- Fragment transactions are batched into a `FragmentTransaction`.

```java
FragmentManager fragmentManager = getSupportFragmentManager();
FragmentTransaction transaction = fragmentManager.beginTransaction();
transaction.replace(R.id.fragmentContainer, new ProfileFragment());
transaction.addToBackStack("profile");   // pressing Back will pop this transaction
transaction.commit();

// Shorthand, chained:
getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragmentContainer, new ProfileFragment())
        .addToBackStack(null)
        .commit();
```

```xml
<!-- Host Activity layout -->
<FrameLayout
    android:id="@+id/fragmentContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

--> **`add` vs `replace`** -- `add()` layers a new Fragment on top of the container (previous ones stay, unless hidden manually); `replace()` first removes everything in the container, then adds the new one -- `replace()` is by far the more common choice for typical screen-to-screen navigation.
--> **`addToBackStack()`** -- without it, pressing Back exits the Activity directly; with it, Back first pops Fragment transactions off the FragmentManager's own back stack, one at a time, before ever reaching the Activity's own back behavior.
--> **`commit()` is asynchronous** -- it schedules the transaction to run on the main thread at the next opportunity, it does NOT execute immediately -- `commitNow()` exists for the rare cases requiring synchronous execution, but most code should just use `commit()`.

# The Navigation Component

--> The **Navigation Component** (part of Jetpack) is Google's recommended abstraction over raw `FragmentTransaction` management -- it centralizes all navigation destinations and the paths between them into a single XML **navigation graph**, and generates the boilerplate for Fragment transactions, back stack handling, and argument passing.

```xml
<!-- res/navigation/nav_graph.xml -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment
        android:id="@+id/homeFragment"
        android:name="com.example.myapp.HomeFragment"
        android:label="Home">
        <action
            android:id="@+id/action_home_to_detail"
            app:destination="@id/detailFragment" />
    </fragment>

    <fragment
        android:id="@+id/detailFragment"
        android:name="com.example.myapp.DetailFragment"
        android:label="Detail">
        <argument
            android:name="itemId"
            app:argType="integer" />
    </fragment>

</navigation>
```

```xml
<!-- Host Activity layout: a NavHostFragment replaces the manual FrameLayout container -->
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/navHostFragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:navGraph="@navigation/nav_graph"
    app:defaultNavHost="true" />
```

```java
// Navigating, from within HomeFragment
NavController navController = NavHostFragment.findNavController(this);
navController.navigate(R.id.action_home_to_detail);

// With Safe Args (a Gradle plugin that generates type-safe argument classes from the nav graph):
HomeFragmentDirections.ActionHomeToDetail action =
        HomeFragmentDirections.actionHomeToDetail(itemId);
navController.navigate(action);
```

--> **Benefits over manual FragmentTransactions** -- automatic back stack management, a visual editor for the navigation graph, built-in support for animated transitions, deep linking, and (with the Safe Args plugin) COMPILE-TIME-checked argument passing instead of raw Bundle keys that can silently typo.
--> **`NavHostFragment`** -- the special container Fragment that swaps destinations in and out as the user navigates -- it IS itself a Fragment hosted by the FragmentManager, so Navigation Component is really "FragmentManager, with a well-designed abstraction on top," not a replacement mechanism.

# Communicating Between Fragments

--> Fragments should NEVER talk to each other directly (e.g. one Fragment holding a direct reference to another) -- that tightly couples them and breaks if either is recreated independently. The two standard patterns:
--> **Shared ViewModel** -- a `ViewModel` SCOPED TO THE ACTIVITY (rather than to each individual Fragment) is visible to every Fragment hosted by that Activity, making it a natural shared communication channel via observable data (e.g. `LiveData`).

```java
public class SharedViewModel extends ViewModel {
    private final MutableLiveData<String> selectedItem = new MutableLiveData<>();

    public void select(String item) { selectedItem.setValue(item); }
    public LiveData<String> getSelectedItem() { return selectedItem; }
}

// In FragmentA (sends data)
SharedViewModel model = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
model.select("Item clicked in Fragment A");

// In FragmentB (receives data) -- note: same ViewModelProvider owner (requireActivity()), so it's the SAME instance
SharedViewModel model = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
model.getSelectedItem().observe(getViewLifecycleOwner(), item -> {
    // update FragmentB's UI in response to FragmentA's action
});
```

--> **Fragment Result API** -- a lighter-weight, one-off alternative to a shared ViewModel, good for simple "send one result back" scenarios (e.g. a picker Fragment returning a chosen value to its caller) without needing a dedicated ViewModel class.

```java
// The Fragment that PRODUCES a result
getParentFragmentManager().setFragmentResult("requestKey", resultBundle);

// The Fragment that LISTENS for it (registered before the result can arrive, e.g. in onCreate)
getParentFragmentManager().setFragmentResultListener("requestKey", this, (requestKey, bundle) -> {
    String value = bundle.getString("bundleKey");
});
```

--> Both patterns route communication THROUGH a shared owner (the Activity's ViewModel store, or the shared FragmentManager) rather than one Fragment holding a reference to another -- this keeps Fragments independently testable and safely reusable.

# Common Gotchas

--> **Calling `getActivity()`/`requireContext()` before `onAttach()` or after `onDetach()`** -- returns `null` (or the older accessors did) -- `requireActivity()`/`requireContext()`/`requireView()` (the "require" family) throw a clear, descriptive exception immediately instead of a confusing later NullPointerException, and are generally preferred over the nullable `getActivity()`/`getContext()` when you know the Fragment must be attached at that point.
--> **Observing LiveData with the wrong LifecycleOwner** -- always pass `getViewLifecycleOwner()` (not `this`, the Fragment itself) when observing LiveData inside a Fragment's View-related code -- using the Fragment's own lifecycle instead of the View's can cause duplicate/late callbacks across View recreation, since the Fragment object can outlive its View.
--> **Fragment transaction after `onSaveInstanceState`** -- committing a FragmentTransaction after the Activity's state has already been saved (e.g. from an async callback that returns after the Activity was backgrounded) throws `IllegalStateException` ("Can not perform this action after onSaveInstanceState") -- a very common late-callback bug.

# Best Practices

--> Prefer the Navigation Component over hand-rolled `FragmentTransaction` calls for any app with more than a couple of screens -- it centralizes the navigation structure in one graph, which is far easier to reason about than transactions scattered across many classes.
--> Use Safe Args for passing data through navigation actions instead of raw Bundle keys -- compile-time checked, avoids stringly-typed key mismatches.
--> Keep Fragments free of direct references to each other; route all cross-Fragment communication through a shared ViewModel or the Fragment Result API.
--> Always null out View-related fields (ViewBinding instances especially) in `onDestroyView()`.
