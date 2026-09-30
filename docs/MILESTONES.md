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

## M2: Mode A link and test screen (goals 1 and 2, Mode A half), **built, awaiting hardware test**

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

**Hardware test with the MOGA Pocket, please report back**
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

## M3: Mode A bridge spike (goal 3), go/no-go first

Needs one MOGA-enhanced game APK from the owner. Steps: find its SDK version and how it
binds (implicit intent, or an explicit package); write down the parcel layouts; and if it
is viable, implement `com.bda.controller.IControllerService` as a hand-written Binder in
Swivel's service. See RESEARCH.md for why this may only work for sideloaded old games, or
only under PowerA's package name.

## M4: Installed games list (goal 4)

A curated list of MOGA-enhanced package names. The Pivot APK's bundled icons name 23 of
them, a start. Visibility comes from one `<queries><package/></queries>` entry per game,
with no `QUERY_ALL_PACKAGES`, and games launch through `getLaunchIntentForPackage`.
Detecting unlisted games by scanning APKs for the SDK would need a broad `<queries>` on the
launcher intent: to be discussed.

## M5: Remapping (goal 5), needs a decision first

As written, per-game Mode B remapping is not possible for an ordinary app. See the M1
report and DECISIONS.md D-015.
