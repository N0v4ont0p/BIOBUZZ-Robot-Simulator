Hardware partly superseded 2026-10-17 (no hood, a position servo per turret, a string ramp on 2 continuous servos, a 2-servo back gate, 2 intake motors): see docs/HARDWARE_UPDATE_PLAN.md

# BIOBUZZ Setup Simulator: Plan

Status: BUILT (branch biobuzz-sim). How to run it: `sim/README.md`. What changed from this plan while building: section 13.

## 0. What this is

A practice robot on the laptop. You run **our real setup programs 1-9** on it, exactly as you will on the
real robot: configure the robot, pick the program on a Driver Station screen, INIT, START, read the guided
screens, press PS controller buttons, do the "by hand" jobs (turn a turret, push the robot, film the gates),
copy the results file, send it, get a new build, and the next program stops saying NOT READY.

The robot inside the simulator has hidden "true" numbers (how far the turret can turn, how fast the flywheel
really spins, where the pods really are). The programs have to measure them. At the end, the simulator shows how close
your measured numbers came to the truth, so you learn what a good run looks like before the real robot exists.

**In scope**
- Setup programs 1-9, the Pedro tuning web page (program 4), and the results file loop (send, enter, render, build, install).
- Driver Station flow: Configure Robot, OpMode list, INIT / START / STOP, telemetry, PS controller, auto-chaining.
- Faults to practise on: wrong name, reversed motor, loose encoder, flat battery, reversed pod, and more.
- Basic TeleOp / Auto drive on an empty field, only to confirm the tuned numbers work.

**Out of scope**
- Full game simulation: no other robots, no scoring, no match strategy, no HIVE tipping.
- Field layout beyond what the shot tuner needs (one CELL target and a floor).

## 1. A session, from the student's side

1. Run `sim/run.sh`. The browser opens two tabs: **Driver Station** and **Workbench** (the robot on a box or field).
2. A scenario starts, e.g. "New robot, 2 wiring faults". The Robot Controller boots and shows "No configuration".
3. On the Driver Station, open **Configure Robot**. Add the hubs, then type each device name on its port (`lf`, `lb`, ... `bg`).
   Physically, the sim robot's parts sit on fixed ports. If you put `lf` on the wrong port, program 1 or 2 shows it.
4. Plug in the PS controller and press **options + cross** to make it gamepad 1, exactly like the real Driver Station.
5. Choose **1 Check Devices**, INIT, read the screen, START. The program asks you to turn each part by hand.
   On the Workbench, drag the wheel, turret or flywheel and the encoder counts, so the line goes OK.
6. Finish it and press STOP. **2 Motor Directions** loads by itself (the real `Guide.loadNextOnStop` code), and so on.
7. When the program says "Send to the programmer", press **Workbench > Copy results (adb pull)**.
   This copies `biobuzz_results.csv` out of the sim robot's `/sdcard/FIRST`.
8. Choose who does the programmer step:
   - **Coach mode:** the sim does it, with the same mapping the prerequisites doc gives the programmer.
   - **Real mode:** you send the file to the programmer (Claude) and they do the real steps on a sandbox copy.
   Either way: `team-config.yaml` is updated, `render.sh` runs, the code compiles, and the robot "reinstalls"
   (the RC restarts, so static state is wiped, like a real install).
9. The next program's INIT stops saying NOT READY. Repeat until program 9 loads **BIOBUZZ TeleOp**.
10. **Report card:** each recorded value vs the hidden truth, with green / yellow / red marks and a tip for each red one.

## 2. Architecture

