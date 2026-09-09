# What Android Development With Java Actually Involves

--> Android apps are built as a collection of loosely-coupled COMPONENTS (Activities, Services, Broadcast Receivers, Content Providers) that the Android OS creates, destroys, and manages according to its own lifecycle rules -- unlike a desktop Java program where `main()` runs top to bottom under your control, an Android app has no single entry point YOU control; the OS decides when your code runs.
--> Java remains a first-class language on Android (alongside Kotlin, which Google now recommends for new projects) -- everything in these notes uses Java because it's the classic entry point for Java developers moving into Android, and the underlying APIs (Activity, Intent, View, etc.) are identical regardless of which language calls them.
--> Android apps run on the **Android Runtime (ART)**, which compiles your app's bytecode (compiled from Java source via `javac` then `d8`/`r8` into DEX -- Dalvik Executable -- format) ahead-of-time on the device, rather than the JVM used for standard desktop/server Java.

# Installing and Setting Up Android Studio

--> **Android Studio** is the official IDE for Android development, built on JetBrains' IntelliJ IDEA platform -- it bundles the Android SDK, an emulator manager, a layout editor, a profiler, and Gradle build tooling into one install.
--> **Key first-run setup steps:**

```text
1. Download Android Studio from developer.android.com/studio
2. Run the Setup Wizard -- installs the Android SDK, SDK Platform-Tools, and an emulator system image
3. Create/open a project -- "Empty Views Activity" is the classic starting template for Java
4. Configure an AVD (Android Virtual Device) via Device Manager -- a virtual phone to run/test your app
5. (Optional but faster) Enable a physical device via USB debugging -- Settings > About Phone > tap
   "Build Number" 7 times to unlock Developer Options, then enable "USB Debugging"
```

--> **SDK Platforms vs SDK Tools** -- a SDK Platform is a specific Android API level's set of libraries and system image (e.g. "Android 14, API 34") used to COMPILE and TEST against; SDK Tools are shared utilities (Platform-Tools like `adb`, Build-Tools like `aapt2`, emulator binaries) used regardless of which API level you target.
--> **`adb` (Android Debug Bridge)** -- the command-line bridge between your development machine and a connected device/emulator; used constantly for installing APKs, viewing logs (`adb logcat`), and shelling into the device (`adb shell`).

# Anatomy of an Android Studio Project

--> A new project is NOT one flat folder of `.java` files -- it's a structured, multi-module Gradle project. The default single-module layout looks like this:

```text
MyApplication/
├── app/                                  <- the actual application module
│   ├── build.gradle(.kts)                <- module-level build config (dependencies, SDK versions)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/myapp/   <- your Java source code, package-per-folder
│   │   │   ├── res/                      <- XML resources: layouts, strings, images, colors
│   │   │   │   ├── layout/                <- activity_main.xml etc. (UI layout files)
│   │   │   │   ├── values/                <- strings.xml, colors.xml, styles.xml, themes.xml
│   │   │   │   ├── drawable/               <- images, vector icons, shape XML
│   │   │   │   ├── mipmap/                 <- launcher icons at multiple densities
│   │   │   │   └── menu/, xml/, anim/, ...  <- other resource types as needed
│   │   │   └── AndroidManifest.xml         <- app-level declaration file (components, permissions)
│   │   ├── test/                          <- local JVM unit tests (no device needed)
│   │   └── androidTest/                    <- instrumented tests (run ON a device/emulator)
├── build.gradle(.kts)                     <- project (top-level) build config
├── settings.gradle(.kts)                  <- declares which modules belong to this project
├── gradle.properties                      <- global Gradle/AGP flags (JVM args, AndroidX flag, etc.)
└── gradle/wrapper/                        <- the Gradle Wrapper (pins a specific Gradle version)
```

