# BIOBUZZ setup simulator

Practice robot for the 19859 setup process. Runs the real TeamCode (from a sandbox copy) on the real FTC SDK 12.0
and Pedro 3.0.1 classes, against a hidden simulated robot. Plan: `docs/BIOBUZZ_SIMULATOR_PLAN.md`.

```bash
./sim.sh               # from the repo root: builds, runs at http://localhost:8080, picks up where you left off
./sim.sh --fresh       # a new hidden robot
sh sim/run.sh --scenario B-wiring-day.json --seed 7    # a scenario and its hidden robot
```

Options: `--scenario <file>` `--seed <n>` (same seed = same hidden robot) `--fresh` (new robot; without it and
without `--scenario`/`--seed` it resumes the last one) `--keep` `--port <n>` `--no-browser` `--pedro-page`.
Checkpoints (Start here tab) are saved when a configuration is activated and at every install; Restore brings
one back.

- Driver Station: left. Workbench (the robot, wiring, files, results, report card): right.
- Pedro's drive tuning is program 5 on the Driver Station (no web page; `--pedro-page` still serves Pedro's own page).
- Gamepad: PS4/PS5 controller, then options + cross. Or Driver Station menu, use keyboard.

## The student loop

The **Start here** tab is the whole path as a checklist that ticks itself, in the real robot's 3 sessions: Configure
Robot and the CAD sheet (build 1), programs 1 to 4 on blocks, 5 to 7 on the floor, 8 and 9 on the field, then one
copy and the final install (build 2), TeleOp, report card. Each line opens the tab it needs.

