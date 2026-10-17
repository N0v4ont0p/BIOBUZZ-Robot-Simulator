// Start here: the whole setup, A to Z, as a checklist that ticks itself from what has really happened (results on
// the robot, results copied, builds installed). Each line opens the tab you need.
const START_PEDRO = ['xPodOffset', 'yPodOffset', 'maxAchievableForwardVelocity', 'maxAchievableStrafeVelocity', 'naturalForwardDeceleration',
  'naturalStrafeDeceleration', 'headingBrakingLinearCoefficient', 'headingBrakingQuadraticCoefficient', 'headingKP',
  'forwardBrakingLinearCoefficient', 'forwardBrakingQuadraticCoefficient', 'strafeBrakingLinearCoefficient', 'strafeBrakingQuadraticCoefficient',
  'forwardTranslationalPrimaryKP', 'forwardTranslationalSecondaryKP', 'strafeTranslationalPrimaryKP', 'strafeTranslationalSecondaryKP', 'coastKV', 'brakeKV'];
const START_PROGRAMS = ['1 Check Devices', '2 Motor Directions', '3 Turret Servos', '4 Servo Positions', '5 Drive Tuning', '6 Position Check',
  '7 Flywheel Tuner', '8 Back Shot Tuner', '9 Front Shot Tuner'];

// Every setup program uses the numbers the earlier ones saved on the robot (the yellow "from the robot's results
// file" line on the Driver Station), so the whole setup needs one build at the start and one at the end.
const START_STEPS = [
  {session: 'Session 0, at the desk'},
  {title: 'Configure Robot (D-1)', tabs: ['wiring'],
    text: 'Driver Station menu (&#8942;) &rarr; Configure Robot. Give every port its name from the setup guide (lf, lb, rf, rb, intake, pfeed, fs, bs, rampl, rampr as Continuous Rotation Servo, fg, bg, bg2, fyaw, byaw, pp). Follow the cables on the Wiring tab. Save, then activate.',
    done: c => !!c.s.sim.activeConfig},
  {title: 'CAD sheet in the code: build 1 of 2 (C-1, C-2, H-1)', tabs: ['field'], text: 'Field tab: "Enter the CAD sheet in the code" (robot edge sizes, turret pivots, CELL positions). That installs the first build. No more builds until the end.',
    done: c => ['centerToFront', 'centerToBack', 'centerToLeft', 'centerToRight'].every(k => c.st.yaml[k] != null)},
  {session: 'Session A, robot on blocks: programs 1 to 4'},
  {title: 'Program 1: Check Devices', tabs: ['robot'], text: 'TeleOp list &rarr; "1 Check Devices". INIT, read the screen, START. Turn each part by hand on the Robot tab when it asks.',
    done: c => c.ran(START_PROGRAMS[0])},
  {title: 'Program 2: Motor Directions', tabs: ['robot'], text: 'It loads by itself when you STOP program 1. Hold R2, watch the part, answer cross (yes) or circle (no).',
    done: c => c.ran(START_PROGRAMS[1])},
  {title: 'Program 3: Turret Servos', tabs: ['turret', 'robot'], text: 'Loads after program 2. For each turret: press square, nudge the servo with R1/L1, R2/L2 until the barrel points straight forward (cross), straight left, straight right (a straightedge on the Turret tab helps), then as far left and right as the cable allows. Then film both turrets swinging and count the frames of the slower one.',
    done: c => c.ran(START_PROGRAMS[2])},
  {title: 'Program 4: Servo Positions', tabs: ['servos', 'robot'], text: 'Press square to move each gate servo, nudge it with R1/L1, R2/L2, save with cross (the back gate: servo 1 alone, then servo 2 until it stops buzzing). Count the gate frames in slow motion. Ramp: hold square to run it out and in and count the frames.',
    done: c => c.ran(START_PROGRAMS[3])},
  {session: 'Session B, robot on the floor: programs 5 to 7'},
  {title: 'Program 5: Drive Tuning (Pedro, on the Driver Station)', tabs: ['field', 'robot'], text: 'Loads after program 4. A list of 4 parts: 1 wheel directions (robot on blocks), 2 Pinpoint (on the floor, you push it), 3 Foresight (it drives itself), 4 Tests. dpad picks, cross runs. Each part saves its numbers on the robot; the next uses them.',
    done: c => START_PEDRO.every(k => c.has(k))},
  {title: 'Program 6: Position Check', tabs: ['field', 'robot'], text: 'Pick the alliance in INIT. Velocity check, a 96 in push and 10 turns (scale checks), then a loop back to the start corner.',
    done: c => c.ran(START_PROGRAMS[5])},
  {title: 'Program 7: Flywheel Tuner', tabs: ['flywheels', 'robot'], text: 'Nothing loaded, hands clear. Load ONE element only when step 3 asks.', done: c => c.ran(START_PROGRAMS[6])},
  {session: 'Session C, on the field: programs 8 and 9, then the final build'},
  {title: 'Programs 8 and 9: Shot Tuners (back first)', tabs: ['field', 'robot'], text: 'Start corner, options, START: the robot drives itself to each spot. L2 load, R2 fire, cross in / triangle not in, square/circle speed, dpad left/right yaw trim (no hood: the launch angle is fixed). 4 in a row saves the point and it drives to the next. 10+ points for the back (POLLEN) launcher, 6+ for the front. Film a few shots in slow motion for G-6.',
    done: c => c.ran(START_PROGRAMS[7]) && c.ran(START_PROGRAMS[8])},
  {title: 'Copy everything off the robot (adb pull, USB cable)', tabs: ['results'], send: true, text: 'Send results tab: Copy results. One copy holds every program\'s results and both points files.',
    done: c => c.pulled(START_PROGRAMS)},
  {title: 'Enter everything: build 2 of 2', tabs: ['results'], send: true, text: 'Coach mode enters the numbers and installs the final build. Now they are in the code: the yellow "from the robot\'s results file" line is gone.',
    done: c => c.sent(START_PROGRAMS)},
  {title: 'BIOBUZZ TeleOp', tabs: ['field', 'robot'], text: 'INIT (red start corner by default; square = blue), START. L1 launchers, hold R1 fire; the turrets lock on by themselves. Same buttons as DECODE; L1 = fire mode on/off, options = corner reseed, triangle = ramp.', done: c => c.teleop},
  {title: 'Report card', tabs: ['report'], text: 'See how close your numbers came to the hidden robot, and which faults you caught.', done: () => false},
];

