Superseded 2026-10-17: see docs/HARDWARE_UPDATE_PLAN.md

# Turret homing: plan and what was built

Status: built 2026-10-08 (programs 3, 8, 9 and TeleOp; simulator tests `test_homing.py` and `test_p3_yaw.py`). Two
details differ from the first version of this plan: the second side runs when the whole homing is *projected* to end
within 4 s (not "if the first side took under 2 s"), and the crossing is capped by the distance left to the expected
stop so a late loop cannot carry the turret into the stop at crossing speed.

Goal: after a power-up (or a twist while off) the turret finds its own zero with no extra hardware, no turning it
forward by hand, and no pit procedure. Decided with the team on 2026-10-08.

## Requirements (the team's words, in order)

| # | Requirement |
|---|---|
| R1 | No extra hardware. |
| R2 | Homing only happens on the options button. A tap does the Pinpoint reseed in the corner and homing. Holding options 1.5 s homes only: the Pinpoint is not reset during or after the hold. |
| R3 | Autos: homing is off. The auto's INIT assumes the robot is at its start pose and the turrets are at 0. |
| R4 | More accurate shots, so accuracy beats availability. |
| R5 | The shot tuners (programs 8 and 9) home the same way as TeleOp. |
| R6 | Fast and accurate: at most 4 s. CHANGED 2026-10-10: both stops on every homing (about 4 to 5 s at worst), because one stop cannot tell a stop from a snag; a sim run under load stalled mid-travel, homed on one stop and was 49 deg off. |
| R7 | While homing the robot can drive and intake, but cannot shoot or enter ready (fire) mode. |
| R8 | Method: go left as fast as possible and hit the stop, back off a tiny bit, creep back in slower for accuracy, then repeat on the right. |
| R9 | The turret finds its position wherever it was left, including after being twisted while the power was off. |

## How it works

The Yaw Tuner's stops were found by hand, so program 3 gets a new motor-driven step that re-measures them. Homing then
uses the same method, so any bias cancels out.

### Program 3, new step (after the friction and gain steps, so the power sign, kS and kV are known)

1. For each side, hit the stop 5 times at a slow creep speed (about 20 deg/s): drive in, detect the stall, record the
   angle, back off 5 deg, repeat.
2. A side passes if its 5 stall angles agree within **0.5 deg**. A side that fails is not used. If neither passes,
   homing is OFF and the manual step (both turrets forward, press cross) stays.
3. Speed ladder (about 15, 30, 60, 90, 120, 150 deg/s, STOP aborts): the fastest hit speed whose rough stall stays
   within about 3 deg of the precise creep stall. That is V_safe.
4. The fastest speed for the crossing between stops that the turret can brake from, from the braking numbers the
   program already measures (V_cross).
5. Results (new keys, through the code, the config file, LiveResults and apply_results):
   `yawHomeLeftDeg`, `yawHomeRightDeg` (front-turret deg of each passing stop, from forward), `yawHomeSpreadDeg`
   (info only), `yawHomeFastDegS`, `yawHomeCreepDegS`, `yawHomeCrossDegS`.
6. The screen prints the worst-case and typical homing time for the measured travel, and the encoder tick size
   (the accuracy floor). If one tick is over 0.25 deg, the 0.5 deg pass mark cannot be met and it says so.

### The homing run (a small state machine called once per loop; the drive, intake and Pinpoint code are independent)

1. **Pick the first stop:** the passing stop nearest the turret's best guess (the current encoder angle: about forward
   after a power-up, accurate after a plain OpMode restart).
2. **Fast hit:** sweep to it at V_safe. A stall is "motor driven, encoder moved at most 1 tick in 3 loops". Power off.
   If the app is running and the encoder is probably valid (a hold on options mid-session), slow down for the last
   25 deg before the expected stop.
3. **Back off about 3 deg, creep in at the creep speed,** stall, dwell 0.15 s. That angle is reference 1. The zero is
   set from it, and the turret now knows its position.
4. **Second side, only if reference 1 was ready within about 2 s:** cross under position control at V_cross with a
   braking ramp to about 6 deg before the other stop, creep in, stall, dwell. That is reference 2.
