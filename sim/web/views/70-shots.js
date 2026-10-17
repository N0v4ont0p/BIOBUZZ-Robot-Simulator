// The field from above (robot, HIVE, CELLs, balls) and a shot camera from the side. Programs 8 / 9 and TeleOp:
// the tape shows how far the turret is from your CELL; watch each shot on the camera and decide yourself whether
// it went in (cross) or not (circle).
Bench.register({
  id: 'shots', title: 'Field + shot camera', order: 70,
  mount(el) {
    el.innerHTML = `<style>
        .sh-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10px; }
        @media (max-width: 900px) { .sh-grid { grid-template-columns: 1fr; } }
        .sh-coach { border: 1px dashed var(--warn); border-radius: 8px; padding: 6px 10px; margin-top: 8px; }
        .sh-coach[hidden] { display: none; }
        .sh-coach h4 { margin: 0 0 4px; color: var(--warn); font-size: 13px; }
      </style>
      <h3>Field and shot camera</h3>
      <p class="hint">Red alliance wall on the left, audience at the bottom. The dashed tape runs from the turret axis to your CELL
        (the one on your side of the HIVE). Push the robot to a distance, then fire and watch the camera.</p>
      <div class="controls">
        <label>Alliance <select id="shAlliance"><option value="red">red</option><option value="blue">blue</option></select></label>
        <label>Tape from <select id="shPivot"><option value="0">front turret</option><option value="1">back turret</option></select></label>
        <button class="btn secondary" data-m="-6">6 in closer</button>
        <button class="btn secondary" data-m="6">6 in farther</button>
        <button class="btn secondary" data-s="6">6 in left</button>
        <button class="btn secondary" data-s="-6">6 in right</button>
        <button class="btn secondary" id="shRetrieve">Retrieve balls</button>
      </div>
      <div class="sh-grid">
        <svg viewBox="0 0 452 452" id="shField"></svg>
        <div><b>Shot camera</b> <span id="shCamNote" class="hint"></span><svg viewBox="0 0 440 240" id="shCam"></svg>
          <div class="kv" id="shKv"></div></div>
      </div>
      <label><input type="checkbox" id="shCoach"> Coach (shows hidden numbers)</label>
      <div class="sh-coach" id="shCoachBox" hidden><h4>Coach</h4><div class="kv" id="shCoachKv"></div>
        <div class="controls"><button class="btn secondary" id="shSolve">What scores from here?</button><span id="shSolveOut"></span></div></div>`;
    this.S = 3;
    this.live = {};
    this.recent = [];
    this.lastId = null;
    el.querySelector('#shCoach').onchange = (e) => { el.querySelector('#shCoachBox').hidden = !e.target.checked; };
    el.querySelector('#shRetrieve').onclick = () => { App.hand('shots', 'retrieve'); this.recent = []; this.live = {}; };
    el.querySelectorAll('[data-m]').forEach(b => b.onclick = () => this.move(+b.dataset.m, 0));
    el.querySelectorAll('[data-s]').forEach(b => b.onclick = () => this.move(0, +b.dataset.s));
    el.querySelector('#shSolve').onclick = async () => {
      const out = el.querySelector('#shSolveOut');
      if (!this.tape) { out.textContent = 'no CELL in sight'; return; }
      const side = +el.querySelector('#shPivot').value;
      const r = await App.api(`shots/solve?side=${side}&distance=${this.tape.d.toFixed(1)}`);
      out.textContent = r.reachable
        ? `${side ? 'back' : 'front'}: about ${Math.round(r.speed / 20) * 20} ticks/s (fixed launch ${r.launchDeg.toFixed(1)}°), scores ${Math.round(r.makeChance * 100)}% of shots`
        : `nothing scores from this distance at the fixed ${r.launchDeg.toFixed(1)}° launch angle`;
    };
    const svg = el.querySelector('#shField');
    const f = this.fieldXY.bind(this), S = this.S;
    Svg.el('rect', {x: 10, y: 10, width: 144 * S, height: 144 * S, fill: 'var(--bg)', stroke: 'var(--metal)', 'stroke-width': 3}, svg);
    for (let k = 24; k < 144; k += 24) {
      Svg.el('line', {x1: f(k, 0).x, y1: f(k, 0).y, x2: f(k, 144).x, y2: f(k, 144).y, stroke: 'var(--line)'}, svg);
      Svg.el('line', {x1: f(0, k).x, y1: f(0, k).y, x2: f(144, k).x, y2: f(144, k).y, stroke: 'var(--line)'}, svg);
    }
    Svg.el('line', {x1: 10, y1: 10, x2: 10, y2: 10 + 144 * S, stroke: '#e53935', 'stroke-width': 5}, svg);
    Svg.el('line', {x1: 10 + 144 * S, y1: 10, x2: 10 + 144 * S, y2: 10 + 144 * S, stroke: '#1e88e5', 'stroke-width': 5}, svg);
    Svg.text(svg, 226, 448, 'audience', {'text-anchor': 'middle', fill: 'var(--muted)', 'font-size': 11});
    const fr = f(72 - 24.7, 72 + 19.5);
    Svg.el('rect', {x: fr.x, y: fr.y, width: 49.4 * S, height: 39 * S, fill: 'none', stroke: 'var(--part)', 'stroke-dasharray': '4 4'}, svg);
    Svg.el('line', {x1: f(72 - 24.7, 72).x, y1: f(0, 72).y, x2: f(72 + 24.7, 72).x, y2: f(0, 72).y, stroke: 'var(--part)', 'stroke-width': 4}, svg);
    this.cellLayer = Svg.el('g', {}, svg);
    this.dyn = Svg.el('g', {}, svg);
  },

  fieldXY(x, y) {
    return {x: 10 + x * this.S, y: 10 + (144 - y) * this.S};
  },

  pivot(w, side) {
    const d = w.drive, p = w.shots.pivots[side], h = d.headingDeg * Math.PI / 180;
    return {x: d.x + Math.cos(h) * p[0] - Math.sin(h) * p[1], y: d.y + Math.sin(h) * p[0] + Math.cos(h) * p[1]};
  },

  ownCell(w, alliance) {
    const far = w.drive.y >= 72;
    const id = alliance + (far ? 'Far' : 'Audience');
    return w.shots.cells.find(c => c.id === id);
  },

  // push the robot along the tape (closer / farther) or across it, in the robot's own frame like a real push
  move(along, across) {
    const w = App.last && App.last.world;
    if (!w || !this.tape) return;
    const dx = this.tape.ux * -along + -this.tape.uy * across, dy = this.tape.uy * -along + this.tape.ux * across;
    const h = w.drive.headingDeg * Math.PI / 180;
    App.hand('drive', 'push', {forward: dx * Math.cos(h) + dy * Math.sin(h), left: -dx * Math.sin(h) + dy * Math.cos(h), seconds: 1});
  },

  update(el, s) {
    const w = s.world;
    if (!w || !w.shots || !w.drive) return;
    const f = this.fieldXY.bind(this), S = this.S, sh = w.shots, coach = el.querySelector('#shCoach').checked;
    if (!this.cellsDrawn) {
      this.cellsDrawn = true;
      for (const c of sh.cells) {
        const red = c.id.startsWith('red'), depth = 8 * -c.facing;
        const a = f(c.x - c.halfW, c.y), b = f(c.x + c.halfW, c.y + depth);
        Svg.el('rect', {x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), width: Math.abs(b.x - a.x), height: Math.abs(b.y - a.y),
          fill: red ? '#e5393533' : '#1e88e533', stroke: red ? '#e53935' : '#1e88e5'}, this.cellLayer);
        Svg.el('line', {x1: a.x, y1: a.y, x2: f(c.x + c.halfW, c.y).x, y2: a.y, stroke: red ? '#e53935' : '#1e88e5', 'stroke-width': 4}, this.cellLayer);
      }
    }
    const g = this.dyn;
    g.innerHTML = '';
    for (const b of sh.landed) {
      const p = f(b[0], b[1]);
      Svg.el('circle', {cx: p.x, cy: p.y, r: (b[2] ? 1.8 : 1.4) * S, fill: b[2] ? '#e53935' : '#fdd835', stroke: '#0006'}, g);
    }
    // recent trajectories from above
    for (const r of this.recent) {
      if (!r.samples) continue;
      const pts = r.samples.map(p => { const q = f(p[0], p[1]); return q.x + ',' + q.y; }).join(' ');
      const color = !coach ? 'var(--muted)' : r.result === 'made' ? 'var(--good)' : 'var(--bad)';
      Svg.el('polyline', {points: pts, fill: 'none', stroke: color, 'stroke-width': 1.5, 'stroke-dasharray': '3 2'}, g);
    }
    // robot, turrets, tape
    const d = w.drive, rp = f(d.x, d.y);
    const robot = Svg.el('g', {transform: `translate(${rp.x},${rp.y}) rotate(${-d.headingDeg})`}, g);
    Svg.el('rect', {x: -9 * S, y: -9 * S, width: 18 * S, height: 18 * S, rx: 4, fill: 'var(--panel)', stroke: 'var(--ink)', 'stroke-width': 2}, robot);
    Svg.el('polygon', {points: `${9 * S},0 ${6 * S},${-3 * S} ${6 * S},${3 * S}`, fill: 'var(--accent)'}, robot);
    if (w.turret) {
      [w.turret.frontDeg, w.turret.backDeg].forEach((deg, i) => {
        const p = sh.pivots[i], a = -deg * Math.PI / 180;
        Svg.el('circle', {cx: p[0] * S, cy: -p[1] * S, r: 2.5 * S, fill: 'var(--part)'}, robot);
        Svg.el('line', {x1: p[0] * S, y1: -p[1] * S, x2: p[0] * S + Math.cos(a) * 6 * S, y2: -p[1] * S + Math.sin(a) * 6 * S,
          stroke: i ? '#ef6c00' : '#1565c0', 'stroke-width': 3, 'stroke-linecap': 'round'}, robot);
      });
    }
    const side = +el.querySelector('#shPivot').value, cell = this.ownCell(w, el.querySelector('#shAlliance').value);
    this.tape = null;
    if (cell) {
      const pv = this.pivot(w, side), dist = Math.hypot(cell.x - pv.x, cell.y - pv.y);
      this.tape = {d: dist, ux: (cell.x - pv.x) / dist, uy: (cell.y - pv.y) / dist};
      const a = f(pv.x, pv.y), b = f(cell.x, cell.y);
      Svg.el('line', {x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke: '#f9a825', 'stroke-width': 2, 'stroke-dasharray': '6 4'}, g);
      Svg.text(g, (a.x + b.x) / 2 + 6, (a.y + b.y) / 2, dist.toFixed(1) + ' in', {'font-weight': 700, 'font-size': 13});
    }
    // balls in the air, with a shadow on the floor
    const flying = new Set();
    for (const b of sh.inFlight) {
      const [x, y, z, type, id] = b;
      flying.add(id);
      (this.live[id] = this.live[id] || []).push([x, y, z]);
      const p = f(x, y);
      Svg.el('circle', {cx: p.x + z * 0.15, cy: p.y + z * 0.15, r: 1.6 * S, fill: '#0003'}, g);
      Svg.el('circle', {cx: p.x, cy: p.y, r: (type ? 1.8 : 1.4) * S * (1 + z / 80), fill: type ? '#e53935' : '#fdd835', stroke: '#0008'}, g);
    }
    if (sh.lastId !== this.lastId || this.flying && this.flying.size > flying.size) this.fetch(sh.lastId);
    this.lastId = sh.lastId;
    this.flying = flying;
    this.camera(el, w, coach);
  },

  async fetch(lastId) {
    if (this.fetching) return;
    this.fetching = true;
    try {
      const r = await App.api(`shots?since=${Math.max(0, lastId - 6)}&samples=1`);
      this.recent = r.shots;
      for (const x of r.shots) if (x.result !== 'flying') delete this.live[x.id];
    } catch (e) {
      console.warn(e);
    }
    this.fetching = false;
  },

  // side view of the newest shot, in the vertical plane it was launched in
  camera(el, w, coach) {
    const svg = el.querySelector('#shCam'), kv = el.querySelector('#shKv');
    const shot = this.recent[this.recent.length - 1];
    svg.innerHTML = '';
    if (!shot) {
      el.querySelector('#shCamNote').textContent = 'no shot yet';
      kv.innerHTML = '';
      return;
    }
    const yaw = shot.yawDeg * Math.PI / 180, ox = shot.from[0], oy = shot.from[1];
    const along = (x, y) => (x - ox) * Math.cos(yaw) + (y - oy) * Math.sin(yaw);
    const pts = shot.result === 'flying' ? (this.live[shot.id] || []) : (shot.samples || []);
    const cell = w.shots.cells.find(c => c.id === shot.target);
    let L = 40;
    for (const p of pts) L = Math.max(L, along(p[0], p[1]) + 10);
    if (cell) L = Math.max(L, along(cell.x, cell.y) + 25);
    const k = Math.min(420 / L, 200 / 80), X = (a) => 10 + a * k, Z = (z) => 225 - z * k;
    Svg.el('line', {x1: 0, y1: Z(0), x2: 440, y2: Z(0), stroke: 'var(--metal)', 'stroke-width': 2}, svg);
    Svg.el('rect', {x: X(-9), y: Z(shot.from[2]), width: 18 * k, height: shot.from[2] * k, fill: 'var(--part)', opacity: 0.6}, svg);
    if (cell) {
      const t = cell.tiltDeg * Math.PI / 180, a = along(cell.x, cell.y), sy = Math.sin(yaw);
      const vA = -cell.facing * Math.cos(t) * sy, vZ = Math.sin(t), nA = cell.facing * Math.sin(t) * sy, nZ = Math.cos(t);
      const p1 = [a - cell.halfH * vA, cell.z - cell.halfH * vZ], p2 = [a + cell.halfH * vA, cell.z + cell.halfH * vZ];
      const q1 = [p1[0] - 12 * nA, p1[1] - 12 * nZ], q2 = [p2[0] - 12 * nA, p2[1] - 12 * nZ];
      const color = cell.id.startsWith('red') ? '#e53935' : '#1e88e5';
      Svg.el('polygon', {points: [p1, p2, q2, q1].map(p => X(p[0]) + ',' + Z(p[1])).join(' '), fill: color + '33', stroke: color}, svg);
      Svg.el('line', {x1: X(p1[0]), y1: Z(p1[1]), x2: X(p2[0]), y2: Z(p2[1]), stroke: color, 'stroke-width': 4}, svg);
      Svg.el('line', {x1: X(a), y1: Z(cell.z), x2: X(a), y2: Z(0), stroke: 'var(--line)', 'stroke-dasharray': '3 3'}, svg);
    }
    if (pts.length) {
      Svg.el('polyline', {points: pts.map(p => X(along(p[0], p[1])) + ',' + Z(p[2])).join(' '), fill: 'none', stroke: 'var(--accent)', 'stroke-width': 2}, svg);
      const last = pts[pts.length - 1];
      Svg.el('circle', {cx: X(along(last[0], last[1])), cy: Z(last[2]), r: (shot.type === 'NECTAR' ? 1.8 : 1.4) * k,
        fill: shot.type === 'NECTAR' ? '#e53935' : '#fdd835', stroke: '#0008'}, svg);
    }
    el.querySelector('#shCamNote').textContent = `shot ${shot.id}, ${shot.side} launcher, ${shot.type}` + (shot.result === 'flying' ? ' (in the air)' : '');
    kv.innerHTML = `<span>Launch angle</span><b>${shot.launchDeg.toFixed(1)}°</b>`;
    el.querySelector('#shCoachKv').innerHTML = !coach ? '' :
      `<span>Result</span><b>${shot.result}${shot.detail ? ' (' + App.esc(shot.detail) + ')' : ''}</b>` +
      `<span>Distance</span><b>${shot.distanceIn.toFixed(1)} in from the turret axis to the ${App.esc(shot.target)} CELL</b>` +
      `<span>Exit speed</span><b>${shot.exitMps.toFixed(2)} m/s, wheel at ${Math.round(shot.wheelTicks)} ticks/s</b>` +
      `<span>Aim error</span><b>${shot.aimErrorDeg.toFixed(1)}° (+ = left)</b>`;
  },
});
