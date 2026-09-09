# Why RecyclerView Exists

--> **`RecyclerView`** displays large (or dynamic) data sets by RECYCLING a small number of View objects rather than creating one View per data item -- as the user scrolls, views that scroll off-screen are not destroyed but repurposed ("recycled") to display the NEXT item that scrolls on-screen, which keeps memory and layout-inflation cost roughly constant regardless of how many items are in the underlying list.
--> It is the modern successor to the older `ListView`/`GridView` widgets, which technically supported a similar "view holder" optimization but didn't enforce it -- `RecyclerView` makes the ViewHolder pattern MANDATORY, decouples layout (via a pluggable `LayoutManager`) from item rendering, and adds built-in item animations.
--> Three collaborating pieces make up every RecyclerView setup:

| Piece | Responsibility |
|---|---|
| `RecyclerView.Adapter` | Creates ViewHolders and binds data into them for each position. |
| `RecyclerView.ViewHolder` | Holds references to a single item's Views (avoids repeated `findViewById()` calls). |
| `RecyclerView.LayoutManager` | Decides HOW items are positioned -- linear list, grid, staggered grid -- and handles recycling/scrolling mechanics. |

# Basic Setup

```xml
<!-- item_user.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp">

    <TextView
        android:id="@+id/textName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/textEmail"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</LinearLayout>
```

```java
public class User {
    final long id;
    final String name;
    final String email;
    User(long id, String name, String email) { this.id = id; this.name = name; this.email = email; }
}
```

```java
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<User> users;
    private final OnUserClickListener listener;

    interface OnUserClickListener { void onUserClick(User user); }

    UserAdapter(List<User> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView textName;
        final TextView textEmail;
        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textName);
            textEmail = itemView.findViewById(R.id.textEmail);
        }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);   // 'false' -- RecyclerView attaches it, not us
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        holder.textName.setText(user.name);
        holder.textEmail.setText(user.email);
        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() { return users.size(); }
}
```

```java
RecyclerView recyclerView = findViewById(R.id.recyclerView);
recyclerView.setLayoutManager(new LinearLayoutManager(this));
recyclerView.setAdapter(new UserAdapter(userList, user -> openDetail(user)));
```

--> **`onCreateViewHolder()`** is called only as many times as needed to fill the screen (plus a small recycling buffer) -- NOT once per data item -- this is the entire performance win; it inflates a fresh item layout and wraps it in a new ViewHolder.
--> **`onBindViewHolder()`** is called far more often -- every time a (possibly recycled) ViewHolder needs to display a different position's data -- it should be CHEAP: just assign values to already-found views, never call `findViewById()` here (that's what the ViewHolder's constructor is for) and never inflate layouts here.
--> Always pass `false` as the third argument to `inflate()` in `onCreateViewHolder()` -- `RecyclerView` itself calls `addView()` at the right time; passing `true` double-attaches the view and crashes or misbehaves.

# Item Click Handling Patterns

--> Setting a click listener directly in `onBindViewHolder()` (as above) is the simplest approach and fine for most apps, but it means a NEW lambda/listener object is (potentially) created on every bind -- for very performance-sensitive lists, an alternative is binding the listener ONCE in the ViewHolder's constructor and looking up the CURRENT item via `getBindingAdapterPosition()` at click time.

```java
static class UserViewHolder extends RecyclerView.ViewHolder {
    final TextView textName;
    User currentUser;

    UserViewHolder(@NonNull View itemView, OnUserClickListener listener) {
        super(itemView);
        textName = itemView.findViewById(R.id.textName);
        itemView.setOnClickListener(v -> {
            if (currentUser != null) listener.onUserClick(currentUser);
        });
    }
}

@Override
public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
    holder.currentUser = users.get(position);
    holder.textName.setText(holder.currentUser.name);
}
```

--> Always use `getBindingAdapterPosition()` (formerly `getAdapterPosition()`, now deprecated) rather than capturing `position` directly in a bind-time lambda if you need the position AT CLICK TIME rather than at bind time -- the list may have changed (items inserted/removed) between binding and the actual click, and a captured stale `position` int would then point at the WRONG item; `getBindingAdapterPosition()` returns `RecyclerView.NO_POSITION` if the item has since been removed, which must be checked before using it.

