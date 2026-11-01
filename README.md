# FTC robot code (team 31744)

goBILDA **mecanum** StarterBot (DECODE season; 4 wheels, launcher not installed
yet) on the FTC Robot Controller SDK v12.0.

- **Windows:** use Android Studio; see [Windows (Android Studio)](#windows-android-studio).
- **Linux:** everything is built and deployed from a headless Docker toolchain
  driven by `make`, so no Android Studio is needed and nothing but Docker is
  installed on the laptop; see [Linux (Docker + make)](#linux-docker--make).

The rest of this README gives the `make` commands; the Windows section maps each
one to its Windows equivalent. The code, robot configuration and Driver Hub steps
are the same on both.

## How the pieces fit

| Device | App | Role |
|---|---|---|
| **Control Hub** (on the robot) | Robot Controller | Runs the OpModes (Java *and* Blocks), drives the motors. **Deploys go here.** |
| **Driver Hub** (handheld) | Driver Station | Gamepads, INIT/START/STOP, telemetry. Never gets robot code. |

They talk over the Control Hub's Wi-Fi network, `31744-RC`. Robot
configurations (which device is on which port, and its name) are files on the
**Control Hub**; the Driver Hub only edits and activates them remotely.

- **Java OpModes** are compiled into the Robot Controller APK (`make deploy-wifi`).
- **Blocks OpModes** are `.blk` files on the Control Hub, edited in its web UI
  (`make blocks`).
- Both kinds show up together in the Driver Hub's OpMode list.

## Layout

The repository root is an Android Studio project (FIRST's FtcRobotController
SDK) with the team's tooling alongside it.

```
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/   ← our OpModes
FtcRobotController/   FIRST's Robot Controller app module (don't edit)
build.gradle, settings.gradle, gradlew, gradlew.bat, ...  FIRST's Gradle build
README-FTC-SDK.md     FIRST's SDK README and release notes
robot-config/         Robot configurations pushed to the hub with `make hub-config`
toolchain/            Dockerfile (JDK 21 + Android SDK tools), Wi-Fi hop script, host udev rule
Makefile              Linux build / deploy / backup commands (`make help`)
StarterBotWithMecanumsCode.zip  original goBILDA download of the mecanum TeleOp

Local only (gitignored):
hub-backup/           Snapshots of the Control Hub's /sdcard/FIRST (`make hub-backup`)
usb-backup/           Snapshots from a USB-connected device, e.g. the Driver Hub (`make usb-backup`)
apks/                 Driver Station APK cache for `make driver-station`
```

### OpModes

| OpMode | Status |
|---|---|
| `StarterBotTeleopMecanums` | **The driver OpMode.** Drives now. Launcher controls switch on automatically once `launcher`, `left_feeder` and `right_feeder` are in the robot configuration. |
| `StarterBotAutoMecanums` | **The autonomous.** Back against the goal: fires 3 shots (skipped until the launcher is configured), backs up 4", turns 45° (left on RED, right on BLUE), backs 26" off the line. Press X (BLUE) or B (RED) during INIT. |
| `StarterBotAuto` | goBILDA's original *two-wheel* autonomous, `@Disabled`; kept for reference. |

### Controls (gamepad 1)

| Input | Action |
|---|---|
| Left stick up/down | Forward / back |
| Left stick left/right | Strafe |
| Right stick left/right | Rotate |
| D-pad up / down | Drive speed limit up / down, in 25% steps from 25% to 100% (starts at 100%; shown in telemetry) |
| Y / B | Flywheel on / off *(once the launcher is installed)* |
| Right bumper | Fire one shot *(once the launcher is installed)* |

## Windows (Android Studio)

The `make` toolchain relies on Linux-only pieces (udev, NetworkManager, Docker
USB access), so on Windows use Android Studio, which is FIRST's standard setup.
It works on the same files: your Java goes in
`TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.

### Setup

1. Install [Android Studio](https://developer.android.com/studio) (**Narwhal 3
   Feature Drop or later**, which the SDK v12.0 build requires). It bundles the
   JDK and the Android SDK, so nothing else is needed for building.
2. Install the [REV Hardware Client](https://docs.revrobotics.com/rev-hardware-client).
   It installs the USB drivers for the Control Hub and Driver Hub, and can update
   the hubs' software.
3. Clone the repository (Android Studio's **File → New → Project from Version
   Control**, GitHub Desktop, or `git clone`), then **open the repository
   folder** itself. It's an Android Studio project, so nothing needs importing.
   Let the Gradle sync finish; the first sync downloads about 1 GB and needs
   internet.
4. `adb` comes with Android Studio but isn't on the `PATH`. In a PowerShell
   window (or Android Studio's **Terminal** tab), set this once per session to use
   the commands below:
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   & $adb devices
   ```

### Deploying

- **USB:** plug the Control Hub's USB-C port into the laptop. It appears in the
  device list at the top of Android Studio; select **TeamCode** and click **Run ▶**.
- **Wi-Fi:** connect the laptop to `31744-RC`, run `& $adb connect 192.168.43.1:5555`,
  then **Run ▶** as above. You have no internet while on the robot's network.
- **First deploy to a hub:** if Android Studio reports that the existing app has a
  *different signature*, accept its offer to uninstall it (or run
  `& $adb uninstall com.qualcomm.ftcrobotcontroller`), then Run again. Back up
  first (below): uninstalling keeps `/sdcard/FIRST`, but it's cheap insurance.

### `make` equivalents

Run these from the repository root in PowerShell after setting `$adb`. Commands
that talk to the Control Hub need either its USB cable or the laptop on
`31744-RC` plus `& $adb connect 192.168.43.1:5555`.

| Linux | Windows |
|---|---|
| `make build` / `make deploy` / `make deploy-wifi` | Android Studio **Run ▶** (see Deploying) |
| `make deploy-wifi-fresh` | Accept Android Studio's uninstall offer, or `& $adb uninstall com.qualcomm.ftcrobotcontroller` then Run |
| `make hub-config` | `& $adb push robot-config\StarterBotMecanum.xml /sdcard/FIRST/`, then Activate on the Driver Hub |
| `make hub-backup` | `& $adb pull /sdcard/FIRST hub-backup\YYYYMMDD-HHMMSS` (today's date) |
| `make usb-backup` | Same `adb pull` with only the Driver Hub plugged in |
| `make driver-station` | REV Hardware Client (update the Driver Hub), or download `FtcDriverStation-release.apk` from the [SDK release](https://github.com/FIRST-Tech-Challenge/FtcRobotController/releases) and `& $adb install -r FtcDriverStation-release.apk` |
| `make blocks` | `& $adb forward tcp:8080 tcp:8080`, open <http://localhost:8080>; when done `& $adb forward --remove tcp:8080` |
| `make devices` | `& $adb devices -l` |
| `make logcat` | Android Studio's **Logcat** tab |
| `make udev` | Not needed on Windows (the REV Hardware Client installs the drivers) |

## Linux (Docker + make)

### One-time laptop setup

Let the toolchain use the hubs over USB. This installs a udev rule on the host
by SSHing to `root@localhost` (override with `ROOT_SSH=...`), so it works from
inside the VS Code container:

```sh
make udev
```

For Wi-Fi deploys, join `31744-RC` by hand once so the laptop saves its password.

### First-time robot setup

1. **Install our Robot Controller build.** A factory or REV Hardware Client
   install is signed with a different key, so the first deploy must uninstall it
   (programs, configs and Blocks in `/sdcard/FIRST` are kept):
   ```sh
   make hub-backup          # safety copy first
   make deploy-wifi-fresh
   ```
2. **Match the Driver Station version** (Driver Hub on USB). A mismatch shows
   "the driver station app is obsolete":
   ```sh
   make driver-station
   ```
3. **Push and activate the robot configuration:**
   ```sh
   make hub-config
   ```
   Then on the Driver Hub: **⋮ → Configure Robot → StarterBotMecanum → Activate**.

   | Control Hub port | Name |
   |---|---|
   | Motor 0 | `left_front_drive` |
   | Motor 1 | `left_back_drive` |
   | Motor 2 | `right_back_drive` |
   | Motor 3 | `right_front_drive` |

### Everyday use

```sh
make build             # compile to TeamCode/build/outputs/apk/debug/TeamCode-debug.apk
make deploy-wifi       # build online, hop to 31744-RC, install + start, hop back
make deploy            # same over the Control Hub's USB-C cable
make blocks            # Blocks / OnBot Java editor at http://localhost:8080 over USB
make hub-config        # push robot-config/*.xml to the hub (then Activate on the Driver Hub)
make hub-backup        # copy the hub's /sdcard/FIRST (Blocks, configs, OnBot Java) into hub-backup/
make usb-backup        # same, from a USB-connected device (e.g. the Driver Hub)
make driver-station    # update the USB-connected Driver Hub to Driver Station DS_VERSION
make devices           # what's visible over USB / adb?
make logcat            # stream robot logs (Ctrl-C to stop)
make shell             # shell inside the toolchain (adb, gradlew, sdkmanager)
```

The first build downloads about 1 GB of Gradle dependencies (~3 min) into the
`ftc-toolchain-home` Docker volume. Later builds work offline. The toolchain
image rebuilds only when `toolchain/Dockerfile` changes (`make image` forces it).

#### Deploying over Wi-Fi

`make deploy-wifi` builds first (while you still have internet), then installs
and starts the app over wireless adb. `make hub-config` and `make hub-backup`
reach the hub the same way. How they get onto the robot's network depends on
the laptop:

- **Second Wi-Fi adapter on the robot's network** (or you joined it by hand):
  the hub already answers at `192.168.43.1:5555`, so nothing is switched and your
  internet stays up.
- **One adapter:** NetworkManager on the host (over `ssh root@localhost`) joins
  the robot's network (`HUB_SSID`, default `31744-RC`), runs the command, and
  switches back to the Wi-Fi you were on, even if it fails or you press Ctrl-C.
  Internet (and `wg0`) drop for about 30 s.

With a second adapter, stop the robot's network from taking over your default
route and DNS (one-time, on the laptop):

```sh
nmcli connection modify 31744-RC ipv4.never-default yes ipv4.ignore-auto-dns yes
```

The robot must be powered on so `31744-RC` is broadcasting. At competitions, use
the USB cable (`make deploy`) instead: events don't allow extra devices on
robot networks.

## Testing after a deploy

Wheels **off the ground** first. Select `StarterBotTeleopMecanums`, then INIT
(expect `Launcher: not configured - drive only` until the launcher exists), then
START:

| Stick | Expected |
|---|---|
| Left stick forward | All four wheels forward |
| Left stick right | Front-left and back-right forward; front-right and back-left backward |
| Right stick right | Left wheels forward, right wheels backward |

**Autonomous** (`StarterBotAutoMecanums`): during INIT, push the robot by hand
and check that all four `Encoders` numbers change; the moves depend on the drive
encoder cables. Test on the floor with room behind the robot. If the 45° turn is
short or overshoots, adjust `TRACK_WIDTH_MM` / `WHEEL_BASE_MM` (measured
wheel-center to wheel-center); if distances are off, check `WHEEL_DIAMETER_MM`.
Each move gives up after 4 s, so a missing encoder shows a `WARNING` instead of
driving forever.

A wheel spinning the wrong way needs its `setDirection` flipped in
`StarterBotTeleopMecanums.java`. The wrong wheel moving means two motor cables
(or config ports) are swapped.

## Blocks

Blocks programs live on the Control Hub and are edited in its web UI. To edit
from the laptop without leaving your normal Wi-Fi:

1. Plug the **Control Hub's** USB-C port into the laptop (the Driver Hub can stay
   plugged in too; it's skipped). The hub must be powered by the robot battery.
2. In a VS Code terminal:
   ```sh
   make blocks
   ```
3. When it prints `Editor ready: http://localhost:8080`, open that address in
   your browser and click **Blocks** (OnBot Java and Manage are there too).
4. When done, press **Ctrl-C** in the terminal, then save a copy:
   ```sh
   make hub-backup
   ```

`make blocks` forwards laptop port 8080 to the hub's web server over the USB
cable with adb, so it only works while that terminal is running.

Without the cable: join `31744-RC` and open <http://192.168.43.1:8080> (no
internet while connected). On the Driver Hub itself: **⋮ → Program & Manage**.

Saved Blocks programs appear on the Driver Hub right away, next to the Java
OpModes, with no deploy needed. Give Blocks programs names that differ from the
Java OpModes; duplicate names conflict.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `make blocks` stuck on "Waiting for the Control Hub" | Use the Control Hub's USB-C port, power the hub, and check `make devices`. |
| `make blocks`: `cannot bind` | Port 8080 is in use on the laptop: run `make blocks BLOCKS_PORT=8090` and open `http://localhost:8090`. |
| `make blocks` finds the hub but never prints "Editor ready" | The Robot Controller app isn't running: power-cycle the hub. |
| `adb` shows the device as `no permissions`, or nothing, over USB | Run `make udev`, then unplug and replug. |
| Deploy fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE ... signatures do not match` | The hub has a differently-signed Robot Controller (for example after a REV Hardware Client update): `make deploy-wifi-fresh`. |
| INIT error `Unable to find a hardware device with name "left_front_drive"` | The wrong robot configuration is active: Activate `StarterBotMecanum` on the Driver Hub. |
| Edits made in the Driver Hub's configuration editor don't stick | Press **Done → Save** before leaving the editor. `make hub-config` overwrites same-named configs with `robot-config/`, so update the XML there too. |
| Autonomous encoders stay at `0` when pushing the robot, or `WARNING: a move timed out` | The drive motors' encoder cables aren't plugged into the Control Hub's encoder ports (next to each motor port). |
| Driver Hub says "the driver station app is obsolete" | The Driver Hub is on USB: `make driver-station`. |
| Driver Hub shows the robot disconnected after a deploy | Wait a few seconds for the Robot Controller to restart; power-cycle the hub if it doesn't come back. |
| `make deploy-wifi` can't join `31744-RC` | The robot is off or out of range. The laptop switches back to your Wi-Fi automatically. |

## Git and the FTC SDK

This repository is FIRST's
[FtcRobotController](https://github.com/FIRST-Tech-Challenge/FtcRobotController)
project with its full history, plus our commits on top: the same setup FIRST
recommends for teams. Clone it and open the folder in Android Studio:

```sh
git clone git@github.com:31744FTCRobotics/FTCRobotics-2026.git
```

- **Our robot code** goes in `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.
  Commit it like any other file.
- **Avoid editing FIRST's files** (`FtcRobotController/`, `build.gradle`,
  `build.common.gradle`, `build.dependencies.gradle`, `settings.gradle`). Changes
  there can conflict with FIRST's changes at every SDK update.
- **Local only:** `hub-backup/`, `usb-backup/`, `apks/`, `.claude/`, plus the
  usual Android Studio and build files (`.idea/`, `*.iml`, `local.properties`,
  `build/`) from FIRST's `.gitignore`.
- `.gitattributes` keeps `gradlew`, `*.sh` and `Makefile` on LF line endings, so
  a Windows checkout can't break the Linux toolchain.
- **Linux, using both Android Studio and `make`:** Android Studio writes
  `local.properties` with the host's Android SDK path, which the toolchain
  container can't use. Delete `local.properties` before running `make build`
  (Android Studio recreates it on its next sync).

To see which SDK version is in the repo:

```sh
grep versionName FtcRobotController/src/main/AndroidManifest.xml
```

## Updating the SDK

Each season (and for mid-season fixes), FIRST publishes a new release on the
[SDK releases page](https://github.com/FIRST-Tech-Challenge/FtcRobotController/releases).
Update from a clean working tree (`git status` shows nothing to commit):

1. **Merge the new release**, replacing `v13.0` with the release tag:
   ```sh
   git pull --no-rebase https://github.com/FIRST-Tech-Challenge/FtcRobotController.git v13.0
   ```
   This creates a merge commit. Our files normally merge cleanly: FIRST doesn't
   ship files with our names, and their README edits follow the rename into
   `README-FTC-SDK.md`. If git reports conflicts, fix the listed files, `git add`
   them, and `git commit`. In Android Studio, the same merge is
   **Git → Pull** with the URL and tag.
2. **Read the release notes** at the top of `README-FTC-SDK.md` for breaking
   changes (renamed classes, a newer Android Studio requirement), and fix our
   OpModes if the build complains.
3. **Linux toolchain:** check `compileSdkVersion` in `build.common.gradle` and
   the AGP version in `build.gradle` against `SDK_PACKAGES` in
   `toolchain/Dockerfile`, and update the image tag in the Makefile (`IMAGE`) if
   you change the Dockerfile. Windows: install the Android Studio version the
   release notes require.
4. **Driver Station:** bump `DS_VERSION` in the Makefile to the same tag and run
   `make driver-station` with the Driver Hub on USB (Windows: REV Hardware
   Client). The Driver Station and Robot Controller must match, or the Driver
   Hub reports "obsolete".
5. **Build and deploy:** `make build`, then `make deploy-wifi` (Windows: Run ▶),
   and test on the robot. The pull already committed the new SDK; commit any
   fixes from steps 2-4 on top:
   ```sh
   git commit -am "Adapt to FTC SDK v13.0"
   ```

If an update goes badly, `git reset --hard ORIG_HEAD` (before committing
anything else) returns to the previous SDK.
