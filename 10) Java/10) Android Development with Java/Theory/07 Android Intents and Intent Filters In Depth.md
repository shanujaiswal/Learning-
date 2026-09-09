# Recap -- What an Intent Is

--> An **Intent** is a messaging object that requests an action from another app component (start an Activity, start a Service, or deliver a broadcast) -- the basic explicit-vs-implicit split is covered in the Activities and Lifecycle file; this file goes deeper into HOW the OS resolves intents, how to declare intent filters yourself, deferred/permission-carrying intents (`PendingIntent`), and the pitfalls that show up once an app grows past toy examples.
--> Every Intent has up to six pieces of information the OS/receiving component can act on: **component** (explicit target), **action**, **data** (a `Uri` + MIME type), **category**, **extras** (a `Bundle` of key-value data), and **flags** (control task/back-stack behavior, e.g. `FLAG_ACTIVITY_NEW_TASK`).

```java
Intent intent = new Intent();
intent.setAction(Intent.ACTION_VIEW);
intent.setData(Uri.parse("https://example.com"));
intent.addCategory(Intent.CATEGORY_BROWSABLE);
intent.putExtra("SOURCE", "share_button");
intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
```

# Explicit Intents -- Beyond the Basics

--> An explicit intent names the target component directly, either via `Class` (within your own app) or via `ComponentName` (package name + class name), which is how one app can explicitly target a component in ANOTHER app -- provided that component is `exported` and you know its fully-qualified names.

```java
// Targeting a component in another installed app by package + class name
ComponentName component = new ComponentName(
        "com.example.otherapp",
        "com.example.otherapp.ui.ShareActivity");
Intent intent = new Intent();
intent.setComponent(component);
startActivity(intent);
```

--> Explicit intents can ALSO carry an action/data/extras -- "explicit" only means a component is set; the OS does not need to resolve anything because the target is already unambiguous, but the receiving component can still read `getAction()`/`getData()`/extras exactly as with an implicit intent -- useful when one Activity handles several distinct "modes" reached from different callers.
--> Since API 30 (Android 11), **package visibility** restrictions mean your app cannot query or explicitly start arbitrary components in other apps unless you declare a `<queries>` element in the manifest naming the target package, an intent action/intent-filter it matches, or hold the `QUERY_ALL_PACKAGES` permission (heavily restricted on Play Store) -- this is a common source of "works on my old emulator, breaks on a real device" bugs.

```xml
<queries>
    <package android:name="com.example.otherapp" />
    <intent>
        <action android:name="android.intent.action.SEND" />
        <data android:mimeType="image/*" />
    </intent>
</queries>
```

# Implicit Intents -- How Resolution Actually Works

--> When you fire an implicit intent, the OS's **Package Manager** compares the intent's action, categories, and data (URI scheme/host/path + MIME type) against every `<intent-filter>` declared by every installed app's manifest, and finds the set of components whose filter matches ALL of the intent's characteristics.
--> If exactly one component matches, it launches directly; if MULTIPLE match, the OS shows a disambiguation dialog (the "Open with..." chooser) UNLESS you explicitly force a chooser yourself with `Intent.createChooser()` -- forcing the chooser is recommended for actions like sharing, since it guarantees the user always sees a picker rather than accidentally setting a default handler.
--> If ZERO components match, `startActivity()` throws an `ActivityNotFoundException` -- always check resolution is possible before calling it in code paths where the target might not exist:

```java
Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Eiffel+Tower"));
if (intent.resolveActivity(getPackageManager()) != null) {
    startActivity(intent);
} else {
    Toast.makeText(this, "No app found to handle this request", Toast.LENGTH_SHORT).show();
}
```

--> `resolveActivity()` itself is subject to the same package-visibility rules noted above on API 30+ -- if you're checking for a specific known app (not a generic action like `ACTION_VIEW`), you likely need a `<queries>` entry for it too.

# Declaring Intent Filters in the Manifest

