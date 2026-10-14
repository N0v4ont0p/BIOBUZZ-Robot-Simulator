// The results loop: copy the results off the robot, the programmer step (coach or real mode), install the new build.
// Also the numbers nobody's program file carries: robot mass (E-1); Pedro's numbers by hand as a backup.
const PEDRO_FIELDS = [
  ['Pinpoint Tuner', [['xPodOffset', 'xPodOffset'], ['yPodOffset', 'yPodOffset']]],
  ['Foresight Tuner', [
    ['maxAchievableForwardVelocity', 'maxAchievableForwardVelocity'], ['maxAchievableStrafeVelocity', 'maxAchievableStrafeVelocity'],
    ['naturalForwardDeceleration', 'naturalForwardDeceleration'], ['naturalStrafeDeceleration', 'naturalStrafeDeceleration'],
    ['headingBrakingLinearCoefficient', 'headingBrakingLinearCoefficient'], ['headingBrakingQuadraticCoefficient', 'headingBrakingQuadraticCoefficient'],
    ['heading kP', 'headingKP'],
    ['forwardBrakingLinearCoefficient', 'forwardBrakingLinearCoefficient'], ['forwardBrakingQuadraticCoefficient', 'forwardBrakingQuadraticCoefficient'],
    ['strafeBrakingLinearCoefficient', 'strafeBrakingLinearCoefficient'], ['strafeBrakingQuadraticCoefficient', 'strafeBrakingQuadraticCoefficient'],
    ['forwardTranslational Primary kP', 'forwardTranslationalPrimaryKP'], ['forwardTranslational Secondary kP', 'forwardTranslationalSecondaryKP'],
    ['strafeTranslational Primary kP', 'strafeTranslationalPrimaryKP'], ['strafeTranslational Secondary kP', 'strafeTranslationalSecondaryKP'],
    ['coast kV', 'coastKV'], ['brake kV', 'brakeKV']]],
];
const PEDRO_DIRECTIONS = [
  ['frontLeftDirection', ['FORWARD', 'REVERSE']], ['frontRightDirection', ['FORWARD', 'REVERSE']],
  ['backLeftDirection', ['FORWARD', 'REVERSE']], ['backRightDirection', ['FORWARD', 'REVERSE']],
  ['xPodDirection', ['FORWARD', 'REVERSED']], ['yPodDirection', ['FORWARD', 'REVERSED']],
];

