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
**What.** Only AndroidX, Compose, Kotlin and kotlinx-coroutines, plus two the owner approved: JUnit 4 for unit tests (never in the APK), and the Shizuku API 13.1.5 (MIT) for the virtual gamepad (D-024). Navigation uses string routes.
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
**What.** Confirmed by the owner: no A/B switch, pairs as `BD&A`. The owner's controller is part CPFA000253-01, the original 2012 "MOGA Mobile Gaming System", later sold as the MOGA Pocket. As far as I know it has no A/B switch and no HID mode, and advertises a `BD&A` name. If the owner confirms that, milestone 2 (Mode A) becomes the first milestone the owner can use. Milestone 1's hardware test shrinks to pairing it in Mode A and checking that Swivel recognises it as Mode A.
**Why.** Mode B screens cannot be tested with a controller that has no Mode B. The Pocket also lacks a D-pad, L2/R2 and L3/R3, and uses the first-generation 12-byte reports (commands 65 and 68).
**Trade-off.** The Mode B path stays unproven on real hardware until someone tries it with a MOGA Pro-family controller or any other Bluetooth gamepad. Any gamepad will do for the test screen.

## D-017: Bluetooth broadcasts are received with no export flag, and bonding is also polled
**What.** `BluetoothGateway.events()` registers its receiver with plain `registerReceiver(receiver, filter)`, with no `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`. While the wizard is bonding, it also checks the bond state every second, for up to 60 seconds.
**Why.** In the first hardware test the wizard spun forever after the PIN. The receiver was registered `RECEIVER_NOT_EXPORTED`, and Bluetooth broadcasts come from the Bluetooth process rather than the system server, so they never arrived. Android asks apps to register receivers of system broadcasts only with no flag (developer.android.com/about/versions/14/behavior-changes-14). Polling means a lost broadcast can only slow the wizard down, never hang it.
**Trade-off.** None for security: all seven actions are protected broadcasts that only the system can send. The poll costs one bond-state read a second, and only while bonding.

## D-018: How the Mode A link survives sleep, Doze and app switching
**What.** The link runs in a `connectedDevice` foreground service that starts only from a visible screen. It retries after a drop with growing delays (2, 4, 8, 16, then 30 s) for 5 minutes, then gives up and stops the service. It retries at once when the controller reconnects at the Bluetooth level. The service is `START_NOT_STICKY`, and there is no CompanionDeviceManager presence wake-up yet.
**Why.** The service keeps the process alive and is exempt from App Standby, and Doze does not suspend Bluetooth sockets. The phone cannot wake a sleeping controller, so paging it forever only drains both batteries. Five minutes covers "I put it down to answer a message".
**Trade-off.** After 5 minutes asleep the user taps Connect again. The next step, if hardware testing shows the Pocket reconnects to the phone by itself when switched on, is a CompanionDeviceManager presence observer (`ObservingDevicePresenceRequest` on Android 16+) to restart the link with the app closed.

## D-019: Mode A stick Y is flipped
**What.** The decoder negates both stick Y bytes, so up reads -1 as on Android. Confirmed on the owner's MOGA Pocket.
**Why.** moga-uinput flips Y for Linux, which shares Android's convention. It is the only source.
**Trade-off.** If the Pocket turns out to report up as negative already, this is a one-line change in `ModeADecoder` plus its test. The test screen shows the raw report so the owner can settle it.

## D-020: The report format is guessed from the name, then checked
**What.** A `BD&A` name starts with the first-generation commands (65/68), anything else with the second (69/70). If nothing answers within 2.5 s, the link tries the other format. If nothing answers either way within 6 s, twice in a row, it stops with "never answered in Mode A".
**Why.** The name is right for every controller the research covers, and the fallback covers renamed or unknown models.
**Trade-off.** A wrong guess costs 2.5 s on the first connect.

## D-021: Notifications are asked for at the first Mode A connect
**What.** On Android 13+, tapping Connect first asks for the notification permission, then connects whatever the answer.
**Why.** The foreground service runs without it, but its notification (with the Disconnect button) would be hidden. Asking at the moment it matters makes the reason obvious.
**Trade-off.** One more prompt on first connect.

