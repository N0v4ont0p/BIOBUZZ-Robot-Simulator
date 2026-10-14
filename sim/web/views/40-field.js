// The field from above (red alliance wall on the left, audience at the bottom), with the robot where it really is.
// Hands: put it on the floor or a box, place it in a start corner, push or drag it, spin it; a tape measure;
// and the CAD sheet (what CAD tells the team before the robot exists). Coach mode shows where the Pinpoint
// thinks the robot is.
Bench.register({
  id: 'field', title: 'Field', order: 40,
  mount(el) {
    el.innerHTML = `<h3>Field (top view)</h3>
      <p class="hint">Red alliance wall on the left, audience at the bottom, +x toward blue, +y toward the far wall.
        Drag the robot to push it. Program 6 and the shot tuners start in your GARDEN corner: back against the alliance wall,
        right side against the side wall, facing across the field.</p>
      <div class="controls">
        <button class="btn secondary" data-r="onFloor">Put on floor</button>
        <button class="btn secondary" data-r="onBox">Put on box</button>
        <button class="btn secondary" data-place="center">Place mid-field</button>
      </div>
      <div class="controls">
        <span>Push:</span>
        <button class="btn secondary" data-p='{"forward":12}'>1 ft forward</button>
        <button class="btn secondary" data-p='{"forward":-12}'>1 ft back</button>
        <button class="btn secondary" data-p='{"left":12}'>1 ft left</button>
        <button class="btn secondary" data-p='{"left":-12}'>1 ft right</button>
        <button class="btn secondary" data-p='{"forward":48,"seconds":4}'>48 in forward (slow, along the tape)</button>
        <button class="btn secondary" data-p='{"turnDeg":90}'>turn 90&deg; left</button>
        <button class="btn secondary" data-p='{"turnDeg":-90}'>turn 90&deg; right</button>
        <button class="btn secondary" data-p='{"turnDeg":180,"seconds":3}'>spin 180&deg; counter-clockwise</button>
        <button class="btn secondary" data-p='{"turnDeg":360,"seconds":5}'>spin a full turn</button>
        <button class="btn secondary" data-p='{"forward":-4,"left":-4,"seconds":1,"square":true}'>press back + right against the walls</button>
        <button class="btn secondary" data-p='{"forward":-4,"left":4,"seconds":1,"square":true}'>press back + left against the walls</button>
      </div>
      <div class="controls">
        <button class="btn secondary" id="fdTape">Tape measure: click two points</button>
        <span id="fdTapeOut"></span>
      </div>
      <div class="controls">
        <label><input type="checkbox" id="fdCoach"> Coach: show where the Pinpoint thinks the robot is</label>
      </div>
      <div class="controls" id="fdStarts">
        <span>Place where the code starts (the Pinpoint lines up there):</span>
        <button class="btn secondary" data-place="red">Red GARDEN corner (setup programs, TeleOp)</button>
        <button class="btn secondary" data-place="blue">Blue GARDEN corner</button>
        <span id="fdAutoStarts"></span>
        <label><input type="checkbox" id="fdCareful"> line it up carefully</label>
      </div>
      <div style="display:grid;grid-template-columns:minmax(0,1fr) 260px;gap:12px;align-items:start">
        <svg viewBox="-10 -10 620 620" id="fdSvg"></svg>
        <div>
          <h3>CAD sheet</h3>
          <p class="hint">From the robot's CAD and the field drawing (setup Stage 1). The programmer enters these.</p>
          <div class="kv" id="fdCad"></div>
          <div class="controls"><button class="btn secondary" id="fdCadGo">Enter the CAD sheet in the code</button></div>
          <h3 style="margin-top:12px">Robot</h3>
          <div class="kv" id="fdKv"></div>
        </div>
      </div>`;
    const S = 600 / 144;
    const P = (x, y) => [x * S, 600 - y * S];
    this.P = P;
    this.S = S;
    const svg = el.querySelector('#fdSvg');
    Svg.el('rect', {x: 0, y: 0, width: 600, height: 600, fill: '#3a3f44'}, svg);
    for (let i = 1; i < 6; i++) {
      Svg.el('line', {x1: i * 100, y1: 0, x2: i * 100, y2: 600, stroke: '#4b5157', 'stroke-width': 1}, svg);
      Svg.el('line', {x1: 0, y1: i * 100, x2: 600, y2: i * 100, stroke: '#4b5157', 'stroke-width': 1}, svg);
    }
    Svg.el('rect', {x: -6, y: 0, width: 6, height: 600, fill: '#c62828'}, svg);
    Svg.el('rect', {x: 600, y: 0, width: 6, height: 600, fill: '#1565c0'}, svg);
    Svg.el('rect', {x: 0, y: 600, width: 600, height: 6, fill: '#90a4ae'}, svg);
    Svg.text(svg, 300, 616, 'audience', {'text-anchor': 'middle', fill: 'var(--muted)', 'font-size': 11});
    // GARDEN tape strips (23 x 2 in) in the two start corners
    const garden = (x0, y0, x1, y1, color) => {
      const a = P(x0, y1), b = P(x1, y0);
      Svg.el('rect', {x: a[0], y: a[1], width: b[0] - a[0], height: b[1] - a[1], fill: color, opacity: 0.8}, svg);
    };
    garden(0, 0, 23, 2, '#e57373');
    garden(0, 0, 2, 23, '#e57373');
    garden(121, 142, 144, 144, '#64b5f6');
    garden(142, 121, 144, 144, '#64b5f6');
    this.cells = Svg.el('g', {}, svg);
    this.ghost = Svg.el('polygon', {fill: 'none', stroke: '#ffd54f', 'stroke-width': 2, 'stroke-dasharray': '5 4'}, svg);
    this.robot = Svg.el('g', {class: 'grab'}, svg);
    this.body = Svg.el('polygon', {fill: 'rgba(144,164,174,.35)', stroke: '#eceff1', 'stroke-width': 2}, this.robot);
    this.arrow = Svg.el('line', {stroke: '#ffd54f', 'stroke-width': 4, 'stroke-linecap': 'round'}, this.robot);
    this.center = Svg.el('circle', {r: 3, fill: '#ffd54f'}, this.robot);
    this.tape = Svg.el('line', {stroke: '#fff176', 'stroke-width': 2, 'stroke-dasharray': '6 4'}, svg);

    el.querySelectorAll('[data-r]').forEach(b => b.onclick = () => App.hand('robot', b.dataset.r));
    el.querySelectorAll('[data-p]').forEach(b => b.onclick = () => App.hand('drive', 'push', Object.assign({seconds: 1.2}, JSON.parse(b.dataset.p))));
    el.addEventListener('click', (e) => {
      const b = e.target.closest('[data-place]');
      if (!b) return;
      const s = App.last && App.last.world;
      if (!s) return;
      const cad = s.drive.cad, careful = el.querySelector('#fdCareful').checked;
      App.hand('robot', 'onFloor');
      if (b.dataset.place === 'red') App.hand('drive', 'place', {x: cad.centerToBack, y: cad.centerToRight, headingDeg: 0, careful});
      else if (b.dataset.place === 'blue') App.hand('drive', 'place', {x: 144 - cad.centerToBack, y: 144 - cad.centerToRight, headingDeg: 180, careful});
      else if (b.dataset.place === 'auto') {
        // an auto's start pose (blue as written; red rotated 180 deg about mid-field, as MatchState.poseFactory does)
        const x = Number(b.dataset.x), y = Number(b.dataset.y), h = Number(b.dataset.h);
        if (b.dataset.red === '1') App.hand('drive', 'place', {x: 144 - x, y: 144 - y, headingDeg: ((h + 180 + 180) % 360) - 180, careful});
        else App.hand('drive', 'place', {x, y, headingDeg: h, careful});
      } else App.hand('drive', 'place', {x: 72, y: 72, headingDeg: 0, careful});
    });
    // the start of every AutoTool auto in the robot code, read again every 15 s (AutoTool rewrites them)
    const kind = (n) => /^near/i.test(n) ? 'close' : /^far/i.test(n) ? 'far' : n.replace(/_/g, ' ');
    const loadStarts = async () => {
      try {
        const autos = await App.api('autos/starts');
        const html = autos.map(a => ['Blue', 'Red'].map(al =>
          `<button class="btn secondary" data-place="auto" data-red="${al === 'Red' ? 1 : 0}" data-x="${a.x}" data-y="${a.y}" data-h="${a.headingDeg}"
            title="${App.esc(al + '_' + a.name)} starts at ${al === 'Red' ? `(${(144 - a.x).toFixed(1)}, ${(144 - a.y).toFixed(1)})` : `(${a.x}, ${a.y})`}">${al} ${App.esc(kind(a.name))} start (${App.esc(al + '_' + a.name)})</button>`).join('')).join('');
        const box = el.querySelector('#fdAutoStarts');
        if (box.dataset.html !== html) { box.dataset.html = html; box.innerHTML = html; }
      } catch (err) { /* robot restarting */ }
    };
    loadStarts();
    setInterval(loadStarts, 15000);

    const toField = (e) => {
      const pt = new DOMPoint(e.clientX, e.clientY).matrixTransform(svg.getScreenCTM().inverse());
      return [pt.x / S, (600 - pt.y) / S];
    };
    // drag the robot: the hands push it toward the pointer
    let last = null;
    Bench.drag(this.robot, (dx, dy, m) => {
      const s = App.last && App.last.world;
      if (!s || !last) return;
      const p = toField(m), h = s.drive.headingDeg * Math.PI / 180;
      const fx = p[0] - last[0], fy = p[1] - last[1];
      if (Math.hypot(fx, fy) < 0.5) return;
      last = p;
      App.hand('drive', 'push', {forward: fx * Math.cos(h) + fy * Math.sin(h), left: -fx * Math.sin(h) + fy * Math.cos(h), seconds: 0.25});
    }, () => { last = null; }, (e) => { last = toField(e); });

    let tapeA = null, tapeMode = false;
    el.querySelector('#fdTape').onclick = () => { tapeMode = true; tapeA = null; el.querySelector('#fdTapeOut').textContent = 'click the first point'; };
    svg.addEventListener('click', (e) => {
      if (!tapeMode) return;
      const p = toField(e);
      if (!tapeA) { tapeA = p; el.querySelector('#fdTapeOut').textContent = 'click the second point'; return; }
      const a = P(tapeA[0], tapeA[1]), b = P(p[0], p[1]);
      ['x1', 'y1', 'x2', 'y2'].forEach((k, i) => this.tape.setAttribute(k, [a[0], a[1], b[0], b[1]][i]));
      el.querySelector('#fdTapeOut').textContent = Math.hypot(p[0] - tapeA[0], p[1] - tapeA[1]).toFixed(1) + ' in';
      tapeMode = false;
    });
    this.loadCad = async () => {
      try {
        const x = await App.api('expected');
        const keys = ['centerToFront', 'centerToBack', 'centerToLeft', 'centerToRight', 'frontPivotX', 'frontPivotY', 'backPivotX', 'backPivotY',
          'redFarCellX', 'redFarCellY', 'redAudienceCellX', 'redAudienceCellY'];
        this.cad = x;
        this.cadKeys = keys.filter(k => x[k]);
        el.querySelector('#fdCad').innerHTML = keys.filter(k => x[k]).map(k => `<span>${k}</span><b>${x[k].value.toFixed(2)} in</b>`).join('');
      } catch (e) { /* robot restarting */ }
    };
    this.loadCad();
    el.querySelector('#fdCadGo').onclick = async (e) => {
      // the programmer copies the CAD sheet into team-config.yaml (setup C-1, C-2, H-1), renders, builds
      const b = e.target;
      b.disabled = true;
      try {
        const values = {};
        for (const k of this.cadKeys || []) values[k] = Number(this.cad[k].value.toFixed(2));
        await App.api('sandbox/set', {values});
        App.toast('CAD sheet entered, new build installed');
      } catch (err) { alert(err.message); } finally { b.disabled = false; }
    };
  },
  update(el, s) {
    const w = s.world;
    if (!w || !w.drive) return;
    if (!this.cadLoaded || this.cadScenario !== s.sim.scenario + s.sim.seed) { this.cadLoaded = true; this.cadScenario = s.sim.scenario + s.sim.seed; this.loadCad(); }
    const P = this.P, d = w.drive;
    this.body.setAttribute('points', d.corners.map(c => P(c[0], c[1]).join(',')).join(' '));
    const c = P(d.x, d.y), h = d.headingDeg * Math.PI / 180;
    this.center.setAttribute('cx', c[0]); this.center.setAttribute('cy', c[1]);
    const t = P(d.x + Math.cos(h) * 10, d.y + Math.sin(h) * 10);
    this.arrow.setAttribute('x1', c[0]); this.arrow.setAttribute('y1', c[1]);
    this.arrow.setAttribute('x2', t[0]); this.arrow.setAttribute('y2', t[1]);
    this.body.setAttribute('fill', w.onBox ? 'rgba(255,183,77,.35)' : 'rgba(144,164,174,.35)');
    const coach = el.querySelector('#fdCoach').checked && w.pinpoint;
    if (coach) {
      const p = w.pinpoint, ph = p.headingDeg * Math.PI / 180, dx = p.posXIn - d.x, dy = p.posYIn - d.y, dh = ph - h;
      // the robot outline moved to where the Pinpoint says it is
      this.ghost.setAttribute('points', d.corners.map(k => {
        const rx = k[0] - d.x, ry = k[1] - d.y;
        return P(p.posXIn + rx * Math.cos(dh) - ry * Math.sin(dh), p.posYIn + rx * Math.sin(dh) + ry * Math.cos(dh)).join(',');
      }).join(' '));
      this.ghost.style.display = '';
    } else {
      this.ghost.style.display = 'none';
    }
    const cells = (w.shots && w.shots.cells) || [];
    if (this.cells.childNodes.length !== cells.length) {
      this.cells.innerHTML = '';
      for (const k of cells) {
        const q = P(k.x, k.y);
        Svg.el('rect', {x: q[0] - 10, y: q[1] - 10, width: 20, height: 20, fill: 'none', stroke: k.name && k.name.startsWith('red') ? '#ef5350' : '#42a5f5', 'stroke-width': 3}, this.cells);
      }
    }
    el.querySelector('#fdKv').innerHTML =
      `<span>Where</span><b>${w.onBox ? 'on the box' : 'on the floor'}</b>` +
      `<span>Speed</span><b>${d.speed.toFixed(1)} in/s, ${d.turnDegS.toFixed(0)} deg/s</b>` +
      (coach ? `<span>True pose</span><b>(${d.x.toFixed(1)}, ${d.y.toFixed(1)}) ${d.headingDeg.toFixed(1)}&deg;</b>` +
        `<span>Pinpoint says</span><b>(${w.pinpoint.posXIn.toFixed(1)}, ${w.pinpoint.posYIn.toFixed(1)}) ${w.pinpoint.headingDeg.toFixed(1)}&deg;</b>` : '');
  },
});
