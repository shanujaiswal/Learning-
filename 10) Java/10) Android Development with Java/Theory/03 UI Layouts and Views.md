# Views and the UI Hierarchy

--> Everything drawn on an Android screen is a **View** (or a subclass of it, like `TextView`, `Button`, `ImageView`) or a **ViewGroup** (a View that contains other Views, like `LinearLayout` or `ConstraintLayout`) -- the entire screen is a TREE of Views rooted at a single top-level ViewGroup, which is exactly what an XML layout file describes declaratively.
--> UI can be built two ways: **declaratively in XML** (the overwhelmingly common approach, kept separate from Java logic) or **programmatically in Java** (creating `new TextView(context)` etc. and adding it to a parent at runtime) -- XML is preferred for anything static because it separates design from logic and works with the visual Layout Editor and resource-qualifier system (covered in the Fundamentals file).

```xml
<!-- res/layout/activity_main.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/textTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello, Android!"
        android:textSize="24sp" />

    <Button
        android:id="@+id/buttonSubmit"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Submit" />

</LinearLayout>
```

--> **`layout_width` / `layout_height`** -- every View must specify both, using one of: an exact size (e.g. `48dp`), `wrap_content` (as small as the content needs), or `match_parent` (fill the available space from the parent).
--> **`dp` vs `sp` vs `px`** -- `dp` (density-independent pixels) is the standard unit for layout dimensions, automatically scaled by the OS so a `48dp` button looks the same physical size across different screen densities; `sp` (scale-independent pixels) is the same idea but ALSO respects the user's font-size accessibility setting, and should be used for `textSize` specifically; `px` (raw pixels) should almost never be used directly in layouts since it ignores density entirely.

# Common Layout Types

--> **`LinearLayout`** -- arranges children in a single row or column (`android:orientation="horizontal"|"vertical"`) -- simple and predictable, but nesting many LinearLayouts to achieve complex UI hurts performance (deeper view hierarchies take longer to measure/layout) and is a classic beginner anti-pattern.

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="Left (takes 1 share)" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="2"
        android:text="Right (takes 2 shares)" />
