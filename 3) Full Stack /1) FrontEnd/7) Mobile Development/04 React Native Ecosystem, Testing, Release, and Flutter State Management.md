# Scope of This File

--> The React Native and Flutter Fundamentals file covers the core mental model of each framework. This file covers the surrounding practical ecosystem needed to ship a real app -- React Native's essential libraries, how mobile apps actually get tested, what releasing to the app stores involves, common performance/offline patterns, and Flutter's state management options.

# React Navigation -- Routing for React Native

--> Mobile apps have no URL bar, so "routing" means something different than React Router (covered in the React Router file) -- **React Navigation** is the de facto standard library, managing a stack of screens and the platform-appropriate transition animations/gestures between them.

```jsx
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";

const Stack = createNativeStackNavigator();

function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="Profile" component={ProfileScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

function HomeScreen({ navigation }) {
  return (
    <Button
      title="Go to Profile"
      onPress={() => navigation.navigate("Profile", { userId: 42 })}   // Params passed like a route param, covered in React Router file
    />
  );
}
```

--> `createNativeStackNavigator` renders actual native navigation primitives under the hood (`UINavigationController` on iOS, `Fragment`-based navigation on Android) -- giving platform-correct push/pop animations and swipe-back gestures for free, rather than reimplementing them in JS, the same "render to real native views" principle the React Native and Flutter Fundamentals file describes for basic components.
--> Beyond the stack navigator, React Navigation also provides tab navigators (`createBottomTabNavigator`) and drawer navigators (`createDrawerNavigator`) -- the three navigation shapes covering the vast majority of real mobile app structures, and they nest inside each other (a tab navigator where one tab is itself a stack navigator, a very common real app shape).

# Reanimated and Gesture Handler

--> React Native's default `Animated` API runs animation logic on the JavaScript thread, which can visibly stutter if that thread is busy (e.g. handling a network response or a big list re-render) at the exact moment an animation is playing.
--> **Reanimated** solves this by letting animation "worklets" run on the native UI thread directly, independent of whatever the JS thread is doing -- animations stay smooth even under JS thread load, which matters especially for gesture-driven UI (a swipeable card, a bottom sheet drag) where any dropped frame is immediately visible to the user.

```jsx
import Animated, { useSharedValue, useAnimatedStyle, withSpring } from "react-native-reanimated";
import { Gesture, GestureDetector } from "react-native-gesture-handler";

function DraggableCard() {
  const offsetX = useSharedValue(0);            // Lives on the native UI thread, not a normal React state value

  const panGesture = Gesture.Pan()
    .onUpdate((e) => { offsetX.value = e.translationX; })
    .onEnd(() => { offsetX.value = withSpring(0); });   // Springs back to 0 on release

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offsetX.value }],
  }));

  return (
    <GestureDetector gesture={panGesture}>
      <Animated.View style={[styles.card, animatedStyle]} />
    </GestureDetector>
  );
}
```

--> **Gesture Handler** is Reanimated's usual pairing -- it recognizes native touch gestures (pan, pinch, long-press, swipe) using the platform's own native gesture-recognition system rather than React Native's plain `onTouchStart`/`onTouchMove` events, which are both less precise and run on the JS thread.
--> The combination (Gesture Handler recognizing the gesture natively, Reanimated animating the response natively) is why libraries built on top of them (bottom sheets, swipeable list rows, drag-to-reorder lists) feel indistinguishable from a fully native app's equivalent interaction, despite being written in JS/React.

# Platform.OS and Platform-Specific Code

--> Despite React Native's "write once" pitch (covered in the React Native and Flutter Fundamentals file), some UI/behavior genuinely needs to differ by platform -- iOS and Android have different design conventions (e.g. where a back button lives), different APIs, and occasionally different bugs to work around.

```jsx
import { Platform, StyleSheet } from "react-native";

const styles = StyleSheet.create({
  header: {
    paddingTop: Platform.OS === "ios" ? 44 : 24,       // Different safe-area/status-bar heights
    ...Platform.select({
      ios: { shadowColor: "#000", shadowOpacity: 0.1 }, // iOS uses shadow* properties
      android: { elevation: 4 },                        // Android uses elevation for the same visual effect
    }),
  },
});

if (Platform.OS === "android") {
  // Android-only logic, e.g. a permission request flow that differs from iOS's
}
```

--> `Platform.select()` is the cleaner alternative to scattering `Platform.OS === "ios"` ternaries everywhere -- it picks the right value/style object per platform in one place, and also supports platform-specific FILE extensions (`Button.ios.js` / `Button.android.js`, both imported as just `./Button`) for cases where the divergence is large enough to warrant an entirely separate component implementation rather than inline branching.