5. **Check and combine:** the distance between the two references must match the span from program 3 within 0.7 deg,
   otherwise one stall was false and homing fails to the manual step. If both agree, the zero is the average of the
   two. If the second side was skipped for time, reference 1 alone is used ("one side only" on the screen).
6. **Gear play:** after homing the turret is treated as having last moved toward the stop it touched last, so the play
   compensation stays consistent with how the stop was approached.
7. **Failure** (no stall after the full travel plus a margin, about 15 s, no movement at all, references disagree):
   power off, message, the manual zero (cross in INIT) is the fallback. No retry loop.

Typical time is about 3.2 to 3.4 s; the "second side only if the first finished within about 2 s" rule keeps the
total near 4 s even when the turret starts at the far end. Formula: first leg = distance to the nearer stop / V_safe,
plus about 0.6 s, plus about 1.6 s for the second side. Program 3 prints the numbers for the real travel.

### Triggers (options button)

| Where | Action |
|---|---|
| TeleOp or shot tuner INIT | A short press (acts on release) sets the start corner and **arms** homing. A hold of 1.5 s arms homing only (no start corner). The turret moves only after START (R102/G403). |
| TeleOp, after START | Homing starts **at the press**. A short release (under 1.5 s) then does the Pinpoint reseed in the corner. Holding to 1.5 s means the release does nothing: the Pinpoint is never reset. |
| Shot tuner, after START | In the tuners options already means "undo the last point", so a short press stays undo. A hold of 1.5 s re-homes (undo does not happen). Arming in INIT works as in TeleOp. |
| Autos | No homing, no change (R3). |

A turret that is not zeroed and not armed keeps today's manual rule: INIT shows "TURRET NOT ZEROED", cross zeroes by
hand, and the turret is off until then.

### While homing (R7)

- Drive, R2 intake in and L2 eject work normally.
- L1 (fire mode) is ignored with a message and fire mode is forced off; the flywheels stay idle and the gates closed.
  R1 runs the intake as it does when not armed. After homing, fire mode stays off until L1 is pressed.
- Screen: "TURRET HOMING" with a timer, a distinct pad light colour, and a short rumble when it finishes.
- Shot tuners: R2 fire is ignored while homing; their drive to the first spot continues.
- STOP at any time stops the motor; the turret stays unzeroed.

## Files to change

Turret code (`Yaw` homing state machine, `TurretZero`), `YawTuner` (the new step), `TeleOpMain` (options tap and hold,
fire blocking), `ShotTuner` (options, fire blocking), `Tuned` and its template, `team-config.yaml`, `LiveResults`
(keys apply by name), `apply_results.py`, the Start-tab text, and `docs/BIOBUZZ_PREREQUISITES.md` (program 3, controls).

## Tests (simulator)

The sim has hard stops (150 to 175 deg each side by default), a cable-tight scenario (soft, should fail the 0.5 deg
check), and a scenario setting `turret.startDeg` for a turret left anywhere. A new simulated power-up is a new run.

1. Program 3 measures both stop angles within 0.5 deg of truth, picks the faster method, prints times.
2. The tight-cable robot fails the check and shows the fallback.
3. Turret started at several angles (near forward, near each end), robot on the floor, tap options in the corner:
   zero within 0.6 deg of truth, total time at most 4.2 s in the sim, driving works during the homing.
4. Hold 1.6 s: homing runs, the pose is unchanged. Tap: the pose changes on release.
5. While homing, L1 does not arm, R1 does not fire, the intake runs, the gates stay closed.
6. Never-stall and motor-dead cases abort cleanly. Skipped-second-side case uses reference 1 alone.
7. A shot tuner run waits for the zero, then fires.
8. The existing TeleOp tests that zero with cross keep working through the manual fallback; the full suite at the end.

## What only the real robot can show

How fast the stops can be hit safely (the ladder, with someone watching and STOP ready), whether the stops repeat
within 0.5 deg, the real stall detection, and the real homing time. First real run: robot on a box, low speed first.

## Known trade-offs the team accepted

- Every options tap in the corner re-homes (about 3 to 4 s without aiming; driving and intake still work).
- A bad by-eye zero from an auto carries into TeleOp until options is held to re-home.
- A stop that does not repeat within 0.5 deg switches homing off (accuracy over availability).