```
 Browser                          Desktop JVM (one process)
 ┌─────────────────────┐   HTTP   ┌──────────────────────────────────────────────────────────┐
 │ Driver Station page │◀──SSE───▶│ Sim server (JDK HttpServer, no extra deps)              │
 │  OpMode list, INIT, │  POST    │  ├─ DS service: config, opmode select, init/start/stop  │
 │  telemetry, gamepad │  gamepad │  ├─ OpMode manager (sim replacement, same class name)   │
 │  (Gamepad API, PS)  │          │  │    runs REAL TeamCode OpModes on REAL SDK classes     │
 ├─────────────────────┤          │  ├─ REAL SDK: HardwareMap, DcMotorImplEx, ServoImpl,    │
 │ Workbench page      │          │  │    GoBildaPinpointDriver, Gamepad, TelemetryImpl      │
 │  robot views, hand  │          │  ├─ Fake hub controllers (below the SDK classes)        │
 │  tools, slo-mo cam  │          │  ├─ Physics (1 kHz): drive, turret, flywheels, servos,  │
 ├─────────────────────┤          │  │    elements, shots                                    │
 │ Pedro tuning page   │◀────────▶│  ├─ REAL Pedro tuning WebServer + WebSocketServer        │
 │  (REAL Pedro assets)│  :10158  │  │    serving Pedro's own index.html                     │
 └─────────────────────┘          │  ├─ /sdcard/FIRST  -> sim/robot-sdcard/                  │
                                  │  └─ Truth model (hidden robot), faults, grader           │
                                  └──────────────────────────────────────────────────────────┘
```

### 2.0 Already proven (feasibility probe, 2026-10-05)
Compiled all 98 TeamCode classes **unchanged** with plain `javac` against the real SDK 12.0.0 and Pedro 3.0.1 jars from
the Gradle cache. Ran a real `LinearOpMode` through init, waitForStart, loop and stop on the SDK's own opmode thread,
with telemetry captured. Real `HardwareMap`, `DcMotorImplEx`, `ServoImpl` and `GoBildaPinpointDriver` worked.
Pedro `Mecanum` + `PinpointLocalizer`, built through our own `Constants.drivetrain()`, drove and gave a pose.
The probe is in the session scratchpad (temporary); phase 1 moves it into `sim/`.

### 2.1 Real vs fake (rule: fake only what is physical)

| Layer | Real (reused unchanged) | Fake (sim-built) |
|---|---|---|
| Our code | every file in `TeamCode/src/main/java`, plus `config/Tuned.java` and `pedro/Constants.java` rendered by our real `render.sh` | none |
| FTC SDK | `RobotCore`, `Hardware`, `FtcCommon` 12.0.0 jars: `LinearOpMode`, `OpMode`, `HardwareMap`, `DcMotorImplEx`, `ServoImpl`, `GoBildaPinpointDriver`, `Gamepad` (incl. `fromByteArray`, the Driver Station wire format), `TelemetryImpl`, `ElapsedTime`, `ReadXMLFileHandler` (config XML) | `OpModeManagerImpl` replacement (same name, earlier on classpath); ~10 android shims |
| Pedro | `core`, `revhub` (`Mecanum`, `PinpointLocalizer`), `tuning` (`Procedure`, `TuningSession`, `WebServer`, `WebSocketServer`, and the bundled web page `assets/pedro/index.html`) | an `AssetManager` shim that serves the bundled assets from disk |
| Hardware | none | `DcMotorControllerEx` / `ServoControllerEx` per hub, the Pinpoint I2C register map, `VoltageSensor` |
| Driver Station | the button layout, flow and gamepad byte format copied from the real DS | the web page itself |
| Codegen | `team-config.yaml`, `tools/codegen/render.sh`, `emit_tuning.py`, `config_lint` | none (runs on a sandbox copy) |

### 2.2 Sim build (`sim/`, separate from the robot app)
- Use a Gradle `java` project or a plain script; nothing in `TeamCode/` changes.
- The build:
  1. Extracts `classes.jar` from the SDK and Pedro AARs in `~/.gradle/caches` (pinned to SDK 12.0.0 and Pedro 3.0.1 / tuning 1.0.1).
  2. Generates the `R` classes from each AAR's `R.txt` (the probe's `genR.py`).
  3. Compiles `../TeamCode/src/main/java` against them.
  4. Puts the shims first on the classpath.