# Mobile Testing Tools

--> Unit/component-level testing of the JS logic itself reuses the same tools covered in the Testing React Applications file (Jest, React Testing Library's React Native-flavored counterpart `@testing-library/react-native`) -- what's genuinely NEW for mobile is END-TO-END testing that drives the actual compiled app on a real device/simulator, since there's no browser/DOM to run RTL-style tests directly against.

--> **Detox** -- a gray-box E2E testing framework built specifically for React Native, distinctive for automatically SYNCHRONIZING with the app (waiting for animations, network requests, and timers to settle) before each test action, which avoids the flaky arbitrary `sleep()`-based waits that plague many E2E test suites.
```javascript
describe("Login flow", () => {
  it("logs in with valid credentials", async () => {
    await element(by.id("email-input")).typeText("alice@example.com");
    await element(by.id("password-input")).typeText("hunter2");
    await element(by.id("login-button")).tap();
    await expect(element(by.text("Welcome, Alice"))).toBeVisible();
  });
});
```
--> **Appium** -- a broader, platform-agnostic mobile automation framework (also usable for native iOS/Android apps built without React Native, covered in the Native iOS and Android Development file, and even mobile web) built on the WebDriver protocol -- more setup overhead than Detox, but not tied to React Native specifically, useful when a team needs one E2E tool across mixed native/cross-platform apps.
--> **Maestro** -- a newer, notably simpler E2E tool using a plain YAML flow syntax instead of a full JS test file, aimed at drastically lowering the setup/maintenance cost of writing mobile E2E tests.
```yaml
appId: com.example.myapp
---
- tapOn: "Email"
- inputText: "alice@example.com"
- tapOn: "Password"
- inputText: "hunter2"
- tapOn: "Log In"
- assertVisible: "Welcome, Alice"
```
--> Practical choice -- Detox for React Native-specific projects wanting tight integration and synchronization; Appium for cross-platform/native mixed environments or existing WebDriver expertise; Maestro when minimizing test-writing/maintenance overhead matters more than fine-grained control.

# App Store Release Process

--> Shipping a mobile app update is meaningfully different from deploying a web app (covered throughout the Full Stack track's deployment-related files) -- most changes go through a STORE REVIEW process and a rollout delay, rather than being live the moment you deploy.

--> **Code signing** -- both platforms require every app binary to be cryptographically signed before it can be installed/distributed. iOS uses a certificate + provisioning profile pair (managed through an Apple Developer account, often via Xcode or a CI service); Android uses a keystore file containing a private key that signs the APK/AAB -- LOSING an Android signing key is often unrecoverable, since Google Play requires updates to be signed with the same key as the original upload, making secure backup of the keystore a genuinely critical operational concern.
--> **Store review** -- Apple's App Store review (typically hours to a couple of days, done by a human reviewer checking against App Store guidelines) is generally slower and stricter than Google Play's review (more automated, generally faster) -- both can reject a submission for policy violations, requiring a fix and resubmission, which is why release timelines need buffer that a web deploy doesn't.
--> **CodePush / EAS Update -- Over-The-Air (OTA) updates** -- for JS-only changes (no native code/dependency changes), both Microsoft's CodePush (older, now largely superseded for Expo-based projects) and Expo's **EAS Update** let you push a new JS bundle directly to already-installed apps, WITHOUT going through app store review at all.
```bash
eas update --branch production --message "Fix checkout bug"
```
--> This works because the JS bundle is downloaded and swapped in by the app itself at launch (or on a background check) -- but it only covers the JS/asset layer; any change touching native code, a new native dependency, or permissions still requires a full store submission and review, since OTA updates cannot alter the compiled native binary itself.
--> This OTA capability is a genuine practical advantage React Native (and Expo specifically) has over fully native development (covered in the Native iOS and Android Development file) -- a critical bug fix can reach users in minutes rather than waiting on app review turnaround.

# Mobile Performance and Offline Patterns

--> **List virtualization** -- rendering a long scrollable list (a social feed, a chat history) by only mounting the currently-visible rows plus a small buffer, directly the same core idea as `react-window` covered in the Advanced React Patterns file, applied to mobile's own list components.
```jsx
import { FlatList } from "react-native";

<FlatList
  data={messages}
  keyExtractor={(item) => item.id}
  renderItem={({ item }) => <MessageBubble message={item} />}
  windowSize={5}                 // How many "screens" worth of content to keep rendered around the viewport
/>
```
--> `FlatList` (and the newer, more performant `FlashList` from Shopify, a drop-in-ish replacement optimizing for cases where item sizes vary) handle this virtualization automatically -- rendering all items unvirtualized (`.map()` inside a plain `<ScrollView>`) is a common, easy-to-make mobile performance mistake for any list beyond a couple dozen items.
--> **Image caching** -- unlike a browser (which has its own HTTP cache handling repeat image requests, covered in the Web Performance file), React Native's default `<Image>` component has more limited disk caching behavior. Libraries like `react-native-fast-image` (or Expo's built-in `expo-image`) provide aggressive disk+memory caching, priority loading, and smoother placeholder/fade-in behavior specifically tuned for mobile scrolling performance.
--> **Network resilience** -- mobile connectivity is far less reliable than a typical desktop web connection (subway dead zones, weak signal, airplane mode) -- production apps commonly pair a data-fetching layer (React Query, covered in the Data Fetching with React Query and SWR file, has a React Native-compatible core) with `@react-native-community/netinfo` to detect connectivity changes, pausing/resuming queries and showing an explicit "you're offline" state rather than silently failing requests.
```jsx
import NetInfo from "@react-native-community/netinfo";

NetInfo.addEventListener((state) => {
  if (!state.isConnected) {
    showOfflineBanner();
  }
});
```
--> Persisting fetched data locally (via `AsyncStorage`, or a proper embedded database like WatermelonDB/SQLite for larger datasets) so the app has SOMETHING to show immediately on a cold, offline launch is the mobile equivalent of the offline-first caching strategies covered in the Progressive Web Apps In Depth file's Service Workers section -- same underlying goal (don't show a blank screen just because the network is unavailable right now), different mechanism since there's no service worker/fetch-interception layer on native mobile.

