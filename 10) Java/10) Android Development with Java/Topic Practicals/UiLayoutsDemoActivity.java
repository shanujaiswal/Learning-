/*
 * UiLayoutsDemoActivity.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class / generated ViewBinding classes from res/ resources) to compile and
 * run. The classes below are meant to be dropped into app/src/main/java/<your/package>/ as
 * SEPARATE .java files (UiLayoutsDemoActivity.java, MessageAdapter.java) alongside matching
 * layouts (sketched at the bottom of this file in comments). ViewBinding must additionally
 * be enabled in app/build.gradle:
 *     android { buildFeatures { viewBinding true } }
 *
 * Demonstrates, from Theory chapter:
 *     10) Java/10) Android Development with Java/Theory/03 UI Layouts and Views.md
 *
 * Covers:
 *     1. findViewById() -- the classic, manual way to look up inflated Views by id
 *     2. ViewBinding -- the modern, null-safe, typo-proof alternative (generated ActivityUiLayoutsDemoBinding)
 *     3. Common Views wired up with listeners: TextView, Button, EditText, ImageView
 *     4. A minimal RecyclerView Adapter (+ ViewHolder) skeleton for displaying a list
 */

package com.example.uilayoutsdemo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uilayoutsdemo.databinding.ActivityUiLayoutsDemoBinding;

import java.util.Arrays;
import java.util.List;

public class UiLayoutsDemoActivity extends AppCompatActivity {

    private static final String TAG = "UiLayoutsDemoActivity";

    // Holds the generated ViewBinding class instance once inflated in onCreate(). ViewBinding
    // generates one binding class per XML layout (activity_ui_layouts_demo.xml ->
    // ActivityUiLayoutsDemoBinding), with one strongly-typed field per android:id in that file.
    private ActivityUiLayoutsDemoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ---------------------------------------------------------------------------------
        // APPROACH A: findViewById (classic)
        // ---------------------------------------------------------------------------------
        // setContentView(R.layout.activity_ui_layouts_demo);
        // TextView titleTextClassic = findViewById(R.id.textTitle);
        // Button submitButtonClassic = findViewById(R.id.buttonSubmit);
        //
        // Downsides shown by this style: every lookup is a manual cast-free-but-untyped-until-
        // resolved call, each id string is duplicated across every Activity/Fragment that
        // needs that View, a lookup for an id that doesn't exist in the CURRENT layout compiles
        // fine and only fails at runtime with a NullPointerException, and there is no compiler
        // help if the XML id is renamed and the Java call site is missed.