- Runs on JDK 21 (installed) and uses an empty `libRobotCore` (the only native code is USB, camera and clock; none is used).
- Includes a smoke test that runs every OpMode's INIT once and fails loudly on any android "Stub!" call.

### 2.3 Android shims (proven list, grow as needed)
`android.util.Log`, `LongSparseArray`, `android.os.SystemClock`, `Process`, `Environment` (points `/sdcard` at
`sim/robot-sdcard/`), `SystemProperties`, `Context` / `ContextWrapper` / `Application`, `AssetManager`
(for the Pedro page). A fake `Application` goes into `AppUtil.application`, and `ConfigurationTypeManager` gets the
class scan the real app does at boot. Without that scan, motor types crash.

### 2.4 OpMode manager (sim replacement of `OpModeManagerImpl`)
It must behave like the real one, because our `Guide` and Pedro tuning both use it:
- `getOpModeManagerOfActivity`, `registerListener` / `unregisterListener` (keep the SDK's weak-reference behaviour,
  so the `Guide.pendingLoad` fix is exercised for real), `initOpMode(String)`, `startActiveOpMode`, `requestOpModeStop(OpMode)`.
- Calls `onOpModePreInit` / `onOpModePreStart` / `onOpModePostStop` at the same moments as the real manager.
- Finds OpModes by scanning `@TeleOp` / `@Autonomous`, and registers Pedro's `"Pedro Tuning"` opmode as Pedro's hook would.
- Calls the package-private `internalPreInit / internalInit / internalOnEventLoopIteration / internalStart / internalStop`
  and `newGamepadDataAvailable`, from a runner class in `com.qualcomm.robotcore.eventloop.opmode` (proven in the probe).
- **Stop timeout like the real RC:** an OpMode that doesn't stop within the real limit gets "stuck in stop()" and the RC restarts.
- **Restart Robot** wipes static state: `MatchState`, `Guide.pendingLoad`, the `Tuned` overrides set by `YawTuner`.
  Statics are wiped by building a new classloader.

### 2.5 Fake hardware (below the SDK classes, so the SDK code stays real)
- **Motor controller fake (one per hub, 4 ports):**
  - **Modes:** `RUN_WITHOUT_ENCODER` maps power to voltage. `RUN_USING_ENCODER` runs the hub's velocity PIDF.
    The F/P/I/D scaling matches the Lynx firmware, with the hub's ~50 ms velocity window.
    The flywheel tuner's `F = 32767/top` must come out right.
  - `STOP_AND_RESET_ENCODER`, `ZeroPowerBehavior` BRAKE vs FLOAT (back-EMF short vs free spin), direction flip on both power and encoder.
  - **Current:** reported from the motor model.
  - **Encoder:** quadrature counts from the mechanism's true shaft angle × true counts per rev × true encoder sign.
    Velocity is measured over the same window the hub uses.
- **Servo controller fake:** commanded position goes through the PWM range, then the servo's true speed (deg/s), then
  the true linkage stops. Driving into a stop makes it **buzz**: a sound, a shake icon, and a current/heat bar that rises.
  Hold it there too long and the servo "burns out" (the scenario fails with an explanation).
- **Pinpoint fake:** emulates the I2C register map, so the real `GoBildaPinpointDriver` decodes it. A `SimPinpoint`
  subclass overrides `doInitialize()`. The fake models:
  - device version ≥ 3 with CRC (our `Constants` switches CRC on);
  - a 1500 Hz update, status READY / CALIBRATING / FAULT_*;
  - pod ticks from the true pod positions and directions, IMU yaw with small drift, `setOffsets`, `resetPosAndIMU`.