// TeleOp counts once it was INIT-ed on this robot, whichever tab was open
const START_TELEOP = {};
App.onState(s => { if (s.ds.active === 'BIOBUZZ TeleOp') START_TELEOP[s.sim.scenario + ':' + s.sim.seed] = true; });

Bench.register({
  id: 'start', title: 'Start here', order: 5,
  mount(el) {
    el.innerHTML = `<style>
      .start ol { list-style: none; padding: 0; margin: 8px 0; }
      .start li { display: grid; grid-template-columns: 26px 1fr; gap: 2px 8px; padding: 7px 8px; border-radius: 6px; }
      .start li.now { background: var(--bg); outline: 2px solid var(--accent); }
      .start li.send { margin-left: 18px; }
      .start .box { width: 20px; height: 20px; border: 2px solid var(--muted); border-radius: 4px; display: flex; align-items: center; justify-content: center; font-weight: 700; color: #fff; }
      .start li.done .box { background: var(--good); border-color: var(--good); }
      .start li.done .t { color: var(--muted); }
      .start .t { font-weight: 600; }
      .start .t a { font-weight: 400; font-size: 12px; margin-left: 8px; color: var(--accent); }
      .start .d { grid-column: 2; color: var(--muted); font-size: 12px; }
      .start li.session { display: block; font-weight: 700; color: var(--accent); padding: 12px 8px 2px; border-bottom: 1px solid var(--line); border-radius: 0; }
    </style>
    <div class="start">
      <h3>Start here: set up the robot from zero</h3>
      <p class="hint">Same order as the real robot (docs/BIOBUZZ_PREREQUISITES.md). The boxes tick themselves when the simulator sees the step done.
        A program that needs an earlier step shows a red NOT READY line in INIT: do that step first. Each program uses what the earlier
        ones saved on the robot, so there are only two builds: one at the start, one at the end.</p>
      <ol id="stList"></ol>
      <h3>Checkpoints</h3>
      <p class="hint">Saved by themselves when you activate a configuration and every time a build is installed. Restoring one brings back
        that robot, its configuration, the results on it and the code, so you skip everything you already did.
        Closing the simulator is safe too: ./sim.sh picks up where you left off.</p>
      <div class="controls"><button class="btn secondary" id="stSave">Save checkpoint now</button></div>
      <table class="ports" id="stCps"></table>
    </div>`;
    el.querySelector('#stSave').onclick = async () => { await App.api('checkpoint/save', {label: 'saved by hand'}); App.toast('Checkpoint saved'); this.poll(el); };
    el.querySelector('#stCps').onclick = async (e) => {
      const b = e.target.closest('button[data-cp]');
      if (!b || !confirm('Go back to "' + b.dataset.cp + '"? What you did after it is thrown away (save a checkpoint first to keep it).')) return;
      App.toast('Restoring...');
      try { await App.api('checkpoint/restore', {name: b.dataset.cp}); App.toast('Restored'); } catch (err) { alert(err.message); }
      this.poll(el);
    };
    this.poll(el);
  },

  async poll(el) {
    try { this.st = await App.api('loop/status'); } catch (e) { /* robot restarting */ }
    try {
      const cps = await App.api('checkpoints');
      const html = cps.map(c => `<tr><td>${App.esc(c.name)}</td><td>${App.esc(c.scenario.replace(/\.json$/, ''))} seed ${c.seed}</td>
        <td><button class="btn secondary" data-cp="${App.esc(c.name)}">Restore</button></td></tr>`).join('') || '<tr><td class="empty">None yet</td></tr>';
      const t = el.querySelector('#stCps');
      if (t.dataset.html !== html) { t.dataset.html = html; t.innerHTML = html; }
    } catch (e) { /* robot restarting */ }
    this.render(el);
  },

  render(el) {
    const s = App.last, st = this.st;
    if (!s || !st) return;
    const c = {
      s, st, teleop: !!START_TELEOP[s.sim.scenario + ':' + s.sim.seed],
      ran: p => st.programs[p] != null,
      has: k => (st.results || {})[k] != null || st.yaml[k] != null,
      // everything those programs wrote is in the newest copy
      pulled: ps => ps.some(p => st.programs[p] != null) && !!st.pull
        && ps.every(p => st.programs[p] == null || (st.pull.programs[p] || 0) >= st.programs[p]),
    };
    // ... and a build was installed after that copy
    c.sent = ps => c.pulled(ps) && st.builtAt >= st.pull.time;
    let now = false;
    const html = START_STEPS.map((step, i) => {
      if (step.session) return `<li class="session">${step.session}</li>`;
      const done = step.done(c);
      const cls = [done ? 'done' : '', !done && !now ? 'now' : '', step.send ? 'send' : ''].join(' ');
      if (!done) now = true;
      return `<li class="${cls}"><span class="box">${done ? '&#10003;' : ''}</span>
        <span class="t">${step.title}<a href="#" data-i="${i}">open</a></span><span class="d">${step.text}</span></li>`;
    }).join('');
    const list = el.querySelector('#stList');
    if (list.dataset.html === html) return;
    list.dataset.html = html;
    list.innerHTML = html;
    list.querySelectorAll('a[data-i]').forEach(a => a.onclick = (e) => {
      e.preventDefault();
      const tabs = START_STEPS[a.dataset.i].tabs;
      const id = tabs.find(t => document.querySelector(`#benchTabs button[data-id="${t}"]`)) || 'robot';
      Bench.show(id);
    });
  },

  update(el) {
    const t = Date.now();
    if (!this.last || t - this.last > 2000) { this.last = t; this.poll(el); }
  },
});