Between the two builds every setup program (and Pedro's tuners) reads what the earlier ones saved in the robot's
results file (`LiveResults` in TeamCode; the Driver Station shows a yellow line). The simulator gives the robot code
its install time (`PackageInfo.lastUpdateTime`) like the real app, so "saved after this build" works the same.

1. Run programs 1 to 9.
2. **Send results** tab, step 1: **Copy results (adb pull)**. Copies `biobuzz_results.csv` and the shot
   `points`/`shots` files from the robot (`<work>/robot-sdcard/FIRST`) to `<work>/outbox/<time>/`. The tab shows the real
   `adb pull` commands for the same files.
3. Step 2, the programmer step, in one of two modes:
   - **Coach mode:** the simulator does what `docs/BIOBUZZ_PREREQUISITES.md` Part 2 tells the programmer
     (`tools/apply_results.py`): newest line per key; YAML keys get `value` + `origin: measured`; program 2 answers set
     `Intake.REVERSED` and `Launcher.FRONT/BACK_REVERSED`; info-only keys are listed, not entered; a red STOP result
     (`*FlywheelEncoderOpposite = true`, `*FlywheelThrowsOut = false`) enters nothing for that flywheel; the points files
     become `<side>ShotNN{Distance,Speed,Hood,Feed}`. Then `render.sh`, build, install. The tab shows the diff it made.
   - **Real mode:** the tab lists the files to send and what the programmer (Claude) will do in the sandbox
     (`<work>/sandbox`). When they are done, press **Install new build**.
4. Step 3, **Install new build**: compiles the sandbox TeamCode and restarts the robot (statics wiped). If it doesn't
   compile, the robot keeps the old build and the error shows on the Driver Station and in the tab.

Numbers no program file carries are typed in on the same tab: robot mass (E-1). Pedro's tuners save to the results file;
the Pedro form is only for numbers typed from a screenshot. The CAD sheet is entered from the Field tab.

**Report card** tab: every key a part of the hidden robot knows the answer for, with the student's newest value
(results file first, else the sandbox `team-config.yaml`): green = within tolerance, yellow = within 2×, red, grey =
not measured. **Reveal truth** shows the hidden values. Below it, every fault in this robot, the program step that
should catch it, and whether the results show it was caught.

Nothing here ever touches the real `team-config.yaml` or TeamCode: all edits go to the sandbox copy.

## Scenarios (`sim/scenarios/*.json`)

```json
{
  "name": "B. Wiring day",
  "description": "Shown to the student when they pick it.",
  "config": "preset",
  "battery": {"charge": 0.3},
  "faults": [{"type": "swapMotorCables", "a": "CH.0", "b": "CH.1"}]
}
```

- **Hidden-robot values:** any key the physics reads (`battery.charge`, `turret.startDeg`, `intake.inSign`, ...).
  A value is a number, `{"min": a, "max": b}` or `{"value": v, "jitter": j}`; the seed picks it.
- **`config`:** leave it out and the student builds the configuration. `"preset"` = the configuration from the setup
  guide, already active. `{"preset": true, "rename": {"lt": "yaw"}}` = a teammate's configuration with those names typed wrong.
- **`faults`:** wiring (`swapMotorCables` `a`/`b`, `encoderUnplugged` `port`, `encoderFrom` `port`/`part`,
  `encoderReversed` `port`, `swapServoCables` `a`/`b`; ports are `CH.0`-`CH.3`, `EH.0`-`EH.3`, servos `CH.0`-`CH.5`),
  plus labels the report card explains: `lowBattery`, `turretNotForward`, `intakeReversed`. Any fault can carry
  `"set": {"battery.charge": 0.3}` (hidden-robot values it changes), and `"text"` / `"caughtBy"` to describe a new type.
- **`randomFaults`:** `{"count": 2, "pool": [fault, ...]}` picks `count` faults from the pool by seed.

The resolved scenario the robot runs on is written to `<work>/scenario.json`.

To add a scenario: copy one of the files, change `name`, `description` and the faults, then pick it in the scenario
menu (top of the page) and press **New robot**.

| File | What it practises |
|---|---|
| A-perfect-robot | the flow, no surprises |
| A2-perfect-preset-config | the same, configuration already done |
| B-wiring-day | swapped drive motor cables, unplugged flywheel encoder, low battery |
| B2-wrong-names | a teammate's configuration with wrong names |
| C-turret-tight-cable / -sticky / -backlash | program 3: cable short of 90 deg, binding bearings, loose gears |
| C-servo-hood-binds / -slow-gate | program 4: a hood stop far too early (buzz, heat, burnout), one slow gate |
| C-launcher-reversed-flywheel / -dragging-flywheel | programs 2 and 7: power wires swapped, a dry bearing |
| D-odometry-reversed-pod / -pod-lifted, D-drive-reversed-wheel | programs 1, 5, 6: odometry and drive faults |
| E-human-error, E-launcher-leftover-elements | low battery, crooked turrets at INIT, two elements loaded |
| F-turret-index-switch | the optional index switch (`yz`, digital port 0), turrets 7° crooked: program 3 measures the switch, INIT zeroes on it |
| F-random | two random faults from a pool |

## What is real and what is simulated

Real, unchanged: the team's TeamCode (built from the sandbox), the FTC SDK 12.0 OpMode manager, registered OpModes,
HardwareMap, `DcMotorImplEx` / `ServoImplEx` (incl. the goBILDA direction flip), `GoBildaPinpointDriver` API,
`Gamepad` (wire format, edge flags), `TelemetryImpl`, the stuck-OpMode rules, Pedro 3.0.1 (Follower, Foresight,
`Mecanum`, `PinpointLocalizer`, and Pedro's own tuner code run by program 5 on the Driver Station), `render.sh` / `emit_tuning`.

Simulated: the REV hubs (Lynx velocity PIDF whose output is power, so battery sag matters; 50 ms encoder velocity
window; bulk-cached reads; unchanged commands not resent; ~1.3 / 2.5 ms per command; 8 digital ports), the Pinpoint
firmware (pod wheels a few tenths of a percent off their catalog size, a gyro scale a little off 1), motors, battery,
mecanum floor physics, turret (gear play between the motor encoder and each turret, optional index switch), servos
(stopping at the edge of their deadband, so the side they came from matters) and linkages, elements and shots, the
Driver Station app, and the network between them.

Known limits:
- Real time only; JVM thread timing is not Control Hub timing.
- An OpMode stuck in a pure CPU loop (no hardware calls) restarts the robot like the real app, but its thread keeps
  running in the simulator until it is closed (one Java process cannot kill a thread).
- Pedro's own web page (only with `--pedro-page`) always uses ports 10158 and 12649: only one simulator at a time can show it.
- The numbers are a plausible goBILDA robot, not this robot. Never copy a simulator number into the real config.

## Tests

```bash
sh sim/tests/run_all.sh                          # every test, with a summary; logs in sim/work/tests/
python3 sim/tests/test_p1_check_devices.py       # one test (each starts its own headless simulator)
```

Endpoints for scripts (`sim/tools/simctl.py`): `loop/status`, `loop/pull`, `loop/plan`, `loop/coach`,
`loop/report?reveal=1`, plus `build` and `sandbox/set`.
