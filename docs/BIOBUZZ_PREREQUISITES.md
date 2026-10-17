# BIOBUZZ 19859 — how to finish the robot code (full walkthrough)

**Who does what.** You (the team) measure things; the setup programs save the results on the robot. The
programmer (Claude) puts them into the code and sends back a build: once after the CAD sheet, once after
program 9. You never edit code.

**The robot this guide is for (team, 2026-10-17):** two turrets, each turned by its own yaw servo; no hood (the
launch angle is fixed by the build); two intake motors (main intake + POLLEN feeder); a ramp on two continuous
rotation servos that wind strings; a front gate (1 servo) and a back gate (2 servos). The older yaw motor, turret
homing, index switch and hood steps are gone. What changed and why: `docs/HARDWARE_UPDATE_PLAN.md`.

**Contents**
- Part 0 — the idea behind the whole process
- Part 1 — the walkthrough: Stage 1 CAD → Stage 2 wiring → Stage 3 the 9 setup programs → check on the real
  robot → Stage 4 field → every match → every event
- Parts 2–6 — reference (programmer notes, units, design, open decisions, DECODE numbers)

---

# Part 0. The idea behind the whole process

**The code is already written. Every physical number the CAD does not give is blank.** Mass, turret servo
positions, motor directions, gate positions, ramp times, flywheel gains, the shot table: each one is stored as
"not measured yet" (`NaN`). The code checks this everywhere. A part whose numbers are blank stays switched off,
and the Driver Station says which step fills it in (for example "gate positions/travel not set (E-8)").

**Why it is done this way.** A guessed number compiles, passes every check, and then drives the robot
wrong. Blank-until-measured makes "not tuned yet" visible instead of dangerous.

**How numbers get into the code (two builds for the whole setup):**

1. **You measure**: from the CAD (Stage 1) or with a guided program on the robot (Stage 3). The programs
   write everything into one file on the robot, `/sdcard/FIRST/biobuzz_results.csv`, and so do Pedro's
   tuners. Programs 8 and 9 also write their own points file and shots file.
2. **The next program uses it straight away.** Every setup program reads the results file when it starts
   and uses any number the code does not have yet. The Driver Station shows it in yellow: "Using N numbers
   from the robot's results file (not in the code yet)". So there is **no build between programs**.
3. **At the end you send everything at once**: one `adb pull` of the results file and both points files,
   over a **USB cable** from the laptop to the Control Hub (the laptop stays on normal Wi-Fi).
4. **The programmer enters it**: each value goes into `team-config.yaml` marked "measured";
   `tools/codegen/render.sh` regenerates the code; a check confirms no number was typed by hand; the
   programmer builds.
5. **You install the final build**: Android Studio, Run ▶ (also over the USB cable). The yellow line is gone.

So the whole setup is **build 1** (the CAD sheet, Stage 1) and **build 2** (everything measured, after
program 9). TeleOp and the autos never read the results file: they only run on numbers in the code, so a
match never depends on a file.

**Order matters.** Each program needs numbers from the ones before it (in the code or in the results file).
You can't skip ahead by accident: a program whose inputs are missing shows a red **NOT READY** line in INIT
and does nothing.

**Backup: photos.** Every program also ends with a **RESULTS P<n>** block on the Driver Station (`key = value`
lines, ending with `END P<n>`). A photo of it is enough if the file is ever lost. Each result also stays in
the results file (`program,key,value,time_ms`); a redone step adds a newer line, and the newest line wins.

**Units and directions used everywhere** (more in Part 3):
- inches, degrees, seconds; servo positions 0–1; flywheel speed in ticks per second.
- **Robot:** centre = middle of the 4 wheel contact points. Front = intake / NECTAR side. +x forward, +y
  left, counter-clockwise (seen from above) is +.
- **Field:** (0, 0) = corner where the red alliance wall meets the audience wall. +x toward the blue wall,
  +y toward the far wall, heading 0 = facing +x. Red = blue turned 180° around the field centre (72, 72).
  Never mirror.

---

# Part 1. The walkthrough

## Stage 1 — before the robot exists (CAD + field drawing)

**Why:** geometry is exact in CAD and slow to measure on a built robot. These numbers feed the aiming and
the position tracking.

### C. The CAD sheet

Most of this sheet is already done: the programmer read it from the Fusion CAD "19859 BIOBUZZ bot v1" on
2026-10-17 (`cad/CAD_INTAKE_V1.md`). **Entered** = in `team-config.yaml` with `origin: measured`, source CAD v1.
**Open** = still needed from the team or a setup program.

