# Smooth shot curve — PLAN (not built)

Goal: the speed and hood the robot uses at any distance are as close as possible to the middle of the "it scores"
window, not just to the setting that happened to pass 4-in-a-row at each spot. Programs 8 / 9 stay exactly as they
are (manual buttons, 4 in a row saves a point). Only how the saved points are turned into a speed / hood at a
distance changes.

## 1. Why a curve, and what kind

Today: straight lines between saved points (`Launcher.lookup`), ends held flat.

- The curvature error of straight lines is tiny (~0.3 ticks/s between points 5 in apart on DECODE's own fit).
- The real error is **where each point landed in its window.** A point is the first setting that went 4 for 4, so it
  can sit anywhere in the scoring window (roughly ±25–40 ticks/s, ±0.01–0.02 hood). Straight lines copy that scatter
  exactly. A fit that **averages neighbouring points** estimates the window's centre — the most forgiving setting.

So the curve must **smooth** (not pass through every point) but stay **local** (one far point must not bend the
whole table) and **never overshoot** between points.

Chosen method: **local weighted quadratic regression (LOESS)**, one per column (speed, hood).
At distance d: fit a quadratic to the nearest K points (K = 5), weighted by distance from d (tricube weights),
evaluate at d. Smooth, local, handles any shape (including the hood flattening out at long range).

Rejected:
- one global polynomial (DECODE's way): one bad point or a shape change at the far end bends everything;
- a curve through every point (spline / PCHIP): smooth but keeps every point's scatter — no accuracy gain;
- fitting offline and typing coefficients: one more manual step to get wrong.

## 2. Picking the method from the data (no guessing)

For each column, **leave-one-out check**: for every saved point, predict it from all the others with each method
(straight lines, LOESS with K = 4, 5, 6) and take the method with the smallest typical miss. With well-behaved points
LOESS wins; with very few points (< 5) straight lines are used automatically. Computed on the robot when the table
loads (16 points: well under 1 ms), nothing to type.

## 3. Guards (accuracy first, never worse than today)

1. **Never far from a measured point.** At each saved distance the curve may differ from that point by at most
   2 tuning steps (40 ticks/s, 0.01 hood). Further = that point is clamped back to the limit, and the point is named
   on the screen (it is probably a lucky / bad point: redo it).
2. **No overshoot.** Between two neighbouring points the curve stays within their range plus 1 step.
3. **Speed must not drop as distance grows** (same rule as today): a fit that would dip is replaced by straight lines
   for that stretch.
4. **Ends:** past the first / last point the curve's end value is held. (The team first chose DECODE's "keep the fit
   going"; the sim scored it worst, see section 8, and the team switched.)
5. **Clamps:** hood inside program 4's range, speed under 95 % of top speed.
6. **Speed and hood together:** they are smoothed separately, which is only safe if the student keeps one
   consistent style (mostly speed changes, hood moved only when needed). The check in 1 catches a table that mixes
   "slow + high arc" and "fast + flat" spots; the guide will say to keep the style consistent.

## 4. Where it lives

- New `ShotCurve` (plain Java, no hardware): points in, `speed(d)` / `hood(d)` / `feed(d)` out, plus the chosen
  method and each point's leave-one-out miss.
- `Launcher` builds it once from the table (code or results file) and uses it instead of `lookup`.
- A `Tuned` switch `shotCurve` = `smooth` / `lines` (default `smooth`), so the team can compare on the real robot and
  fall back with one change.
- Program 8 / 9 screen: after each save, one line "curve: smooth (K = 5), biggest point miss 12 ticks/s at 56 in".
  Nothing automatic happens; the student decides whether to redo a point (options = undo, R1 = back).
- Setup Report: the method per launcher and every point's miss.

## 5. Proving it (before it is the default)

1. **Unit rules** (no sim): a straight line and a parabola come back exactly; noisy points come back closer to the
   true curve than straight lines (measured over many random cases); never overshoots; monotone stays monotone;
   one wild point moves the curve by less than a third of its error and is named; < 5 points = straight lines.
2. **Sim, head to head:** the same saved tables, scored by the sim's grader with `lines` and with `smooth`, over at
   least 10 seeds and both launchers. `smooth` becomes the default only if its make rate is **higher on average and
   never lower by more than one shot in 24 on any seed**. The numbers go in this doc.
3. **Sim, between points:** the grader also scores distances halfway between saved points (where the two methods
   differ most).
4. **Real robot (after programs 8 / 9):** shoot 10 from three in-between distances with `lines`, then with `smooth`;
   keep the better one.

## 6. Team decisions (2026-10-10)

1. **Past the last tuned point: DECODE's logic.** DECODE evaluated its fitted curve at any distance and clamped the
   result (speed 1000–2000, hood 0.2–1.0). Here: beyond the first / last point the local quadratic fitted to the end
   points keeps going, clamped to the hood range (program 4) and 95 % of top speed. Guard 4 in section 3 changes to
   this. (Straight lines, the fallback, still hold the end value.)
2. **Feed: set automatically from the wheel's dip** (no buttons). Section 7.
3. **Default: `smooth`**, `lines` one switch away, once section 5 passes.

## 7. Feed power, automatic (no buttons)

### What the feed can and cannot be judged by

- The **depth** of the wheel's dip when a ball goes through is mostly the energy the ball took: it depends on wheel
  speed and the ball, hardly on the feed. A rule "change feed until the dip hits a target" would chase noise.
- The **spread** of the dips over several shots is the useful signal: the energy a ball takes follows its exit speed,
  so steady dips = steady exit speeds = repeatable shots. Feed changes that on a real robot (how squarely the ball
  enters, slip, balls bunching).
- **Two dips** inside one feed = two balls went together (feed too fast / gate open too long): always bad.

### The algorithm

Per launcher, at three spots (near, middle, far: the first, the middle and the last spot of the session):

1. Candidate feeds 0.35, 0.5, 0.65, 0.8 (intake power), 3 shots each, at that spot's tuned speed and hood (after
   the point saved, so the speed / hood tuning is never mixed up with feed changes). The student only loads and
   fires; the screen says "feed check: shot 5 of 12".
2. Each shot: dip depth (before minus lowest speed in the feed window), double dip (a second drop > half the first
   within the window), time from gate open to dip.
3. Score per feed: the spread of its dips (median absolute deviation, robust to one odd shot); any double dip = that
   feed is out; ties (within 10 %) go to the faster feed (shorter cycle).
4. Bracket refine: one more feed half way to the best neighbour, 3 shots, if it is not already the best.
5. The chosen feed is saved with that point; the other points get their feed from the curve through the three
   (smoothed like speed and hood, clamped 0.3–1.0). Points tuned later in the session use the curve's feed.

Cost: about 15 shots per launcher in total, only once per session.

### Proving it

- Unit rules: a fake wheel where one feed is steadiest is found from noisy dips; a double dip removes a feed; ties go
  faster.
- **The simulator does not model a feed effect today** (feed only adds its own speed to the exit). To test the
  algorithm end to end the sim needs a feed model: entry scatter growing away from a best feed, and double feeds
  above a feed limit, both hidden per robot like the other truths. That model is a guess about real robots, so the
  sim proves the algorithm finds the steadiest feed *if* such an effect exists; whether it exists on our robot is
  shown only by the real robot's dip numbers (the screen shows them per feed).
- Real robot: the shot log keeps every dip, so the choice can be checked afterwards.

## 8. Built 2026-10-10: sim results

`sim/tests/test_shot_curve.py`, 10 robots × both launchers, student-like tables (each point a random setting inside the
window that scores ≥ 75 %), make chance (48 shots each) summed over the 20 launchers:

| where | straight lines | curve |
|---|---|---|
| at the saved distances | 19.33 | **19.69** |
| half way between them | **19.83** | 19.76 |
| past the last point: DECODE's "keep the fit going" | 17.40 (hold) | 15.20 |
| past the last point: curve's slope as a line | 17.40 | 15.44 |
| past the last point: hold the curve's end value | 17.40 | **18.34** |

Worst single launcher inside the range: −3.1 % (limit −4.2 %, one shot in 24). The team switched past-the-end to
"hold the curve's end value". Unit rules (`test_tuning_rules.py`): over 500 noisy tables the curve sits 21 % nearer the
window middle than straight lines (speed 11.6 vs 14.7 ticks/s, hood 0.0049 vs 0.0062), never clearly worse; the feed
check finds the steadiest feed within 0.15 in 88 % of noisy cases, at most 15 shots.