--> **Why `res/` is split into typed subfolders** -- Android's resource system resolves the CORRECT variant of a resource at runtime based on device configuration (screen density, language, orientation, API level) purely from folder naming conventions, e.g. `layout-land/` for landscape, `values-fr/` for French strings, `drawable-hdpi/` vs `drawable-xhdpi/` for different pixel densities -- this is "resource qualification," and it means you almost never write conditional code to pick the right image or string; the OS does it via folder name matching.
--> **`R.java` (the generated resource class)** -- the build system auto-generates a class `R` containing an integer constant for every resource (`R.layout.activity_main`, `R.id.button_submit`, `R.string.app_name`) -- you never write `R.java` by hand, and referencing a resource in Java code always goes through this generated class.

# Gradle -- The Build System Behind Every Android App

--> **Gradle** is a general-purpose build automation tool; the **Android Gradle Plugin (AGP)** teaches Gradle how to build Android-specific outputs (APKs/AABs) -- "Gradle" and "the Android build system" are used almost interchangeably in Android dev conversation, but technically Gradle is the engine and AGP is the Android-specific plugin running on top of it.
--> **Two levels of build files:**

```groovy
// Top-level build.gradle (project-wide settings, rarely edited day-to-day)
plugins {
    id 'com.android.application' version '8.2.0' apply false
}

// app/build.gradle (module-level -- this is the one you touch constantly)
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.example.myapp'
    compileSdk 34                    // API level used to COMPILE the app

    defaultConfig {
        applicationId "com.example.myapp"   // unique app ID, used on the Play Store
        minSdk 24                            // oldest Android version the app installs on
        targetSdk 34                         // API level the app is designed/tested against
        versionCode 1                        // internal integer, must increase every release
        versionName "1.0"                    // human-readable version shown to users
    }

    buildTypes {
        release {
            minifyEnabled true                // enables R8 code shrinking/obfuscation
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    testImplementation 'junit:junit:4.13.2'
}
```

--> **`minSdk` vs `targetSdk` vs `compileSdk`** -- a common source of confusion for newcomers:

| Field | Meaning |
|---|---|
| `compileSdk` | The API level whose libraries/lint rules you compile against -- doesn't affect what devices can install the app, just what APIs your code can reference. |
| `minSdk` | The OLDEST Android version the app is allowed to install on -- the Play Store filters devices below this out. |
| `targetSdk` | The API level you've tested against and declare the app "behaves correctly" on -- affects which OS compatibility behaviors get applied at runtime (Android applies stricter behavior changes if `targetSdk` is high). |

--> **Gradle sync** -- whenever you change a `build.gradle` file, Android Studio must "sync" (re-resolve dependencies, regenerate project model) before it reflects in the IDE or a build -- this is one of the most common early points of confusion ("I added a dependency but it's not found yet") until the sync completes.
--> **The Gradle Wrapper (`gradlew` / `gradlew.bat`)** -- ensures every developer (and CI machine) builds with the exact same Gradle version regardless of what's globally installed, by downloading/using a pinned version recorded in `gradle/wrapper/gradle-wrapper.properties`.

# AndroidManifest.xml -- The App's Declaration to the OS

--> Every app has exactly one manifest at `app/src/main/AndroidManifest.xml` -- it's an XML file that tells the Android OS what components the app has, what permissions it needs, and how the OS should treat it, BEFORE any of your code runs.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.MyApplication">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".DetailActivity" android:exported="false" />

        <service android:name=".SyncService" />

        <receiver android:name=".BootReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

--> **Every Activity, Service, Broadcast Receiver, and Content Provider must be declared in the manifest** (with some dynamic-registration exceptions for receivers) -- an undeclared Activity throws an `ActivityNotFoundException` at runtime even if the Java class compiles fine, because the OS has no record it exists.
--> **`<uses-permission>`** -- declares permissions the app NEEDS -- for "dangerous" permission groups (camera, location, contacts, etc.) on API 23+, declaring it in the manifest is necessary but not sufficient; the app must also request it at RUNTIME and the user must explicitly grant it (covered further in later Android topics).
--> **The `MAIN`/`LAUNCHER` intent-filter** -- this is what makes an Activity show up as an app icon the user can tap to launch the app; exactly one Activity in a typical app declares this combination.
--> **`android:exported`** -- became a REQUIRED explicit attribute (no longer inferred) for components with intent filters starting at API 31 (Android 12), for security reasons -- `true` means other apps can launch this component, `false` restricts it to your own app (and the system).