## D-022: The MOGA SDK bridge is an exported service in Swivel's own package
**What.** `MogaSdkService` is exported, with the intent filter `com.bda.controller.IControllerService`, and answers the SDK's Binder calls by hand. It relays only while the Mode A link is up, and has no permission guard.
**Why.** Games using the SDK's implicit intent can bind to any package that declares the action, so no PowerA name is needed for them. A permission guard would lock out the very games it exists for, since they cannot request a Swivel permission.
**Trade-off.** Any installed app can bind and read controller input while the Mode A link is connected. That is what Pivot allowed, and it is what any app sees from a Mode B gamepad. The service exposes nothing else.

## D-023: No build under PowerA's package name without the owner's say-so
**What.** Proposed, awaiting the owner. Games patched for Lollipop call `setPackage("com.bda.pivot.mogapgp")` and can only reach an app with that application id. A second build flavour could carry that id, for private sideloading only and never for publishing.
**Why.** It is the only way those games can work. It would also impersonate PowerA's app, clash with an installed Pivot, and could never go on any store. So the owner decides, per D-002.
**Trade-off.** Without it, only unpatched old games reach the bridge. With it, there are two Swivel builds to keep straight.

## D-024: The Pocket becomes a system gamepad through Shizuku, replacing the old plan
**What.** Approved by the owner. The next milestone makes the Mode A controller appear to Android as a standard gamepad, so Play Store games can use it. The installed-games list moves to M5, and remapping to M6. Swivel depends on the Shizuku API (`dev.rikka.shizuku:api` and `provider` 13.1.5, MIT).
**Why.** The owner has no MOGA-SDK game, and the Pocket has no Mode B. Android lets no ordinary app create or fake an input device. Shizuku, started by the user through Wireless debugging, runs approved code as the shell user, which may open `/dev/uhid`, as scrcpy's `--gamepad=uhid` does on unrooted phones. The alternatives were an accessibility service (touch-mapping only, no analog) or root.
**Trade-off.** The user installs Shizuku and restarts it through Wireless debugging after every reboot. It also revises D-015: remapping becomes possible for the Pocket, because Swivel writes the reports itself.

## D-025: The virtual gamepad presents as an Xbox 360 controller
**What.** The virtual gamepad uses vendor 0x045E, product 0x028E (the Xbox 360 controller) and scrcpy's descriptor: sticks on X/Y and Rx/Ry, triggers on Z/Rz, Linux gamepad button order, and a hat switch. It sits on the virtual bus.
**Why.** Android ships a key layout for exactly this controller, which maps those usages to its standard gamepad axes and buttons. Games' controller databases (SDL, Unity and others) recognise it. scrcpy uses the same pairing successfully. A neutral id would fall back to Android's generic mapping, which puts the right stick and triggers on different axes.
**Trade-off.** Games show Xbox button names and treat Select as Back (Android's layout for this controller does). The device borrows Microsoft's ids, as many controller adapters do. It is only visible on the owner's phone.

## D-026: The owner approved shell-level access for the virtual gamepad
**What.** Approved by the owner after the session's safety check first blocked it. `VirtualPadUserService` runs under Shizuku as the shell user (the privilege `adb shell` has). It does one thing: open `/dev/uhid`, create one gamepad, and write reports to it. It is not exported to other apps: only Swivel holds its Binder, which Shizuku hands over.
**Why.** It is the only no-root way to make the Pocket a real gamepad (D-024).
**Trade-off.** While Shizuku runs, Swivel's helper process has more power than a normal app. The helper's code is kept to that one job so it can be reviewed at a glance. Keep it that way: anything else that needs shell privileges gets its own decision.

## D-027: The games list is "games you can play with a controller", not MOGA-SDK games
**What.** The list shows installed games (app category "game" or the older isGame flag) and any app that declares `android.hardware.gamepad`, split into "Controller support" (declared) and "All games". Pivot's 24 MOGA-enhanced titles are marked if present. Visibility comes from a `<queries>` launcher intent, not `QUERY_ALL_PACKAGES`.
**Why.** Since M4 every controller game is playable with the Pocket, while MOGA-SDK games are effectively extinct (M3). Android has no reliable "supports controllers" flag: declaring the gamepad feature is optional and many controller games omit it, so "All games" stays one tap away. The launcher-intent query is the visibility a launcher gets, which the brief asked for instead of `QUERY_ALL_PACKAGES`.
**Trade-off.** "Controller support" misses games that do not declare it, and "All games" includes touch-only ones. A curated database would be more exact, but it would need network access or constant upkeep, and the brief rules out network calls.
