/*
 * 09_recyclerview_in_depth_demo.java
 *
 * NOTE: This file is illustrative Android code, not a standalone runnable Java program.
 * It REQUIRES an Android Studio project (with the Android SDK, AndroidX, and a
 * generated R class from res/ resources) to compile and run. The classes below are meant
 * to be dropped into app/src/main/java/<your/package>/ as SEPARATE .java files
 * (Message.java, MessageAdapter.java, MessageDiffCallback.java, MessageListAdapter.java,
 * FeedActivity.java respectively) in a project that also declares matching minimal layouts
 * (sketched at the bottom of this file in comments).
 *
 * Demonstrates, from Theory chapter:
 *     10) Android Development with Java/Theory/09 RecyclerView In Depth.md
 *
 * Covers:
 *     1. A RecyclerView.Adapter/ViewHolder implementation (hand-rolled, not ListAdapter)
 *     2. A DiffUtil.Callback class computing minimal insert/remove/move/change operations
 *     3. A ListAdapter usage example (DiffUtil built in, background-thread diffing)
 *     4. Multiple view types handling (a header row mixed with chat-style sent/received
 *        message bubbles), including getBindingAdapterPosition() click handling
 */

package com.example.advancedcomponents;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

/**
 * Message -- the data model backing both the hand-rolled adapter and the ListAdapter example.
 * A stable 'id' field and a proper equals()/hashCode() are exactly what make DiffUtil callbacks
 * trivial and correct to write (per the theory chapter's Best Practices).
 */
class Message {
    final long id;
    final String text;
    final boolean isOutgoing; // true = sent by the current user, false = received

    Message(long id, String text, boolean isOutgoing) {
        this.id = id;
        this.text = text;
        this.isOutgoing = isOutgoing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message other = (Message) o;
        return id == other.id && isOutgoing == other.isOutgoing && Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, isOutgoing);
    }
}

/**
 * MessageAdapter -- a hand-rolled RecyclerView.Adapter/ViewHolder implementation demonstrating
 * MULTIPLE VIEW TYPES: position 0 is always a header row, every other position is a chat
 * message bubble. getItemViewType() branches both onCreateViewHolder() and onBindViewHolder().
 */
class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MESSAGE = 1;

    interface OnMessageClickListener {
        void onMessageClick(Message message);
    }

    private final List<Message> messages;
    private final OnMessageClickListener listener;

    MessageAdapter(List<Message> messages, OnMessageClickListener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    /** ViewHolder for the single header row. */
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView textHeader;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textHeader = itemView.findViewById(R.id.textHeader);
        }
        void bind(String title) {
            textHeader.setText(title);
        }
    }

    /**
     * ViewHolder for a single chat message. The click listener is bound ONCE in the
     * constructor (rather than freshly in every onBindViewHolder() call) and looks up the
     * CURRENT item via getBindingAdapterPosition() at click time -- the recommended pattern
     * for performance-sensitive lists, since a captured stale 'position' int from bind time
     * could point at the wrong item if the list changed between binding and the actual click.
     */
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textMessage;

        MessageViewHolder(@NonNull View itemView, List<Message> messages, OnMessageClickListener listener) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return; // item has since been removed -- nothing valid to click
                }
                // -1 to account for the header occupying position 0.
                listener.onMessageClick(messages.get(position - 1));
            });
        }

        void bind(Message message) {
            textMessage.setText(message.text);
            textMessage.setSelected(message.isOutgoing); // purely illustrative style hook
        }
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? TYPE_HEADER : TYPE_MESSAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            // 'false' -- RecyclerView itself calls addView() at the right time; passing 'true'
            // double-attaches the view and crashes or misbehaves.
            View view = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view, messages, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // onBindViewHolder() is called far more often than onCreateViewHolder() -- keep it
        // CHEAP: just assign values to already-found views, never findViewById() or inflate here.
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind("Conversation");
        } else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(messages.get(position - 1)); // offset for header
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + 1; // +1 for the header row
    }
}

/**
 * MessageDiffCallback -- a stand-alone DiffUtil.Callback demonstrating the manual
 * calculate/dispatch dance (as opposed to ListAdapter, which wraps this automatically --
 * see MessageListAdapter below). Useful to understand what ListAdapter is doing under the hood,
 * and for adapters that can't extend ListAdapter for some structural reason.
 */
class MessageDiffCallback extends DiffUtil.Callback {
    private final List<Message> oldList;
    private final List<Message> newList;

    MessageDiffCallback(List<Message> oldList, List<Message> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // Compare a STABLE IDENTITY (a database primary key / unique id field) -- NOT object
        // reference equality unless old and new lists genuinely reuse the same objects.
        return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        // Only ever called for pairs where areItemsTheSame() already returned true. Requires a
        // proper equals() on Message (defined above) -- without it, DiffUtil falls back to
        // reference equality and updated field values on the same object won't be detected.
        return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }
}

