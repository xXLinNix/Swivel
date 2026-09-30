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

From a terminal instead (Windows: `gradlew.bat` in place of `./gradlew`):

```
./gradlew testDebugUnitTest   # unit tests, no phone needed
./gradlew installDebug        # build and install on the connected phone
```

## Using it (milestone 1)

1. Slide the controller's switch to **B** and turn it on.
2. In Swivel, tap **Pair a controller** → **Pair in Mode B** → allow **Nearby devices** →
   **Find my controller**, then pick it from Android's list.
3. When it says **Ready**, tap **Test every button**.

Mode A (the switch on A) can be paired and recognised, but not yet used; that is milestone 2.

## Credits

The Mode A protocol notes build on the MIT-licensed
[MogaSerial](https://github.com/Zel-os/MogaSerial) by Jake Montgomery and
[moga-uinput](https://github.com/jakobend/moga-uinput) by Jakob Endrikat.
