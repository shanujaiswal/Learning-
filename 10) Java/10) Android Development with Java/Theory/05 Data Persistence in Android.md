# Overview -- Choosing the Right Persistence Mechanism

--> Android offers several distinct persistence mechanisms, each suited to a different kind of data -- picking the wrong one is a common source of both bugs and unnecessary complexity.

| Mechanism | Best for |
|---|---|
| `SharedPreferences` | Small amounts of primitive key-value data (settings, flags, a login token) |
| SQLite / Room | Structured, relational, queryable data (lists of records, search, relationships) |
| Internal/External file storage | Larger blobs -- files, images, exported documents, caches |
| A remote server (with local caching) | Data that must sync across devices or be shared with other users |

# SharedPreferences -- Simple Key-Value Storage

--> **`SharedPreferences`** stores primitive key-value pairs (String, int, boolean, float, long, String sets) in a private XML file on the device -- ideal for small settings-like data, NOT for large or structured datasets.

```java
// Writing
SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
SharedPreferences.Editor editor = prefs.edit();
editor.putString("username", "vanisha");
editor.putBoolean("dark_mode_enabled", true);
editor.putInt("launch_count", prefs.getInt("launch_count", 0) + 1);
editor.apply();   // asynchronous write -- fire and forget (preferred)
// editor.commit();   // synchronous write -- returns a boolean success flag, blocks the calling thread

// Reading
String username = prefs.getString("username", "");         // second arg is the default value
boolean darkMode = prefs.getBoolean("dark_mode_enabled", false);
int launches = prefs.getInt("launch_count", 0);
```

--> **`apply()` vs `commit()`** -- `apply()` writes to the in-memory cache immediately (subsequent reads within the same process see it right away) and persists to disk asynchronously in the background -- `commit()` does the disk write SYNCHRONOUSLY on the calling thread and returns whether it succeeded -- `apply()` is preferred for nearly all cases since it avoids blocking, unless you specifically need the synchronous success/failure result before proceeding.
--> **`getSharedPreferences(name, mode)` vs `getPreferences(mode)`** -- the former lets you name a specific preferences file (useful for grouping related settings, or for a shared file accessible from multiple components); the latter (Activity-only) uses one implicit file named after the Activity's class.
--> **`Context.MODE_PRIVATE`** is effectively the only mode used in modern Android -- `MODE_WORLD_READABLE`/`MODE_WORLD_WRITEABLE` were REMOVED entirely in later API levels for security reasons (any inter-app data sharing should go through a proper `ContentProvider` instead).
--> **PreferenceScreen / Jetpack Preference library** -- for building an actual Settings UI backed by SharedPreferences, the AndroidX Preference library (`androidx.preference`) provides ready-made `Preference` Views (`SwitchPreferenceCompat`, `ListPreference`, etc.) wired directly to a SharedPreferences file via a declarative XML preference screen, rather than hand-building Settings UI yourself.

# SQLite -- The Built-in Relational Database

--> Every Android device ships with **SQLite**, a lightweight embedded relational database engine, usable directly via `SQLiteOpenHelper` and raw SQL -- this is the low-level foundation that Room (below) is built on top of.

```java
public class AppDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "app.db";
    private static final int DB_VERSION = 1;

    public AppDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "body TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notes");   // simplest strategy -- real apps use ALTER TABLE migrations
        onCreate(db);
    }
}
```

--> **`onUpgrade()`** is called automatically when `DB_VERSION` is incremented and the app runs against an existing older database file -- a real production migration would use `ALTER TABLE` statements per version step rather than dropping data, since dropping the table destroys all existing user data.
--> Working directly with `SQLiteOpenHelper` means writing raw SQL strings by hand and manually mapping `Cursor` rows to objects -- functional, but verbose, error-prone (typos in SQL are only caught at runtime), and easy to get wrong around resource cleanup (`Cursor.close()`), which is exactly the pain Room was built to remove.

# Room -- The Modern Persistence Library

--> **Room** is a Jetpack library that sits on top of SQLite, providing compile-time-verified SQL, automatic mapping between database rows and Java objects, and elimination of most `SQLiteOpenHelper` boilerplate. Three core pieces:

```java
// 1) Entity -- a Java class annotated to represent a table
@Entity(tableName = "notes")
public class Note {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String title;

    public String body;
}
```

```java
// 2) DAO (Data Access Object) -- an interface declaring queries; Room generates the implementation
@Dao
public interface NoteDao {
    @Insert
    void insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("SELECT * FROM notes ORDER BY id DESC")
    LiveData<List<Note>> getAllNotes();     // LiveData return type -- auto-updates observers on change

    @Query("SELECT * FROM notes WHERE id = :noteId")
    Note getNoteById(int noteId);
}
```