| # | What | Value | Step | Status |
|---|---|---|---|---|
| 1 | Centre → front edge (in) | 10.22 | C-2 | entered |
| 2 | Centre → back edge (in) | 7.50 | C-2 | entered |
| 3 | Centre → left edge (in) | 7.17 | C-2 | entered |
| 4 | Centre → right edge (in) | 7.16 | C-2 | entered |
| 5 | Front turret pivot x, y (in) | 4.32, 0.00 | C-3 | entered |
| 6 | Back turret pivot x, y (in) | −4.63, 0.00 | C-3 | entered |
| 7 | Front turret gear: turret degrees per servo degree | 1.137 (servo 60t → 20t, 36t → 95t ring) | C-5 | entered (cross-check of program 3) |
| 8 | Back turret gear: turret degrees per servo degree | 1.103 (servo 60t → 20t, 25t → 68t ring) | C-5 | entered (cross-check of program 3) |
| 9 | Turret yaw servo part | not chosen yet (the code works for any position servo) | C-5 | open (team) |
| 10 | Turret travel each way (cable limits) | not in the CAD | C-6 | open: program 3 measures it |
| 11 | Launch angle, fixed (no hood) | 59° both (58.7° POLLEN, 58.5° NECTAR) | C-8 | entered; check with a side video |
| 12 | Release height, front / back (element centre above the floor, in) | 12.2 / 12.0 | C-4 | entered |
| 13 | Flywheel: wheel, belt, motor | 72 mm (2.83 in) Rhino wheel, 2:1 GT2 belt (20T motor, 40T wheel), 6000 rpm motor | C-9 | entered |
| 14 | Drive wheel diameter | 104 mm goBILDA mecanum | — | entered |
| 15 | Intake reach past the front plates / mouth width (in) | 0.1 / 9.2 | — | entered (AutoTool) |
| 16 | Odometry pods (in from the centre) | forward (X) pod 2.84 left, strafe (Y) pod 6.43 back. Program 5 should save about `xPodOffset` +2.84, `yPodOffset` −6.43 | C-10 | entered as the check for program 5 (program 5's numbers are used) |
| 17 | Feed gate servos | front gate 1 servo, back gate 2 servos | C-11 | done: program 4 measures the positions |
| 18 | Red far CELL opening centre x, y (in) | 57.84, 89.17 | H-1 | entered (field CAD); tape-check H-1b |
| 19 | Red audience CELL opening centre x, y (in) | 57.84, 52.0 | H-1 | entered (field CAD); tape-check H-1b |

**What each line is for, and what happens if it is wrong:**

| Line | Exactly what it is | Used for | If it is wrong |
|---|---|---|---|
| 1–4 (C-1, C-2) | From the robot centre to the outside edge, each side (frame plates; the intake wheels reach 0.08 in past the front) | Start corner and corner reseed: the code puts the robot centre that far from each wall | The position is off by the error, so every distance to the CELL is off |
| 5–6 (C-3) | Each turret's rotation axis: x forward, y left from the centre (turntable / ring gear centre) | Distance and angle from each launcher to the CELL | Aim is off by the pivot error, worst up close |
| 7–8 (C-5) | The gears from each yaw servo to its turret | Cross-check of what program 3 measures, and the simulator | Only a warning; program 3 is the real source |
| 9 (C-5) | Which servo turns the turrets | The code treats it as a position servo that says nothing back | One with a position wire could be read back: tell the programmer (one change in `Yaw`) |
| 10 (C-6) | How far each turret turns each way before the cable pulls tight or it hits a stop | Program 3 measures it (cable limits are not in the CAD). It needs each turret to reach straight left and straight right (90°) | Less than 90° each way = program 3 can't finish. Tell the CAD team now |
| 11 (C-8) | The angle the element leaves at. No hood: the build fixes it | Simulator shots and the reach check | Distance comes from speed alone; a fixed angle scores only over a band of distances. A side video of a shot checks it (if the element springs off the last roller at once it could be up to 65°) |
| 12 (C-4) | Element centre at release, above the floor | Simulator and reach check | Only the simulator and AutoTool use it |
| 13 (C-9) | Flywheel wheel, belt ratio, motor | Simulator and the motor tuning overhaul | Only the simulator uses it |
| 14–15 | Drive wheel; intake reach and width | Config record, Pedro sanity check, AutoTool | Warning only |
| 16 (C-10) | Odometry pod positions from the centre: forward pod's distance left (`xPodOffset`), strafe pod's distance forward (`yPodOffset`, negative = behind) | Check of Pedro's Pinpoint Tuner (program 5), whose numbers are the ones used | Program 5 more than 0.5 in off these: the push or spin was off (redo it), or a pod moved |
| 17 (C-11) | Front gate 1 servo, back gate 2 (one gate, a servo each side, mirrored) | Gates hold elements off the flywheels until firing | A different count needs a code change |
| 18–19 (H-1) | From the AndyMark field CAD: centre (x, y) of each red CELL's opening (the open outer end of its basket, facing away from the HIVE pivot), from the inner face of the red ALLIANCE wall and the audience wall. The HIVE seesaws: a CELL's opening is 16.1 in from the pivot and 59 in high when its side is up, 21.1 in and 37.6 in when down. Entered midway (2.5 in either way). Blue is calculated | The aim target | Every shot misses by the error |

Size: frame 17.72 in long (10.22 + 7.50; the intake wheels add 0.08 in at the front) × 14.33 in wide. The back turret
looks 1 in past the back plates in the CAD only because it is turned there; straight, it is inside. Start each
match with both turrets straight (R102 / R105: 18 in cube at the start).

### H-2a. The auto draft (optional, before the robot)

1. Open the Pedro visualizer with the BIOBUZZ field image. Draw the auto on the **blue** side.
2. Start pose: touching the wall, on our side, not in the LOADING ZONE, not touching a FLOWER (G304).
3. Export as **Class** (not "full"). Never tick "mirror".
4. Send the export, and for each path say what happens at its end: SHOOT, INTAKE, PARK or nothing. Also
   say which paths push into elements or a wall.

**What the programmer does with Stage 1:** the CAD lines marked "entered" are already in the code; enters the
CELLs when they come; keeps lines 7–8 to check program 3; writes the real autos from the export (a class per auto
next to `opmodes/SampleAutoPaths.java`, which stays the reference).

---

## Stage 2 — robot wired

### D-1. Device names

On the Driver Station: **Configure Robot**. Use exactly these names:

| Device | Name | Type | Note |
|---|---|---|---|
| Drive motors: left-front, left-back, right-front, right-back | `lf` `lb` `rf` `rb` | motor | — |
| Pinpoint | `pp` | goBILDA Pinpoint (I2C) | — |
| Main intake motor (roller + NECTAR feed) | `intake` | motor | — |
| POLLEN feeder motor (the 2 wheels at the back) | `pfeed` | motor | — |
| Front / back flywheel | `fs` `bs` | motor | encoder cable in the same port number |
| Front / back turret yaw | `fyaw` `byaw` | servo | one position servo per turret |
| Ramp, left / right | `rampl` `rampr` | **Continuous Rotation Servo** | mirrored; they wind the ramp's strings. Configured as "Servo" they are not found |
| Front gate (NECTAR) | `fg` | servo | — |
| Back gate (POLLEN), servo 1 / servo 2 | `bg` `bg2` | servo | one gate, a servo each side, mirrored |

- That is 16 devices: 8 motors, 7 servos, the Pinpoint.
- Want other names? Send them; the programmer changes one line and rebuilds.
- Check: program **1 Check Devices**, step 1, lists any name that doesn't match (a ramp servo set up as a plain
  servo shows as "rampl (as Continuous Rotation Servo)").
- Motors are exactly at the 8-motor limit (R503). Any extra motor is illegal. Servos: 7 of 8.

### D-2. Hubs

Name the hubs `19859-RC` / `19859-DS`. Change the Control Hub Wi-Fi password (R705 / R711).

---

## Stage 3 — the 9 setup programs (robot built and wired)

### How every program works

- Driver Station → TeleOp list → group **"19859 Setup"**. Run them **in number order**.
- **Top of the screen:** "PROGRAM 3 of 9: Turret Servos", then "STEP 4 of 11: …" and a progress bar.
- **"WHY:" line:** what this step is for.
- **Bold "> …" lines:** exactly what to do now.
- **"GOOD:" line:** what a good result looks like.
- **Grey "Buttons:" line:** only the buttons that work in this step.
- **Buttons:** **cross ✕** = next / save, **triangle △** = back, **circle ○** = no (where asked).
  Holding a button does not repeat it.
- **Red NOT READY line in INIT:** something this program needs is missing. Do that first.
- **Nothing moves in INIT** (rule R102). Motors and servos move only after START. On the servo position steps of
  programs 3 and 4, a servo moves only once you press square □.
- **PROGRAM COMPLETE:** press STOP. The next program loads by itself. Read its INIT screen, then press
  START.
- **Results:** every value goes into `biobuzz_results.csv`. Redoing a step adds a newer line; the
  newest one wins. The next program uses them straight away (yellow line in INIT); no build in between.
- **Battery:** use a charged one (12.5 V or more) for every tuning run.

Three sessions, no build in between:

| Session | # | Program | Where | Needs first |
|---|---|---|---|---|
| A, robot on blocks | 1 | Check Devices | box, then floor | D-1 |
| | 2 | Motor Directions | box | 1 |
| | 3 | Turret Servos | box | 1 |
| | 4 | Servo Positions | box | 1, 2 (the ramp direction) |
| B, robot on the floor | 5 | Drive Tuning (Pedro's tuners on the Driver Station; part 1 still on blocks) | open floor | 1 |
| | 6 | Position Check | field | 5, CAD edge sizes (C-2) |
| | 7 | Flywheel Tuner | box | 2, 4 |
| C, on the field | 8 | Back Shot Tuner (POLLEN, the main launcher) | field | 3, 4, 5, 6, 7 + CELLs |
| | 9 | Front Shot Tuner (NECTAR, the backup) | field | same as 8 |

**After program 9:** one `adb pull` over the USB cable (results file, both points files, both shots
files), the programmer enters everything, you install build 2.

**Any time: "0 Setup Report"** (nothing moves). One screen with every number the programs and the CAD sheet
put into the robot: per program each value and where it is (in the code, only in the results file = not built in
yet, or missing), values outside a sanity range, and values a later run changed a lot. It also writes
`/sdcard/FIRST/biobuzz_setup_report.txt` (pull it with the others). Every program also warns under its header
when it saves a value far from the last one ("a big change since the last run": battery? rubbing? a changed part?).

### Program 1 — Check Devices

**Why:** a wrong name or a loose encoder cable makes every later program fail in confusing ways. This
finds them first. It moves nothing.

**Before START:** robot on a box, wheels free.

| Step | You do | The screen | Good | If not |
|---|---|---|---|---|
| 1 Names | read | "Found N of 16" and any missing name | "Every device name matches" → ✕ | Fix Configure Robot, run again (✕ won't advance) |
| 2 Battery | ✕ | voltage | 12.5 V or more | Swap the battery |
| 3 Encoders | one part at a time, as the screen says: "Turn ONLY the left-front wheel now", then left-back, right-front, right-back wheel, intake roller, POLLEN feeder wheels, front flywheel, back flywheel. Touch nothing else | that part's encoder must count, and no other one | "OK: its encoder counts, and no other one did" → ✕ (next part) | "MIXED UP: lf and lb" = turning that part counted on another port. Two parts turned at once, or one still spinning: let everything stop, △, turn only that part again. Same again = an encoder cable is in the wrong port (each one goes in the same port number as its own motor). Never counts = loose encoder cable or wrong port. △ = turn it again; ✕ always goes on and notes the problem in the results file |
| 4 Pods | robot on the floor; push about 1 ft forward, then slide about 1 ft sideways | "OK forward pod counts", "OK strafe pod counts" | both OK → ✕ | Pod cable unplugged, or a pod not touching the floor |

Afterwards the screen stays as a live readout of every motor encoder, the last command of each position servo,
both pods and the battery (useful later).

### Program 2 — Motor Directions

**Why:** + power must mean "pull in" for the intake and the POLLEN feeder, "throw out" for each flywheel, and
"out" for the ramp. If it doesn't, the code reverses that part.

**Before START:** robot on a box, **ramp folded up by hand**. Hands clear of the intake, the POLLEN feeder, the
flywheels and the ramp.

**How:** each test runs one part at 30% only **while you hold R2**.

| Step | Watch | Answer | If it stops |
|---|---|---|---|
| 1 Intake | do elements go IN? | ✕ yes / ○ no | — |
| 2 POLLEN feeder | do the POLLEN feeder wheels move POLLEN toward the back launcher? | ✕ / ○ | — |
| 3 Front flywheel | does the wheel surface move the way that throws an element out? | ✕ / ○ | Red STOP "encoder counts the opposite way": the hub's speed control would run this flywheel at full speed. Check its encoder cable is in the same port number as its motor. If it is, swap that motor's two power wires (red / black) at its connector. Run program 2 again (this answer will change) |
| 4 Back flywheel | same | ✕ / ○ | same as step 3 |
| 5 Ramp | hold R2 only for a moment (let go before it reaches the end): did the ramp go OUT (down toward the floor)? | ✕ / ○ | Both servos buzz and the ramp hardly moves: the two fight. One is mounted the same way round as the other instead of mirrored. Turn it round on its mount, run program 2 again |

- For the flywheels it also checks the encoder counts while running; you can't answer until it does. The intake
  and the POLLEN feeder only need R2 held once (they may have no encoder cable).
- After a red STOP the program goes no further and does not load program 3.
- Afterwards: free jog. Dpad up/down picks any motor (intake, POLLEN feeder, flywheels, drive), R2 / L2 runs it.

Nothing to send now: programs 3 to 9 use these answers from the results file. **Programmer (at the end):**
sets `Intake.REVERSED`, `Intake.POLLEN_REVERSED`, `Launcher.FRONT/BACK_REVERSED` and `Ramp.RAMP_REVERSED`.

### Program 3 — Turret Servos

**Why:** each turret is turned by its own servo through gears. The servo says nothing back, so the code needs:
- the servo position where the turret points straight forward, and how much the position changes per degree;
- how far each way the turret may turn, so it never pulls the cable tight or pushes into a stop;
- how fast the turrets turn, so it knows when a turret has had time to get where it was sent.

**Before START:** robot on a box (it does not drive). Cables on the turrets routed as in a match. Nothing moves
until you press square □ on a step. Keep fingers away from the turrets.

**Buttons on every position step:**
- square □ = move the servo to the shown position (**it doesn't move until you press □**);
- R1 / L1 = ±0.01; R2 / L2 = ±0.001;
- ✕ = save and next; △ = back.

The FRONT turret first (steps 1–5), then the BACK (steps 6–10). The next step on the same servo starts where the
last one ended.

| Step | Move the turret | Saved | Rule |
|---|---|---|---|
| 1 / 6 | **straight forward**: sight along the barrel; it must point along the robot's centre line, toward the front | `<side>YawForward` | every aim is counted from here (0°) |
| 2 / 7 | **straight LEFT** (90°): hold a square against the robot's left side, the barrel must be parallel to it | — | |
| 3 / 8 | **straight RIGHT** (90°): the same against the right side | `<side>YawPerDeg` = (left − right) ÷ 180 (+ = turret turns left) | refused if left and right are the same position. Forward should sit midway between them: more than 3° off saves it but warns "check the marks (triangle = redo), or the servo is not linear" |
| 4 / 9 | **as far LEFT as it can go**: stop where the cable is still loose, before the servo buzzes or strains. The screen shows the angle | `<side>YawMaxDeg` = that angle − 5° | must be left of forward |
| 5 / 10 | **as far RIGHT as it can go**, the same way | `<side>YawMinDeg` = that angle + 5° | must be right of forward |
| 11 | **Turret turn speed.** Film both turrets from above in **240 fps slow motion** while pressing □: each press swings both turrets straight right → straight left, the next press back. In the video, count the frames from the slower turret starting to move until it stops at the other side. Enter the count: dpad ±1, R1 / L1 ±10 → ✕ | `yawDegPerS` = 180° ÷ the slower turret's time | the code counts a turret as on target once it has had this long to get there |

When it finishes, both turrets go back to straight forward.

**Nothing to send now:** the results file keeps everything, and the next programs use it. **Programmer
(at the end):** compares each `YawPerDeg` with the CAD gear (C-5) once the servo part is known (a big mismatch
means a misread "straight").

### Program 4 — Servo Positions

**Why:**
- **Gates:** closed (holds elements off the flywheel) and open positions. The back gate has a servo each side:
  mirrored servos are never exactly 1 − p, so each is measured on its own.
- **Gate travel time:** how long firing waits before feeding.
- **Ramp times:** nothing tells the code where the ramp is, so it runs the servos for a measured time out and in.
  AutoTool uses them too.

**Before START:** program 2's ramp step is done. Ramp folded up (stowed) by hand. Fingers out of the ramp and
gates.

**Buttons on every gate position step (1–6):**
- square □ = move the servo to the shown value (**it doesn't move until you press □**);
- R1 / L1 = ±0.01; R2 / L2 = ±0.001;
- ✕ = save and next; △ = back.

| Step | Set | Rule | Saved |
|---|---|---|---|
| 1 | front gate closed | with an element in place, it must not touch the front flywheel | `frontGateClosed` |
| 2 | front gate open | an element passes through freely | `frontGateOpen` |
| 3 | back gate servo 1 closed | servo 2 is limp. Move servo 1 (`bg`) so the gate holds an element clear of the back flywheel | `backGateClosed` |
| 4 | back gate servo 2 closed | servo 1 holds the gate. Press □, then step servo 2 (R2 / L2) until the buzz stops. Keep going until it starts again on the other side, then come back halfway: neither servo pushes | `backGate2Closed` |
| 5 | back gate servo 1 open | servo 2 is limp. An element passes through freely | `backGateOpen` |
| 6 | back gate servo 2 open | the same as step 4 | `backGate2Open` |
| 7 | both back servos together | □ a few times (closed ↔ open). Listen and touch: no buzz, nothing hot. Closed holds an element clear, open lets it pass. A buzz in one place: △ back to that servo-2 step and step it until quiet. ✕ = quiet both ways | — |
| 8 | gate travel time | gates start closed. Film all three gate servos in **240 fps slow motion** while pressing □ (each press opens or closes both gates). In the video, count the frames from the slower gate starting to move until it is fully open. Enter the count: dpad ±1, R1 / L1 ±10 → ✕ | `gateTravelS` |
| 9 | ramp time out | film the ramp from the side in **240 fps slow motion**. Ramp folded up. **Hold** □: the ramp goes out; let go the moment it is fully out (on its floor stop). Count the frames from the ramp starting to move until it stops; enter them the same way → ✕ | `rampOutS` |
| 10 | ramp time in | ramp fully out. **Hold** □: it folds up; let go the moment it is fully up. Count and enter the same way → ✕ | `rampInS` |

Servo 2's first value on steps 4 and 6 is the mirror of servo 1 (1 − its position), only a starting guess.
When it finishes, the ramp stays held up at the saved power: check it stays up. Nothing to send now. Program 4
ends by loading program 5.

### Program 5 — Drive Tuning (Pedro, on the Driver Station)

**Why:** Pedro, the path-following library, needs the drive motor directions, the odometry pod geometry,
and 17 driving numbers (top speeds, braking, gains). Pedro measures these itself, with its own tuners.

**How:** program 5 runs Pedro's own tuners **on the Driver Station**: no laptop, no web page, no Wi-Fi
switch. It loads by itself when you STOP program 4.

- The first screen is a list of 4 parts with their status. **dpad up / down** picks one, **✕** runs it. The
  next part to do is already picked. A done part can be run again.
- Each part saves its numbers in the robot's results file **the moment it finishes**, and the next part uses
  them. A STOP or a reboot is fine: the list shows what is done.
- Questions the robot can answer itself (device names, pod type) are not asked.
- Every driving step shows what it will do first: **✕ = start**. Nothing moves before that. Steps where you
  push or watch end with **✕ = done**. **STOP always stops the robot.**
- Questions (a distance, which way a wheel spun): **dpad up / down** picks the line, **dpad left / right**
  changes it (hold L1 for steps of 10), **✕ = OK**.

| Part | Where | You do | Saves |
|---|---|---|---|
| 1 Wheel directions (Mecanum Tuner) | robot on blocks, wheels free | for each wheel: ✕ starts it spinning (the screen marks which wheel: forward = its top rolls toward the front), ✕ when you know; then answer: did that wheel spin (yes / no) and which way (forward / reverse) | 4 drive directions |
| 2 Pinpoint (Pinpoint Tuner) | on the floor | ✕, push the robot forward about 2 ft, ✕; ✕, push it left about 2 ft, ✕; ✕, turn it half a turn counter-clockwise by hand, ✕ | pod offsets and directions |
| 3 Foresight (Foresight Tuner) | 10 ft clear in front and to the left | first a **drive check**: a gentle 1 ft drive forward on parts 1 and 2's answers; if the Pinpoint does not see it go straight forward, part 3 stops and says what to redo (a turn = a wrong wheel answer, part 1; backward / sideways = wheels or pods). Then keep the suggested distances and speed (✕); before each run put the robot where it has room, ✕; it drives fast lines and turns by itself | 17 driving numbers |
| 4 Tests (optional) | room to drive 4 ft | pick Line (or Curve, Hold, Localization), ✕; watch it; ✕ ends it | — |

- When parts 1 to 3 are done the list says so: press STOP and program 6 loads by itself.
- Part 4 Line: it should drive smoothly to 48 in and stop on the end. Not smooth = run part 3 again.
- Backup: each part ends with a RESULTS P5 block (a photo of it is enough if the file is lost).
- TeleOp's predicted stop (when you let go of the sticks the turrets aim at where the robot will stop) uses
  Foresight's numbers too (after build 2).
- Pedro's own tuning web page is not in the robot app any more (rule R704 question gone).

### Program 6 — Position Check

**Why:**
- **How the robot knows where it is:** the Pinpoint measures only movement (2 pod wheels + a gyro), so it
  is told the starting spot once and then adds up every movement.
- **Velocity direction:** the turrets keep aiming while you drive, and TeleOp's predicted stop works from the
  robot's speed. Both need that speed in field directions. This program proves it.
- **Scale:** pod wheels are never exactly their catalog size, and the gyro reads a few tenths of a percent
  off. 1% off is 1 in for every 100 in driven. Program 6 measures both and uses them straight away.
- **Accuracy:** it measures how far the position drifts over a loop.

**In INIT:** pick the alliance: square □ = blue, circle ○ = red.

| Step | You do | Inside | Good |
|---|---|---|---|
| 1 Direction | right stick (or by hand) until Heading ≈ 90; then drive straight with the left stick, or push, for about 1 s | compares the reported velocity direction with how the position really changed. At heading 90 robot and field directions differ by 90°, so a wrong setting can't match | "PASS: velocity is in field directions" → ✕. FAIL → tell the programmer |
| 2 Start corner | put the robot in the **start corner** (below) → ✕ | sets the position from the robot's edge sizes | — |
| 3 Pod scale | lay a tape measure along the side wall, 0 at the robot's back edge; push it (or drive slowly) straight along the wall, right side touching, until the back edge is at **96 in** → ✕ | pod ticks per mm = the ticks it counted ÷ 96 in | within 5% of 96 (else: push exactly 96 in; still off = a pod slips or is lifted) |
| 4 Gyro scale | drive to open space (2 ft from walls), line the front edge up with a tile seam or tape → ✕: it **spins 10 turns by itself**. Then turn it back (slowly with the right stick, or by hand) until the front edge lines up with the SAME seam, the same way round → ✕ | yaw scalar = 3600° ÷ what the gyro counted | within 45° of 3600 |
| 5 Start corner again | back into the start corner → ✕ | — | — |
| 6 Loop | drive at least 15 s anywhere (turns, both directions), then back into the **same corner turned a quarter turn left**: left side flat on your alliance wall, back flat on the side wall → ✕ | position error vs where the robot must be in that corner (from its edge sizes). Coming back turned is what shows wrong pod offsets: coming back the same way hides them | within 1 in and 2° = PASS. Fails twice → the Pinpoint tuning (program 5) or the edge sizes (C-2) are wrong: tell the programmer |

**The start corner** (programs 6, 8, 9 and TeleOp without an auto). It is **not** the auto start:
1. Stand behind your alliance wall, looking at the field.
2. Take the corner on your **right**. That is your GARDEN corner; take any POLLEN out.
3. Robot's **back** flat on your alliance wall, **right side** flat on the side wall, **front** (intake)
   pointing straight across the field.

```
              field (away from you)
                      ^ front
   |          +-------+|
   |          | ROBOT ||  <- right side touching the side wall
   |          +-------+|
   ===========================  <- your alliance wall (back touching it)
                 YOU
```

### Program 7 — Flywheel Tuner

**Why:** each flywheel holds its speed with the Control Hub's built-in speed control. That needs gains
(P, I, F), a **ready band** (how close to the target counts as "at speed" before firing), and an **idle
speed** (the pre-spin between shots; DECODE used 1000–1300).

**Before START:** robot on a box, nothing loaded, hands and cables clear. The wheels get loud and fast.

For the FRONT flywheel, then the BACK:

| Step | You do | Inside | Result | If not |
|---|---|---|---|---|
| 1 Top speed | ✕ | full power until the speed stops rising: it averages each whole second and stops once a second's average is within 0.5% of the one before (at most 8 s; the screen shows "Waiting for the speed to stop rising"). Top speed = that last second's average | F = 32767 ÷ top speed, P = F ÷ 10, I = P ÷ 10, D = 0 (FIRST / REV starting rule). △ = run again. "Still rising after 8 s" = check for rubbing, △ | Red STOP "encoder counts the opposite way" (speed read below 0): it will not tune this flywheel. Same fix as program 2: encoder cable in the same port number as its motor; if it is, swap that motor's red / black power wires; run program 2 again. "Speed read 0": the encoder cable is loose or in the wrong port |
| 2 Hold | ✕ | holds 70% of top speed; waits until the speed has stayed within 5% of it for 1 s (at most 6 s), then for 2 s measures how much the speed readings spread (standard deviation; the hub reads speed in 20 ticks/s steps) and how far the average is off the target | ready band = 1.5 × the larger of one reading step and 2.5 × the spread, + how far the average is off the target (the largest single swing came out half or double from run to run; the spread does not) (ready to fire = within the band of the target) | "did not stay within 5%": the band is measured anyway; a wide band = check for rubbing, △ |
| 3 Recovery | load **ONE** element in this launcher only (hold L2); R2 fires once the speed is steady. After the first shot: did the element fly OUT? ✕ yes / ○ no. Then after every shot the screen shows the result and the next P: press ✕ when you are ready, load the next element, R2. About 9 shots (at most 12). △ = do not count this shot (same P again), □ = stop and keep the best P so far | opens the gate, waits the gate time, feeds 0.55 s like a match shot, then watches 2 s: how far the speed dropped, when it is back in the band, and when it is back **to stay** ("steady after": ready for the next element). The program tries the start P × 1, 2, 4, 8, 16, 32 (and × 0.5 if × 1 is best), then just below and above the best, then fires the best once more to check it. Shots where no element reached the wheel, or two went at once, are not counted. Every shot goes to `biobuzz_flywheel_trials.csv` | the P whose shots are steady soonest (two P values within 0.05 s: the softer one). ✕ keeps it. dpad ↑ / ↓ = set P yourself instead (then ✕ keeps your P) | ○ no = it asks "is it a DIRECTION problem?": watch the wheel where it touches the element. ✕ = its surface moves into the robot: the program flips the wheel, saves the new direction (`<side>FlywheelReversed`) and you fire again. ○ = it spins the launch way: check the gate opens and the intake pushes the element in, fire again. L2 runs the intake to load |
| 3b Band again | nothing (it starts by itself after ✕ keeps the P) | holds the test speed again with the chosen P and measures the wobble | the ready band with the chosen P is saved (a stronger P can change the wobble). If it is more than 25% narrower than the first one, one check shot (load ONE, R2) shows the wheel still settles in it; if not, △ runs the P search again | — |
| 4 Idle | ✕, nothing loaded, hands clear (about 30 s). Then dpad ±50 (hold L1 = ±200) if you want to change it, ✕ saves, △ = measure again | the wheel holds 0, 20, 35, 50 and 65% of the test speed in turn and times the climb from each to the test speed; the pick and its neighbours run twice | the lowest idle whose climb is within 15% (or 0.1 s) of the fastest; never above 65% of the test speed | a climb over 3 s = check for rubbing |

**Later, once programs 8 and 9 are done (optional): "7 Flywheel Tuner: shot-speed check".** Program 7 tunes at
70% of top speed; real shots run from the shortest to the longest table speed. This checks each flywheel at both:
it holds each (nothing loaded) to measure the band there, then you fire two shots at each (load ONE, R2), then it
picks the idle speed again against both. The band per speed is saved only if every check passes (both speeds
settled, each band under 10% of its speed, program 7's band fits between them within 40%, all four shots steady); if
the two bands are within 25% of each other one band is enough. A band is never saved narrower than program 7's (a
too narrow band would hold shots back), so this only ever widens the band
where the speed needs it. TeleOp, the autos and the shot tuners then use the band of the speed they are at. Nothing
saved = nothing changes. About 3 minutes.

**Speed, not power:** the controller holds the speed whatever the battery does. Both DECODE robots did the
same. The hub's speed control outputs power, though, so step 1 also saves the battery voltage it ran at, and
the code rescales the gains for the battery of the moment (a few times a second). Nothing to send now.

**Flywheel behaviour, DECODE vs BIOBUZZ:**

| What | DECODE | BIOBUZZ |
|---|---|---|
| Stored value | speed (ticks/s), not power | the same |
| Pre-spin in auto | flywheel at shot speed for the whole auto | the same: both launchers spin to the live-distance speed on every leg |
| Pre-spin in TeleOp | 1000 (19859) / 1300 (32008) when off | idle speed from program 7; gates are closed, so idling never launches anything |
| Spin in INIT | none (R102) | none (R102) |
| Before a shot | 32008 far: 0.45 s minimum spin-up + 0.75 s continuously at speed | fires only after every firing launcher's flywheel stays inside its ready band without a break (0.3 s in auto, 0.15 s in TeleOp and programs 8 and 9) |
| Between balls | 32008 auto paused the feed | each lane pauses for its own flywheel: the main intake waits while any firing flywheel is out of its band, the POLLEN feeder only for the back one |

### Programs 8 and 9 — Back / Front Shot Tuner (need 3, 4, 5, 6, 7 + the CELLs)

**POLLEN first.** Program 8 = **Back Shot Tuner** (POLLEN, the main launcher): aim for 10 points or more.
Program 9 = **Front Shot Tuner** (NECTAR, the backup): 6 points or more will do.

**Why:** the shot table. At up to 16 distances it stores the measured flywheel speed (and feed) that score.
There is no hood: the launch angle is fixed by the build (C-8), so distance is reached by speed alone. In a
match the code blends between the measured points and never guesses beyond the measured range. This is more
accurate than curve fits, which bent the wrong way in 32008's code.

**HIVE up or down:** the seesaw moves a CELL's opening about 21 in in height (59 in up, 37.6 in down) and 5 in
sideways. One table cannot score at both. Tune with the HIVE in the state the matches start in (red audience
CELL up), then try a few shots with it the other way and tell the programmer how far off they are.

**Semi-automatic:** the robot drives itself (Pedro, odometry) to each shooting spot, facing the CELL, and
holds it there. This launcher's turret aims itself; the other turret stays straight forward and its flywheel
off. You load, fire, say in / not in, and nudge the settings. Spots start 36 in from the CELL and go out 5 in at
a time, fanned out sideways so they stay on your half of the field. Keep the field clear: it drives at START and
after every saved point.

**The points file is the whole table.** Every point stays in `biobuzz_<side>_points.csv` from one run to
the next, so you can stop and carry on another day. Spots that already have a point are skipped. The columns are
`distance_in,speed,feed,battery_v`; files from before 2026-10-17 with a hood column are still read (the hood is
skipped).

**In INIT:**
1. Pick the alliance: □ blue / ○ red.
2. Put the robot in the start corner (program 6) and press **options**. No auto run is needed.
3. "Points saved earlier" shows the points already in the file. To build a new table instead, press
   **share**: it says START OVER, and at START the old file is renamed to
   `biobuzz_<side>_points_<date>_<time>.csv` (kept as a backup, not used). Press share again to keep them.
4. The "Ready" line must be green. Press START. The robot drives to the first spot.

**At each spot:**

| Button | Does |
|---|---|
| L2 (hold) | intake: load ONE element |
| R2 | fire: waits until still, aimed and at speed, then opens the gate and feeds, like a match shot |
| ✕ / △ | it went in / it did not go in |
| □ / ○ | flywheel speed − / + (20 ticks/s) |
| dpad ← / → | yaw trim left / right (0.5°) |
| dpad ↑ / ↓ | nothing (no hood) |
| sticks | drive yourself (takes over from the auto drive) |
| R1 | drive back to the planned spot |
| L1 | skip this spot (it can't score here) |
| options | undo the last point |
| share | finish (asks again if you have fewer than 10 on the back, 6 on the front) |

Hold □ / ○ and dpad ← / → to repeat. Short or long: change the speed. Left or right: yaw trim (it stays for later
spots). Any change restarts the count. **4 in a row:** the point saves itself, the pad rumbles and the robot
drives to the next spot. "Off the spot" shows how close it parked. "This launcher can't reach the CELL from here"
= its turret can't turn that far: R1 or L1. If it shows "CHECK: speed drops as distance grows", redo that point
(options = undo, R1 = back to the spot).

**Feed check (about 3 times per launcher: near, middle, far).** Right after a point saves with no feed-checked
point within 20 in, the screen says **FEED CHECK**: the robot stays put; load ONE and R2, again and again (about
12–15 shots), **nothing to answer**. The program fires each at a different feed (intake power 0.35 / 0.5 / 0.65 /
0.8, then one in between; on the back launcher the main intake and the POLLEN feeder both run at it, on the front
only the main intake) and watches how much the flywheel slows on each: the feed whose slow-downs are the most
alike gives the most repeatable shots; a feed that sends two elements at once is out; nearly equal = the faster
one. That point's feed is replaced ("FEED 0.65 at 36 in"), later spots start from it, and the robot drives on by
itself. **L1 = skip the check and drive on** (the point keeps its feed; no new check within 20 in this session).
Speed and trim buttons do nothing during a check. The feed only changes when one is clearly steadier than the
feed in use (by more than the hub's speed reading can blur); otherwise it stays.

**How the table is used:** not straight lines between the points but a curve that averages neighbouring points
(each point is just the first setting that went 4 for 4, somewhere inside the window that scores; the average
lands nearer the middle). The curve stays within 2 presses of every point; if it cannot (a lucky / unlucky point)
the screen says "curve could not follow speed at 56 in: redo that point?". Past the farthest point it holds the
curve's end value (the sim tested DECODE's "keep the curve going": it scored worst). `Launcher.SMOOTH_CURVE = false`
goes back to straight lines.

The average yaw trim is saved with every point (`<side>YawTrimDeg`, in that turret's own degrees, so a STOP
before share keeps it) and the code aims with it from then on. More than 10° is never saved or used: it means
program 3's straight-forward position for this turret is off. Redo program 3. More than about 1°: tell the
programmer (the CELL position may be off).

**Send:**

```bash
adb pull /sdcard/FIRST/biobuzz_back_points.csv
adb pull /sdcard/FIRST/biobuzz_back_shots.csv
adb pull /sdcard/FIRST/biobuzz_front_points.csv
adb pull /sdcard/FIRST/biobuzz_front_shots.csv
```

Plus a photo of the RESULTS block on the Driver Station.

The `_points_<date>_<time>.csv` backups are not needed. Program 8 ends by loading program 9; program 9 ends by
loading BIOBUZZ TeleOp.

### Alongside the programs (no program needed)

- [ ] **E-12** Tape-measure the three CAD checks: frame width 14.33 in, turret pivot spacing 8.94 in, drive
  wheel 104 mm. All within 0.1 in, or tell the programmer (the CAD frame or units are off). Then the edges (C-2)
  and turret pivots (C-3): send any difference over 0.25 in.
- [ ] **G-7** In practice watch "Locked" and the "Turret F" / "Turret B" lines in TeleOp (where the turret is →
  where it should be). If shots miss while "Locked" shows, send the distance and those lines.
- [ ] **G-8** If the drivers always trim the same way at one distance, rerun the Shot Tuner there (keep
  the old points: don't press share in INIT) and save a new point; it replaces the old one.

**Step IDs that appear in on-screen messages:**

| Step IDs | Where |
|---|---|
| E-3, E-4 | program 2 |
| G-1 | program 3 |
| E-8 | program 4 |
| F-1 … F-5, E-9 | program 5 |
| E-10, H-0 | program 6 |
| G-3 | program 7 |
| G-5 | programs 8 and 9 |

---

## Check on the real robot after this update (2026-10-17)

The code, the simulator and AutoTool were changed for the new robot without it. Check these on the real robot:

- [ ] Program 1: all 16 devices found (8 motors including `pfeed`, 7 servos, the Pinpoint).
- [ ] Program 2: intake, POLLEN feeder, both flywheels and the ramp go the right way.
- [ ] Program 3: each turret forward, straight left and right, both far ends; the turn-speed video.
- [ ] Program 4: front gate; back gate with both servos and no buzz; both ramp times.
- [ ] The ramp rests up with its servos stopped, and the servos really stop at the end of each run (no creep, no buzz).
- [ ] A RAMP leg at the FLOWER takes POLLEN; nothing droops while driving.

---

## Stage 4 — on a real field

- [ ] **H-2b Autos.** Run "SAMPLE Auto Blue" and "Red" first (the reference route: shoot, intake, shoot, ramp, park), then the real autos. Send what goes wrong:
  - a leg that stalls;
  - a pose that is off (how much, which side);
  - the "opponent side" warning (G402).
- [ ] **H-3 Corner reseed.** Put the robot square in any corner, press options in TeleOp. The pose shown
  should match that corner.
- [ ] **H-1b Real CELL positions.** Real fields are built to about an inch of the drawing. At every event,
  tape-measure each CELL opening's centre (x from your ALLIANCE wall, y from the audience wall, HIVE level or
  say which side is up) and send them; the programmer updates H-1 (one build over the USB cable). A `<side>YawTrimDeg` over about 1° from programs 8 / 9 also points here.

## Before every match

1. Both turrets pointing straight forward by hand (the servos take over at START; nothing moves in INIT). Ramp
   folded up by hand.
2. INIT our real auto for our side (until there is one, **"SAMPLE Auto Blue"** or **"SAMPLE Auto Red"**). That sets
   the alliance and hands the position to TeleOp.
   - The screen must NOT say "WILL NOT DRIVE".
   - The Pinpoint line must say `READY`.
3. After the auto, **BIOBUZZ TeleOp** is already selected. INIT it; check the alliance and pose on screen.

## Before an event

- [ ] **R-1** Panels and Pedro's tuning web page are not in the app (program 5 runs Pedro's tuners on the
  Driver Station). Nothing to ask.
- [ ] **R-2** The programmer rechecks the rules once Team Updates 1–3 are loaded.

---

# Part 2. For the programmer: how values get into the code

1. Put the value into `team-config.yaml`: `value: <number>, origin: measured`. For CAD values, add
   `source: "CAD v1 (Fusion MCP) <date>, cad/CAD_INTAKE_V1.md"` and note which [ROBOT] step checks it.
2. Run:

   ```bash
   sh tools/codegen/render.sh
   ```

   It must end with `rendered`. `verify:` lines instead mean a number was typed by hand outside Pedro's
   own tuners (`pedro/procedures`): put it in the YAML and render again.
3. Build:

   ```bash
   ./gradlew :TeamCode:assembleDebug
   ```

Never hand-edit numbers in `config/Tuned.java`, `config/DeviceNames.java` or `pedro/Constants.java`;
`render.sh` rewrites them.

Until build 2, setup programs (and Pedro's tuners) also read the results file themselves
(`config/LiveResults.java`): a code number still `NaN` takes the file's newest value, and anything saved
after the running build was installed replaces the code's. TeleOp and the autos call `LiveResults.restore()`
and use only the code. After a mid-setup build that skipped some results, the directions and reversed flags
(which have no `NaN`) come from the code again: enter the results before any mid-setup build.

Settings that live in code instead:

| Setting | File | Step |
|---|---|---|
| `Intake.REVERSED`, `Intake.POLLEN_REVERSED` | `subsystems/Intake.java` | E-3 |
| `Launcher.FRONT_REVERSED`, `BACK_REVERSED` | `subsystems/Launcher.java` | E-4 |
| `Ramp.RAMP_REVERSED` | `subsystems/Ramp.java` | program 2 step 5 |
| drive motor directions (`frontLeftDirection` … `backRightDirection`), pod directions (`xPodDirection`, `yPodDirection`): `public static` fields | `tools/codegen/Constants.java.tpl`, then `render.sh`. Not in `team-config.yaml`: `emit_tuning` renders numbers only | F-1, F-2 |
| sample auto (reference route, waypoints are examples) | `opmodes/SampleAutoPaths.java`, `SampleAutoBlue`, `SampleAutoRed`; keep current after every step, see BIOBUZZ_PLAN "Sample auto" | H-2 |

Calculations:

| Step | Calculation |
|---|---|
| C-5 | cross-check only, once the servo part is known: `<side>YawPerDeg` ≈ 1 ÷ (the servo's travel in degrees for positions 0 to 1 × `<side>YawGear`); compare with G-1 |
| all | `biobuzz_results.csv` lines are `program,key,value,time_ms`. Use the latest line per key. `<side>` = `front` or `back`. **Into `team-config.yaml`, same key name:** frontYawForward, frontYawPerDeg, frontYawMinDeg, frontYawMaxDeg, backYawForward, backYawPerDeg, backYawMinDeg, backYawMaxDeg, yawDegPerS (program 3); frontGateClosed, frontGateOpen, backGateClosed, backGateOpen, backGate2Closed, backGate2Open, gateTravelS, rampOutS, rampInS (4); xPodOffset, yPodOffset and the 17 Foresight numbers (5, Pedro's tuners, saved under the code's names); podTicksPerMm, imuYawScalar, velocityFrameField (6); `<side>`FlywheelP, `<side>`FlywheelI, `<side>`FlywheelD, `<side>`FlywheelF, `<side>`ReadyBand, `<side>`IdleVelocity, `<side>`FlywheelVoltage (7); `<side>`ReadyBandLow, `<side>`ReadyBandHigh, `<side>`ReadyBandLowSpeed, `<side>`ReadyBandHighSpeed (7 shot-speed check, optional); frontYawTrimDeg, backYawTrimDeg (8, 9; never one over 10°). **Into code:** intakeReversed → `Intake.REVERSED`, pollenFeedReversed → `Intake.POLLEN_REVERSED`, frontFlywheelReversed / backFlywheelReversed → `Launcher.FRONT_REVERSED` / `BACK_REVERSED` (2; program 7 saves a new one if it flipped a flywheel), rampReversed → `Ramp.RAMP_REVERSED` (2); frontLeftDirection … backRightDirection, xPodDirection, yPodDirection → the `Constants.java.tpl` fields (5). **Info only:** devicesFound, batteryVolts, encodersNotCounting (1; ports whose own part never counted), encodersCrossed (1; `lf-lb` = turning the lf part counted on port lb: an encoder cable in the wrong port, or two parts turned at once; `none` = good); `<side>`FlywheelEncoderOpposite (2; true = program 2 stopped for the wiring fix); velocityFrame, loopErrorInches, loopErrorDegrees (6); `<side>`TopVelocity, `<side>`FlywheelThrowsOut (7; false = wrong program 2 answer, nothing else saved for that flywheel); `<side>`ShotPoints (8, 9: table points in the points file, earlier runs included). **Results file only (the shot tuners read it back, not entered):** `<side>`FeedChecked (8, 9: distances whose feed was checked). |
| G-5 | `biobuzz_<side>_points.csv` is the launcher's whole table (earlier runs included; ignore the `_points_<date>_<time>.csv` backups). **Table points:** every line that starts with a number, except one cancelled by a later `# removed <distance>` line with the same distance (2 decimals). The header and each `# columns …` line say which column is which: `distance_in,speed,feed,battery_v`, or in files from before 2026-10-17 `distance_in,speed,hood,feed,battery_v` (skip the hood). **Not table points:** the header, `# columns` lines, `# session <date time>` lines (one per run), `# removed` lines, and `# check …` lines (older runs only). The feed column is the fixed feed power the tuner fired with. A `<side>YawTrimDeg` result over about 1° means the aim is off: recheck program 3's forward positions and the CELL coordinates (H-1). Enter the table points as `<side>ShotNN{Distance,Speed,Feed}` = `value: <number>, origin: measured`, NN = 01, 02, … (any order; the code sorts by distance). **Then clear every higher slot left from an older table:** `value: null, origin: untuned` on all 3 keys, up to 16. A null renders as `Double.NaN` and `Launcher` skips any row with a NaN, but an old complete row would still be used. The code runs its curve (`ShotCurve`) through the points |

---

# Part 3. Units and directions

- **Units:** inches, degrees, seconds, servo position 0–1, flywheel speed ticks/s. CAD mm ÷ 25.4 = in.
- **Robot centre:** middle of the 4 wheel contact points. Tape-mark it on the robot.
- **Robot directions:** front = intake / NECTAR launcher side. +x forward, +y left. Counter-clockwise
  seen from above = +.
- **Field directions:** (0, 0) = corner where the red alliance wall meets the audience wall. +x toward
  the blue alliance wall, +y toward the far wall. Heading 0 = facing +x. Same as the Pedro visualizer.
- **Red vs blue:** red = blue turned 180° around the field centre (72, 72). Never mirror.
- **Sign check:** the setup programs do this for you (programs 2 and 3).

---

# Part 4. The design the code follows

- Mecanum drive, goBILDA Pinpoint with 4-bar pods, Pedro Pathing 3.0.1, FTC SDK 12.0, no camera.
- **Launchers:** two, on two turrets. Back launches POLLEN (the main one, tuned first); front launches NECTAR (the
  backup). Each has 1 flywheel motor (72 mm wheel, 2:1 belt) and a fixed launch angle (59° from the CAD, no
  hood). Front gate: 1 servo. Back gate: 2 servos, one each side, mirrored, each measured on its own.
- **Yaw:** one position servo per turret, through gears. Each turret aims on its own, from its own pivot. The
  servo says nothing back, so a turret counts as on target once the move has had time to finish at the turn
  speed program 3 measured.
- **Feed:** two intake motors. The main motor (`intake`) runs the roller and pushes NECTAR up the inside ramp
  to the front launcher; the POLLEN feeder (`pfeed`, 2 wheels) carries POLLEN under it to the back launcher.
  Gates hold elements off the flywheels. Collecting, firing POLLEN and ejecting run both motors; firing NECTAR
  only the main one. Each lane pauses for its own flywheel.
- **Capacity:** 4 POLLEN with no NECTAR, or at most 2 NECTAR + 2 POLLEN (G407: at most 4, limited by the
  mechanism).
- **Aim:** odometry only. Target = our CELL on the robot's side of y = 72. A launcher whose turret can't reach
  the CELL keeps its gate shut; the other fires.
- **Ramp:** takes POLLEN from the bottom of a FLOWER. Two continuous rotation servos (mirrored) wind strings to
  let it out and pull it up. Nothing says where it is: it runs for `rampOutS` out and `rampInS` in. The strings
  keep tension and it rests up by itself; the servos stop between runs. Not powered in INIT (R102).
- **Park:** LOADING ZONE.
- **Parts:** motors 8 of 8 (4 drive, 2 flywheels, main intake, POLLEN feeder); servos 7 of 8 (2 turret yaw,
  2 ramp, front gate, 2 back gate). Any extra motor breaks R503.
- **Auto:** a path runner. INIT-ing the correct-side auto sets the alliance and hands the robot position to
  TeleOp. The turrets are pointed forward by hand; the servos take over at START.
- **Rules in the code:** nothing moves during INIT (R102). No shots while moving. Panels only in practice
  OpModes (R704).

### Driver controls ("BIOBUZZ TeleOp", field-centric)

The buttons are DECODE's. The only BIOBUZZ extra is the ramp, on a shape button DECODE leaves free. TeleOp is always field-centric.

| Input | Action |
|---|---|
| gp1 left stick / right stick X | drive / turn |
| gp1 R2 | intake in (launchers off, gates closed) |
| gp1 L2 | eject |
| gp1 L1 | **fire mode** on/off. On (armed): the launchers spin up and R1 may fire once it is ready (turrets locked, speed steady, robot stopped). Off: R1 collects. The pad light is red while armed but not ready, green when ready to fire, and the pad rumbles at ready |
| gp1 R1 (hold) | armed: fire: waits for aim lock + every firing flywheel at speed for 0.15 s, opens the gate of each launcher whose turret reaches the CELL, feeds. It never fires on the move: it waits until the robot has stopped. When you let go of the sticks the turrets already aim at where the robot will stop (Foresight's braking model), so they are locked by the time it is still; the screen then shows "last stop: predicted X in off" (about 1 in is good; several inches means redo the Foresight Tuner). Not armed: intake in (DECODE) |
| (always) | auto-turret = DECODE lock-on: each turret follows the CELL all the time, launchers on or off. A turret that can't reach it shows OUT OF REACH and its launcher does not fire. The robot never turns by itself |
| dpad up/down | distance trim ±2 in (speed ±20 ticks/s when auto-shooter is off) |
| dpad left/right | angle trim ±1° (separate near / far) |
| gp2 L1 / gp2 R1 | auto-turret on/off (off = both turrets straight forward) / auto-shooter on/off (DECODE) |
| gp1 options (DECODE's start) | **tap:** the Pinpoint is reseeded: the robot is square in the nearest field corner. In INIT (no auto before): a tap = the robot is in the start corner now. In the shot tuners a tap is "undo last point" |
| gp1 triangle (extra) | ramp out / in. The screen shows "going out N %", "OUT", "going in N %" or "stowed". "ramp times not set (E-8)" = program 4 not done |
| feedback | rumble when ready; pad light green = ready, red = launchers on but not ready |

INIT without an auto first: red, start corner (H-0), nothing to press. square = blue, circle = red (sets the
start corner again), options = the robot is in the start corner now. A setup program's pose is never used.
Turrets: nothing to zero. Point both straight forward by hand; the screen says "Turrets: the servos take over at
START (nothing moves in INIT)". Ramp folded up by hand.
PS4/PS5 controllers (the pad light is used).

---

# Part 5. Still open (team decisions)

- Gates: code assumes the front gate has 1 servo and the back gate 2 (C-11). Which CAD servos are which: team to
  confirm.
- Turret yaw servo part: not chosen. The code works for any position servo; one with a position wire would let
  the code read where the turret is (one change in `Yaw`).
- Ramp: not in the CAD yet (reach, pivot, spools); AutoTool's `rampReachIn` stays blank until it is.
- Physical status light: 1 servo slot free; no rule found about lights, so ask the inspector.

---

# Part 6. DECODE numbers (comparison only; nothing physical carries over)

- **19859 DECODE** (`PP_19859_DECODE` working tree):
  - Robot and Pedro: mass 14.45; zero-power accel −21.72 / −70.38; velocity 79.85 / 59.03; heading
    2.4935 / 0.2301; braking 0.067 / 0.1177 / 0.00068; pods −155.5 mm, X REVERSED / Y FORWARD.
  - Flywheel: PIDF 40/0/0/9.5; speed = 0.0501d² + 1.4015d + 1090.7 (max 2000); hood = 0.0043d + 0.0902;
    feed = clip(1.114 − 0.0025d, 0.4, 1) − 0.05.
  - Turret: 668 ticks/180°; kS 0.038, kV 0.0014, kA 3e−6, brake 0.152, kP 5; limits ±170°.
  - Gates `lg` / `rg`: open 0.41 / 0.57, closed 0.51 / 0.47.
- **32008 DECODE** (Slowstart):
  - Robot and Pedro: mass 13.7; zero-power accel −27.35 / −56.36; velocity 81.18 / 64.14; heading
    1.1 / 0.11; braking 0.1 / 0.0644 / 0.00238; pods −141.5 mm.
  - Flywheel: PIDF 80/0/0/11, ready band 20, idle 1300.
  - Turret: 1229 ticks/rev; kS 0.029, kV 0.0016, kA 6e−6, brake 0.069 at 13.84 V; kP 12, kD 0.2;
    limits ±175°.
- **Lessons:**
  - Both teams fed slower at long range.
  - Both turrets twitched at rest (fixed here).
  - 19859 autos fired on timers with no aim or speed check.
  - 19859 Pinpoint health display never updated.
  - 32008 practice teleops never updated the Pinpoint.
  - Panels was used in match code.