- **Voltage sensor:** the battery model (section 3.6). Registered in `hardwareMap.voltageSensor`, because `Yaw` and program 1 iterate it.
- **LynxModule:** `getAll(LynxModule.class)` returns an empty list (bulk caching becomes a no-op). Noted as a known difference.
- **Hardware map:** built from the student's **Configure Robot** XML using the SDK's own `ReadXMLFileHandler`.
  The physical "which part sits on which port" comes from the truth model. A wrong name gives the real SDK error.

### 2.6 Time
Everything runs in real time. Our code and Pedro read `System.nanoTime()`, so no clock tricks are needed. Physics
runs on its own 1 kHz thread; device reads come from the latest physics state. Faster-than-real-time replay (for
automated tests) is a later option: rewrite `nanoTime` with ASM, which is already in the Gradle cache.

## 3. Hidden robot ("truth model", `sim/robots/*.yaml`)

Each scenario has a truth file. It is never shown until the report card. Defaults below are plausible goBILDA values;
each scenario randomises them within a range so nobody memorises answers.

| Part | True values the programs must discover |
|---|---|
| Wiring | which part is on which hub port; encoder cable present / swapped / missing |
| Drive | 4 wheel directions; mecanum geometry; motor free speed and torque; robot mass; floor friction |
| Pinpoint | pod positions (become `xPodOffset` / `yPodOffset`); pod directions; ticks/mm (4-bar 19.89); IMU drift |
| Geometry (CAD sheet) | `centerToFront/Back/Left/Right`, `front/backPivotX/Y` (shown on a "CAD" card for Stage 1 practice) |
| Turret (one motor, both turrets) | counts/rev × gear ratio per turret, so `yawTicksPerDegFront/Back` with true signs; hard stops; cable-tight angles; static/kinetic friction per direction (kS); free rate per volt (kV); time constant; inertia; backlash (E-6 play) |
| Flywheels (×2) | direction; encoder sign; free speed at 12 V (~2800 ticks/s for 6000 rpm with 28 cpr); inertia; motor time constant; speed noise; energy taken by each element type |
| Hoods (×2) | servo position to angle map; low/high stops; servo speed |
| Ramp | stowed / deployed stops; whether "deployed" fits 18×24 and reaches the FLOWER opening |
| Gates (×2) | closed range that holds clear of the flywheel; open range that passes freely; servo speed (one gate slower) |
| Intake | direction; capacity 4; passive sort (POLLEN to the back lane, NECTAR to the front) |
| Shot | exit speed = efficiency × wheel surface speed after the dip; launch angle from the hood map; release height; small random spread |
| Battery | open-circuit voltage by charge, internal resistance (sag under load), drains over the session |

## 3.6 Physics (simple, but each one captures what the program measures)

- **DC motor:** `V = I·R + kE·ω`, `τ = kT·I`, then gearbox, friction (static > kinetic, per direction), and inertia.
  This one model gives kS, kV and the time constant on the turret, and spin-up, dip and recovery on the flywheels.
- **Battery:** `V = Voc − I_total·R_int`. Program 1's 12.5 V check and the yaw tuner's `yawTuningVoltage` see real sag.
- **Mecanum chassis:** wheel speeds to body twist (standard inverse), with traction limits and mass/inertia.
  Driving gives Pedro's Foresight tuner the velocities and decelerations it measures. Hand pushes on the Workbench
  apply a force, so the pods count when you "push it forward a foot".
- **Turret:** motor model, gear ratio, backlash, hard stops (stiff spring), and cable tension past the cable-tight angle.
  FLOAT mode lets you drag it by hand.
- **Servo:** first-order slew at the true speed, clamped at the true stops. Commanding past a stop sets the "buzz" state.
- **Element flow:**
  - an element at the intake mouth moves in only when the roller spins the right way;
  - it sorts into the front or back lane and waits at a closed gate;
  - an open gate plus feed power push it into the wheel, which takes the energy hit (dip).
