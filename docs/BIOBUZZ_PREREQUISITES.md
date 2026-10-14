# BIOBUZZ 19859 — how to finish the robot code (full walkthrough)

**Who does what.** You (the team) measure things; the setup programs save the results on the robot. The
programmer (Claude) puts them into the code and sends back a build: once after the CAD sheet, once after
program 9. You never edit code.

**Contents**
- Part 0 — the idea behind the whole process
- Part 1 — the walkthrough: Stage 1 CAD → Stage 2 wiring → Stage 3 the 9 setup programs → Stage 4 field →
  every match → every event
- Parts 2–6 — reference (programmer notes, units, design, open decisions, DECODE numbers)

---

# Part 0. The idea behind the whole process

**The code is already written. Every physical number in it is blank.** Mass, turret scale, motor
directions, servo positions, flywheel gains, the shot table: each one is stored as "not measured yet"
(`NaN`). The code checks this everywhere. A part whose numbers are blank stays switched off, and the
Driver Station says which step fills it in (for example "gate positions not set (E-8)").

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

Fill in this sheet and send it all at once.

| # | What | Value | Step |
|---|---|---|---|
| 1 | Centre → front edge (in) | | C-2 |
| 2 | Centre → back edge (in) | | C-2 |
| 3 | Centre → left edge (in) | | C-2 |
| 4 | Centre → right edge (in) | | C-2 |
| 5 | Front turret pivot x, y (in) | | C-3 |
| 6 | Back turret pivot x, y (in) | | C-3 |
| 7 | Yaw motor model (part number) | | C-5 |
| 8 | Gear ratio yaw motor → front turret | | C-5 |
| 9 | Gear ratio yaw motor → back turret | | C-5 |
| 10 | Both turrets turn the same direction? (yes / no) | | C-5 |
| 11 | Front turret travel from facing front: left ° / right ° | | C-6 |
| 12 | Yaw angle read from the yaw motor's own encoder? (yes / no) | | C-7 |
| 13 | Forward pod y, strafe pod x (in) | | C-10 |
| 14 | Number of feed gate servos (expected 2) | | C-11 |
| 15 | Red far CELL opening centre x, y (in) | | H-1 |
| 16 | Red audience CELL opening centre x, y (in) | | H-1 |
| 17 | (optional) ball exit height, hood angle range, flywheel motor + ratio + wheel size | | C-4, C-8, C-9 |

**What each line is for, and what happens if it is wrong:**

| Line | Exactly what to measure | Used for | If it is wrong |
|---|---|---|---|
| 1–4 (C-1, C-2) | From the robot centre to the outside edge, each side | Start corner and corner reseed: the code puts the robot centre that far from each wall | The position is off by the error, so every distance to the CELL is off |
| 5–6 (C-3) | Each turret's rotation axis: x forward, y left from the centre | Distance and angle from each launcher to the CELL | Aim is off by the pivot error, worst up close |
| 7–9 (C-5) | Yaw motor model and gear ratio to each turret | Cross-check of what the Yaw Tuner measures | Only a warning; the tuner is the real source |
| 10 (C-5) | Count the gear meshes from the motor to each turret. Both odd or both even = same direction | One motor aims both turrets midway | **Opposite directions: one shared angle can't aim both (23° miss vs 4°). Tell the CAD team now; code can't fix it** |
| 11 (C-6) | How far the turret turns each way before a hard stop or the cable is tight | The Yaw Tuner turns each turret straight left and straight right (90°), so each stop must be past 90° (95° or more to be safe) | Less = the Yaw Tuner can't finish part A. Tell the CAD team now |
| 12 (C-7) | Does the yaw angle come from the yaw motor's own encoder? | The code assumes yes | A separate turret encoder needs a code change |
| 13 (C-10) | Odometry pod positions from the centre | Cross-check of Pedro's Pinpoint Tuner | Warning only |
| 14 (C-11) | How many gate servos (2 expected, one per launcher) | Gates hold elements off the flywheels until firing | 1 or 0 needs a code change |
| 15–16 (H-1) | From the official field drawing: centre (x, y) of the red far and red audience CELL openings. Blue is calculated | The aim target | Every shot misses by the error |

Also check in CAD: folded robot fits an 18 in cube; unfolded fits 18 × 24 × 29 in (R102 / R105).

### H-2a. The auto draft (optional, before the robot)

1. Open the Pedro visualizer with the BIOBUZZ field image. Draw the auto on the **blue** side.
2. Start pose: touching the wall, on our side, not in the LOADING ZONE, not touching a FLOWER (G304).
3. Export as **Class** (not "full"). Never tick "mirror".
4. Send the export, and for each path say what happens at its end: SHOOT, INTAKE, PARK or nothing. Also
   say which paths push into elements or a wall.

