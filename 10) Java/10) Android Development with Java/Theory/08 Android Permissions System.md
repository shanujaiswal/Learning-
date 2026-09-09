# Why Permissions Exist

--> Android sandboxes every app -- by default, an app can only access its OWN files/data and a small set of harmless OS APIs; anything that touches sensitive user data (location, contacts, camera) or system-wide resources (internet, exact alarms) requires the app to explicitly DECLARE a **permission** in its manifest, and for the sensitive ones, ask the user's explicit consent AT RUNTIME.
--> The permission model has evolved significantly -- pre-Android 6.0 (API 23), ALL declared permissions were granted at INSTALL time, take-it-or-leave-it; from API 23 onward, dangerous permissions must be requested at RUNTIME while the app is running, and the user can revoke them individually at any time from Settings, even after granting.

# Protection Levels -- Normal vs Dangerous vs Signature

| Protection Level | Examples | Grant Mechanism |
|---|---|---|
| **normal** | `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `SET_ALARM` | Granted automatically at install time, simply by being declared in the manifest -- no user prompt, low risk to privacy/OS operation. |
| **dangerous** | `CAMERA`, `ACCESS_FINE_LOCATION`, `READ_CONTACTS`, `RECORD_AUDIO`, `POST_NOTIFICATIONS` (API 33+) | Declared in the manifest AND must be requested at RUNTIME via a system dialog the user explicitly approves or denies. |
| **signature** | Custom permissions between apps signed with the SAME certificate | Granted automatically ONLY if the requesting app is signed with the same key as the app that declared the permission -- used for trusted communication between an app family. |
| **signatureOrSystem** | Rare, OS/OEM-level | Granted to apps signed with the same key as the system image, or apps installed in the system partition. |

--> Declaring a `dangerous` permission in the manifest is necessary but NOT sufficient -- without ALSO requesting it at runtime and having the user approve it, the OS silently denies the protected operation (typically by throwing a `SecurityException` or returning empty/null results, depending on the API).

```xml
<uses-permission android:name="android.permission.INTERNET" />                 <!-- normal, no prompt -->
<uses-permission android:name="android.permission.CAMERA" />                   <!-- dangerous, runtime prompt required -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />     <!-- dangerous -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />       <!-- dangerous since API 33 -->
```

# Permission Groups

--> Dangerous permissions are organized into **groups** (e.g. `LOCATION`, `CAMERA`, `CONTACTS`, `CALENDAR`, `SMS`, `STORAGE`, `MICROPHONE`, `CALL_LOG`, `PHONE`, `SENSORS`, `NOTIFICATIONS`) -- historically, granting ANY permission in a group silently granted the whole group if the app had declared other permissions from that same group, but as of API 30+ each dangerous permission is prompted and tracked individually regardless of group, so treat each permission as needing its own explicit request.
--> **Special/"above dangerous" permissions** exist for particularly sensitive capabilities and use a DIFFERENT grant flow than the standard runtime dialog -- e.g. `SYSTEM_ALERT_WINDOW` (draw over other apps), `WRITE_SETTINGS`, and all-files access (`MANAGE_EXTERNAL_STORAGE`) require sending the user to a dedicated Settings screen via `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (or similar) rather than a simple dialog -- `ActivityCompat.requestPermissions()` cannot grant these.

# Requesting a Runtime Permission -- the Modern Way

--> Just like Activity results, permission requests used to go through `requestPermissions()`/`onRequestPermissionsResult()`, but the modern, type-safe approach is the same **Activity Result API** contract mechanism: `ActivityResultContracts.RequestPermission()` (single) or `RequestMultiplePermissions()` (batch).

```java
private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startCamera();
            } else {
                showRationaleOrDisabledState();
            }
        });

private void checkAndRequestCameraPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
        startCamera();
    } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
        // User denied once before (but not permanently) -- show an explanation UI,
        // THEN request again after they acknowledge it.
        showRationaleDialog(() -> requestPermissionLauncher.launch(Manifest.permission.CAMERA));
    } else {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }
}
```

--> Just like `registerForActivityResult()`, the permission launcher MUST be registered unconditionally before the component reaches `STARTED` (a field initializer or unconditional call in `onCreate()`), never inside a click handler.
--> **`shouldShowRequestPermissionRationale()`** returns `true` only after the user has denied the permission at least once WITHOUT selecting "Don't ask again" -- it returns `false` both on the very first request (nothing to explain yet) AND after a permanent denial, so you can't use it alone to distinguish those two states; combine it with your own tracking (e.g. a flag in `SharedPreferences`) if you need to detect "permanently denied" specifically to redirect the user to app Settings.

```java
private void openAppSettingsForPermission() {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.fromParts("package", getPackageName(), null));
    startActivity(intent);
}
```