Bench.register({
  id: 'results', title: 'Send results', order: 95,
  mount(el) {
    let mode = 'coach';
    try { mode = localStorage.getItem('loopMode') || 'coach'; } catch (e) { /* storage off */ }
    el.innerHTML = `<style>
      .loop section { border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; margin: 0 0 12px; }
      .loop h4 { margin: 0 0 6px; }
      .loop code, .loop pre { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
      .loop pre { background: var(--bg); border: 1px solid var(--line); border-radius: 6px; padding: 8px; overflow: auto; max-height: 320px; white-space: pre-wrap; }
      .loop .stop { color: var(--bad); font-weight: 600; }
      .loop .ok { color: var(--good); font-weight: 600; }
      .loop .note { color: var(--warn); }
      .loop .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 4px 12px; }
      .loop .grid label { display: flex; justify-content: space-between; gap: 6px; font-size: 12px; align-items: center; }
      .loop input, .loop select { width: 110px; padding: 3px 5px; border: 1px solid var(--line); border-radius: 4px; background: var(--panel); color: var(--ink); }
      .loop .seg button { border: 1px solid var(--accent); background: transparent; color: var(--accent); padding: 4px 10px; }
      .loop .seg button.on { background: var(--accent); color: #fff; }
      .loop ol { margin: 4px 0; padding-left: 20px; }
      .diff-add { color: var(--good); } .diff-del { color: var(--bad); }
    </style>
    <div class="loop">
      <h3>Send results, enter them, install the new build</h3>
      <p class="hint">On the real robot every setup program uses what the earlier ones saved on the robot, so this is needed once,
        after programs 8 and 9: copy everything off the robot, the programmer enters it, you install the final build.
        You can also do it in between (a photo of a RESULTS block, or a copy), but nothing needs it.</p>

      <section>
        <h4>1. Get the results off the robot</h4>
        <p class="hint"><b>After programs 8 and 9:</b> one copy holds everything (every program's results and both points files).
          On the real robot, with a USB cable from the laptop to the Control Hub (no Wi-Fi switching):</p>
        <pre id="lpCmd"></pre>
        <div class="controls"><button class="btn" id="lpPull">Copy results (adb pull)</button><span id="lpPulled"></span></div>
        <p class="hint"><b>Backup:</b> each program also ends with a RESULTS block on the Driver Station. "Take the photo" copies it off the
          screen (or type it in the box, one <code>key = value</code> per line under <code>RESULTS P3</code>).</p>
        <div class="controls"><button class="btn secondary" id="lpPhoto">Take the photo</button>
          <textarea id="lpPaste" rows="3" cols="34" placeholder="RESULTS P3&#10;yawKs = 0.08&#10;..."></textarea>
          <button class="btn secondary" id="lpPasteGo">Send typed results</button></div>
      </section>

      <section>
        <h4>2. The programmer enters them</h4>
        <div class="controls seg"><button data-m="coach">Coach mode: the simulator does it</button><button data-m="real">Real mode: send to the programmer</button></div>
        <div id="lpPlan"><p class="empty">Copy the results first.</p></div>
        <div class="controls" id="lpCoachBar"><button class="btn" id="lpCoach">Enter the results, build and install</button></div>
        <div id="lpReal"></div>
        <div id="lpOut"></div>
      </section>

      <section>
        <h4>3. Install the new build</h4>
        <p class="hint">Like Android Studio's Run button: compiles the robot code and restarts the robot (everything in memory is wiped).
          If the code doesn't compile, the robot keeps the old build.</p>
        <div class="controls"><button class="btn" id="lpBuild">Install new build</button><span id="lpBuildState"></span></div>
        <p class="hint">When the robot code in the repo changed (new or fixed setup programs), this copies it into the sandbox,
          keeps every number already entered, and installs it. A checkpoint is saved first.</p>
        <div class="controls"><button class="btn secondary" id="lpUpdate">Update the robot code (keep my numbers)</button></div>
        <pre id="lpBuildErr" hidden></pre>
      </section>

      <section>
        <h4>Program 5: Pedro's numbers by hand (only if they are not in the results file)</h4>
        <p class="hint">Program 5 saves Pedro's numbers in the results file, so the copy above carries them. This form is for
          numbers typed from a photo of program 5's RESULTS block. Only filled boxes are entered.</p>
        <div id="lpPedro"></div>
        <div class="controls"><button class="btn" id="lpPedroGo">Enter, build and install</button></div>
      </section>

      <section>
        <h4>Alongside the programs</h4>
        <div class="grid"><label>E-1 robot mass with battery (kg) <input id="lpMass" type="number" step="0.01"></label></div>
        <div class="controls"><button class="btn secondary" id="lpMassGo">Enter mass, build and install</button></div>
      </section>
    </div>`;
    const $ = (s) => el.querySelector(s);
    const out = $('#lpOut');
    const busy = async (btn, f) => {
      btn.disabled = true;
      try { await f(); } catch (e) { out.innerHTML = `<p class="stop">${App.esc(e.message)}</p>`; } finally { btn.disabled = false; }
    };

    const setMode = (m) => {
      mode = m;
      try { localStorage.setItem('loopMode', m); } catch (e) { /* storage off */ }
      el.querySelectorAll('.seg button').forEach(b => b.classList.toggle('on', b.dataset.m === m));
      $('#lpCoachBar').hidden = m !== 'coach';
      $('#lpReal').hidden = m !== 'real';
      this.refresh();
    };
    el.querySelectorAll('.seg button').forEach(b => b.onclick = () => setMode(b.dataset.m));

    $('#lpPull').onclick = () => busy($('#lpPull'), async () => {
      const r = await App.api('loop/pull', {});
      out.innerHTML = '';
      await this.refresh();
      App.toast(r.files.length ? 'Copied ' + r.files.join(', ') : 'No results on the robot yet');
    });
    const sendText = (text) => busy($('#lpPhoto'), async () => {
      const r = await App.api('loop/paste', {text});
      out.innerHTML = '';
      await this.refresh();
      App.toast('Sent ' + r.count + ' result(s) from ' + r.programs.join(', '));
    });
    $('#lpPhoto').onclick = () => {
      const lines = (App.last.ds.telemetry || []).map(l => l.replace(/<[^>]*>/g, '').replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&'));
      if (!lines.some(l => /^RESULTS P\d/.test(l))) { App.toast('No RESULTS block on the Driver Station. Finish a program first (it shows the block at the end).'); return; }
      sendText(lines.join('\n'));
    };
    $('#lpPasteGo').onclick = () => sendText($('#lpPaste').value);
    $('#lpCoach').onclick = () => busy($('#lpCoach'), async () => {
      out.innerHTML = '<p>Entering, rendering, building...</p>';
      const r = await App.api('loop/coach', {});
      out.innerHTML = this.applied(r);
    });
    $('#lpUpdate').onclick = () => busy($('#lpUpdate'), async () => {
      $('#lpBuildState').textContent = 'Updating and building...';
      const r = await App.api('sandbox/update', {});
      $('#lpBuildState').innerHTML = `<span class="ok">Updated and installed. Kept ${Object.keys(r.kept).length} entered numbers.</span>`
        + (r.dropped.length ? ` <span class="note">Not in the new code: ${App.esc(r.dropped.join(', '))}</span>` : '');
    });
    $('#lpBuild').onclick = () => busy($('#lpBuild'), async () => {
      $('#lpBuildState').textContent = 'Building...';
      try {
        await App.api('build', {});
        $('#lpBuildState').innerHTML = '<span class="ok">Installed. The robot restarted.</span>';
      } catch (e) {
        $('#lpBuildState').innerHTML = '<span class="stop">Build failed. The robot keeps the old build.</span>';
      }
    });

    $('#lpPedro').innerHTML = PEDRO_FIELDS.map(([title, fields]) => `<p class="hint"><b>${title}</b></p><div class="grid">` +
      fields.map(([label, key]) => `<label title="team-config.yaml: ${key}">${App.esc(label)} <input type="number" step="any" data-key="${key}"></label>`).join('') + '</div>').join('') +
      `<p class="hint"><b>Mecanum Tuner and Pinpoint Tuner directions</b> (these go into the code, not team-config.yaml)</p><div class="grid">` +
      PEDRO_DIRECTIONS.map(([key, opts]) => `<label>${key} <select data-key="${key}"><option value="">(not set)</option>${opts.map(o => `<option>${o}</option>`).join('')}</select></label>`).join('') + '</div>';
    const enter = async (values) => {
      if (!Object.keys(values).length) throw new Error('Nothing typed in.');
      out.innerHTML = '<p>Entering, rendering, building...</p>';
      const r = await App.api('sandbox/set', {values});
      out.innerHTML = `<p class="ok">Entered ${Object.keys(values).length} value(s) and installed the new build.</p><pre>${App.esc(r.render || '')}</pre>`;
      this.refresh();
    };
    $('#lpPedroGo').onclick = () => busy($('#lpPedroGo'), () => {
      const values = {};
      $('#lpPedro').querySelectorAll('[data-key]').forEach(i => { if (i.value !== '') values[i.dataset.key] = i.tagName === 'SELECT' ? i.value : Number(i.value); });
      return enter(values);
    });
    $('#lpMassGo').onclick = () => busy($('#lpMassGo'), () => enter($('#lpMass').value === '' ? {} : {robotMass: Number($('#lpMass').value)}));

    this.el = el;
    setMode(mode);
  },

  async refresh() {
    const el = this.el;
    if (!el) return;
    const $ = (s) => el.querySelector(s);
    let st;
    try { st = await App.api('loop/status'); } catch (e) { return; }
    const files = st.robotFiles.length ? st.robotFiles : ['biobuzz_results.csv'];
    $('#lpCmd').textContent = files.map(f => 'adb pull /sdcard/FIRST/' + f).join('\n');
    $('#lpPulled').textContent = st.pull ? `Last copy: outbox/${st.pull.dir} (${new Date(st.pull.time).toLocaleTimeString()})` : 'Nothing copied yet.';
    if (!st.pull) { $('#lpPlan').innerHTML = '<p class="empty">Copy the results first.</p>'; $('#lpReal').innerHTML = ''; return; }
    let plan;
    try { plan = await App.api('loop/plan'); } catch (e) { $('#lpPlan').innerHTML = `<p class="stop">${App.esc(e.message)}</p>`; return; }
    $('#lpPlan').innerHTML = this.planHtml(plan);
    $('#lpReal').innerHTML = `<p><b>Send these files</b> from <code>${App.esc(st.paths.outbox)}/${App.esc(st.pull.dir)}</code>: ${plan.files.map(f => '<code>' + App.esc(f) + '</code>').join(', ') || 'none'}.</p>
      <p><b>What the programmer (Claude) does</b>, in the sandbox copy of the robot code at <code>${App.esc(st.paths.sandbox)}</code>:</p>
      <ol><li>Reads the newest line for each key.</li>
        <li>Puts each team-config.yaml value in with <code>origin: measured</code>, and sets the code settings (table above).</li>
        <li>Runs <code>sh tools/codegen/render.sh</code>. It must end with <code>rendered</code>.</li>
        <li>Builds. Here: when the programmer says done, press <b>Install new build</b> below.</li></ol>`;
  },

  planHtml(p) {
    const rows = Object.entries(p.values).map(([k, v]) => `<tr><td><code>${App.esc(k)}</code></td><td>${App.esc(String(v))}</td>
      <td>${k.includes('.') || k.endsWith('Direction') ? 'code' : 'team-config.yaml'}</td></tr>`).join('');
    const info = Object.entries(p.info).map(([k, v]) => `${App.esc(k)} = ${App.esc(v)}`).join(', ');
    const hand = {};
    for (const b of p.byHand) (hand[b.ref || 'other'] = hand[b.ref || 'other'] || []).push(b.key);
    return (p.stop || []).map(s => `<p class="stop">STOP: ${App.esc(s)}</p>`).join('') +
      (p.notes || []).map(s => `<p class="note">${App.esc(s)}</p>`).join('') +
      (rows ? `<table class="ports"><tr><th>Key</th><th>Value</th><th>Goes into</th></tr>${rows}</table>` : '<p class="empty">No values to enter from these files.</p>') +
      (info ? `<p class="hint">Info only, not entered: ${info}</p>` : '') +
      (p.unknown.length ? `<p class="note">Keys the programmer doesn't know: ${p.unknown.map(App.esc).join(', ')}</p>` : '') +
      (Object.keys(hand).length ? `<details><summary>Still blank, entered by hand (not from these files)</summary>${Object.entries(hand).map(([ref, keys]) =>
        `<p class="hint"><b>${App.esc(ref)}</b>: ${keys.map(App.esc).join(', ')}</p>`).join('')}</details>` : '');
  },

  applied(r) {
    const diff = (r.diff || '').split('\n').map(l => `<span class="${/^(\+\+\+|---)/.test(l) ? '' : l.startsWith('+') ? 'diff-add' : l.startsWith('-') ? 'diff-del' : ''}">${App.esc(l)}</span>`).join('\n');
    const head = !r.ok ? '<p class="stop">Entering the results failed. Nothing was installed.</p>'
      : r.installed ? '<p class="ok">Entered, rendered and installed. The robot restarted with the new build.</p>'
      : '<p class="stop">Entered, but the build failed. The robot keeps the old build.</p>';
    return head + (r.diff ? `<p class="hint">What changed in the robot code:</p><pre>${diff}</pre>` : '<p class="hint">Nothing in the code changed.</p>') +
      (r.buildError ? `<pre>${App.esc(r.buildError)}</pre>` : '') +
      `<details><summary>render.sh output</summary><pre>${App.esc(r.render || '(not run: nothing to enter)')}</pre></details>`;
  },

  update(el, s) {
    const err = s.sim.buildError;
    const pre = el.querySelector('#lpBuildErr');
    pre.hidden = !err;
    if (err && pre.textContent !== err) pre.textContent = err;
    const key = s.sim.scenario + s.sim.seed;
    if (this.seen !== key) { this.seen = key; this.refresh(); }
  },
});