--> A component (`<activity>`, `<service>`, `<receiver>`) advertises the implicit intents it's willing to handle by declaring one or more `<intent-filter>` blocks -- a component can have MULTIPLE filters, and a single filter can list multiple actions/categories, but only ONE `<data>` specification pattern combination per filter is generally recommended for clarity (multiple `<data>` tags are unioned, which gets confusing fast).

```xml
<activity android:name=".ShareTargetActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>

<activity android:name=".DeepLinkActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="www.example.com"
            android:pathPrefix="/product" />
    </intent-filter>
</activity>
```

--> **`<action>`** -- the operation being requested (`ACTION_VIEW`, `ACTION_SEND`, or a custom string like `"com.example.MY_ACTION"` -- custom actions should be namespaced with your package name to avoid collisions).
--> **`<category>`** -- `CATEGORY_DEFAULT` MUST be present on a filter for it to receive implicit intents fired via plain `startActivity()` (the category is added automatically by `Intent` factory methods, but you must still declare it in the filter) -- `CATEGORY_BROWSABLE` is required for a filter to be reachable from a web browser link/deep link.
--> **`<data>`** -- matched on scheme, host, port, path (or `pathPrefix`/`pathPattern`), and `mimeType` -- an intent's data matches a filter's `<data>` only if EVERY attribute the filter specifies is present and matches; omitted attributes in the filter act as wildcards for that attribute.
--> **`android:exported`** -- as of API 31 (Android 12), any component with an `<intent-filter>` MUST explicitly declare `android:exported="true"` or `"false"` -- omitting it is a manifest-merge error at build time; this is a deliberate hardening measure so developers consciously decide whether a component is reachable from outside the app.

# Deep Links vs App Links

--> A **deep link** (custom scheme, e.g. `myapp://product/42`, or an `https` URI matched only via `BROWSABLE`/`DEFAULT`) can be claimed by ANY app whose filter matches -- if two apps declare the same scheme/host, the user sees a disambiguation dialog, which is a weak guarantee and a common phishing vector for imitation apps.
--> An **Android App Link** (API 23+) uses `https`/`http` intent filters PLUS `android:autoVerify="true"`, backed by a `assetlinks.json` file hosted at `https://<host>/.well-known/assetlinks.json` that cryptographically proves your app owns that domain -- once verified, the OS routes matching links straight to your app with NO disambiguation dialog, since domain ownership has already been proven.

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="www.example.com" />
</intent-filter>
```

# PendingIntent -- Handing Off Permission to Fire an Intent Later

--> A **`PendingIntent`** wraps a regular `Intent` together with the permission and identity to execute it LATER, as if your app fired it, even from a different process that doesn't have your app's permissions -- this is essential because you can't just hand a raw `Intent` to the system/another app and expect it to be fired with YOUR app's privileges; the classic use cases are notifications (tapping a notification fires an intent from the System UI process), alarms (`AlarmManager`), app widgets, and home-screen shortcuts.

```java
Intent intent = new Intent(this, DetailActivity.class);
intent.putExtra("ITEM_ID", 42);

int requestCode = 0;
int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent, flags);