- **Shot:** 2-D ballistic flight in the shot plane, plus yaw error. The flight uses exit speed and hood angle from truth,
  then air drag (one coefficient). "Made" means it passes through the CELL opening (20 × 14 in). The student still presses cross or circle;
  the grader compares the button with what really happened, which teaches honest marking.

## 4. Driver Station page (copy of the real one)

- **Top bar:** robot name, RC status (Robot Controller / Running / Stopped / Error), Driver Station and **Robot battery**
  voltages (from the battery model), ping, a gamepad 1 / gamepad 2 badge (lights up on input).
- **Menu:** Configure Robot, Restart Robot, Program & Manage (opens the Pedro page at `:10158`, like the real Control Hub address).
- **Configure Robot:** hubs, then ports, then device type, then name. Save. The configuration is active after restart.
  Same device types as the real menu (goBILDA 5203, REV servo, goBILDA Pinpoint, ...).
- **OpMode selection:** TeleOp and Autonomous lists, sorted by group like the real DS ("19859 Setup" programs show as `1 ...` to `9 ...`).
  Then INIT, START (play triangle) and STOP (square) in the same places.
  - Autonomous gets the 30 s timer and auto-loads its `preselectTeleOp` at the end.
  - Program auto-chaining appears exactly as on the robot: the next program's name shows and it is INIT-ed by itself.
- **Telemetry pane:** renders the same HTML subset the real DS does (`<big>`, `<b>`, `<font color>`) and nothing more,
  so our `Guide` screens look the same as on the robot.
- **Gamepad:**
  - The browser Gamepad API reads the PS4 / PS5 controller (USB or Bluetooth on the laptop).
  - The page packs the state into the SDK's own `Gamepad.toByteArray` format. The RC side calls the real `fromByteArray`,
    so `crossWasPressed()` and the other edge flags come from real SDK code.
  - **options + cross / options + circle** assigns user 1 / user 2, like the real DS.
  - Rumble and LED: the sim drains the real `Gamepad.rumbleQueue` / `ledQueue` and plays them on the controller
    (`vibrationActuator`). The LED colour shows on screen, because browsers can't set it.
  - Keyboard fallback, with a visible PS button map, for laptops without a controller.
- **Errors look real:** a missing device name shows the same red "Unable to find a hardware device with name ..." banner
  the SDK produces.

## 5. Workbench page (the robot and your hands)

2-D SVG views, no 3-D engine. Every by-hand job the setup doc asks for has one gesture here.

| View | Used by | Shows | Hand tools |
|---|---|---|---|
| Robot on a box (top) | 1, 2, 3, 6, 7 | wheels, intake, both turrets with barrel lines, robot edge lines, ruler overlay | drag any wheel, roller, turret or flywheel to turn it; "put on box / on floor" |
| Turret close-up | 3 | barrel vs edge, angle hidden (you judge by eye); cable tension colour; hard-stop contact | drag, with resistance at the stops; "wiggle" for play |
| Flywheel side view | 2, 7 | rotation arrow, element path at the contact point; RPM sound pitch; speed trace graph | place ONE element in a lane |
| Hood / ramp / gate side views | 6 | linkage moving to the command; stop contact; buzz shake and heat bar; element-vs-flywheel clearance; 18 in cube and 18×24 outlines; FLOWER opening | place element against the gate |
| Slow-motion camera | 6 (gates), G-6 (flight) | 240 fps recording of the last motion; frame scrubber and frame counter; both gates in frame | record, step frame by frame |
| Field (top) | 4, 5, 8, 9, TeleOp, Auto | floor tape, walls, GARDEN corners, one CELL target, robot pose truth (hidden in real mode) | drag to push robot; "place in start corner" (snap with a small random error unless you line it up); retrieve balls |
| Shot camera | 8, 9 | the ball's flight to the CELL from the side | none (you judge made or missed) |
| Tools | all | scale (E-1), tape measure (E-12), battery swap and charge, "copy results (adb pull)", "install new build" | none |

