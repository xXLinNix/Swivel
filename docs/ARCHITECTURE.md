# Architecture

One Gradle module, `app`, in four layers. Each layer only calls the one below it.

```
ui/        Compose screens (…Content), thin Android wrappers (…Screen), ViewModels
  │
data/      ControllerRepository ── the only thing ViewModels talk to
  │           ├─ bluetooth/BluetoothGateway   adapter, bonds, SDP, discovery, broadcasts
  │           ├─ bluetooth/CompanionPairing   CompanionDeviceManager chooser
  │           ├─ input/GamepadInputSource     Mode B: InputDevices + key/motion events
  │           ├─ modea/ModeALink              Mode A: RFCOMM socket, watchdog, reconnection
  │           ├─ modea/ModeAService           foreground service (connectedDevice) holding the link
  │           └─ bridge/MogaSdkService        exported service answering the old MOGA SDK, fed by the link
  │
core/      Plain Kotlin, no android.* imports, all unit-tested on the JVM
              model/   ControllerSnapshot, GamepadButton, ControllerMode, BatteryReading
              detect/  MogaNames, ModeDetector, PairingJudge
              hid/     HidSnapshotReducer (Android key/axis events → snapshot)
              protocol/  Mode A commands, stream parser, report decoder, watchdog, reconnect policy
              bridge/    the MOGA SDK's Binder constants, event mapping, listener registry
```

`CorePurityTest` fails if `core` ever imports Android. The Mode A protocol lives in
`core/protocol`, so it is tested byte by byte without a controller.

Both modes produce the same `ControllerSnapshot`, so the test screen (and later the
remapper) never needs to know which mode a controller is in.

### Why each `…Screen` is split from its `…Content`

`…Content` takes a state object and callbacks, and has no Android dependencies beyond
Compose. `…Screen` holds the pieces that need an Activity: permission launchers, the
companion chooser, and intents to Settings. Splitting them keeps the Android-only code small
and lets `…Content` be previewed and type-checked on its own.

## Screen flow

```
Home ──► Pair ──► ChooseMode ─► [NeedsPermission] ─► [NeedsBluetooth] ─► Prepare
  │                                                                          │
  │                          ┌── Find my controller (companion chooser) ─────┤
  │                          ├── Search inside Swivel (Android 12+ scan) ────┤
  │                          ├── Pair in Android's Bluetooth settings ───────┤
  │                          └── Already paired: Use ────────────────────────┤
  │                                                                          ▼
  │                                                   Bonding ─► Verifying (≤20 s)
  │                                                                          │
  │           ┌──────────────┬───────────────┬───────────────┬──────────────┘
  │           ▼              ▼               ▼               ▼
  │      ReadyModeB    PairedModeA      WrongMode       NotConfirmed
  │           │         (M1: stub)    (flip switch,    (wake it,
  └─► Test ◄──┘                        check again)     check again)
       screen
```

Verifying settles as soon as it has strong evidence (see `PairingJudge`):

- **Mode B wanted:** Android creates a gamepad `InputDevice` → ReadyModeB. A fresh SDP
  reply offering only SPP → WrongMode(A). The name alone only counts after the timeout.
- **Mode A wanted:** any gamepad or HID service → WrongMode(B). SPP only → PairedModeA.
  In milestone 2, a successful Mode A handshake becomes the confirmation.

Later milestones add: a Games tab (M4), the bridge if it passes its spike (M3), per-game
remapping (M5), and a Settings screen (auto-reconnect window, player light) once there is
more than one setting worth changing.

## Bluetooth and permission choices

| Topic | Choice | Source |
|---|---|---|
| Permissions, Android 12+ | `BLUETOOTH_CONNECT`, plus `BLUETOOTH_SCAN` with `neverForLocation`, asked together as one "Nearby devices" prompt | [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) |
| Permissions, Android 8 to 11 | `BLUETOOTH` and `BLUETOOTH_ADMIN` with `maxSdkVersion="30"`. No location permission: the in-app scan is only offered on 12+ | same |
| Finding the controller | CompanionDeviceManager chooser filtered to MOGA names: no scan permission needed, and it sets up the background-start exemption M2 needs | [Companion device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing) |
| Bonding | `createBond()` and `ACTION_BOND_STATE_CHANGED` | [Connect Bluetooth devices](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices) |
| Mode B input | Activity `dispatchKeyEvent` / `dispatchGenericMotionEvent`, joystick axes from `InputDevice.motionRanges` | [Handle controller actions](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input) |
| Mode A link (M2) | RFCOMM client socket on the SPP UUID, `cancelDiscovery()` before `connect()`, blocking I/O on its own thread | [Connect Bluetooth devices](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices) |
| Keeping Mode A alive (M2) | Foreground service with `foregroundServiceType="connectedDevice"` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`; prerequisite met by holding `BLUETOOTH_CONNECT` | [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) |
| Restarting with the app closed (later, D-018) | `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` plus device-presence observing (`ObservingDevicePresenceRequest` on Android 16+) | [Background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) |