# DiffUtil -- Efficient List Updates

--> Calling `notifyDataSetChanged()` after updating the underlying list is simple but WASTEFUL -- it tells RecyclerView "everything might have changed," discarding all item animations and forcing every visible ViewHolder to rebind, even for items that didn't actually change.
--> **`DiffUtil`** computes the minimal set of insert/remove/move/change operations between an OLD list and a NEW list (using a variant of the Myers diff algorithm) and dispatches precise `notifyItemInserted()`/`notifyItemRemoved()`/`notifyItemMoved()`/`notifyItemChanged()` calls instead -- this both performs better on large lists and enables RecyclerView's built-in item animations (fade-in for new items, slide for moves) to run correctly.

```java
public class UserDiffCallback extends DiffUtil.Callback {
    private final List<User> oldList;
    private final List<User> newList;

    UserDiffCallback(List<User> oldList, List<User> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override public int getOldListSize() { return oldList.size(); }
    @Override public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        // Are these the SAME logical entity (usually compare stable IDs)?
        return oldList.get(oldPos).id == newList.get(newPos).id;
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        // Given they're the same entity, has its DISPLAYED content changed?
        // Requires a proper equals() (or manual field comparison) on User.
        return oldList.get(oldPos).equals(newList.get(newPos));
    }
}
```

```java
void updateUsers(List<User> newUsers) {
    DiffUtil.DiffResult result = DiffUtil.calculateDiff(new UserDiffCallback(this.users, newUsers));
    this.users.clear();
    this.users.addAll(newUsers);
    result.dispatchUpdatesTo(this);   // fires precise notifyItemXxx() calls on the adapter
}
```