**Coach overlay** (toggle): shows what is happening inside, e.g. "turret is 3.2° off parallel" or "gate rubs the flywheel by
0.1 in". It is off in "real mode".

## 6. Program by program: what the sim must do

| # | Program | Physical side the sim provides | Truth checked on the report card |
|---|---|---|---|
| 0 | Configure Robot (D-1) | ports, types, names | every name on the right port |
| 1 | Check Devices | name check; battery; hand-turn each of 8 parts; push and slide for the pods | faults found vs faults injected |
| 2 | Motor Directions | roller and flywheel surface direction arrows, element pulled in or thrown out | `intakeReversed`, `*FlywheelReversed`, `*EncoderOpposite` |
| 3 | Yaw Tuner | Part A by eye at the robot edges and stops; Part B/C motor model with friction, sag and backlash | ticks/deg (both signs), limits inside the stops, kS, kV, kP, settle |
| 4 | Drive Tuning (Pedro page) | wheels on blocks, push and spin gestures, 10 ft clear floor, Foresight runs | wheel and pod directions, pod offsets, Foresight values vs a reference tune of the truth chassis |
| 5 | Position Check | turn to heading 90, start corner placement, drive a loop and return | loop error comes from the real Pinpoint fake + offsets |
| 6 | Servo Positions | hood, ramp and gate views with buzz; slow-mo gates | each endpoint inside the safe band; `gateTravelS` vs the true slower gate |
| 7 | Flywheel Tuner | spin-up, noise, one-element dip and recovery, idle sound | F vs true top speed, ready band vs true noise |
| 8, 9 | Shot Tuners | field, distance, element loading, shot flight, made/missed camera | the table vs the true makeable speed/hood band at each distance; honest marking |
| — | TeleOp / Auto | empty field drive, aim and fire on the CELL | "it drives and hits", nothing scored |

## 7. Results loop (send, enter, render, build, install)

1. Programs write to `sim/robot-sdcard/FIRST/` (the real `Guide.record` code, redirected by the `Environment` shim).
2. **Copy results** = the `adb pull` step: the file goes to `sim/outbox/`.
3. The programmer step runs on a **sandbox copy** (a git worktree `sim/sandbox/`), **never on the real `team-config.yaml`.**
   Fake robot numbers must never reach the real robot code.
   - **Coach mode:** `sim/apply_results.py` writes each key into the sandbox `team-config.yaml` with `origin: measured`,
     `confirmed: true`, following the mapping table in the prerequisites doc.
   - **Real mode:** you send it to Claude, who does it by hand in the sandbox, like the real workflow.
4. The sim runs the real `tools/codegen/render.sh`, `emit_tuning verify` and `config_lint` in the sandbox, then recompiles
   the sim classes.
5. **Install new build** restarts the RC: new classloader, statics wiped.
6. Rehearses the hand edits the doc requires (`Intake.REVERSED`, `Launcher.*_REVERSED`, drive and pod directions in
   `Constants.java.tpl`). Coach mode shows the diff.

## 8. Scenarios and faults

| Scenario | Purpose |
|---|---|
| A. Perfect robot | learn the flow, no surprises |
| B. Wiring day | wrong name, swapped ports, missing encoder cable, reversed flywheel, low battery |
| C. Mechanical day | tight turret cable (short travel), sticky turret (high kS), backlash, hood binding early, one slow gate |
| D. Odometry day | reversed pod, pod not touching (stops counting), wrong pod offsets left from a previous build |
| E. Human error | misjudged "straight" by 5°, turret not forward at INIT, two balls loaded in program 7, wrong made/missed marks, forgot to reinstall |
| F. Random | any mix; the report card says what was wrong and which program should have caught it |

