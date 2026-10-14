// Report card: what the setup programs measured vs the hidden robot, and every scenario fault with the program step
// that should have caught it. The true values stay hidden until the coach presses "Reveal truth".
Bench.register({
  id: 'report', title: 'Report card', order: 96,
  mount(el) {
    el.innerHTML = `<style>
      .rc .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; vertical-align: middle; background: var(--line); }
      .rc .green { background: var(--good); } .rc .yellow { background: var(--warn); } .rc .red { background: var(--bad); }
      .rc td.how { color: var(--muted); font-size: 12px; }
      .rc .sum { display: flex; gap: 14px; flex-wrap: wrap; margin: 6px 0 10px; }
      .rc .caught { color: var(--good); font-weight: 600; } .rc .missed { color: var(--bad); font-weight: 600; }
    </style>
    <div class="rc">
      <h3>Report card</h3>
      <p class="hint">Green: within tolerance of the hidden robot. Yellow: within twice the tolerance. Red: further off. Grey: not measured yet.
        Values come from the robot's results file, or from team-config.yaml for numbers typed in by hand (Pedro, mass, flight).</p>
      <div class="controls"><button class="btn secondary" id="rcRefresh">Refresh</button>
        <button class="btn danger" id="rcReveal">Reveal truth (coach)</button><span id="rcScenario"></span></div>
      <div class="sum" id="rcSum"></div>
      <table class="ports" id="rcTable"></table>
      <h3 style="margin-top:14px">Faults in this robot</h3>
      <table class="ports" id="rcFaults"></table>
    </div>`;
    this.reveal = false;
    el.querySelector('#rcRefresh').onclick = () => this.load(el);
    el.querySelector('#rcReveal').onclick = () => {
      if (!this.reveal && !confirm('Show the hidden true values? Only do this when the students are finished.')) return;
      this.reveal = !this.reveal;
      this.load(el);
    };
    this.load(el);
  },

  async load(el) {
    const r = await App.api('loop/report' + (this.reveal ? '?reveal=1' : '')).catch(e => ({error: e.message}));
    el.querySelector('#rcReveal').textContent = this.reveal ? 'Hide truth' : 'Reveal truth (coach)';
    if (r.error) { el.querySelector('#rcTable').innerHTML = `<tr><td class="empty">${App.esc(r.error)}</td></tr>`; return; }
    const label = {green: 'good', yellow: 'close', red: 'off', unmeasured: 'not measured yet', other: 'not a number'};
    el.querySelector('#rcScenario').textContent = `${r.scenario} (seed ${r.seed})`;
    el.querySelector('#rcSum').innerHTML = Object.entries(r.count).filter(([, n]) => n)
      .map(([k, n]) => `<span><span class="dot ${k}"></span>${n} ${label[k]}</span>`).join('') || '<span class="empty">Nothing to grade yet.</span>';
    el.querySelector('#rcTable').innerHTML = '<tr><th>Key</th><th>Measured</th><th>Mark</th>' + (r.revealed ? '<th>Truth</th>' : '') + '<th>How it is measured</th></tr>' +
      (r.entries.map(e => `<tr><td><code>${App.esc(e.key)}</code></td>
        <td>${e.measured == null ? '<span class="empty">-</span>' : App.esc(e.measured) + ' ' + App.esc(e.unit || '')}${e.from === 'team-config.yaml' ? ' <small>(yaml)</small>' : ''}</td>
        <td><span class="dot ${e.status}"></span>${label[e.status]}</td>
        ${r.revealed ? `<td>${e.truth == null ? '-' : Number(e.truth).toPrecision(4)} &plusmn; ${e.tolerance}</td>` : ''}
        <td class="how">${App.esc(e.how || '')}</td></tr>`).join('') || '<tr><td colspan="5" class="empty">No graded keys yet.</td></tr>');
    el.querySelector('#rcFaults').innerHTML = '<tr><th>What was wrong</th><th>Which step should catch it</th><th>Caught?</th></tr>' +
      (r.faults.map(f => `<tr><td>${App.esc(f.text)}</td><td>${App.esc(f.caughtBy)}</td>
        <td>${f.caught === true ? '<span class="caught">yes</span>' : f.caught === false ? '<span class="missed">not yet</span>' : '<span class="empty">check by eye</span>'}</td></tr>`).join('')
        || '<tr><td colspan="3" class="empty">This robot has no faults on purpose.</td></tr>');
  },

  update(el, s) {
    const key = s.sim.scenario + s.sim.seed;
    if (this.seen !== key) { this.seen = key; this.reveal = false; this.load(el); }
  },
});
