# Milestones

The owner tests each milestone on their own phone and controller before the next one
starts. Goals are numbered as in the brief: 1 pairing, 2 test screen, 3 Mode A bridge,
4 games list, 5 remapping.

## M1: Mode B pairing and test screen (goals 1 and 2, Mode B half), **done as far as the owner's hardware allows**

**Built**
- A pairing wizard: choose the mode, the Nearby devices permission (including "denied twice,
  open Settings"), Bluetooth on, find the controller (companion chooser, in-app scan on
  Android 12+, or Android's settings), bond, then confirm the mode, with a wrong-mode
  screen for both directions.
- A home screen with connected gamepads and paired MOGAs, each with its detected mode and
  the reason for it.
- A test screen: both sticks drawn and numeric, triggers as bars, every button lit,
  every raw axis the device declares with its range, a key-event log with key codes and
  scan codes, the battery level if Android gets one, vendor and product id. It swallows
  controller input so B does not act as Back, and it survives the controller sleeping.
- `core`: MOGA name rules, mode detection, the wizard's decision rules, and the HID
  mapping. 29 JVM unit tests.

**Verified:** `core` compiles and all 29 tests pass on the JVM. `data` compiles against the
Android 17 framework classes, and the view models and `…Content` composables against
Compose. The owner's first Gradle sync (AGP 9.2.1) built the app, which installed and
opened on a Pixel 9 Pro emulator (API 37.1) showing the home screen. All 29 unit tests also pass under AGP in Android Studio.
**Not yet verified:** anything that needs real Bluetooth, which the emulator cannot
provide.

**Found in hardware testing** (MOGA Pocket, part CPFA000253-01, on a Pixel 9 Pro)
- The Pocket has no A/B switch and pairs as `BD&A`, as the research predicted (D-016).
- Fixed: the wizard hung after the PIN in Mode A, because Bluetooth broadcasts never
  reached the app (D-017). Retested: the Pocket pairs, takes the PIN and ends on
  "Paired in Mode A".
- The Mode B path (test screen, wrong-mode detection for a switch on B) is untested on
  real hardware: the owner has no Mode B controller. Any Bluetooth gamepad would do for
  the test screen.

**Hardware test, please report back**
1. Home with nothing paired: no crash, nothing listed.
2. Switch on **B**, Pair → Mode B → Find my controller. Does Android's list show it, and
   under what exact name? Were you asked for a PIN?
3. Does it reach "Ready"? Roughly how long did Verifying take?
4. Test every button. Report any that light the wrong lamp, and any that only appear under
   "Held keys with no button mapping". Push each stick fully up and left: both should read
   about -1. Check that the triggers go smoothly from 0 to 1. Is a battery level shown?
   Please send a screenshot of "Raw axes".
5. Switch on **A**, run the wizard for Mode B: expect "The switch is on A". Then the reverse:
   switch on B, wizard for Mode A: expect "The switch is on B".
6. The open question from the research: when you flip the switch, does the controller show
   up under a different name, and do you have to Forget it and pair again?
7. Leave the test screen open until the controller sleeps, then press a button. Does it come
   back by itself?
8. Deny Nearby devices twice. Does the wizard then offer "Open app settings"?

## M2: Mode A link and test screen (goals 1 and 2, Mode A half), **done**

**Built**
- `core/protocol`: the command encoder, a stream parser that resynchronises on `0x7A`
  and checksums, first- and second-generation decoding into `ControllerSnapshot` with the
  low-battery flag, the link watchdog, and the reconnect policy. 22 new tests (51 in all).
- `ModeALink`: an RFCOMM client on the SPP UUID, secure socket first and insecure second.
  It sets the player light, then polls and streams. The watchdog resends if the first
  command is ignored, tries the other report format if the name guessed wrong, polls
  after 2 s of quiet, and calls the link dead after 6 s.
- Reconnection: after a drop it retries at 2, 4, 8, 16 then every 30 s, for 5 minutes. If
  the controller reconnects to the phone by itself (Bluetooth ACL) while it waits, it
  retries at once (D-018).
- `ModeAService`: a `connectedDevice` foreground service with a notification that shows
  the link state and a Disconnect button. It stops itself when the link stops.
- UI: Connect / Test / Disconnect for Mode A controllers on Home, "Connect and test" at
  the end of Mode A pairing, and the test screen for Mode A. The test screen shows sticks,
  buttons, raw axes, the last report in hex, button events, and the low-battery flag.
- The "slide the switch to B" advice is gone for first-generation controllers.

**Verified here:** everything but the Android-only glue compiles against Android 17 and
Compose, and all 51 tests pass. **Not verified:** anything on the phone.

**Hardware test** (MOGA Pocket on a Pixel 9 Pro): the link connects and the test screen
shows every button and both sticks correctly, including the Y direction (D-019). After
power-cycling the controller it reconnects in about 10 s. Disconnect from the
notification works, and connecting again is quick. Not yet reported: staying connected
in the background (step 5), giving up after 5 minutes (step 7), and the low-battery flag
(step 4 with flat batteries). Fixed after the test: centred sticks read "-0.000".

**The checklist used**
1. Home → the BD&A row → **Connect**. Allow notifications if asked. Does the test screen
   reach the controls within a few seconds? A notification should say it is connected.
2. Press every button: A, B, X, Y, L1, R1, Start, Select. Does each light the right lamp?
   If one lights the wrong lamp, note which.
3. Sticks: push each fully **up**, then **right**. Up should read about -1 on Y, right
   about +1 on X. Please send a screenshot of "Raw axes" and "Last report" with the left
   stick pushed up; that settles the Y direction (D-019).
4. Battery: the line should say "Battery: OK". If you have a nearly flat set of batteries,
   does it change to "low"?
5. Press Home on the phone and wait a minute. Is it still connected when you come back?
6. Turn the controller **off** while connected. The screen should say "Connection lost.
   Trying again…". Turn it back **on**: does it reconnect by itself, and how fast?
7. Leave it off for more than 5 minutes: it should give up, and the notification should
   disappear.
8. Tap **Disconnect** in the notification: the link should end.
9. If anything fails, filter Logcat by `Swivel` and send what it shows.

## M3: MOGA SDK bridge (goal 3), **built; closed without a game to test it with**

**Spike result: go, with a narrow reach.** The details are in RESEARCH.md, "Milestone 3 spike findings". The SDK's contract was recovered from SDK 1.3.0. The bridge works for games that bind with the implicit intent. On a 64-bit-only phone those also have to be pure Java or 64-bit, which few 2013–14 games are.

**Built**
- `core/bridge`: the SDK's constants, key codes in both styles, getState answers,
  snapshot differences as key and motion events, and the listener registry with each
  game's activity state. 12 new tests (64 in all).
- `MogaSdkService`: an exported service answering `com.bda.controller.IControllerService`.
  It answers all 14 transactions by hand and sends key, motion and state events to every
  resumed game as one-way calls, fed by a buffered report stream so quick taps are not
  lost. Home shows how many games are listening.

**Verified here:** compiles against Android 17, and all 64 tests pass. **Not verified:**
a real game binding to it. The obvious free test game, Mupen64Plus AE 2.4.4, is 32-bit
only and cannot install on a Pixel 9 Pro.

**How to use it:** connect the controller in Swivel first, then start the game.

**Closed.** The owner has no MOGA game to play, and no current game uses the SDK, so the
bridge stays in place, untested. The private build under PowerA's name (D-023) and a
self-test were declined. The goal that matters, playing Play Store games with the Pocket,
became M4 (D-024).

## M4: The Pocket as a gamepad for Play Store games, **done**

Replaces the old plan's M4 and M5 order (D-024). Android lets no ordinary app create an
input device, so this uses Shizuku, which the owner approved, together with its
shell-level access (D-026).

**Built**
- `core/virtualpad`: the HID descriptor and 15-byte report (scrcpy's gamepad layout under
  the Xbox 360 controller's ids, D-025), the D-pad as a hat switch, and the `/dev/uhid`
  message layout. 9 new tests (73 in all).
- `VirtualPadUserService`: runs in a process Shizuku starts as the shell user. It opens
  `/dev/uhid`, creates the gamepad, writes reports, and removes it on close.
- `VirtualPad`: while the "Share as a gamepad" switch is on, Shizuku is ready and the
  Mode A link is connected, it keeps the gamepad open and forwards every report. It
  removes the gamepad when the link drops, so games see a disconnect.
- Home: a "Play Store games" section that walks through installing, starting and allowing
  Shizuku, with the switch and the pad's state.
- The Mode A test screen now swallows controller input too, since the virtual gamepad's
  B and Select would otherwise act as Back.

**Verified here:** compiles against Android 17 and the Shizuku API; all 73 tests pass.
**Not verified:** anything on the phone, including the key assumption that Shizuku's
shell process may open `/dev/uhid` on a Pixel 9 Pro. scrcpy doing exactly that on
unrooted phones is the evidence for it.

**Hardware test** (MOGA Pocket, Pixel 9 Pro, Android 17, Shizuku over Wireless debugging):
it works. Shizuku's shell process may open `/dev/uhid` on this phone, and the Xbox app
and Android's own navigation both respond to the Pocket as a gamepad.

**The checklist used**
1. Install **Shizuku** from the Play Store. Open it, choose **Start via Wireless
   debugging** and follow its pairing steps (Developer options → Wireless debugging →
   Pair device with pairing code).
2. In Swivel, Home → Play Store games → **Allow Swivel in Shizuku**.
3. Connect the Pocket. The section should say games now see it as a standard gamepad,
   and **Connected controllers** should list "Swivel virtual gamepad". Tap **Test** there:
   every button and both sticks should behave on the Mode B test screen, since that is
   exactly what games receive.
4. Try a Play Store game that supports controllers. Does it respond? Name the game.
5. Turn the Pocket off, then on again. The gamepad should disappear and come back.
6. If it fails, send the section's error text and Logcat filtered by `Swivel` (the pad's
   own process logs as `SwivelPad`).

## M5: Installed games list (goal 4), **built, awaiting hardware test**

Reshaped by the pivot: it now lists the games the owner can play with the controller,
not only old MOGA-SDK games (D-027).

**Built**
- `core/games`: which installed apps are listed and in what order. Games that declare the
  gamepad feature come first, then the rest under "All games". The 24 games Pivot listed as
  MOGA-enhanced are marked. 4 new tests (77 in all).
- `InstalledGames`: reads launchable apps through a `<queries>` launcher intent (no
  `QUERY_ALL_PACKAGES`), checks each for the game category and the gamepad feature, loads
  icons into a small cache, and launches games.
- A Games screen, opened from Home: a controller-readiness line with a Connect button, a
  "Controller support" / "All games" filter, and tap-to-play. It refreshes whenever it
  comes back into view.

**Verified here:** compiles against Android 17, and all 77 tests pass. **Not verified:** on the phone.

**Hardware test, please report back**
1. Home → **Games**. Do your controller games appear under "Controller support"? Which
   ones are missing there but appear under "All games"?
2. With the Pocket off, the Controller line should say none is connected and offer
   **Connect my controller**. Tap it: it should switch to "Ready".
3. Tap a game: it should start, and the Pocket should work in it.
4. Install or uninstall a game, then come back: the list should update.

## M6: Remapping (goal 5)

Now possible for the Pocket: Swivel writes the virtual gamepad's reports itself, so it can
remap buttons before they reach any game (D-015, as revised by D-024). Remapping a real
Mode B controller is still impossible.