/*
 * Example of driving MessageDiffCallback manually from a plain RecyclerView.Adapter
 * (would live as a method on MessageAdapter above, in real code):
 *
 * void updateMessages(List<Message> newMessages) {
 *     DiffUtil.DiffResult result = DiffUtil.calculateDiff(new MessageDiffCallback(this.messages, newMessages));
 *     this.messages.clear();
 *     this.messages.addAll(newMessages);
 *     result.dispatchUpdatesTo(this);   // fires precise notifyItemXxx() calls, not notifyDataSetChanged()
 * }
 *
 * For very large lists, DiffUtil.calculateDiff() is O(N + D^2) in the worst case and should be
 * run on a background thread (pass in immutable snapshots of both lists, then dispatch the
 * result back on the main thread) -- ListAdapter below does exactly this automatically.
 */

/**
 * MessageListAdapter -- ListAdapter<T, VH> usage example: DiffUtil calculation is wrapped
 * automatically on a background thread, so submitList() is the entire update contract --
 * no manual notifyItemXxx()/notifyDataSetChanged() calls anywhere in this class.
 */
class MessageListAdapter extends ListAdapter<Message, MessageListAdapter.MessageViewHolder> {

    private static final DiffUtil.ItemCallback<Message> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Message>() {
                @Override
                public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                    return oldItem.equals(newItem);
                }
            };

    MessageListAdapter() {
        super(DIFF_CALLBACK);
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView textMessage;
        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
        }
        void bind(Message message) {
            textMessage.setText(message.text);
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        // getItem(position) -- ListAdapter manages the backing immutable list snapshot internally.
        holder.bind(getItem(position));
    }
}

/*
 * ---------------------------------------------------------------------------------------------
 * FeedActivity.java -- save as a SEPARATE file. Wires up both adapters to two separate
 * RecyclerViews purely for side-by-side illustration; a real screen would pick ONE approach.
 * ---------------------------------------------------------------------------------------------
 *
 * package com.example.advancedcomponents;
 *
 * import android.os.Bundle;
 * import androidx.appcompat.app.AppCompatActivity;
 * import androidx.recyclerview.widget.LinearLayoutManager;
 * import androidx.recyclerview.widget.RecyclerView;
 * import java.util.ArrayList;
 * import java.util.List;
 *
 * public class FeedActivity extends AppCompatActivity {
 *
 *     private MessageListAdapter listAdapter;
 *
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.layout.activity_feed);
 *
 *         List<Message> initialMessages = new ArrayList<>();
 *         initialMessages.add(new Message(1, "Hey there!", false));
 *         initialMessages.add(new Message(2, "Hi! How's the RecyclerView chapter going?", true));
 *
 *         // Hand-rolled multi-view-type adapter (header + messages).
 *         RecyclerView recyclerViewMulti = findViewById(R.id.recyclerViewMultiType);
 *         recyclerViewMulti.setLayoutManager(new LinearLayoutManager(this));
 *         recyclerViewMulti.setHasFixedSize(true); // free layout-pass optimization when overall size is known stable
 *         recyclerViewMulti.setAdapter(new MessageAdapter(initialMessages,
 *                 message -> { /* open message detail *\/ }));
 *
 *         // ListAdapter-based list -- DiffUtil handled internally.
 *         RecyclerView recyclerViewDiffing = findViewById(R.id.recyclerViewListAdapter);
 *         recyclerViewDiffing.setLayoutManager(new LinearLayoutManager(this));
 *         listAdapter = new MessageListAdapter();
 *         recyclerViewDiffing.setAdapter(listAdapter);
 *         listAdapter.submitList(initialMessages);
 *     }
 *
 *     private void onNewMessageArrived(Message newMessage) {
 *         // Caveat: mutating the SAME list instance previously passed to submitList() and
 *         // passing it again does nothing (ListAdapter short-circuits on reference equality) --
 *         // always build and pass a NEW list.
 *         List<Message> updated = new ArrayList<>(listAdapter.getCurrentList());
 *         updated.add(newMessage);
 *         listAdapter.submitList(updated); // diffing happens off the main thread automatically
 *     }
 * }
 *
 * ---------------------------------------------------------------------------------------------
 * Minimal res/layout/item_header.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="16dp">
 *     <TextView android:id="@+id/textHeader"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content"
 *         android:textStyle="bold" android:textSize="18sp" />
 * </LinearLayout>
 *
 * Minimal res/layout/item_message.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="wrap_content" android:padding="12dp">
 *     <TextView android:id="@+id/textMessage"
 *         android:layout_width="wrap_content" android:layout_height="wrap_content" android:textSize="16sp" />
 * </LinearLayout>
 *
 * Minimal res/layout/activity_feed.xml:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
 *     <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerViewMultiType"
 *         android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
 *     <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerViewListAdapter"
 *         android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
 * </LinearLayout>
 */
