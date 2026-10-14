# BIOBUZZ Robot Simulator

Practise setting up and tuning an FTC BIOBUZZ (2026–27) robot **before the robot exists**. The simulator runs real
FTC robot code (FTC SDK 12.0 + Pedro Pathing 3.0.1) against a hidden virtual robot in your browser: a Driver Station,
a gamepad, motors, servos, two turrets, flywheels, shots and the field. Made by FTC team 19859.

This repo contains:

| Folder | What it is |
|---|---|
| `sim/` | The simulator (Java engine + web page + Python tests) |
| `TeamCode/` | Example robot code it runs: two-turret shooter, autonomous engine (`AutoRunner`), TeleOp, and 9 guided setup programs |
| `team-config.yaml` | Every robot number, **all blank**. The setup programs measure them; nothing is copied from a real robot |
| `docs/BIOBUZZ_PREREQUISITES.md` | The setup guide the programs follow (step by step, written for students) |
| `docs/BIOBUZZ_SIMULATOR_PLAN.md` | How the simulator is built and what it models |
| `FtcRobotController/`, `gradle*`, `build*.gradle` | The standard FTC SDK project (unchanged) |

---

## 1. Install (once)

You need a Mac or Linux computer (Windows: use WSL) with:

1. **Android Studio** (it installs the Android SDK). Open this folder in Android Studio once and press
   **Build → Make Project**. This downloads the FTC SDK and Pedro Pathing into Gradle's cache, which the simulator
   uses. You never need Android Studio again after that.
2. **Java 17 or newer** (`java -version`). Android Studio's own Java works.
3. **Python 3** (`python3 --version`) and a C compiler (`cc`; on a Mac: `xcode-select --install`).

## 2. Start it

From this folder:

```bash
./sim.sh
```

Open **http://localhost:8080** in your browser. Leave the terminal open; **Ctrl+C** stops the simulator.

- `./sim.sh --fresh` gives you a new hidden robot (different motor directions, sizes, faults).
- `./sim.sh --scenario B-wiring-day.json --seed 7` picks a practice scenario (list below).
- It remembers where you left off. **Checkpoints** (Start here tab) save and restore your progress.

## 3. Use it

The page has two halves:

- **Left: Driver Station.** Pick an OpMode, press INIT, then START, exactly like the real app. Drive with a
  PS4/PS5 controller (press options + cross to connect) or the keyboard.
- **Right: Workbench.** Tabs: **Start here** (the checklist), Robot, Wiring, Turret, Field, Servos, Slow-mo camera,
  Intake + launchers, Shots, Files, Results, Report card.

Follow **Start here** from the top. It ticks itself as you go:

1. Configure Robot (type the device names, like on a real Driver Station), build 1.
2. Program 1 Check Devices → 2 Motor Directions → 3 Yaw (turret) Tuner → 4 Servo Positions (robot on blocks).
3. Program 5 Drive Tuning → 6 Position Check → 7 Flywheel Tuner (robot on the floor).
4. Programs 8 and 9 Shot Tuners (robot on the field).
5. Enter everything, build 2 of 2. Then TeleOp and the autonomous use every number you measured.
6. Report card: what you got right, and what the hidden robot really was.

Each program saves its results on the robot; the next program uses them right away. **Results** shows them,
**Report card** grades them.

### Test mode (skip the tuning)

**Test mode** (top of the page) fills in the values of a fully built and tuned robot in one click, so you can try
TeleOp or an autonomous straight away. **End test mode** deletes every test value and puts your own work back
exactly as it was.

### Practice scenarios

Pick one from the scenario menu at the top of the page, then press **New robot**.

| Scenario | What it practises |
|---|---|
| A-perfect-robot / A2 | The whole flow, no surprises (A2: configuration already done) |
| B-wiring-day / B2-wrong-names | Swapped motor cables, unplugged encoder, low battery / a teammate's typos |
| C-turret-* | Turret cable too short, sticky bearings, loose gears |
| C-servo-* | A hood that binds, a slow gate |
| C-launcher-* | A reversed or dragging flywheel |
| D-* | Reversed odometry pod, lifted pod, reversed drive wheel |
| E-* | Human errors: crooked turrets at INIT, elements left in the launcher |
| F-random | Two random faults |
| G-* | Flywheel speed wobble, turret with no play, turret snag |

Make your own: copy a file in `sim/scenarios/` and change it (format in `sim/README.md`).

## 4. Autonomous

`TeamCode/.../opmodes/AutoRunner.java` runs an autonomous made of **legs**: a path, what to do while driving
(intake, ramp) and at the end (shoot, wait, park). `SampleAutoPaths.java` is a small example route.

To draw your own routes, use **BIOBUZZ AutoTool** (separate repo). Put both folders side by side and run
`./autotool.sh` from this folder; AutoTool writes `<Name>.java` autos into `TeamCode/.../opmodes/`. Then check every
auto in the simulator on a tuned robot:

```bash
python3 sim/tests/test_autos_tuned.py
```

## 5. Tests

```bash
sh sim/tests/run_all.sh
```

Runs every test (each starts its own simulator in the background; logs in `sim/work/tests/`). One test:
`python3 sim/tests/test_p1_check_devices.py`.

## 6. Using it for your own robot

- **Blank numbers stay blank on purpose.** A value nobody measured is `null` in `team-config.yaml` and `NaN` in the
  code, and the robot refuses that action with a clear message instead of guessing.
- The hidden robot is a plausible goBILDA robot, **not your robot**. Never copy a number from the simulator into a
  real robot's config: measure it with the setup programs on the real robot.
- The robot code assumes this design: mecanum drive, goBILDA Pinpoint odometry, one intake, two turret launchers
  with a hood and a gate each, one turret motor, a ramp servo. A different robot needs code changes (see the guide).

## Troubleshooting

| Message | Fix |
|---|---|
| `Missing ... in the Gradle cache` | Open the folder in Android Studio and build once (step 1) |
| `Need JDK 17 or newer` | Install Java 17+, or set `JAVA_HOME` |
| `Need the Android SDK` | Install it with Android Studio, or set `ANDROID_HOME` |
| Port 8080 busy | `./sim.sh` stops an older simulator on 8080 itself; or `sh sim/run.sh --port 8090` |
| Page shows a build error | Your edit to `TeamCode` does not compile; the message names the file and line |

## License

The FTC SDK parts keep FIRST's license (`LICENSE`, `FTC_SDK_README.md`). Pedro Pathing is used as a library.