--> `areItemsTheSame()` should compare a STABLE IDENTITY (a database primary key, a unique ID field) -- NOT object reference equality (`==`) unless old and new lists genuinely reuse the same objects, and NOT full content equality (that's what the second method is for).
--> `areContentsTheSame()` is only even CALLED for pairs where `areItemsTheSame()` already returned `true` -- it determines whether `notifyItemChanged()` (triggering a rebind + change-animation) is needed, or the item can be left alone entirely.
--> `DiffUtil.calculateDiff()` does synchronous work proportional to `O(N + D^2)` in the worst case (N = list sizes, D = number of edits) -- for very large lists, run it on a background thread (pass a snapshot of both lists in, since neither should mutate mid-calculation) and dispatch the result back on the main thread.

# ListAdapter -- DiffUtil Built In

--> **`ListAdapter<T, VH>`** (from `androidx.recyclerview.widget`) is a convenience base class that wraps `DiffUtil` calculation automatically on a background thread, so you don't hand-write the calculate/dispatch dance shown above -- just call `submitList()` with the new data.

```java
public class UserListAdapter extends ListAdapter<User, UserListAdapter.UserViewHolder> {

    UserListAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<User> DIFF_CALLBACK = new DiffUtil.ItemCallback<User>() {
        @Override
        public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return oldItem.id == newItem.id;
        }
        @Override
        public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return oldItem.equals(newItem);
        }
    };

    static class UserViewHolder extends RecyclerView.ViewHolder {
        UserViewHolder(@NonNull View itemView) { super(itemView); }
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = getItem(position);   // ListAdapter manages the backing list internally
        // bind user into holder's views
    }
}
```

```java
// Elsewhere, e.g. after new data arrives from a ViewModel/LiveData observer:
adapter.submitList(newUserList);   // diffing happens off the main thread automatically
```

--> With `ListAdapter`, you never call `notifyDataSetChanged()`/manage the list yourself at all -- `submitList()` and `getItem(position)` are the entire contract; the adapter internally keeps an immutable snapshot of the current list.
--> **Caveat**: mutating the SAME list instance you previously passed to `submitList()` and passing it again does nothing, since `ListAdapter` short-circuits on reference equality for a fast path -- always pass a NEW list (e.g. `new ArrayList<>(existingList)` with your changes applied) so the diff actually runs.

# Multiple View Types

--> A single RecyclerView can display DIFFERENT layouts for different items (e.g. a chat screen with sent vs received message bubbles, or a feed mixing headers/items/ads) by overriding `getItemViewType()` and branching in both `onCreateViewHolder()` and `onBindViewHolder()`.

```java
private static final int TYPE_HEADER = 0;
private static final int TYPE_ITEM = 1;

@Override
public int getItemViewType(int position) {
    return (position == 0) ? TYPE_HEADER : TYPE_ITEM;
}

@NonNull
@Override
public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_HEADER) {
        return new HeaderViewHolder(inflater.inflate(R.layout.item_header, parent, false));
    }
    return new UserViewHolder(inflater.inflate(R.layout.item_user, parent, false));
}

@Override
public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof HeaderViewHolder) {
        ((HeaderViewHolder) holder).bind("Users");
    } else if (holder instanceof UserViewHolder) {
        ((UserViewHolder) holder).bind(users.get(position - 1));   // offset for the header
    }
}

@Override
public int getItemCount() { return users.size() + 1; }   // +1 for the header
```

--> Each DISTINCT view type gets its OWN separate recycling pool by default -- RecyclerView never tries to recycle a header-type ViewHolder into an item-type slot, which is correct behavior but means many view types with few instances each can reduce recycling efficiency; `RecyclerView.RecycledViewPool` can be shared across multiple RecyclerViews (e.g. nested horizontal lists inside a vertical list) to improve this.
--> For genuinely heterogeneous data, model it with a common sealed/marked interface or a wrapper class holding a type tag, so `getItemViewType()` and the bind branch stay in sync with a single source of truth rather than two independently-maintained position checks.

# LayoutManagers

| LayoutManager | Use Case |
|---|---|
| `LinearLayoutManager` | Standard vertical or horizontal scrolling list. |
| `GridLayoutManager` | Fixed-column grid; supports `setSpanSizeLookup()` for items that span multiple columns (e.g. a full-width header in a grid). |
| `StaggeredGridLayoutManager` | Pinterest-style masonry grid with variable item heights. |

# Common Gotchas

--> **Calling `notifyDataSetChanged()` out of habit** -- always prefer `DiffUtil`/`ListAdapter` or the specific `notifyItemXxx()` calls; blanket invalidation kills animations and re-binds everything unnecessarily.
--> **Doing expensive work in `onBindViewHolder()`** -- e.g. loading an image synchronously, formatting a date with a newly-constructed `SimpleDateFormat` every call -- causes visible scroll jank since binding runs on the main thread during scrolling; use an image-loading library (with its own caching) and cache/reuse formatters.
--> **Capturing stale `position` in a listener set during `onBindViewHolder()`** -- use `getBindingAdapterPosition()` at click time instead, and check for `RecyclerView.NO_POSITION`.
--> **Missing/broken `equals()`/`hashCode()` on the data model** used by `areContentsTheSame()` -- without a real `equals()`, DiffUtil falls back to reference equality, so updated field values on the SAME object references won't be detected as changed (or, if new object instances are always created, every content check spuriously reports "changed").
--> **Mutating the adapter's backing list directly** instead of swapping in a new list -- causes `IndexOutOfBoundsException` crashes if a `notifyItemXxx()` call fires (or DiffUtil computes a diff) against a list that has already been mutated out from under it mid-calculation, especially on a background thread.

# Best Practices

--> Prefer `ListAdapter` over hand-rolled `Adapter` + manual `DiffUtil` wiring for any list backed by mutable/changing data -- it removes an entire class of "forgot to call `notifyItemXxx`" bugs.
--> Give data models stable, comparison-friendly `id` fields and proper `equals()`/`hashCode()` (or use Java records / Kotlin data classes) specifically so DiffUtil callbacks are trivial and correct to write.
--> Keep ViewHolders "dumb" -- they hold view references and simple `bind(data)` methods; business logic and click handling delegation belong in the Activity/Fragment/ViewModel, not the adapter.
--> Set `setHasFixedSize(true)` on the RecyclerView when you know the overall list size changes won't affect the RecyclerView's own size (common case) -- a minor but free layout-pass optimization.
