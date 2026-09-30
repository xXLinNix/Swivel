# Milestones

The owner tests each milestone on their own phone and controller before the next one
starts. Goals are numbered as in the brief: 1 pairing, 2 test screen, 3 Mode A bridge,
4 games list, 5 remapping.

## M1: Mode B pairing and test screen (goals 1 and 2, Mode B half), **built, awaiting hardware test**

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
opened on a Pixel 9 Pro emulator (API 37.1) showing the home screen. **Not yet verified:**
the unit tests under AGP, and anything that needs real Bluetooth, which the emulator
cannot provide.

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

## M2: Mode A link and test screen (goals 1 and 2, Mode A half)

- `core/protocol`: frame encoder, a streaming parser that resynchronises on `0x7A` and
  checksums, and first- and second-generation payload decoding into `ControllerSnapshot`,
  including the low-battery flag. Tests use byte fixtures from the research.
- `ModeALink`: an RFCOMM client on the SPP UUID in a `connectedDevice` foreground service
  with a notification, stream mode (70 or 68) with a keepalive poll every 2 s, and a resend
  of the stream command if the first is ignored.
- Reconnect with backoff after the controller sleeps. CompanionDeviceManager presence
  events restart the service from the background. It disconnects after a set idle time
  with the screen off, so the controller can sleep and Doze is respected.
- The wizard's Mode A path confirms with a real handshake. The test screen shows Mode A
  controllers. A small Settings screen covers auto-reconnect and the player LED.

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
