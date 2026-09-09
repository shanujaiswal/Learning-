# Why IndexedDB Exists

--> `localStorage` (covered in the JSON/Storage/Modules file) only stores simple strings, is synchronous (blocks the main thread on every read/write), and has a small storage limit (typically ~5-10MB) -- fine for small settings/flags, unsuitable for large or structured client-side data.
--> IndexedDB is a full transactional, asynchronous, object-oriented database built into the browser -- designed for storing large amounts of structured data client-side (offline app data, cached API responses, file blobs).

# Core Concepts

--> Database -- the top-level container, versioned (schema changes require bumping the version number).
--> Object Store -- roughly equivalent to a "table" -- stores JavaScript objects, not just strings.
--> Index -- lets you query an object store efficiently by a field other than its primary key.
--> Transaction -- every read/write happens within a transaction, scoped to specific object stores, with a mode (`readonly` or `readwrite`).

# Opening a Database and Creating a Store

```javascript
const request = indexedDB.open("MyAppDB", 1);   // (name, version)

request.onupgradeneeded = (event) => {
  const db = event.target.result;
  // Runs only when the DB is first created OR the version number increases
  const store = db.createObjectStore("notes", { keyPath: "id" });
  store.createIndex("byTitle", "title", { unique: false });
};

request.onsuccess = (event) => {
  const db = event.target.result;
  console.log("Database opened successfully");
};

request.onerror = (event) => {
  console.error("Database error:", event.target.error);
};
```

# Reading and Writing Data

```javascript
function addNote(db, note) {
  const tx = db.transaction("notes", "readwrite");
  const store = tx.objectStore("notes");
  store.add(note);   // note = { id: 1, title: "Groceries", body: "Milk, eggs" }
}

function getNote(db, id) {
  const tx = db.transaction("notes", "readonly");
  const store = tx.objectStore("notes");
  const request = store.get(id);

  request.onsuccess = () => console.log(request.result);
}

function getAllNotes(db) {
  const tx = db.transaction("notes", "readonly");
  const store = tx.objectStore("notes");
  const request = store.getAll();

  request.onsuccess = () => console.log(request.result);
}
```

# Why the Raw API Is Rarely Used Directly

--> IndexedDB's native API is entirely callback/event-based and notoriously verbose for something conceptually simple -- most real projects wrap it with a Promise-based library rather than writing raw `onsuccess`/`onerror` handlers everywhere.
--> Dexie.js and `idb` (a lightweight Promise wrapper by Jake Archibald) are the most common choices, turning the callback API above into clean `async`/`await` code.

```javascript
import { openDB } from "idb";

const db = await openDB("MyAppDB", 1, {
  upgrade(db) {
    db.createObjectStore("notes", { keyPath: "id" });
  },
});

await db.add("notes", { id: 1, title: "Groceries", body: "Milk, eggs" });
const note = await db.get("notes", 1);
```

# Common Use Cases

--> Offline-first web apps -- storing app data locally so the app remains functional without a network connection, syncing with the server when connectivity returns.
--> Caching large API responses to avoid re-fetching the same data repeatedly.
--> Storing file/blob data client-side (e.g. a Progressive Web App caching downloaded assets), often used together with Service Workers for full offline support.