        // ---------------------------------------------------------------------------------
        // APPROACH B: ViewBinding (modern, preferred) -- used for the rest of this file
        // ---------------------------------------------------------------------------------
        // inflate() builds the whole View hierarchy from the XML AND populates every binding
        // field in one call; binding.getRoot() is the top-level View to hand to setContentView.
        binding = ActivityUiLayoutsDemoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupTextViewAndButton();
        setupEditTextListener();
        setupImageView();
        setupRecyclerView();
    }

    /** TextView + Button wiring: a click listener that mutates a TextView's text. */
    private void setupTextViewAndButton() {
        TextView titleText = binding.textTitle;
        Button submitButton = binding.buttonSubmit;

        titleText.setText("UI Layouts and Views Demo");

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Lambda form is more common in modern code, shown here as a plain anonymous
                // class first so the underlying View.OnClickListener interface is explicit.
                titleText.setText("Button was clicked!");
                Log.d(TAG, "buttonSubmit clicked");
                Toast.makeText(UiLayoutsDemoActivity.this, "Submitted", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** EditText: reacting to live text changes via a TextWatcher. */
    private void setupEditTextListener() {
        EditText nameInput = binding.editTextName;
        TextView greetingText = binding.textGreeting;

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Called just before the text changes -- rarely needed, included for completeness.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Called as the text is changing -- 's' is the new full text at this point.
                if (s.length() > 0) {
                    greetingText.setText("Hello, " + s + "!");
                } else {
                    greetingText.setText("");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Called after the change is committed -- the safe place to write the final
                // value back into a model object if this were bound to one.
            }
        });
    }

    /** ImageView: setting an image resource and reacting to clicks on it. */
    private void setupImageView() {
        ImageView logoImage = binding.imageLogo;
        logoImage.setImageResource(R.drawable.ic_launcher_foreground);
        logoImage.setOnClickListener(v -> Log.d(TAG, "Logo image tapped"));
    }

    /** RecyclerView: wiring a LayoutManager and our MessageAdapter (defined below) to some data. */
    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.recyclerMessages;

        // LayoutManager decides HOW items are arranged (vertical list here); RecyclerView itself
        // only handles view recycling/scrolling mechanics, not layout policy.
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<String> messages = Arrays.asList(
                "Welcome to the app!",
                "This item was recycled efficiently.",
                "RecyclerView only creates as many Views as fit on screen (+ a buffer).",
                "Scrolling reuses existing View objects instead of creating new ones."
        );

        MessageAdapter adapter = new MessageAdapter(messages, message ->
                Toast.makeText(this, "Tapped: " + message, Toast.LENGTH_SHORT).show());
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Releasing the binding reference avoids leaking the View hierarchy if anything else
        // (e.g. an async callback) still held a reference to `binding` past this Activity's life.
        binding = null;
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * MessageAdapter.java -- save as a SEPARATE file in the same package. A minimal RecyclerView
 * Adapter skeleton: one ViewHolder type, a simple List<String> data source, and a click callback
 * interface so the hosting Activity/Fragment can react to item taps without the adapter knowing
 * anything about Activities.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.uilayoutsdemo;
 *
 * import android.view.LayoutInflater;
 * import android.view.View;
 * import android.view.ViewGroup;
 * import android.widget.TextView;
 * import androidx.annotation.NonNull;
 * import androidx.recyclerview.widget.RecyclerView;
 * import java.util.List;
 *
 * public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
 *
 *     // Callback interface -- keeps the Adapter decoupled from any specific Activity/Fragment.
 *     public interface OnMessageClickListener {
 *         void onMessageClick(String message);
 *     }
 *
 *     private final List<String> messages;
 *     private final OnMessageClickListener listener;
 *
 *     public MessageAdapter(List<String> messages, OnMessageClickListener listener) {
 *         this.messages = messages;
 *         this.listener = listener;
 *     }
 *
 *     // ViewHolder: caches references to a single row's Views so findViewById isn't repeated
 *     // every time that row scrolls back into view.
 *     static class MessageViewHolder extends RecyclerView.ViewHolder {
 *         final TextView messageText;
 *
 *         MessageViewHolder(@NonNull View itemView) {
 *             super(itemView);
 *             messageText = itemView.findViewById(R.id.textMessage);
 *         }
 *     }
 *
 *     @NonNull
 *     @Override
 *     public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
 *         // Inflates one row's layout (item_message.xml) WITHOUT attaching it to the parent yet
 *         // (RecyclerView itself manages attaching/detaching as rows scroll in and out of view).
 *         View itemView = LayoutInflater.from(parent.getContext())
 *                 .inflate(R.layout.item_message, parent, false);
 *         return new MessageViewHolder(itemView);
 *     }
 *
 *     @Override
 *     public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
 *         String message = messages.get(position);
 *         holder.messageText.setText(message);
 *         holder.itemView.setOnClickListener(v -> listener.onMessageClick(message));
 *     }
 *
 *     @Override
 *     public int getItemCount() {
 *         return messages.size();
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/activity_ui_layouts_demo.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent"
 *     android:orientation="vertical" android:padding="16dp">
 *
 *     <TextView android:id="@+id/textTitle"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="20sp" />
 *
 *     <Button android:id="@+id/buttonSubmit"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Submit" />
 *
 *     <EditText android:id="@+id/editTextName"
 *         android:layout_width="match_parent" android:layout_height="wrap_content"
 *         android:hint="Enter your name" />
 *
 *     <TextView android:id="@+id/textGreeting"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" />
 *
 *     <ImageView android:id="@+id/imageLogo"
 *         android:layout_width="64dp" android:layout_height="64dp"
 *         android:contentDescription="@string/app_name" />
 *
 *     <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerMessages"
 *         android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
 *
 * </LinearLayout>
 *
 * Minimal res/layout/item_message.xml (one RecyclerView row):
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="12dp">
 *     <TextView android:id="@+id/textMessage"
 *         android:layout_width="match_parent" android:layout_height="wrap_content" />
 * </LinearLayout>
 *
 * And app/build.gradle (Module) must declare the RecyclerView dependency:
 *     implementation("androidx.recyclerview:recyclerview:1.3.2")
 */