**What the programmer does with Stage 1:** enters the edges, pivots and CELLs; keeps lines 7–9 to check
the Yaw Tuner; writes the real autos from the export (a class per auto next to `opmodes/SampleAutoPaths.java`, which stays the reference).

---

## Stage 2 — robot wired

### D-1. Device names

On the Driver Station: **Configure Robot**. Use exactly these names:

| Device | Name | Type | Note |
|---|---|---|---|
| Drive motors: left-front, left-back, right-front, right-back | `lf` `lb` `rf` `rb` | motor | — |
| Pinpoint | `pp` | goBILDA Pinpoint (I2C) | — |
| Intake motor | `intake` | motor | — |
| Yaw motor | `lt` | motor | its encoder cable in the **same port number** |
| Front / back flywheel | `fs` `bs` | motor | encoder cable in the same port number |
| Front / back hood | `fpanel` `bpanel` | servo | — |
| Front / back gate | `fg` `bg` | servo | — |
| Ramp | `ramp` | servo | — |
| Turret index switch (optional) | `yz` | **Digital Device**, Control Hub digital port 0 | a REV magnetic limit switch that sees a magnet on the front turret at forward |

- **Turret index switch (optional, recommended).** The turret zero is otherwise set by eye at every INIT
  ("both turrets straight forward?"); 1 degree off is 1.4 in sideways at 80 in. With the switch, program 3
  measures where it turns on, and from then on every INIT says "Turret zero: turn the turrets slowly LEFT
  through forward until this says INDEXED". The code works the same without it. A plain jig (pin, notch,
  printed block) that holds the turret exactly forward also helps when there is no switch.
- Want other names? Send them; the programmer changes one line and rebuilds.
- Check: program **1 Check Devices**, step 1, lists any name that doesn't match.
- Motors are exactly at the 8-motor limit (R503). Any extra motor is illegal. Servos: 5 of 8.

### D-2. Hubs

Name the hubs `19859-RC` / `19859-DS`. Change the Control Hub Wi-Fi password (R705 / R711).

---

## Stage 3 — the 9 setup programs (robot built and wired)

### How every program works

- Driver Station → TeleOp list → group **"19859 Setup"**. Run them **in number order**.
- **Top of the screen:** "PROGRAM 3 of 9: Yaw Tuner", then "STEP 4 of 11: …" and a progress bar.
- **"WHY:" line:** what this step is for.
- **Bold "> …" lines:** exactly what to do now.
- **"GOOD:" line:** what a good result looks like.
- **Grey "Buttons:" line:** only the buttons that work in this step.
- **Buttons:** **cross ✕** = next / save, **triangle △** = back, **circle ○** = no (where asked).
  Holding a button does not repeat it.