Each fault lists the program and step that should catch it (from the setup doc's "if not" tables). If no program catches
it, the report card says that too. Those are gaps in our setup programs, worth fixing.

## 9. Testing the simulator (and our setup programs at the same time)

- **Headless "perfect student" scripts:** each program, driven by scripted gamepad bytes and hand gestures, must finish,
  and every recorded value must land within tolerance of truth.
  These double as **regression tests for our real setup programs**: break a tuner and the script fails, before any robot exists.
- **Fault scripts:** each scenario fault must be caught at the expected program and step.
- **Smoke test:** INIT every OpMode with a full config, then with an empty config; no android "Stub!" errors.
- **Physics sanity:** the motor model at 12 V hits the datasheet free speed and stall current within 5%; servo speed
  matches its datasheet.

## 10. Build phases

| Phase | Builds | Done when |
|---|---|---|
| P1 Core | `sim/` build from the probe; shims; OpMode manager replacement; motor and servo controller fakes (no physics, parts turn only by hand); DS page (config, list, INIT/START/STOP, telemetry, gamepad bytes); robot-on-box Workbench | programs 1 and 2 complete with a PS controller; auto-chain 1 → 2 → 3 works; a wrong name shows the SDK error |
| P2 Turret | DC motor and battery models; turret with stops, cable and backlash; turret close-up view | program 3 completes; perfect-student kS/kV/kP within 10% of truth |
| P3 Drive | mecanum chassis; Pinpoint register fake; field view; push gestures; Pedro tuning page on `:10158` with the AssetManager shim | Pedro Mecanum, Pinpoint and Foresight tuners and Tests run in the browser; program 5 passes with good offsets and fails with bad ones |
| P4 Servos | servo model with buzz and heat; hood, ramp and gate views; slow-mo camera | program 6 completes; `gateTravelS` within 1 frame of truth |
| P5 Flywheels | hub velocity PIDF; flywheel model with dip; element flow (intake, sort, gates); sound | program 7 completes; F within 2% of truth |
| P6 Shots | ballistic model; CELL target; shot camera; start-corner placement | programs 8 and 9 complete; the TeleOp aim, ready and fire chain hits from the table |
| P7 Loop | outbox, sandbox worktree, `apply_results.py`, render, rebuild, restart; report card; scenarios A-F; coach overlay | a full A-to-Z run from an all-NaN config to a working TeleOp, in both coach and real mode |

P1 is the biggest risk and goes first. Each later phase adds one subsystem and its program.

## 11. Risks

| Risk | Plan |
|---|---|
| SDK internals (package-private lifecycle, `AppUtil.application`, `ConfigurationTypeManager` scan) change in a later SDK | pin 12.0.0; smoke test fails loudly; update with the robot's SDK |
| Un-shimmed android call throws "Stub!" | loud and local: add one shim each time |
| Lynx velocity PIDF details (units, window) differ from the real hub | match published REV behaviour; calibrate against the real flywheel's first program 7 run |
| Pinpoint register map details | follow the real driver's decode code (it is in `Hardware.jar`); if it fails, fall back to overriding the driver's read methods |
| Pedro tuning `Hooks` needs `FtcEventLoop` | start `WebServer` / `WebSocketServer` directly and set `Hooks.opModeManager` by reflection |
| Sim feels "right" but the real robot differs | the sim teaches the process, not the numbers; never copy a sim number into the real config (the sandbox rule) |
| Browser gamepad mapping differs between PS4 and PS5, and Bluetooth vs USB | a mapping test screen in P1; keyboard fallback |

## 12. Problems in our current code found while planning (fix separately)

1. `ShotTuner.java:287`: the finish screen says send `biobuzz_results.csv`, but programs 8/9 write only the points/shots CSVs.
2. The team plan said 3 makes in a row; the code uses 4 (`ShotTuner.java:36`).
3. The prerequisites doc's info-key list omits `yawSpeedPoints`, `devicesFound` and `batteryVolts`, and calls the column `time` (it is `time_ms`).
4. `render.sh` prints "rendered" even when `emit_tuning verify` fails.
5. Programs 3, 7, 8, 9, TeleOp and Auto crash on a missing device name (no friendly NOT READY like 1, 2 and 6 have).
6. The YAML drive and pod direction keys aren't used; the directions are hard-coded in `Constants.java.tpl:52-55,63-64`.
7. A wrong answer in program 2 (flywheel direction) isn't caught by program 7, because REVERSE flips the encoder too. Only shots reveal it.
8. `*EncoderOpposite` is recorded, but there's no code setting to act on it.

## 13. As built (differences from the plan)

- **OpMode manager:** the plan said a replacement `OpModeManagerImpl`; the build uses the SDK's real one (and its
  real `RegisteredOpModes`, `EventLoopManager` with injected fields). Only two pieces are the simulator's: the event
  loop (what `FtcEventLoop` does) and the stuck-OpMode monitor (the real one ends in `System.exit`; the simulator's
  restarts the simulated Robot Controller with the same messages and timing). A shadow `NetworkConnectionHandler`
  carries everything the Robot Controller sends to the web Driver Station.