# Flutter State Management Options

--> Flutter's basic `setState()` (covered in the React Native and Flutter Fundamentals file) works for simple, single-widget local state, but -- exactly like plain `useState` prop-drilling in React before reaching for Context/Redux/Zustand (covered in the Context API and Redux and Zustand files) -- it doesn't scale cleanly to state shared across many widgets. Flutter's ecosystem has several established answers.

--> **Provider** -- the simplest, most beginner-friendly option, built on top of Flutter's own `InheritedWidget` mechanism -- conceptually close to React Context (covered in the Context API file), making a value available to any descendant widget without manually threading it through every constructor.
```dart
class CartModel extends ChangeNotifier {
  final List<Item> items = [];
  void add(Item item) {
    items.add(item);
    notifyListeners();          // Tells any listening widget to rebuild -- parallel to a Context value changing
  }
}

// Usage
ChangeNotifierProvider(
  create: (context) => CartModel(),
  child: MyApp(),
)

// Reading it further down the tree
final cart = context.watch<CartModel>();   // Rebuilds this widget when notifyListeners() fires
```
--> **Bloc** (Business Logic Component) -- a more structured, EVENT-driven pattern: widgets dispatch Events, a Bloc processes them and emits new States, and the UI rebuilds in response to State changes -- conceptually close to Redux's action/reducer/store cycle (covered in the Redux and Zustand file), and to XState's explicit state-machine model (covered in the React Theory folder's file 21), enforcing a clear, traceable, one-way data flow at the cost of noticeably more boilerplate than Provider.
```dart
// Simplified shape -- an event goes in, a new state comes out
class CounterBloc extends Bloc<CounterEvent, int> {
  CounterBloc() : super(0) {
    on<IncrementPressed>((event, emit) => emit(state + 1));
  }
}
```
--> **Riverpod** -- built by the same author as Provider, as a redesigned successor addressing Provider's limitations (specifically, Provider requires a `BuildContext` to read state, which complicates reading state outside the widget tree, e.g. inside a plain Dart function) -- state is exposed as compile-time-safe, context-independent `Provider` objects, and it's structurally close to Jotai's atom-based model (covered in the React Theory folder's file 21) -- small, independent, composable providers rather than one large store.
```dart
final counterProvider = StateProvider<int>((ref) => 0);

class CounterWidget extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final count = ref.watch(counterProvider);   // Rebuilds only when this specific provider's value changes
    return ElevatedButton(
      onPressed: () => ref.read(counterProvider.notifier).state++,
      child: Text("Count: $count"),
    );
  }
}
```
--> **Choosing among them** -- Provider for small-to-medium apps wanting the least ceremony; Bloc for large teams wanting an enforced, highly testable, explicit event/state contract (especially valued in enterprise Flutter codebases, echoing exactly why Angular/NgRx, covered in the Other Frontend Frameworks folder, gets chosen for similar reasons on the web); Riverpod as the modern, compile-time-safer general-purpose default many new Flutter projects reach for currently -- the same underlying "how much structure vs how much boilerplate" trade-off spectrum the Redux vs Zustand comparison (covered in the Redux and Zustand file) lays out for React, just re-expressed in Flutter/Dart's own set of libraries.