# From Source Code to an Installable App: APK vs AAB

--> **APK (Android Package)** -- the traditional installable, self-contained package format -- a `.apk` file contains all compiled code, resources, and a manifest, and can be installed directly on a device (sideloading) or distributed through any channel.
--> **AAB (Android App Bundle)** -- the format Google Play now REQUIRES for new app submissions -- it is NOT directly installable; instead, Google Play uses it to generate and serve optimized, device-specific APKs (so a user only downloads the resources/native code for THEIR specific device configuration, e.g. only `arm64` native libraries and `xxhdpi` images, not every variant bundled together).

```text
Your Java + resources
        |
        v
   [Gradle + AGP build]
        |
        +---> APK  (debug: for testing/sideloading; release: signed, distributable directly)
        |
        +---> AAB  (upload to Play Console; Play generates split APKs per device at install time)
```

--> **Debug vs release builds** -- a debug build is automatically signed with a debug keystore (works only for local testing, Play Store rejects debug-signed uploads), is NOT minified by default, and includes debugging metadata; a release build must be signed with your own upload key/keystore, typically has `minifyEnabled true` (R8 shrinks unused code and obfuscates names), and is what actually ships.
--> **App signing** -- Android requires every APK to be cryptographically signed; Google Play additionally offers "Play App Signing" where Google manages the final signing key and you only need to protect an upload key -- losing your original signing key historically meant you could never update that app again under the same identity, which is why secure key backup is emphasized so heavily in official docs.

# Common Early Gotchas

--> **Emulator vs physical device performance** -- an emulator without hardware acceleration (HAXM/Hypervisor Framework) can be painfully slow; enabling virtualization in BIOS and using an x86_64 system image (not ARM, unless testing ARM-specific native code) dramatically speeds things up on Intel/AMD development machines.
--> **"Package name" vs "application ID"** -- the Java package name in your source (`com.example.myapp`) and the `applicationId` in `build.gradle` are DECOUPLED in modern Android -- you can change `applicationId` (used for Play Store identity) without renaming your actual Java packages, which is useful for creating `.debug`-suffixed build variants that install alongside the release version.
--> **Resource name collisions/typos** -- `R.java` (or the `R` class reference) fails to resolve if any XML resource has an error anywhere in the module -- a single broken `strings.xml` entry can make the ENTIRE `R` class unavailable, breaking compilation across unrelated files, which is a frequent source of confusing "cannot find symbol R" errors.
--> **Clean/Rebuild as a troubleshooting step** -- "Build > Clean Project" then "Rebuild Project" resolves a surprising number of stale-cache issues (leftover generated code referencing resources/classes that no longer exist) -- a very common first troubleshooting step in the Android community before deeper debugging.

# Best Practices

--> Keep `minSdk` as low as your target audience genuinely requires, but not lower -- every version you support back to is API-level behavior you must test against; check current Android version distribution data periodically rather than guessing.
--> Never commit your release keystore or its passwords to version control -- store them outside the repo (or use a secrets manager / CI secret store), since anyone with the keystore can publish updates impersonating your app identity.
--> Use `local.properties` (already git-ignored by the default `.gitignore`) for machine-specific paths (like the Android SDK location) -- never hardcode absolute SDK paths in a committed `build.gradle`.
--> Prefer AndroidX libraries (`androidx.*`) over the old deprecated `android.support.*` libraries in any new project -- AndroidX is the actively maintained successor and is what Android Studio scaffolds by default today.
