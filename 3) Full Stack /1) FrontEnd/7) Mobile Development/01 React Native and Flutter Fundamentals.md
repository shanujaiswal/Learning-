# Native, Web, and Cross-Platform -- The Three Approaches to Mobile

--> **Fully native development** -- writing separate codebases in Swift/Objective-C for iOS and Kotlin/Java for Android -- the best possible performance and access to every platform-specific feature, at the cost of maintaining TWO entirely separate codebases for what's conceptually one product.
--> **Mobile web / PWAs** -- a website (built with the exact HTML/CSS/JS/React skills covered throughout the Full Stack track) that runs inside a mobile browser, optionally "installed" to a home screen as a Progressive Web App -- the cheapest to build and maintain (one codebase, reusing existing web skills entirely), but limited access to native device APIs and generally weaker performance for graphics-intensive apps.
--> **Cross-platform frameworks** -- React Native and Flutter, covered in this file -- write ONE codebase that compiles/renders to genuinely native iOS and Android apps, aiming for a middle ground: closer to native performance/feel than a web app, while sharing far more code than fully separate native codebases require.

# React Native -- Applying React's Model to Native Views

--> React Native reuses React's core concepts covered extensively in the React folder -- components, props, state, hooks -- but instead of rendering to actual DOM elements (`<div>`, `<button>`) in a browser, it renders to REAL, NATIVE mobile UI components (an actual native iOS `UIView` or Android `View`) behind the scenes.

```jsx
import { View, Text, Button, StyleSheet } from "react-native";
import { useState } from "react";

function Counter() {
  const [count, setCount] = useState(0);

  return (
    <View style={styles.container}>
      <Text style={styles.text}>Count: {count}</Text>
      <Button title="Increment" onPress={() => setCount(count + 1)} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: "center", alignItems: "center" },
  text: { fontSize: 24, marginBottom: 10 }
});
```

--> Notice the direct parallels to web React, covered in the React Fundamentals and Hooks files -- `useState` works identically; the component/props/composition model is unchanged. What DIFFERS is the building-block elements themselves -- `<View>` instead of `<div>`, `<Text>` instead of a plain text node (React Native requires ALL text to be wrapped in a `<Text>` component, unlike HTML), and `StyleSheet.create` instead of CSS files, since there's no actual CSS/browser rendering engine involved at all.

## How React Native Actually Renders Native Views

--> React Native's JavaScript code runs in a JavaScript engine embedded in the app, and communicates with the native side via a "bridge" (in the older architecture) or more directly via JSI (JavaScript Interface, in the newer architecture) -- your React component tree is translated into instructions telling the native iOS/Android layer which actual native views to create and update, meaning the FINAL rendered UI is genuinely native, not a web view pretending to be an app (an older, now largely abandoned approach sometimes called "hybrid" apps, using tools like Cordova/PhoneGap, which wrapped an actual mobile web page in a native shell).

## Accessing Native Device Features

```jsx
import * as Location from "expo-location";
import { Camera } from "expo-camera";

async function getUserLocation() {
  const { status } = await Location.requestForegroundPermissionsAsync();
  if (status === "granted") {
    const location = await Location.getCurrentPositionAsync({});
    return location.coords;
  }
}
```

--> Accessing camera, GPS, push notifications, and other device-specific capabilities requires native modules -- either provided by React Native's own core, by Expo (a popular toolchain that bundles many common native capabilities behind a simplified JavaScript API, dramatically easing the getting-started experience), or by writing custom native code (Swift/Kotlin) when a specific capability isn't already available through an existing library -- the one area where "write once" genuinely has practical limits, and platform-specific native code knowledge sometimes still becomes necessary.

# Flutter -- Google's Approach: A Custom Rendering Engine

--> Flutter takes a philosophically different approach from React Native -- rather than rendering to NATIVE platform UI components, Flutter draws EVERY pixel of its UI itself, using its own high-performance rendering engine (Skia, now transitioning to Impeller) -- meaning a Flutter app looks and behaves IDENTICALLY on iOS and Android, since it isn't relying on either platform's native widget rendering at all.

```dart
import 'package:flutter/material.dart';

class CounterWidget extends StatefulWidget {
  @override
  _CounterWidgetState createState() => _CounterWidgetState();
}

class _CounterWidgetState extends State<CounterWidget> {
  int count = 0;

  void increment() {
    setState(() {
      count++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text('Count: $count', style: TextStyle(fontSize: 24)),
        ElevatedButton(onPressed: increment, child: Text('Increment')),
      ],
    );
  }
}
```

--> Flutter uses Dart, a language most developers coming from JavaScript/TypeScript will find syntactically approachable, but which is nonetheless a genuinely separate language to learn, unlike React Native's direct reuse of existing JavaScript/React skill.
--> `setState()` here plays the same conceptual role as React's `useState` setter -- notifying the framework that state changed and a re-render (rebuild, in Flutter's terminology) is needed -- the underlying mental model of "state changes trigger a UI rebuild" is shared across React Native, Flutter, and web React alike, exactly the same "converging problem, different syntax" observation made about Vue/Angular/Svelte in the previous file.

## Everything Is a Widget

--> Flutter's foundational philosophy -- literally everything in the UI, down to padding and alignment, is a "Widget," composed together in a tree -- there's no separate concept of "layout" (CSS) versus "component" (React) the way web development separates them; layout itself is expressed as nested Widgets (`Padding`, `Center`, `Column`, `Row`).

```dart
Padding(
  padding: EdgeInsets.all(16.0),
  child: Center(
    child: Text('Hello, Flutter!'),
  ),
)
```

# React Native vs Flutter -- Practical Trade-offs

--> **Language and team fit** -- React Native lets an existing JavaScript/React team (skills covered throughout this entire Full Stack track) apply their existing knowledge almost directly to mobile; Flutter requires learning Dart from scratch, a genuinely separate investment, though Dart's syntax is approachable for developers already comfortable with TypeScript-style typed languages.
--> **Look and feel consistency** -- Flutter's custom-rendering approach means pixel-perfect consistency across iOS/Android, which can be an advantage for strict brand consistency, or a disadvantage when users actually EXPECT an app to look/feel native to their specific platform's own conventions; React Native, rendering to genuinely native components, tends to automatically feel more "at home" on each platform by default.
--> **Performance** -- both are generally fast enough for the vast majority of real applications; Flutter's compiled Dart code and custom rendering engine can have an edge for highly animation-heavy, graphics-intensive UIs specifically, while React Native's JavaScript-bridge overhead (in the older architecture) was historically a more common performance bottleneck, though the newer JSI-based architecture has substantially narrowed this gap.
--> **Ecosystem** -- React Native benefits from overlapping (though not identical) libraries and patterns with the broader, larger React/JavaScript ecosystem covered throughout this Full Stack track; Flutter has its own separate, rapidly-growing but comparatively younger package ecosystem (pub.dev).

# Why Cross-Platform Frameworks Matter for a Full-Stack Skillset

--> Given the shared component/state/props mental model covered extensively in the React folder, a developer who deeply understands web React is genuinely close to productive in React Native specifically -- making it a natural extension of the Full Stack skillset covered throughout this track, rather than an entirely separate discipline requiring a fresh start the way native iOS/Android development would.