- **Red NOT READY line in INIT:** something this program needs is missing. Do that first.
- **Nothing moves in INIT** (rule R102). Motors and servos move only after START, and a servo only after
  you press square □ on its step.
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
| | 3 | Yaw Tuner | box | 1 |
| | 4 | Servo Positions | box | 1 |
| B, robot on the floor | 5 | Drive Tuning (Pedro's tuners on the Driver Station; part 1 still on blocks) | open floor | 1 |
| | 6 | Position Check | field | 5, CAD edge sizes (C-2) |
| | 7 | Flywheel Tuner | box | 2, 4 |
| C, on the field | 8 | Front Shot Tuner | field | 3, 5, 6, 7 + CELLs |
| | 9 | Back Shot Tuner | field | same as 8 |

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
| 1 Names | read | lists any missing name | "Every device name matches" → ✕ | Fix Configure Robot, run again (✕ won't advance) |
| 2 Battery | ✕ | voltage | 12.5 V or more | Swap the battery |
| 3 Encoders | one part at a time, as the screen says: "Turn ONLY the left-front wheel now", then left-back, right-front, right-back wheel, intake, turrets, front flywheel, back flywheel. Touch nothing else | that part's encoder must count, and no other one | "OK: its encoder counts, and no other one did" → ✕ (next part) | "MIXED UP: lf and lb" = turning that part counted on another port. Two parts turned at once, or one still spinning: let everything stop, △, turn only that part again. Same again = an encoder cable is in the wrong port (each one goes in the same port number as its own motor). Never counts = loose encoder cable or wrong port. △ = turn it again; ✕ always goes on and notes the problem in the results file |
| 4 Pods | robot on the floor; push about 1 ft forward, then slide about 1 ft sideways | "OK forward pod", "OK strafe pod" | both OK → ✕ | Pod cable unplugged, or a pod not touching the floor |

Afterwards the screen stays as a live readout of every encoder, servo and both pods (useful later).

### Program 2 — Motor Directions

**Why:** + power must mean "pull in" for the intake and "throw out" for each flywheel. If it doesn't, the
code reverses that motor.

**How:** each test runs one motor at 30% only **while you hold R2**.

| Step | Watch | Answer | If it stops |
|---|---|---|---|
| 1 Intake | do elements go IN? | ✕ yes / ○ no | — |
| 2 Front flywheel | does the wheel surface move the way that throws an element out? | ✕ / ○ | Red STOP "encoder counts the opposite way": the hub's speed control would run this flywheel at full speed. Check its encoder cable is in the same port number as its motor. If it is, swap that motor's two power wires (red / black) at its connector. Run program 2 again (this answer will change) |
| 3 Back flywheel | same | ✕ / ○ | same as step 2 |

- For the flywheels it also checks the encoder counts while running; you can't answer until it does.
- After a red STOP the program goes no further and does not load program 3.
- Afterwards: free jog. Dpad up/down picks any motor, R2 / L2 runs it.

Nothing to send now: programs 3 to 9 use these answers from the results file. **Programmer (at the end):**
sets `Intake.REVERSED` and `Launcher.FRONT/BACK_REVERSED`.

### Program 3 — Yaw Tuner

**Why:** one motor turns both turrets through gears. The code needs:
- how many encoder ticks make one degree of each turret, to know the angles;
- where the stops are, so it never hits them;
- how hard the turret is to start (kS), how fast it turns per power (kV), and how hard to push toward a
  target (kP), so it gets there fast without wobbling;
- the gear play (E-6): the encoder is on the motor, and the turret can rest up to half the play either side
  of it. Which side cannot be seen (pushed along it trails, carried by its own momentum it runs ahead), so
  the code does not guess: it aims at the target itself and misses by at most half the play. More than 1°
  of play: tighten the gear mesh.

**Before INIT:** robot on a box (it does not drive). Point both turrets **straight forward**: each barrel
parallel to the robot's left and right side edges. INIT makes that 0°. The INIT screen asks "Both turrets
pointing straight forward? They are zeroed now." If they weren't: STOP, turn them forward by hand, INIT
again.

**Part A — by hand.** The motor goes limp so you can turn it. Dpad left/right also turns it slowly (it may
go the opposite way to the arrow; that's fine). Each turret must turn **more than 90° each way** (C-6). If
you can't get a barrel straight left or right, check the cable and stops and tell the programmer.

| Step | Turn until | Why |
|---|---|---|
| 1 | FRONT barrel points straight **left**: parallel to the robot's front edge → ✕ | |
| 2 | FRONT barrel straight **right** → ✕ | front scale = (left − right ticks) ÷ 180. Using both sides halves any eyeballing error. Forward is midway between them: if that is more than 5° from the INIT zero, it stops with "the turrets were not straight forward at INIT": STOP, turn them forward, INIT again (or redo left / right with △) |
| 3–4 | same for the BACK barrel, against the robot's back edge | back scale |
| 5 | turn **left** until it stops (hard stop or cable tight) → ✕ | upper limit = stop − 10°. It refuses a stop less than 90° from forward |
| 6 | turn **right** until it stops → ✕ | lower limit = stop + 10°. Same 90° rule |
| 7 | back to **forward** (within 15°) → ✕ | Part B starts from the middle |
| 8 | **gear play (E-6):** the motor now holds still. Rock the FRONT turret gently left and right by hand and read on a degree scale how far it moves freely, end to end. dpad ↑ / ↓ = ±0.1° → ✕ | saved; the aim can be off by up to half of it (the code does not guess which side). Over 1°: tighten the gear mesh |
| 9 | clear the area, cables free, hands away → ✕ | from here it moves by itself, fast |

**Index switch (only if the robot has `yz`, D-1):** the screen shows "Index switch: turns on at … deg
turning left" once the turret has passed it **slowly** (under 15°/s) turning left. That happens on the way
back from straight right (step 3, or step 7): turn slowly through forward. "Not passed SLOWLY turning LEFT
yet" = do that before step 8.

**Part B — automatic.**
1. **Direction:** 10% power for a quarter second; which way the angle went = power sign.
2. **Friction (kS):** for each direction it first turns 10° the other way and comes back to the start, so
   the gear play is closed in the direction it will push. Then power slowly rises until the turret has
   turned half a degree. kS = the average of both directions.
3. **Speed (kV) and reaction time:** 4 power levels × 2 directions. Each run starts near one end and
   crosses, cutting power early enough to coast to a stop inside the limit. Steady speed = the angle turned
   over the last 0.3 s ÷ 0.3 s; the run counts only if both halves of that 0.3 s agree (within 10%, plus 2
   encoder ticks). Steady speed per power → kV. Reaction time = how far the turret lagged behind that steady
   speed since the power went on (for this kind of motion, the time to reach 63% of the speed). Both come
   from whole distances turned, so one loop's reading being a tick off doesn't matter.

**Part C — automatic.**
1. Start setting: kP = 1 ÷ (4 × reaction time). That is the setting that should not overshoot (checked in a simulation).
2. The turret jumps +30° → 0 → −30° → 0 and measures how far it **went past** each target, and how long it
   took to settle.
3. If it went past by 1° or less, it tries kP × 1.25 (faster). As soon as it goes past by more, it keeps
   the last good kP. If the very first try goes past, it tries kP × 0.8.

**Part D — automatic: turret homing (the last two steps, "Get ready: homing test" and "Automatic: turret
homing").** With it the turret finds its own zero after a power-up (also if someone turned it while the robot was
off), with no extra hardware and no turning it forward by hand.
1. Press ✕ to start (△ skips it: homing stays off). Hands and cables clear: the turrets are driven into their
   stops. STOP aborts at any time.
2. **Slow touches:** for each stop, the turret creeps in (20°/s), stalls, backs off 5° and creeps in again, 5
   times. A stop is usable only if its 5 angles agree within **0.5°**. The encoder's tick must also be 0.25° or
   finer. A stop that is a tight cable and not a firm stop fails here.
3. **Fast hits:** on the best stop, a ladder of hit speeds (15 to 150°/s, never more than 80% of the turret's top
   speed). The fastest speed where the fast hit lands within 3° of the slow touch is kept (V_fast).
4. **Crossing:** if both stops passed, the fastest crossing speed (up to 250°/s, at most 70% of top speed) that
   stops 6° short of the other stop and goes past its target by 3° or less.
5. The screen says HOMING AVAILABLE (both stops, or one) or NOT AVAILABLE with the reason, and prints the homing
   time for your travel: typical and worst case (both stops are always checked, so about 4 to 5 s at worst). A
   homing on one stop only is not cross-checked: the screen says so; fix the other stop and run part D again. Saved: `yawHomeLeftDeg`, `yawHomeRightDeg`, `yawHomeFastDegS`,
   `yawHomeCreepDegS`, `yawHomeCrossDegS` (NaN = not usable); info only: `yawHomeSpreadDeg`, `yawHomeTypicalS`,
   `yawHomeWorstS`. The angles are measured from the program-3 forward, so a by-eye forward that was off by a
   degree is off by that in every homing (the shot tuners' yaw trim takes that up).

**How homing is used (TeleOp and the shot tuners; the autos do not home):** see the controls table. In INIT,
options arms it; it starts at START and the robot can drive while it runs. After START a press of options starts
it at once. It drives the nearer stop fast, backs off, creeps in (that is the first reference), then always crosses
and checks the other stop (it backs off and creeps in there too, the same touch): the two must match the measured
span within 0.7°, and the zero is their average. The screen shows how far apart they were ("Turret homed in 2.9 s
(both stops, 0.02 deg apart from the measured span)"); a few hundredths is normal, tenths every time = run part D
again. One
stop alone cannot tell the stop from a snag (a cable that catches the turret): a zero taken there would be off by
however far the snag is, so the second stop is never skipped. A mismatch fails loudly ("TURRET HOMING FAILED")
instead of giving a wrong zero. While it homes, fire mode (L1) cannot be turned on and R1 only collects.

**Safety:** if the turret ever goes 5° beyond a limit (before part D), everything stops with a message. Fix what
it says and run again from the start.

**Nothing to send now:** the results file keeps everything, and the next programs use it. **Programmer
(at the end):** compares the measured scale with the CAD ratio (a big mismatch means a misread "straight").

### Program 4 — Servo Positions

**Why:**
- **Hood range:** so the hoods never push into their stops.
- **Ramp:** stowed and deployed positions.
- **Gates:** closed (holds elements off the flywheel) and open positions.
- **Gate travel time:** how long firing waits before feeding.

**Buttons on every servo step:**
- square □ = move the servo to the shown value (**it doesn't move until you press □**);
- R1 / L1 = ±0.01; R2 / L2 = ±0.001;
- ✕ = save and next; △ = back.

| Step | Set | Rule |
|---|---|---|
| 1 / 2 | front hood lowest / highest | stop just BEFORE it buzzes or strains (a buzzing servo burns out) |
| 3 / 4 | back hood lowest / highest | same |
| 5 | ramp stowed | fully in, inside the 18 in starting size |
| 6 | ramp deployed | out to the bottom of a FLOWER, still inside 18 × 24 in |
| 7 / 9 | front / back gate closed | with an element in place, it must not touch the flywheel |
| 8 / 10 | front / back gate open | an element passes through freely |
| 11 | gate travel time | gates start closed. Film both in **240 fps slow motion** while pressing □ (each press opens or closes both). In the video, count the frames from the slower gate starting to move until it is fully open. Enter the count: dpad ±1, R1 / L1 ±10 → ✕ |

The second step on the same servo starts where the first one ended. Nothing to send now. Program 4
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
- TeleOp's heading hold (hold ✕) and its face-the-CELL turn use Foresight's numbers too (after build 2).
- Pedro's own tuning web page is not in the robot app any more (rule R704 question gone).

### Program 6 — Position Check

**Why:**
- **How the robot knows where it is:** the Pinpoint measures only movement (2 pod wheels + a gyro), so it
  is told the starting spot once and then adds up every movement.
- **Velocity direction:** the turret's rate feedforward needs velocity in field directions to stay on the CELL while you drive (no shots are fired on the move). This
  program proves it.
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
| 2 Hold | ✕ | holds 70% of top speed; waits until the speed has stayed within 5% of it for 1 s (at most 6 s), then for 2 s measures the biggest wobble around the average speed, and how far the average is off the target | ready band = 1.5 × wobble + how far the average is off the target (ready to fire = within the band of the target) | "did not stay within 5%": the band is measured anyway; a wide band = check for rubbing, △ |
| 3 Recovery | load **ONE** element in this launcher only (hold L2); R2 fires once the speed is steady. After the first shot: did the element fly OUT? ✕ yes / ○ no. Then after every shot the screen shows the result and the next P: press ✕ when you are ready, load the next element, R2. About 9 shots (at most 12). △ = do not count this shot (same P again), □ = stop and keep the best P so far | opens the gate, waits the gate time, feeds 0.55 s like a match shot, then watches 2 s: how far the speed dropped, when it is back in the band, and when it is back **to stay** ("steady after": ready for the next element). The program tries the start P × 1, 2, 4, 8, 16, 32 (and × 0.5 if × 1 is best), then just below and above the best, then fires the best once more to check it. Shots where no element reached the wheel, or two went at once, are not counted. Every shot goes to `biobuzz_flywheel_trials.csv` | the P whose shots are steady soonest (two P values within 0.05 s: the softer one). ✕ keeps it. dpad ↑ / ↓ = set P yourself instead (then ✕ keeps your P) | ○ no = it asks "is it a DIRECTION problem?": watch the wheel where it touches the element. ✕ = its surface moves into the robot: the program flips the wheel, saves the new direction (`<side>FlywheelReversed`) and you fire again. ○ = it spins the launch way: check the gate opens and the intake pushes the element in, fire again. L2 runs the intake to load |
| 3b Band again | nothing (it starts by itself after ✕ keeps the P) | holds the test speed again with the chosen P and measures the wobble | the ready band with the chosen P is saved (a stronger P can change the wobble). If it is more than 25% narrower than the first one, one check shot (load ONE, R2) shows the wheel still settles in it; if not, △ runs the P search again | — |
| 4 Idle | ✕, nothing loaded, hands clear (about 30 s). Then dpad ±50 (hold L1 = ±200) if you want to change it, ✕ saves, △ = measure again | the wheel holds 0, 20, 35, 50 and 65% of the test speed in turn and times the climb from each to the test speed; the pick and its neighbours run twice | the lowest idle whose climb is within 15% (or 0.1 s) of the fastest; never above 65% of the test speed | a climb over 3 s = check for rubbing |

**Later, once programs 8 and 9 are done (optional): "7 Flywheel Tuner: shot-speed check".** Program 7 tunes at
70% of top speed; real shots run from the shortest to the longest table speed. This checks each flywheel at both:
it holds each (nothing loaded) to measure the band there, then you fire two shots at each (load ONE, R2), then it
picks the idle speed again against both. The band per speed is saved only if every check passes (both speeds
settled, each band under 10% of its speed, program 7's band fits between them within 40%, all four shots steady); if
the two bands are within 25% of each other one band is enough. A band is never saved narrower than program 7's (a
2 s wobble reading is sometimes low, and a too narrow band would hold shots back), so this only ever widens the band
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
| Before a shot | 32008 far: 0.45 s minimum spin-up + 0.75 s continuously at speed | fires only after both flywheels stay inside their ready band without a break (0.3 s in auto, 0.15 s in TeleOp and programs 8 and 9) |
| Between balls | 32008 auto paused the feed | the feed pauses whenever a flywheel drops out of its band |

### Programs 8 and 9 — Front / Back Shot Tuner (need 3, 5, 6, 7 + the CELLs)

**Why:** the shot table. At 10–16 distances it stores the measured flywheel speed and hood that score.
In a match the code blends in a straight line between the two nearest points and never guesses beyond
the measured range. This is more accurate than curve fits, which bent the wrong way in 32008's code.

**Semi-automatic:** the robot drives itself (Pedro, odometry) to each shooting spot, facing the CELL, and
holds it there. The turret aims itself. You load, fire, say in / not in, and nudge the settings.
Spots start 36 in from the CELL and go out 5 in at a time, fanned out sideways so they stay on your half
of the field. Keep the field clear: it drives at START and after every saved point.

**The points file is the whole table.** Every point stays in `biobuzz_<side>_points.csv` from one run to
the next, so you can stop and carry on another day. Spots that already have a point are skipped.

**In INIT:**
1. Pick the alliance: □ blue / ○ red.
2. Put the robot in the start corner (program 5) and press **options**. No auto run is needed.
3. If it says "TURRET NOT ZEROED": press **options** (robot in the start corner: the turret homes itself after
   START, see program 3 part D), or turn **both** turrets straight forward by hand and press **✕**. The zero is
   never taken by itself: after a new build, a restart or a reboot the turrets may still be turned from the last
   program, and a zero taken there makes every shot miss by that angle.
4. "Points saved earlier" shows the points already in the file. To build a new table instead, press
   **share**: it says START OVER, and at START the old file is renamed to
   `biobuzz_<side>_points_<date>_<time>.csv` (kept as a backup, not used). Press share again to keep them.
5. The "Ready" line must be green. Press START. The robot drives to the first spot.

**At each spot:**

| Button | Does |
|---|---|
| L2 (hold) | intake: load ONE element |
| R2 | fire: waits until still, aimed and at speed, then opens the gate and feeds, like a match shot |
| ✕ / △ | it went in / it did not go in |
| □ / ○ | flywheel speed − / + (20 ticks/s) |
| dpad ↑ / ↓ | hood up = more launch angle (steeper, higher arc) / down = less (0.005 a press) |
| dpad ← / → | yaw trim left / right (0.5°) |
| sticks | drive yourself (takes over from the auto drive) |
| R1 | drive back to the planned spot |
| L1 | skip this spot (it can't score here) |
| options | undo the last point |
| share | finish (asks again if you have fewer than 10) |

Hold □ / ○ and the dpad to repeat. Short or long: change the speed or hood. Left or right: yaw trim
(it stays for later spots). Any change restarts the count. **4 in a row:** the point saves itself, the
pad rumbles and the robot drives to the next spot. "Off the spot" shows how close it parked. If it shows
"CHECK: speed drops as distance grows", redo that point (options = undo, R1 = back to the spot).

**Feed check (about 3 times per launcher: near, middle, far).** Right after a point saves with no feed-checked
point within 20 in, the screen says **FEED CHECK**: the robot stays put; load ONE and R2, again and again (about
12–15 shots), **nothing to answer**. The program fires each at a different feed (intake power 0.35 / 0.5 / 0.65 /
0.8, then one in between) and watches how much the flywheel slows on each: the feed whose slow-downs are the most
alike gives the most repeatable shots; a feed that sends two elements at once is out; nearly equal = the faster
one. That point's feed is replaced ("FEED 0.65 at 36 in"), later spots start from it, and the robot drives on by
itself. **L1 = skip the check and drive on** (the point keeps its feed; no new check within 20 in this session).
Speed / hood / trim buttons do nothing during a check. The feed only changes when one is clearly steadier than the
feed in use (by more than the hub's speed reading can blur); otherwise it stays.

**How the table is used:** not straight lines between the points but a curve that averages neighbouring points
(each point is just the first setting that went 4 for 4, somewhere inside the window that scores; the average
lands nearer the middle). The curve stays within 2 presses of every point; if it cannot (a lucky / unlucky point)
the screen says "curve could not follow speed at 56 in: redo that point?". Past the farthest point it holds the
curve's end value (the sim tested DECODE's "keep the curve going": it scored worst). `Launcher.SMOOTH_CURVE = false`
goes back to straight lines.

The average yaw trim is saved with every point (`<side>YawTrimDeg`, in that turret's own degrees, so a STOP
before share keeps it) and the code aims with it from then on. More than 10° is never saved or used: it means
the turret was zeroed while turned (STOP, both turrets straight forward, INIT, ✕). More than about 1°: tell
the programmer (the CELL position may be off).

**Send:**

```bash
adb pull /sdcard/FIRST/biobuzz_front_points.csv
adb pull /sdcard/FIRST/biobuzz_front_shots.csv
adb pull /sdcard/FIRST/biobuzz_back_points.csv
adb pull /sdcard/FIRST/biobuzz_back_shots.csv
```

Plus a photo of the RESULTS block on the Driver Station.

The `_points_<date>_<time>.csv` backups are not needed. Program 9 ends by loading BIOBUZZ TeleOp.

### Alongside the programs (no program needed)

- [ ] **E-1** Weigh the robot with the battery (kg). Send it.
- [ ] **E-12** Tape-measure the edges (C-2) and turret pivots (C-3). Send any difference over 0.25 in.
- [ ] **G-7** In practice watch "Locked" and "Residual F/B" in TeleOp. If shots miss while "Locked" shows,
  send the distance and the residuals.
- [ ] **G-8** If the drivers always trim the same way at one distance, rerun the Shot Tuner there (keep
  the old points: don't press share in INIT) and save a new point; it replaces the old one.

**Step IDs that appear in on-screen messages:**

| Step IDs | Where |
|---|---|
| E-3, E-4 | program 2 |
| G-1, E-6 | program 3 |
| E-7, E-8 | program 4 |
| F-1 … F-5, E-9 | program 5 |
| E-10, H-0 | program 6 |
| G-3 | program 7 |
| G-5 | programs 8 and 9 |

---

## Stage 4 — on a real field

- [ ] **H-2b Autos.** Run "SAMPLE Auto Blue" and "Red" first (the reference route: shoot, intake, shoot, ramp, park), then the real autos. Send what goes wrong:
  - a leg that stalls;
  - a pose that is off (how much, which side);
  - the "opponent side" warning (G402).
- [ ] **H-3 Corner reseed.** Put the robot square in any corner, press options in TeleOp. The pose shown
  should match that corner.
- [ ] **H-1b Real CELL positions.** Real fields are built to about an inch of the drawing. At every event,
  tape-measure each CELL's centre from the two nearest walls and send them; the programmer updates H-1 (one
  build over the USB cable). A `<side>YawTrimDeg` over about 1° from programs 8 / 9 also points here.

## Before every match

1. Both turrets pointing straight forward (with the index switch: INIT, then turn them slowly left through
   forward until the screen says INDEXED). Ramp stowed by hand.
2. INIT our real auto for our side (until there is one, **"SAMPLE Auto Blue"** or **"SAMPLE Auto Red"**). That zeroes the turrets, sets the
   alliance and hands the position to TeleOp.
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
   `source: cad` and note which [ROBOT] step checks it.
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
| `Intake.REVERSED` | `subsystems/Intake.java` | E-3 |
| `Launcher.FRONT_REVERSED`, `BACK_REVERSED` | `subsystems/Launcher.java` | E-4 |
| drive motor directions (`frontLeftDirection` … `backRightDirection`), pod directions (`xPodDirection`, `yPodDirection`): `public static` fields | `tools/codegen/Constants.java.tpl`, then `render.sh`. Not in `team-config.yaml`: `emit_tuning` renders numbers only | F-1, F-2 |
| sample auto (reference route, waypoints are examples) | `opmodes/SampleAutoPaths.java`, `SampleAutoBlue`, `SampleAutoRed`; keep current after every step, see BIOBUZZ_PLAN "Sample auto" | H-2 |

Calculations:

| Step | Calculation |
|---|---|
| C-5 | cross-check only: ticks/deg ≈ encoder counts per motor output rev (hardware catalog) × motor revs per turret rev ÷ 360; compare with G-1 |
| all | `biobuzz_results.csv` lines are `program,key,value,time_ms`. Use the latest line per key. `<side>` = `front` or `back`. **Into `team-config.yaml`, same key name:** yawTicksPerDegFront, yawTicksPerDegBack, yawMinDeg, yawMaxDeg, yawPowerSign, yawKs, yawKv, yawTuningVoltage, yawKp, yawPlayDeg, yawIndexDeg (only with the index switch) (program 3); frontHoodMin, frontHoodMax, backHoodMin, backHoodMax, rampStowed, rampDeployed, frontGateClosed, frontGateOpen, backGateClosed, backGateOpen, gateTravelS (4); xPodOffset, yPodOffset and the 17 Foresight numbers (5, Pedro's tuners, saved under the code's names); podTicksPerMm, imuYawScalar, velocityFrameField (6); `<side>`FlywheelP, `<side>`FlywheelI, `<side>`FlywheelD, `<side>`FlywheelF, `<side>`ReadyBand, `<side>`IdleVelocity, `<side>`FlywheelVoltage (7). **Into code:** intakeReversed → `Intake.REVERSED`, frontFlywheelReversed / backFlywheelReversed → `Launcher.FRONT_REVERSED` / `BACK_REVERSED` (2); frontLeftDirection … backRightDirection, xPodDirection, yPodDirection → the `Constants.java.tpl` fields (5). **Info only:** devicesFound, batteryVolts, encodersNotCounting (1; ports whose own part never counted), encodersCrossed (1; `lf-lb` = turning the lf part counted on port lb: an encoder cable in the wrong port, or two parts turned at once; `none` = good); `<side>`FlywheelEncoderOpposite (2; true = program 2 stopped for the wiring fix); yawReactionTimeS, yawSettleS, yawPastTargetDeg, yawSpeedPoints (3); velocityFrame, loopErrorInches, loopErrorDegrees (6); `<side>`TopVelocity, `<side>`FlywheelThrowsOut (7; false = wrong program 2 answer, nothing else saved for that flywheel); `<side>`ShotPoints (8, 9: table points in the points file, earlier runs included). |
| G-5 | `biobuzz_<side>_points.csv` is the launcher's whole table (earlier runs included; ignore the `_points_<date>_<time>.csv` backups). **Table points:** every line that starts with a number (`distance_in,speed,hood,feed,battery_v`), except one cancelled by a later `# removed <distance>` line with the same distance (2 decimals). **Not table points:** the header, `# session <date time>` lines (one per run), `# removed` lines, and `# check …` lines (older runs only). The feed column is the fixed feed power the tuner fired with. A `<side>YawTrimDeg` result over about 1° means the aim is off: recheck the turret zero (program 3) and the CELL coordinates (H-1). Enter the table points as `<side>ShotNN{Distance,Speed,Hood,Feed}` = `value: <number>, origin: measured`, NN = 01, 02, … (any order; the code sorts by distance). **Then clear every higher slot left from an older table:** `value: null, origin: untuned` on all 4 keys, up to 16. A null renders as `Double.NaN` and `Launcher` skips any row with a NaN, but an old complete row would still be used. Code interpolates linearly between points |

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
- **Launchers:** two, on two turrets. Front launches NECTAR, back launches POLLEN. Each has 1 flywheel
  motor, 1 hood servo and 1 feed gate servo.
- **Yaw:** one motor turns both turrets through gears; its encoder gives the angle. Both turrets must
  turn the same direction.
- **Feed:** the intake pushes elements in; the ramp sends NECTAR up to the front launcher and POLLEN
  under to the back. Gates hold them off the flywheels. To fire, both gates open and the one intake
  motor feeds both launchers.
- **Aim:** odometry only. Target = our CELL on the robot's side of y = 72. The yaw points midway
  between the two launchers' ideal angles.
- **Ramp:** a servo ramp takes POLLEN from the bottom of a FLOWER. At most 4 elements, limited by the mechanism.
- **Park:** LOADING ZONE.
- **Parts:** motors 8 of 8; servos 5 of 8 (2 hoods, ramp, 2 gates). Any extra motor breaks R503.
- **Auto:** a path runner. INIT-ing the correct-side auto sets the alliance, zeroes the yaw and hands
  the robot position to TeleOp.
- **Rules in the code:** nothing moves during INIT (R102). Panels only in practice OpModes (R704).

### Driver controls ("BIOBUZZ TeleOp", field-centric)

The buttons are DECODE's. The only BIOBUZZ extra is the ramp, on a shape button DECODE leaves free. TeleOp is always field-centric.

| Input | Action |
|---|---|
| gp1 left stick / right stick X | drive / turn |
| gp1 R2 | intake in (launchers off, gates closed) |
| gp1 L2 | eject |
| gp1 L1 | **fire mode** on/off. On (armed): the launchers spin up and R1 may fire once it is ready (turret locked, speed steady, robot stopped). Off: R1 collects. The pad light is red while armed but not ready, green when ready to fire, and the pad rumbles at ready |
| gp1 R1 (hold) | armed: fire both: waits for aim lock + both flywheels at speed for 0.15 s, opens gates, feeds. It never fires on the move: it waits until the robot has stopped. When you let go of the sticks the turret already aims at where the robot will stop (Foresight's braking model), so it is locked by the time it is still; the screen then shows "last stop: predicted X in off" (about 1 in is good; several inches means redo the Foresight Tuner). Not armed: intake in (DECODE) |
| (always) | auto-turret = DECODE lock-on: the turrets follow the CELL all the time, launchers on or off. The robot never turns by itself |
| dpad up/down | distance trim ±2 in (speed when auto-shooter is off) |
| dpad left/right | angle trim ±1° (separate near / far) |
| gp2 L1 / gp2 R1 | auto-turret on/off / auto-shooter on/off (DECODE) |
| gp2 triangle / cross (hold) | hood up/down (auto-shooter off) |
| gp1 options (DECODE's start) | **tap:** the turret starts homing (if program 3 part D allows it) and, on release, the Pinpoint is reseeded: the robot is square in the nearest field corner. **Hold 1.5 s:** homes the turret only, the Pinpoint is never reset. In INIT: a tap sets the start corner and arms homing for START, a hold only arms homing. In the shot tuners a short press stays "undo last point" and a hold re-homes |
| gp1 triangle (extra) | ramp out / in |
| feedback | rumble when ready; pad light green = ready, red = launchers on but not ready |

INIT without an auto first: red, start corner (H-0), nothing to press. square = blue, circle = red (sets the
start corner again), options = the robot is in the start corner now. A setup program's pose is never used.
After a new build or a reboot it says TURRET NOT ZEROED: options in the start corner (homing), or both turrets straight forward and ✕. Homing runs while you drive; fire mode cannot be turned on until it ends (blue pad light, a short rumble when done).
With the turret index switch: INIT also asks to turn the turrets slowly left through forward until it says
INDEXED.
PS4/PS5 controllers (the pad light is used).

---

# Part 5. Still open (team decisions)

- Gates: code assumes 2, one per launcher (C-11).
- Physical status light: 3 servo slots free; no rule found about lights, so ask the inspector.
- Yaw homing switch: would remove "turrets face front at INIT"; needs a sensor; must not move in INIT (R102).

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