- **Pedro tuning (program 4):** Pedro's own AutoTune page and websocket (ports 10158 / 12649) run unchanged against
  the simulated robot; only Pedro's app hook and Sinister's OpMode registry are shadowed.
- **Hub timing:** commands cost ~1.3 ms (Control Hub) / ~2.5 ms (Expansion Hub), unchanged commands are not resent
  (LastKnown), encoder reads come from one bulk read per loop (AUTO). Button edges are locked against the SDK's copy-in,
  so a fast loop never loses a press.
- **Tests:** every setup program 1-9 has a scripted "perfect student" test, plus fault tests (`sim/tests/run_all.sh`).
- **Robot code fixed because the simulator showed problems** (all in TeamCode + docs, see git log):
  - hood range with a reversed hood servo;
  - program 1 encoders checked one part at a time (crossed encoder cables showed OK before);
  - program 3 speed runs from the whole window (single-loop speeds were rejected), kS after taking up gear play,
    90 deg travel required and checked, zero reminder;
  - program 5 loop ends turned a quarter turn (wrong pod offsets cancelled out before);
  - program 7 top speed waits for the speed to settle, ready band measured after settling;
  - programs 8/9 points file sessions, check points, hood buttons with a reversed hood;
  - TeleOp hood buttons with a reversed hood; zero reminders on every screen that zeroes the turrets.
- **Setup in 3 sessions, 2 builds (later):** programs renumbered (4 Servo Positions, 5 Drive Tuning, 6 Position
  Check). Setup programs and Pedro's tuners read earlier results from the robot's results file (TeamCode
  `LiveResults`); the simulator's `PackageManager` gives the robot code its install time like the real app.
  Start here follows the sessions; one copy and one install at the end.
- **Accuracy additions (later), each with a simulator model and a test:** turret gear play measured in program 3
  and compensated in `Yaw`; optional turret index switch (simulated digital ports, `F-turret-index-switch`);
  pod scale (96 in push) and gyro scale (10 turns) in program 6 (hidden pod and gyro scale errors in the Pinpoint);
  flywheel gains rescaled for battery voltage; 0.15 s steady before a shot; hood approached from below (simulated
  servos stop at the edge of their deadband); TeleOp faces the CELL while firing and waits to stop until moving
  shots are measured; programs 8/9 drive themselves to each spot.
- **Program 5 on the Driver Station (later):** Pedro's own Mecanum, Pinpoint, Foresight and Tests code runs inside
  setup program 5 through small stand-ins for Pedro's tuning framework (`pedro/procedures`: Procedure, Inputs,
  TuningOpMode, Display, Screen). Pedro's tuning library and web page are out of the robot app; the simulator serves
  the page only with `--pedro-page`. `test_p5_drive_tuning` does program 5 with gamepad presses only.
