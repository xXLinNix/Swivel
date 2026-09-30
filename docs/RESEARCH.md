# Research: MOGA Mode A and the old Pivot app

Written before milestone 1, from four sources:

- **MogaSerial** ([Zel-os/MogaSerial](https://github.com/Zel-os/MogaSerial)), a Windows Mode A driver in C++, MIT licence, 2016. Its author tested on a MOGA Pro Power.
- **moga-uinput** ([jakobend/moga-uinput](https://github.com/jakobend/moga-uinput)), a Linux Mode A prototype in Python, MIT licence, 2018.
- **sensboston/MOGA** ([sensboston/MOGA](https://github.com/sensboston/MOGA)), a Windows Phone and UWP library. It has **no licence file**, so it is all rights reserved: it confirms the protocol, but none of its code may be reused.
- **MOGA Pivot 1.23** (`com.bda.pivot.mogapgp`), the APK the owner supplied. I read only its manifest, class and method names, constant fields and strings. I did not decompile method bodies into source, and nothing from it is copied into this project (see Licensing).

Each fact below is marked **confirmed** (two or more independent sources agree), **single source**, or **inferred** (my reading, not yet tested on hardware).

## The Mode A protocol

### Transport

- Bluetooth Classic RFCOMM on the Serial Port Profile, UUID `00001101-0000-1000-8000-00805F9B34FB`. **Confirmed**: MogaSerial connects with `SerialPortServiceClass_UUID`, moga-uinput uses the RFCOMM port from SDP, sensboston uses `RfcommServiceId.SerialPort`, and Pivot's `ControllerService.CONTROLLER_SERVICE_UUID` holds this UUID.
- Pivot tried a secure socket first, then fell back through several insecure and reflection-based sockets (its log strings include "Using reflection insecure ch1"). This suggests the secure socket failed on some 2013-era phones. **Inferred.** Swivel starts with the public `createRfcommSocketToServiceRecord` and adds `createInsecureRfcommSocketToServiceRecord` only if hardware testing shows it is needed. Both are public API, so no reflection is required.
- Pairing PIN `1234` when one is asked for. **Single source** (MogaSerial README).

### Frames

Host to controller, always 5 bytes. **Confirmed** (all three projects; Pivot's `Device$Gen0` has `SIZE=5` with offsets header 0, size 1, command 2, device id 3):

| Byte | Value |
|---|---|
| 0 | `0x5A` |
| 1 | length, always `5` |
| 2 | command |
| 3 | controller id, 1 to 4 (sets the player LED) |
| 4 | XOR of bytes 0 to 3 |

Controller to host, 12 or 14 bytes: `0x7A`, length, response code, controller id, payload, then the XOR of every earlier byte as the last byte. **Confirmed.**

| Command sent | Meaning | Reply code | Reply length |
|---|---|---|---|
| 65 (`A`) | poll once, digital triggers | 97 | 12 |
| 67 (`C`) | set controller id | none | none |
| 68 (`D`) | stream, digital triggers | 100 | 12 |
| 69 (`E`) | poll once, analog triggers | 101 | 14 |
| 70 (`F`) | stream, analog triggers | 102 | 14 |

**Confirmed** by MogaSerial and moga-uinput. First-generation controllers (names starting `BD&A`: the MOGA Pocket and the original MOGA) use 65/68. Later ones (names starting `Moga`) use 69/70. Pivot's `Device$ControllerModel` has three models: MOGA, MOGA_PRO and MOGA_2.

### Payload layout (offsets from the start of the frame)

| Offset | Content |
|---|---|
| 4 | buttons: bit 0 Y, 1 B, 2 A, 3 X, 4 Start, 5 Select, 6 L1, 7 R1 |
| 5 | D-pad and extras: bit 0 up, 1 down, 2 left, 3 right, 4 L2, 5 R2, 6 L3, 7 R3 |
| 6, 7 | left stick X, Y (signed 8-bit) |
| 8, 9 | right stick X, Y (signed 8-bit; Pivot calls them Z and RZ) |
| 10, 11 | L2, R2 analog, 0 to 255 (14-byte frames only) |
| 10 or 12 | power and version byte (see Battery) |
| 11 or 13 | checksum |

**Confirmed** for bytes 4 to 11 (MogaSerial's bit table and moga-uinput agree; Pivot's `Device$Gen1` and `Device$Gen2` constants have the same bit values and offsets).

Stick sign: moga-uinput converts with `if v >= 128: v -= 255`, which is off by one (a signed byte needs `- 256`). MogaSerial reads the byte as signed. Swivel will use a plain signed byte. moga-uinput also inverts Y. **Not yet checked on hardware.**

### Timing

- About 100 reports a second in streaming mode. **Single source** (MogaSerial).
- The controller sometimes ignores the first stream command after connecting; sending it again a second later works. MogaSerial also polls every 2 seconds while streaming, to notice a dead link. **Single source.** Swivel will do both.

### Battery

MogaSerial's author found "no way to obtain battery status" in Mode A and noticed a constant `0x10` near the end of each frame. Pivot's constants name that byte: `OFFSET_POWER` (10 or 12), with `POWER_LOW = 1` (bit 0) and `POWER_VERSION = 240` (the high nibble). The SDK also exposes `STATE_POWER_LOW`. So Mode A reports a **low-battery flag, not a percentage**, and `0x10` means version 1, battery fine. **Inferred**; to be checked by running a controller flat in milestone 2.

In Mode B, Android 12 and later expose a battery level through `InputDevice.getBatteryState()` if the controller reports one in its HID descriptor. MogaSerial says Mode B does report battery, but whether it reaches that Android API is **unknown**. The milestone 1 test screen will show it.

## Telling A from B

- **Name.** moga-uinput treats names starting `Moga` that contain `HID` as Mode B, and `BD&A` names as first generation. **Single source.** Android caches the name from pairing time, so the name is weak evidence.
- **Services.** Mode A offers SPP (`0x1101`). Mode B must offer HID (`0x1124`) to be a Bluetooth gamepad at all. `fetchUuidsWithSdp()` asks the controller directly. **Inferred** from how Bluetooth works; to be checked on hardware.
- **Input device.** Android creates a gamepad `InputDevice` only for a HID controller. This is the strongest evidence.
- **Unknown:** whether a MOGA keeps the same Bluetooth address in both modes. If it does, Android keeps one pairing whose cached name and services describe whichever mode was paired first, and switching modes may need "Forget" then pair again. The wizard tells the user that, and milestone 1 hardware testing should settle it.
- Pivot shows a switch-mode alert (`GamepadAlert_SwitchMode`), and it checks the HID profile before connecting ("A HID device is connected or connecting"), so it used similar reasoning.

## The old SDK bridge (goal 3): is it realistic?

What the APK shows (names and constants only):

- Pivot ran `com.bda.controller.service.ControllerService` in its own process, with intent-filter actions `com.bda.controller.IControllerService` and `com.bda.controller.manager.IControllerManager`.
- The SDK inside games (`com.bda.controller.Controller.init()`) builds `new Intent("com.bda.controller.IControllerService")` with **no package**, then calls `startService` and `bindService` on it.
- The Binder interface descriptor is `com.bda.controller.IControllerService`, with 14 transactions: registerListener 1, unregisterListener 2, registerMonitor 3, unregisterMonitor 4, getInfo 5, getKeyCode 6, getAxisValue 7, getState 8, sendMessage 9, registerListener2 10, getKeyCode2 11, allowNewConnections 12, disallowNewConnections 13, isAllowingNewConnections 14. Callbacks go through `IControllerListener` (onKeyEvent, onMotionEvent, onStateEvent) with parcelable `KeyEvent`, `MotionEvent` and `StateEvent` types.

What stands in the way on Android 14 to 17:

1. **Implicit service intents.** Since Android 5.0, `bindService` with an implicit intent throws for any app targeting API 21 or higher. The SDK in this APK can therefore only bind from games targeting API 20 or lower.
2. **Old apps no longer install.** Android 14 refuses to install apps targeting below API 23. Reports from the Android 15 previews say the floor rose to 24; I could not confirm the final value from a primary source. Either way, a game old enough to use the implicit intent cannot be installed normally. `adb install --bypass-low-target-sdk-block` still works.
3. **Newer SDK builds.** A MOGA game updated past API 21 must have shipped a newer SDK that names a package explicitly, almost certainly Pivot's own `com.bda.pivot.mogapgp`. Serving those games means publishing under PowerA's package name. That is impersonation, and Swivel should not do it except as a private sideload build the owner chooses knowingly. **Unknown until I see such a game's APK.**
4. **Package visibility.** A game targeting API 30 or higher can only bind to a package it declares in `<queries>`. A game that did not declare Pivot's package cannot see any bridge.
5. **No system-wide injection.** An ordinary app cannot turn Mode A into a system gamepad: there is no uinput without root, and accessibility services cannot inject gamepad events. Mode A input can only reach apps that speak the MOGA SDK.

Background limits are **not** the obstacle. A bound service runs while the game is bound to it, and the RFCOMM link can live in a `connectedDevice` foreground service. A CompanionDeviceManager association lets that service start from the background.

**Verdict: technically possible for a narrow set of games, unconfirmed for the rest.** Old games that use the implicit intent can bind to any package, so they would work if sideloaded with the adb flag. Later games probably need PowerA's package name. Milestone 3 is therefore a research spike with a go/no-go decision. It needs one MOGA-enhanced game APK the owner owns, to see which SDK it bundles. Most MOGA-enhanced games also accept standard gamepad input, so Mode B already covers them.

## Licensing and trademarks

I am not a lawyer; this is the engineering view.

- **PowerA's SDK and Pivot are proprietary.** Swivel contains no PowerA code, art or text. The research above records only facts needed for interoperability: a UUID, byte layouts, an interface descriptor, transaction numbers and constant values. Reverse engineering for interoperability is generally protected (for example the EU Software Directive art. 6, and in the US *Sega v. Accolade* and DMCA §1201(f)). If Swivel is ever published, get real legal advice first.
- **A Binder interface needs only matching strings and parcel layouts.** Implementing `com.bda.controller.IControllerService` in milestone 3 needs the descriptor string and the byte order of each parcel, not PowerA's classes.
- **MogaSerial and moga-uinput are MIT.** Swivel's codec will be written from the protocol facts, not translated from their code. They are credited in the README anyway.
- **sensboston/MOGA has no licence.** Read only to confirm facts.
- **Trademarks.** "MOGA" and "Pivot" belong to PowerA. The app is called Swivel. It mentions MOGA only to say what it works with, and it uses its own package name (D-002).