```java
// 3) Database -- ties Entities and DAOs together, typically as a singleton
@Database(entities = {Note.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract NoteDao noteDao();

    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "app_database")
                            .build();
                }
            }
        }
        return instance;
    }
}
```

```java
// Usage
AppDatabase db = AppDatabase.getInstance(getApplicationContext());

// WRONG -- database calls must NOT run on the main thread (Room throws by default if they do)
// Note note = db.noteDao().getNoteById(1);

// RIGHT -- run off the main thread, e.g. via an Executor or a background thread
Executors.newSingleThreadExecutor().execute(() -> {
    Note note = new Note();
    note.title = "Shopping list";
    note.body = "Milk, eggs, bread";
    db.noteDao().insert(note);
});
```

--> **Why Room enforces compile-time SQL verification** -- annotation processing validates `@Query` strings against the Entity schema when you BUILD the app, catching a misspelled column name at compile time instead of as a runtime crash -- a major reliability improvement over raw `SQLiteOpenHelper` string SQL.
--> **Room forbids main-thread database access by default** -- database I/O is disk I/O, which can block long enough to freeze the UI or trigger an ANR -- Room throws an `IllegalStateException` at runtime if you call a DAO method from the main thread without explicitly opting out (`allowMainThreadQueries()`, strongly discouraged outside of quick prototyping/tests).
--> **Migrations** -- when the schema changes, bumping `version` in `@Database` without providing a `Migration` causes Room to CRASH at startup by default (unless `.fallbackToDestructiveMigration()` is used, which wipes the database) -- production apps define explicit `Migration` objects with the exact `ALTER TABLE`/etc. SQL needed to go from one version to the next, preserving user data.

# Simple File Storage

--> Beyond structured data, apps often need to read/write raw files -- images, exported reports, caches -- Android offers a few distinct storage areas:

```java
// Internal storage -- private to this app, deleted automatically on uninstall, no permission needed
File file = new File(getFilesDir(), "notes_backup.txt");
try (FileOutputStream fos = new FileOutputStream(file)) {
    fos.write("Some content".getBytes());
} catch (IOException e) {
    e.printStackTrace();
}

try (FileInputStream fis = new FileInputStream(file);
     BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
    StringBuilder content = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
        content.append(line).append("\n");
    }
} catch (IOException e) {
    e.printStackTrace();
}

// Cache directory -- also private, but the OS may delete files here under storage pressure
File cacheFile = new File(getCacheDir(), "temp_thumbnail.png");

// External storage (app-specific directory, no special permission needed since API 19)
File externalFile = new File(getExternalFilesDir(null), "exported_report.pdf");
```

--> **Internal vs external storage** -- internal storage (`getFilesDir()`, `getCacheDir()`) is always private to your app and requires no permissions; external storage historically meant the shared SD card and required permissions, but modern Android (Scoped Storage, from API 29+) heavily restricts direct access to OTHER apps' files -- an app's own directory under external storage (`getExternalFilesDir()`) remains permission-free and is the recommended place for larger app-specific files that don't need to be private.
--> **Scoped Storage** -- since Android 10 (API 29), apps can no longer freely browse the entire shared external storage the way older apps could -- accessing files outside your own app-specific directories now generally requires the `MediaStore` API (for media) or the Storage Access Framework (a system file picker, for arbitrary user-selected files) rather than raw file paths.

# Common Gotchas

--> **Using SharedPreferences for large or structured data** -- it loads the ENTIRE file into memory on first access and has no query capability -- once data has any real structure or grows non-trivially, it belongs in Room/SQLite instead.
--> **Forgetting `apply()`/`commit()`** -- calling `edit().putString(...)` without following through with `apply()` or `commit()` silently does nothing; the Editor batches changes and they're a no-op until committed.
--> **Running Room queries on the main thread** -- crashes immediately in debug builds by design -- always dispatch through an Executor, background thread, or (in modern code) Kotlin coroutines/RxJava if the project uses them.
--> **Not handling database migrations** -- shipping a schema change without a `Migration` causes existing users' apps to crash on update (or silently lose all data with `fallbackToDestructiveMigration`) -- always plan the migration path before changing `@Entity` shapes in a released app.

# Best Practices

--> Use SharedPreferences ONLY for small, flat key-value settings; reach for Room the moment data has any real structure, relationships, or needs to be queried/filtered.
--> Wrap all disk/database access in background execution -- never assume "it's probably fast enough" for the main thread, since device performance and storage speed vary wildly across the Android device ecosystem.
--> Treat internal storage as the default for private app data; only use external/shared storage (and the corresponding Scoped Storage APIs) when the data genuinely needs to be visible outside your app.
--> Version your Room schema deliberately and always supply migrations for released apps -- destructive migrations are only acceptable for pre-release / cache-only data.
