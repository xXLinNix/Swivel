# Swivel

A companion app for MOGA Bluetooth controllers (Pocket, Pro, Hero Power, Pro Power) on
current Android, rebuilt from scratch to replace PowerA's discontinued MOGA Pivot.
Kotlin, Jetpack Compose and Material 3, targeting Android 17 (API 37), minimum Android 8.0.

Swivel is an independent project. MOGA and Pivot are trademarks of PowerA, which has no
part in it. It contains no PowerA code or art.

- [docs/RESEARCH.md](docs/RESEARCH.md): what is known about the Mode A protocol and the old SDK bridge.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): layers, screen flow, and the Android docs each choice follows.
- [docs/MILESTONES.md](docs/MILESTONES.md): the plan, and what to test on hardware now.
- [docs/DECISIONS.md](docs/DECISIONS.md): every judgment call, as What / Why / Trade-off.

## Setting up (once)

1. **Android Studio.** Install the current stable release from
   developer.android.com/studio. This project uses Android Gradle Plugin 9.2.1. If Studio says
   the plugin is too new, use Help → Check for Updates.
2. **Open the project.** File → Open, and pick this folder (the one with
   `settings.gradle.kts`). Studio downloads Gradle 9.6.0 and the libraries on the first
   sync, which takes a few minutes. If it offers to install **Android SDK Platform 37** or
   **Build-Tools 36**, accept. Studio's bundled JDK is fine.
3. **Your phone.** Settings → About phone → tap *Build number* seven times to unlock
   Developer options. Then Settings → System → Developer options → turn on *USB debugging*
   (or *Wireless debugging*). Connect the phone and accept the "Allow USB debugging?"
   prompt on it.
4. **Run.** Pick your phone in the device menu at the top of Studio and press ▶ (Run
   'app'). The app installs as **Swivel**.

From a terminal instead. On Windows, PowerShell needs `.\gradlew.bat` in place of
`./gradlew`, and Gradle needs to find a JDK. Studio's own terminal does not set one up, so
point `JAVA_HOME` at the JDK bundled with Studio first (this is the default install path):

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Then:

```
./gradlew testDebugUnitTest   # unit tests, no phone needed
./gradlew installDebug        # build and install on the connected phone
```

## Using it (milestone 1)

1. Slide the controller's switch to **B** and turn it on.
2. In Swivel, tap **Pair a controller** → **Pair in Mode B** → allow **Nearby devices** →
   **Find my controller**, then pick it from Android's list.
3. When it says **Ready**, tap **Test every button**.

**Mode A** (the switch on A, or a MOGA Pocket, which only has Mode A): pair with
**Pair in Mode A**, then tap **Connect and test**. Later, connect from the controller's
row on the home screen. While connected, a notification shows the link and has a
Disconnect button.

## Playing Play Store games with a Mode A controller

Android lets no ordinary app create a gamepad, so Swivel uses **Shizuku** to show your
Mode A controller (such as a MOGA Pocket) to Android as a standard Xbox 360-style
gamepad. Every game that supports controllers can then use it.

1. Install **Shizuku** from the Play Store and open it.
2. Choose **Start via Wireless debugging** and follow its steps. It pairs once using a code
   from Settings → Developer options → Wireless debugging → Pair device with pairing code.
   Android stops Shizuku whenever the phone restarts, so start it again after a reboot.
3. In Swivel: Home → **Play Store games** → **Allow Swivel in Shizuku**.
4. Connect your controller. It now appears to games as a gamepad, and disappears when it
   disconnects. "Share as a gamepad" on the home screen switches this off.

While Shizuku runs, Swivel's small helper process has the same privileges as `adb shell`.
It uses them only to create that one gamepad (docs/DECISIONS.md, D-026).

## Old MOGA-enhanced games (the SDK bridge)

Swivel answers the MOGA SDK the way the Pivot app did, so a game built with it gets
input from a Mode A controller. **Connect the controller in Swivel first**, then start the
game. It only works for games that target Android 4.4W (API 20) or lower. Android 14 and
later refuse to install those normally, so from a computer:

```
adb install --bypass-low-target-sdk-block the-game.apk
```

Phones from the Pixel 7 onward cannot run 32-bit apps, and most games of that era are 32-bit
only. docs/RESEARCH.md has the details.

## Credits

The virtual gamepad's HID layout follows [scrcpy](https://github.com/Genymobile/scrcpy)
(Apache-2.0). Shizuku support uses the [Shizuku API](https://github.com/RikkaApps/Shizuku-API)
(MIT). The Mode A protocol notes build on the MIT-licensed
[MogaSerial](https://github.com/Zel-os/MogaSerial) by Jake Montgomery and
[moga-uinput](https://github.com/jakobend/moga-uinput) by Jakob Endrikat.