</LinearLayout>
```

--> **`layout_weight`** -- distributes remaining space among children proportionally -- setting `layout_width="0dp"` alongside a weight is the standard idiom ("give this View none of the intrinsic space, then divide up all the leftover space by weight").

--> **`ConstraintLayout`** -- the modern, RECOMMENDED default for most screens -- a flat (non-nested) hierarchy where each View's position is defined by CONSTRAINTS relative to the parent or to sibling Views, which avoids deep nesting and generally performs better for complex layouts.

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageView
        android:id="@+id/imageLogo"
        android:layout_width="120dp"
        android:layout_height="120dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="32dp" />

    <TextView
        android:id="@+id/textWelcome"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Welcome"
        app:layout_constraintTop_toBottomOf="@id/imageLogo"
        app:layout_constraintStart_toStartOf="@id/imageLogo"
        app:layout_constraintEnd_toEndOf="@id/imageLogo"
        android:layout_marginTop="16dp" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

--> Every constrained View needs AT LEAST one horizontal and one vertical constraint, or its position is undefined/it collapses to the top-left corner -- this is the single most common ConstraintLayout mistake for newcomers.
--> **`app:layout_constraintX_toYOf`** attributes chain a View's edge to another View's (or the parent's) edge -- `toBottomOf`, `toTopOf`, `toStartOf`, `toEndOf` are the core anchors; `Start`/`End` are preferred over `Left`/`Right` since they automatically flip for right-to-left languages.
--> **`FrameLayout`** -- the simplest ViewGroup, designed to hold a single child (or overlapping children, like an image with a badge on top) -- also the base mechanism Fragments attach to (a `FrameLayout` is a common Fragment container).
--> **`RelativeLayout`** -- an older constraint-like layout, mostly superseded by `ConstraintLayout` in new projects, but still seen in legacy code.

# Common Views

| View | Purpose |
|---|---|
| `TextView` | Displays non-editable text. |
| `EditText` | Editable text input (a `TextView` subclass) -- set `android:inputType` to control the keyboard shown (`text`, `number`, `textPassword`, `textEmailAddress`, etc.). |
| `Button` / `ImageButton` | Clickable button, text or icon. |
| `ImageView` | Displays an image (`android:src` for a static drawable, or set programmatically). |
| `CheckBox` / `RadioButton` / `Switch` | Boolean/choice input controls. |
| `ProgressBar` | Spinner or horizontal progress indicator. |
| `RecyclerView` | Efficient scrolling list/grid of many items (see below). |
| `ScrollView` | Makes a single child (must be one child) vertically scrollable when content overflows the screen. |

```java
Button submitButton = findViewById(R.id.buttonSubmit);
submitButton.setOnClickListener(v -> {
    String text = ((TextView) findViewById(R.id.textTitle)).getText().toString();
    Toast.makeText(MainActivity.this, "Clicked: " + text, Toast.LENGTH_SHORT).show();
});
```

# findViewById vs ViewBinding

--> **`findViewById()`** -- the classic way to get a reference to a View defined in XML, by its `android:id` -- works, but has two drawbacks: it's NOT type-safe at compile time (you must manually cast, and a wrong cast fails only at RUNTIME with a `ClassCastException`), and a wrong/misspelled/renamed ID also only fails at runtime (returns `null`, causing a `NullPointerException` later).

```java
TextView title = findViewById(R.id.textTitle);       // manual cast needed pre-API-26 target, still common
Button submit = findViewById(R.id.buttonSubmit);
```

--> **ViewBinding** -- a build feature that generates a strongly-typed "Binding" class per XML layout, exposing every `id`-tagged View as a typed field -- eliminates both `findViewById` drawbacks: it's compile-time safe (renamed/removed views cause build errors, not runtime crashes) and requires no manual casting.

```groovy
// app/build.gradle
android {
    buildFeatures {
        viewBinding true
    }
}
```

```java
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;   // generated from activity_main.xml

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonSubmit.setOnClickListener(v ->
                binding.textTitle.setText("Clicked!"));
    }
}
```

--> ViewBinding is the currently RECOMMENDED approach over both `findViewById` and the older, now-deprecated `kotlin-android-extensions` synthetic properties -- it's lighter-weight than **DataBinding** (a related but more heavyweight feature supporting XML-embedded expressions and two-way binding), which is still available for apps that need those extra features.

# RecyclerView Basics

--> **`RecyclerView`** is the standard widget for displaying large or dynamic scrolling lists/grids EFFICIENTLY -- rather than creating a View for every single data item (which would be wasteful for a list of thousands), it RECYCLES a small number of item Views as the user scrolls, reusing off-screen views for newly-visible items instead of inflating new ones.
--> Three pieces are required: the `RecyclerView` itself (in XML), a `LayoutManager` (decides how items are arranged -- linear list, grid, staggered grid), and an `Adapter` (bridges your data to the Views, creating and binding item Views on demand).

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/recyclerViewItems"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```java
public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

    private final List<String> items;

    public ItemAdapter(List<String> items) {
        this.items = items;
    }

    // Creates a new item View (only called when there's no recyclable view available)
    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_row, parent, false);
        return new ItemViewHolder(view);
    }

    // Binds data into an existing (possibly recycled) item View -- called far more often than onCreate
    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.textLabel.setText(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView textLabel;

        ItemViewHolder(View itemView) {
            super(itemView);
            textLabel = itemView.findViewById(R.id.textLabel);
        }
    }
}
```

```java
// Wiring it up in the Activity
RecyclerView recyclerView = findViewById(R.id.recyclerViewItems);
recyclerView.setLayoutManager(new LinearLayoutManager(this));
recyclerView.setAdapter(new ItemAdapter(myDataList));
```

--> **`ViewHolder`** -- caches the results of `findViewById()` for one item row so they're looked up ONCE per row-view, not on every single scroll/bind -- this pattern is what makes RecyclerView fast; the older `ListView` widget required manually implementing this pattern (the "ViewHolder pattern") and it was easy to get wrong.
--> **`notifyDataSetChanged()`** -- tells the Adapter the underlying data changed so it should re-bind visible items -- simple but coarse (re-binds everything); more granular methods (`notifyItemInserted`, `notifyItemRemoved`, `notifyItemChanged`) or `DiffUtil` (computes a minimal diff between old/new lists for efficient, animated updates) are preferred for larger lists or frequent updates.

# Common Gotchas

--> **Overly nested LinearLayouts** -- each nesting level adds a full measure+layout pass; deeply nested layouts are a classic performance smell caught by Android Studio's Layout Inspector -- prefer flattening with ConstraintLayout.
--> **Forgetting `android:inputType` on `EditText`** -- users get the generic keyboard instead of a numeric/email-optimized one, hurting usability for no good reason.
--> **Not recycling correctly (custom recycling bugs)** -- forgetting to fully overwrite ALL relevant View state in `onBindViewHolder` (e.g. leaving a previous row's background/visibility set) causes recycled views to visually "leak" old state into new rows as you scroll -- always set every relevant property unconditionally in `onBindViewHolder`, don't assume a fresh view.
--> **Heavy work inside `onBindViewHolder`** -- this runs on the main thread during scrolling; expensive operations (decoding large images synchronously, etc.) here cause visible scroll jank -- use an image-loading library with built-in caching/async decoding for anything beyond trivial text.

# Best Practices

--> Default to `ConstraintLayout` for non-trivial screens; reserve `LinearLayout` for genuinely simple, single-direction rows/columns.
--> Enable and use ViewBinding in all new modules -- it removes an entire class of runtime NPE crashes at essentially no cost.
--> Extract repeated dimensions/colors/strings into `res/values/` resource files (`dimens.xml`, `colors.xml`, `strings.xml`) instead of hardcoding literals in every layout -- keeps theming and localization consistent and centralized.
--> For any list beyond a handful of static items, use `RecyclerView` with `DiffUtil`-backed updates rather than manually clearing/rebuilding views or using the legacy `ListView`.
