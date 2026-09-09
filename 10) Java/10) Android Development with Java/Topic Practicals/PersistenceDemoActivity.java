/*
 * PersistenceDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, the Room persistence
 * library, and a generated R class) to compile and run. The classes below are meant to be
 * dropped into app/src/main/java/<your/package>/ as SEPARATE .java files (PersistenceDemoActivity,
 * Note, NoteDao, AppDatabase), and Room requires annotationProcessor/kapt setup in build.gradle
 * (sketched at the bottom of this file in a comment).
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/05 Data Persistence in Android.md
 *
 * Covers:
 *     1. SharedPreferences -- reading and writing small key-value data (e.g. settings, flags)
 *     2. Room database -- an @Entity, a @Dao, and an @Database annotated illustrative setup
 *     3. Internal file storage -- reading and writing a plain file in the app's private storage
 */

package com.example.persistencedemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

public class PersistenceDemoActivity extends AppCompatActivity {

    private static final String TAG = "PersistenceDemoActivity";

    // ---------------------------------------------------------------------------------------
    // SharedPreferences constants -- a named preferences file plus the keys stored within it.
    // ---------------------------------------------------------------------------------------
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LAUNCH_COUNT = "launch_count";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";

    // Room database instance -- normally you would obtain this from a singleton holder/DI
    // framework rather than creating a new one per Activity; shown inline here for clarity.
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_persistence_demo);

        demonstrateSharedPreferences();
        demonstrateRoomDatabase();
        demonstrateInternalFileStorage();
    }

    /**
     * SharedPreferences: the standard mechanism for small amounts of primitive key-value data --
     * settings, feature flags, "has the user seen this tutorial" booleans, etc. Backed by an XML
     * file in the app's private data directory; NOT suitable for large or structured data (use
     * Room for that instead).
     */
    private void demonstrateSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // --- WRITE ---
        // SharedPreferences.Editor batches changes; apply() commits them asynchronously in the
        // background (fire-and-forget), while commit() would block and return a success boolean.
        // Prefer apply() unless you specifically need to know the write succeeded before proceeding.
        int previousLaunchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USERNAME, "ada_lovelace");
        editor.putInt(KEY_LAUNCH_COUNT, previousLaunchCount + 1);
        editor.putBoolean(KEY_DARK_MODE, true);
        editor.apply();

        // --- READ ---
        // Every getX() call takes a default value to return if the key has never been written --
        // this is what makes SharedPreferences safe to read from on a completely fresh install.
        String username = prefs.getString(KEY_USERNAME, "guest");
        int launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0);
        boolean darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE, false);

        Log.d(TAG, "SharedPreferences -- username=" + username
                + ", launchCount=" + launchCount + ", darkModeEnabled=" + darkModeEnabled);
    }

    /**
     * Room: a compile-time-verified SQL abstraction over SQLite. See the Note (@Entity),
     * NoteDao (@Dao), and AppDatabase (@Database) classes further down in this file.
     */
    private void demonstrateRoomDatabase() {
        database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "app_database")
                // allowMainThreadQueries() is ONLY for this illustrative demo -- Room forbids
                // running queries on the main thread by default because disk I/O can jank the UI.
                // Real apps use Room's suspend/Flow/LiveData query support, or run queries on a
                // background executor as shown here for the insert.
                .allowMainThreadQueries()
                .build();

        NoteDao noteDao = database.noteDao();

        // Running the INSERT off the main thread via a background Executor -- the illustrative
        // pattern for Room calls that are not using Kotlin coroutines/RxJava/LiveData return types.
        Executors.newSingleThreadExecutor().execute(() -> {
            long newRowId = noteDao.insert(new Note("Buy milk"));
            Log.d(TAG, "Room -- inserted note with rowId=" + newRowId);

            List<Note> allNotes = noteDao.getAll();
            Log.d(TAG, "Room -- total notes stored: " + allNotes.size());
            for (Note note : allNotes) {
                Log.d(TAG, "Room -- note #" + note.id + ": " + note.text);
            }
        });
    }

    /**
     * Internal (app-private) file storage: for data too large/unstructured for SharedPreferences
     * but that doesn't need Room's querying -- e.g. a cached blob, a log file, a downloaded asset.
     * Files written here live in /data/data/<package>/files/ and are deleted automatically when
     * the app is uninstalled; other apps cannot access them.
     */
    private void demonstrateInternalFileStorage() {
        String fileName = "notes_backup.txt";
        String contentToWrite = "This line was written to internal storage.\n"
                + "It is private to this app and removed on uninstall.";

        // --- WRITE ---
        // openFileOutput() always writes into the app's internal files directory -- there is no
        // way to accidentally write outside it using this API, which is what makes it "private".
        try (FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE)) {
            fos.write(contentToWrite.getBytes(StandardCharsets.UTF_8));
            Log.d(TAG, "Internal storage -- wrote file: " + fileName);
        } catch (IOException e) {
            Log.e(TAG, "Internal storage -- failed to write file", e);
        }

        // --- READ ---
        try (FileInputStream fis = openFileInput(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder readBack = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                readBack.append(line).append('\n');
            }
            Log.d(TAG, "Internal storage -- read back file contents:\n" + readBack);
        } catch (IOException e) {
            Log.e(TAG, "Internal storage -- failed to read file", e);
        }

        // Listing what's in the internal files directory, and how to delete a file if needed.
        File filesDir = getFilesDir();
        Log.d(TAG, "Internal storage directory: " + filesDir.getAbsolutePath());
        // deleteFile(fileName); // uncomment to clean up
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (database != null) {
            database.close();
        }
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * Note.java -- save as a SEPARATE file. The @Entity annotation makes Room generate a matching
 * SQLite table ("Note" by default, one column per field).
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.persistencedemo;
 *
 * import androidx.room.ColumnInfo;
 * import androidx.room.Entity;
 * import androidx.room.PrimaryKey;
 *
 * @Entity
 * public class Note {
 *
 *     @PrimaryKey(autoGenerate = true)
 *     public long id;
 *
 *     @ColumnInfo(name = "note_text")
 *     public String text;
 *
 *     public Note(String text) {
 *         this.text = text;
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * NoteDao.java -- save as a SEPARATE file. @Dao interfaces declare the SQL Room should run;
 * Room generates the implementation at compile time and verifies the SQL against the @Entity
 * fields, catching typos/column-name mismatches as COMPILE errors instead of runtime crashes.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.persistencedemo;
 *
 * import androidx.room.Dao;
 * import androidx.room.Insert;
 * import androidx.room.Query;
 * import java.util.List;
 *
 * @Dao
 * public interface NoteDao {
 *
 *     @Insert
 *     long insert(Note note); // returns the new row's generated id
 *
 *     @Query("SELECT * FROM Note ORDER BY id DESC")
 *     List<Note> getAll();
 *
 *     @Query("DELETE FROM Note WHERE id = :noteId")
 *     void deleteById(long noteId);
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * AppDatabase.java -- save as a SEPARATE file. @Database ties the @Entity list and version
 * number together, and exposes one abstract getter per @Dao.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.persistencedemo;
 *
 * import androidx.room.Database;
 * import androidx.room.RoomDatabase;
 *
 * @Database(entities = {Note.class}, version = 1, exportSchema = false)
 * public abstract class AppDatabase extends RoomDatabase {
 *     public abstract NoteDao noteDao();
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * app/build.gradle (Module) dependencies needed for Room (Java, using annotationProcessor):
 *
 * dependencies {
 *     implementation("androidx.room:room-runtime:2.6.1")
 *     annotationProcessor("androidx.room:room-compiler:2.6.1")
 * }
 */