// Handed to, e.g., a NotificationCompat.Builder -- the System UI process fires it on tap,
// with YOUR app's identity/permissions, even though System UI itself constructed nothing.
```

--> The four factory methods -- `getActivity()`, `getService()`, `getBroadcast()`, and `getForegroundService()` -- mirror the four ways you could fire the wrapped Intent yourself.
--> **`FLAG_IMMUTABLE`** vs **`FLAG_MUTABLE`** -- since API 31, one of these flags is REQUIRED; `FLAG_IMMUTABLE` (the safe default for most cases, e.g. notification taps) means the receiving system component CANNOT modify the wrapped Intent's extras before firing it -- `FLAG_MUTABLE` is only needed for specific APIs that must fill in data afterward (e.g. `RemoteInput` for notification quick-reply, or exact-alarm scheduling that appends data).
--> **`FLAG_UPDATE_CURRENT`** -- if a `PendingIntent` with the same request code and matching Intent "filter equality" already exists, replace its extras with the new ones rather than creating a duplicate -- without this flag, calling `getActivity()` again with the same request code returns the ORIGINAL PendingIntent's extras, silently ignoring your new ones, which is a very common bug when updating an existing notification.
--> `PendingIntent` equality for the purposes of `FLAG_UPDATE_CURRENT`/reuse is based on the request code AND the wrapped Intent's action/data/categories/component/package -- NOT its extras -- so two logically different pending intents meant to stay independent need distinct request codes.

# Intent Extras -- Best Practices

--> Always namespace extra keys with your package name (`"com.example.app.EXTRA_USER_ID"`) to avoid collisions with extras another app or component might set on the same Intent object, especially for extras read by exported components that any app could target.
--> Prefer defining extra key constants as `public static final String` fields on the class that OWNS/reads them, so callers reference `DetailActivity.EXTRA_ITEM_ID` rather than duplicating a raw string literal in multiple places (a classic copy-paste typo source).
--> Never assume an extra is present -- always use the defaulted getters (`getIntExtra(key, default)`, `getStringExtra(key)` which itself returns `null` if absent) and handle the missing case gracefully, since ANY component that is `exported` can be launched by any other app with an Intent missing expected extras (or none at all).
--> Keep extras small -- the whole Bundle travels through Binder IPC with a strict size limit (historically ~1MB shared across the whole transaction, including savedInstanceState); pass an ID and re-fetch the full object from a database/repository on the receiving side rather than serializing a large object graph into extras.
--> For strongly-typed custom object extras, prefer `Parcelable` (see the Activities and Lifecycle file) -- and as of API 33, use the type-safe `getParcelableExtra(key, Class)` overload instead of the deprecated raw `getParcelableExtra(key)`, which suppresses an unchecked-cast warning.

```java
// API 33+ type-safe retrieval
User user = intent.getParcelableExtra(EXTRA_USER, User.class);

// Pre-33 (deprecated but still functional)
User legacyUser = intent.getParcelableExtra(EXTRA_USER);
```

# Common Gotchas

--> **Forgetting `CATEGORY_DEFAULT` in a filter** -- a filter without it silently never matches intents fired via `startActivity(intent)` with no explicit categories set, which is confusing because the filter LOOKS correct at a glance.
--> **Exported components with no permission check** -- an `exported="true"` Activity/Service/Receiver with a broad intent filter is reachable by ANY app on the device (including malicious ones) -- validate/sanitize incoming extras defensively, and use `android:permission` on the component if it should only be reachable by apps holding a specific permission.
--> **Mutable `PendingIntent` used where immutable would do** -- before API 31 made the flag mandatory, many apps left PendingIntents implicitly mutable, which let a malicious app that obtained a reference to it (e.g. via a leaked notification) rewrite the wrapped Intent's target/extras -- always default to `FLAG_IMMUTABLE` unless you have a specific documented reason to need mutability.
--> **Reusing a `PendingIntent` request code across logically different intents without `FLAG_UPDATE_CURRENT`/distinct request codes** -- leads to the wrong (stale) Intent firing.
--> **Assuming `resolveActivity()`/implicit intents "just work" on API 30+** -- package-visibility filtering silently makes `resolveActivity()` return `null` (as if no app were installed) even when a matching app IS installed, if you haven't declared the right `<queries>` element.

# Best Practices

--> Use explicit intents for all in-app navigation; reserve implicit intents for genuinely inter-app actions (share, view a URL, pick a contact, compose an email).
--> Force a chooser (`Intent.createChooser()`) for share-style actions so the user isn't silently locked into a default handler they didn't consciously pick.
--> Declare `<queries>` entries as narrowly as possible (specific package, or specific action+data) rather than requesting `QUERY_ALL_PACKAGES`, which draws extra Play Store review scrutiny and is rarely justified.
--> Prefer Android App Links (`autoVerify="true"` + `assetlinks.json`) over bare custom-scheme deep links whenever the content is also reachable on the web, since App Links skip the disambiguation dialog and can't be spoofed by another app claiming the same scheme.
--> Always pick `FLAG_IMMUTABLE` for `PendingIntent` unless a specific API (like notification `RemoteInput`) documents that it requires `FLAG_MUTABLE`.
