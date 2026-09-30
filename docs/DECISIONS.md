# Decisions

Each one records What, Why and the Trade-off.

## D-001: Its own repository
**What.** Swivel lives in its own repository, not inside Formicarium or NetLane.
**Why.** The owner chose a new repository. An Android app does not belong in a Unity project.
**Trade-off.** Until the repository exists, the code travels as a zip.

## D-002: Named Swivel, with its own package name
**What.** The app is called "Swivel" and its package is `io.github.xxlinnix.swivel`. It does not use "MOGA Pivot" or `com.bda.pivot.mogapgp`.
**Why.** Both names are PowerA's. Reusing them would impersonate PowerA's app, and would clash with the original if it is installed.
**Trade-off.** MOGA-enhanced games built with a later SDK may bind to Pivot's package by name, which would keep them from finding a bridge (RESEARCH.md). Revisit only in M3, and only for a private build.

## D-003: Current toolchain, built-in Kotlin
**What.** AGP 9.2.1, Gradle 9.6.0, Kotlin 2.4.20, Compose BOM 2026.09.00, compileSdk and targetSdk 37 (Android 17), minSdk 26, JDK 17 bytecode. AGP 9's built-in Kotlin is used, so the `kotlin-android` plugin is not applied.
**Why.** The brief asks for the latest target. These were the current stable versions on 2026-09-30, except AGP: 9.4.0 is out, but the owner's Android Studio supports AGP 9.2.1 at most, and 9.2.1 already supports API 37.
**Trade-off.** Two AGP minor versions behind. Moving to 9.4 later is a one-line change in `gradle/libs.versions.toml`, once Studio is updated.

## D-004: Dependencies
**What.** Only AndroidX, Compose, Kotlin and kotlinx-coroutines, plus JUnit 4 for unit tests (approved by the owner; test-only, never in the APK). Navigation uses string routes.
**Why.** This is the owner's rule. Type-safe navigation routes would need kotlinx-serialization, which is another dependency to ask about.
**Trade-off.** Route strings are checked at runtime, not compile time. With three routes this is fine.

## D-005: Dependency injection by hand
**What.** One `AppContainer` built in `SwivelApp.onCreate`, and view-model factories that read it.
**Why.** The object graph has five objects. Hilt or Koin would each be a dependency to ask about.
**Trade-off.** Adding a service means editing the container by hand.

## D-006: `core` is plain Kotlin, and a test enforces it
**What.** Models, detection, the wizard's decision rules, the HID mapping (and, from M2, the Mode A protocol) live in `core` with no Android imports. `CorePurityTest` fails otherwise. Android key and axis codes are copied as numbers, and `AndroidInputCodesTest` pins them to the framework constants.
**Why.** The brief asks for a protocol layer testable without hardware. These rules are where the bugs would be.
**Trade-off.** Sixteen key codes and twelve axis ids are duplicated, guarded by a test.

## D-007: How the controller is found
**What.** The CompanionDeviceManager chooser comes first. An in-app scan is offered only on Android 12 and later. Android's Bluetooth settings is always offered. The manifest declares no location permission.
**Why.** The companion chooser needs no scan or location permission on any version, and M2 needs the association to restart its service from the background. On Android 8 to 11, an in-app scan would require the location permission, which the chooser avoids.
**Trade-off.** On Android 11 and older the chooser needs Location switched on in quick settings, and the wizard says so. Devices without the companion feature fall back to Settings.

## D-008: Mode detection trusts evidence in a fixed order
**What.** A gamepad input device beats a fresh SDP service list, which beats the Bluetooth name. The cached service list and the name only count after a 20-second wait.
**Why.** Android caches the name and services from pairing time, which may describe the other side of the switch.
**Trade-off.** Detection from a name alone takes 20 seconds. A controller that is asleep and unnamed ends as "Not confirmed" rather than a guess.

## D-009: The test screen follows a controller by its descriptor
**What.** The route carries the `InputDevice` descriptor, not the device id.
**Why.** The id changes every time the controller sleeps and reconnects. The descriptor does not.
**Trade-off.** Two identical controllers could share a descriptor only in odd cases, and the test screen would then pick the first.

## D-010: The test screen swallows controller input
**What.** While the test screen is open, `MainActivity` consumes every gamepad key and motion event after recording it.
**Why.** Otherwise B triggers Back and the D-pad moves focus, so you cannot test them.
**Trade-off.** You leave the screen with the on-screen Back or the system gesture, not with the controller. The screen says so.

## D-011: A controller connecting does not recreate the activity
**What.** `configChanges="keyboard|keyboardHidden|navigation"` on `MainActivity`.
**Why.** A gamepad connecting or disconnecting changes those configurations. Recreating the activity mid-test would flash the screen every time the controller sleeps.
**Trade-off.** None in practice: Compose re-reads the configuration anyway.

## D-012: Text is written inline, in English
**What.** Screen text lives in the `…Content` composables, not in `strings.xml`.
**Why.** There is one user and one language, and this is faster to change during hardware testing.
**Trade-off.** Translating later means moving the strings into resources first.

## D-013: No foreground service in M1
**What.** Mode B needs no service. Android's HID host owns the link.
**Why.** A service with nothing to do would be a notification with no purpose.
**Trade-off.** None. The service arrives with Mode A in M2.

## D-014: Release builds are signed with the debug key
**What.** `release` has minify on and uses the debug signing config.
**Why.** So an optimised build can be sideloaded for testing without setting up a keystore.
**Trade-off.** Never share such an APK. Add a real keystore before anyone else installs it.

## D-015: Goal 5 (per-game Mode B remapping) is not feasible as written
**What.** Proposed, awaiting the owner. Android gives an ordinary app no way to change the input another app receives from a HID gamepad. There is no input injection without root. An accessibility service can intercept key events but cannot send gamepad events, never sees stick motion, and is heavily restricted by Play policy.
**Why.** This is how Android's input security works, not a gap in Swivel.
**Trade-off.** The alternatives: (a) remap only for Mode A games reached through the M3 bridge, where Swivel produces the events itself; (b) buttons to screen taps through an accessibility service (key events only, no sticks); (c) drop goal 5. My recommendation is (a) if M3 is a go, otherwise (c).

## D-016: The owner's controller only speaks Mode A, so Mode A comes next
**What.** Proposed, awaiting the owner. The owner's controller is part CPFA000253-01, the original 2012 "MOGA Mobile Gaming System", later sold as the MOGA Pocket. As far as I know it has no A/B switch and no HID mode, and advertises a `BD&A` name. If the owner confirms that, milestone 2 (Mode A) becomes the first milestone the owner can use. Milestone 1's hardware test shrinks to pairing it in Mode A and checking that Swivel recognises it as Mode A.
**Why.** Mode B screens cannot be tested with a controller that has no Mode B. The Pocket also lacks a D-pad, L2/R2 and L3/R3, and uses the first-generation 12-byte reports (commands 65 and 68).
**Trade-off.** The Mode B path stays unproven on real hardware until someone tries it with a MOGA Pro-family controller or any other Bluetooth gamepad. Any gamepad will do for the test screen.