# Requesting Multiple Permissions at Once

```java
private final ActivityResultLauncher<String[]> multiPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
            boolean cameraGranted = Boolean.TRUE.equals(results.get(Manifest.permission.CAMERA));
            boolean audioGranted = Boolean.TRUE.equals(results.get(Manifest.permission.RECORD_AUDIO));
            if (cameraGranted && audioGranted) {
                startVideoRecording();
            } else {
                Toast.makeText(this, "Camera and microphone are required", Toast.LENGTH_LONG).show();
            }
        });

multiPermissionLauncher.launch(new String[] {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
});
```

--> The callback receives a `Map<String, Boolean>` -- always check EACH permission individually rather than assuming all-or-nothing, since the user can grant some and deny others in the same batch dialog.

# Notable Version-Specific Permission Changes

--> **API 29 (Android 10)** introduced `ACCESS_BACKGROUND_LOCATION` as a SEPARATE permission from foreground location -- an app that wants location while backgrounded must request foreground location first, then separately request background location (often via a Settings redirect on newer versions, since Google increasingly restricts a direct runtime dialog for it).
--> **API 30 (Android 11)** introduced "one-time" permission grants (the "Only this time" option in the dialog) for location/camera/microphone, and auto-resets permissions for apps unused for several months.
--> **API 33 (Android 13)** made `POST_NOTIFICATIONS` a dangerous, runtime-requested permission for the FIRST time -- apps targeting API 33+ that used to post notifications freely now silently fail to show any notification until this is granted; also introduced granular media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`) replacing broad `READ_EXTERNAL_STORAGE` for media access.
--> **API 34 (Android 14)** introduced partial media access (`READ_MEDIA_VISUAL_USER_SELECTED`), letting users grant access to only a subset of photos/videos rather than the whole library.

# Custom Permissions -- Protecting Your Own Components

--> An app can define its OWN permission (typically `signature`-protected) to restrict which other apps may interact with its exported components -- useful for a suite of apps from the same publisher that need to talk to each other but not be open to arbitrary third parties.

```xml
<!-- Declaring app -->
<permission
    android:name="com.example.app.permission.ACCESS_PRIVATE_DATA"
    android:protectionLevel="signature" />

<provider
    android:name=".DataProvider"
    android:authorities="com.example.app.provider"
    android:exported="true"
    android:readPermission="com.example.app.permission.ACCESS_PRIVATE_DATA" />
```

```xml
<!-- Consuming app -->
<uses-permission android:name="com.example.app.permission.ACCESS_PRIVATE_DATA" />
```

# Best Practices for Permission UX

--> Request a permission in CONTEXT, right when the feature that needs it is used (e.g. request camera permission when the user taps "Take Photo"), never in bulk at app launch -- upfront blanket requests have measurably worse grant rates and feel invasive.
--> Always explain WHY a permission is needed BEFORE showing the system dialog, especially on a re-request (`shouldShowRequestPermissionRationale() == true`) -- a bare system dialog with no context is the single biggest driver of "Deny."
--> Design the app to DEGRADE GRACEFULLY when a permission is denied -- disable just the dependent feature with an explanatory UI state, rather than crashing or blocking the entire app; a `SecurityException` from calling a protected API without checking first is a common, entirely avoidable crash.
--> Never assume a previously granted permission is STILL granted -- the user can revoke any permission at any time from Settings (and the OS may auto-reset unused permissions), so always call `checkSelfPermission()` immediately before using a protected API, even if you successfully used it moments ago in the same session in rare edge cases (e.g. returning from Settings via a background/foreground transition).
--> Request the narrowest permission that satisfies the use case (e.g. `ACCESS_COARSE_LOCATION` instead of `ACCESS_FINE_LOCATION` if city-level accuracy suffices) -- Play Store review and users alike are more comfortable granting minimal-scope permissions.

# Common Gotchas

--> **Declaring a permission in the manifest but forgetting the runtime request** -- the single most common permission bug for anyone coming from the pre-API-23 install-time model; the manifest declaration alone does nothing for dangerous permissions on modern Android.
--> **Registering the permission launcher conditionally** -- exactly like `registerForActivityResult()`, this throws `IllegalStateException` if not registered unconditionally before `STARTED`.
--> **Forgetting `POST_NOTIFICATIONS` on API 33+** -- an app that always "just worked" for notifications on older devices silently stops showing any notification at all until this new runtime permission is both declared and granted.
--> **Treating `shouldShowRequestPermissionRationale() == false` as "never asked"** -- it's ALSO false after a permanent "Don't ask again" denial, so code that only shows rationale when this returns `true` will silently skip explaining anything to a permanently-denied user, who then just sees the request go nowhere (the OS auto-denies without even showing a dialog once permanently denied).
