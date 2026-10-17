// Intake and both launchers from the side: an element at the mouth, the roller, the sorter, each lane up to its gate,
// and each flywheel with the way its surface moves where the element touches it. Program 2 asks "did it pull the
// element IN?" and "does the surface move the way that throws it OUT?": watch the arrows and the element.
// Program 7: load ONE element in a launcher lane. Sound: a hum whose pitch follows the flywheel speed.
Bench.register({
  id: 'launcher', title: 'Intake + launchers', order: 60,
  mount(el) {
    el.innerHTML = `<style>
        .lc-coach { border: 1px dashed var(--warn); border-radius: 8px; padding: 6px 10px; margin-top: 8px; }
        .lc-coach[hidden] { display: none; }
        .lc-coach h4 { margin: 0 0 4px; color: var(--warn); font-size: 13px; }
        .lc-note { color: var(--muted); font-size: 12px; }
        .view .lc-coach svg { height: 90px; }
      </style>
      <h3>Intake and launchers (side view)</h3>
      <p class="hint">Elements go in at the left, the sorter sends NECTAR up the ramp to the FRONT launcher and POLLEN under it to the
        BACK launcher. Gates hold them off the flywheels until a shot. Arrows show which way each surface moves where it touches an element.</p>
      <div class="controls">
        <span>At the intake mouth:</span>
        <button class="btn secondary" data-h='["loadIntake",{"type":"NECTAR"}]'>NECTAR</button>
        <button class="btn secondary" data-h='["loadIntake",{"type":"POLLEN"}]'>POLLEN</button>
        <span>Straight into a launcher (against its gate):</span>
        <button class="btn secondary" data-h='["loadLauncher",{"side":0}]'>front: NECTAR</button>
        <button class="btn secondary" data-h='["loadLauncher",{"side":1}]'>back: POLLEN</button>
        <button class="btn secondary" data-h='["unload",{}]'>Empty the robot</button>
      </div>
      <div class="controls">
        <button class="btn secondary" id="lcSound">Sound: off</button>
        <label><input type="checkbox" id="lcCoach"> Coach (shows hidden numbers)</label>
        <span id="lcHeld"></span>
      </div>
      <svg viewBox="0 0 680 330" id="lcSvg"></svg>
      <div class="lc-coach" id="lcCoachBox" hidden><h4>Coach: inside the robot</h4><div class="kv" id="lcKv"></div>
        <svg viewBox="0 0 400 90" preserveAspectRatio="none" id="lcTrace"></svg><div class="lc-note">True wheel speed, last 6 s (ticks/s the throwing way). Blue front, orange back.</div></div>`;
    el.querySelectorAll('[data-h]').forEach(b => b.onclick = async () => {
      const [action, args] = JSON.parse(b.dataset.h);
      const r = await App.hand('intake', action, args);
      if (r && r.ok === false) App.toast(action === 'loadIntake' ? 'Something is already at the mouth.' : 'No room: the robot holds 4 elements at most.');
    });
    el.querySelector('#lcCoach').onchange = (e) => { el.querySelector('#lcCoachBox').hidden = !e.target.checked; };
    el.querySelector('#lcSound').onclick = (e) => this.toggleSound(e.target);

    const svg = el.querySelector('#lcSvg');
    this.K = 16;
    Svg.el('line', {x1: 0, y1: 241, x2: 40, y2: 241, stroke: 'var(--line)', 'stroke-width': 3}, svg);
    Svg.text(svg, 4, 258, 'floor', {fill: 'var(--muted)', 'font-size': 11});
    this.paths = Svg.el('g', {}, svg);
    this.dynamic = Svg.el('g', {}, svg);
    this.trail = [[], []];
    this.lastLaunched = null;
    this.flash = [0, 0];
    this.bounceFlash = [0, 0];
  },

  // element centre on the screen for lane (-1 = mouth / shared channel) at s inches along the path
  point(lane, s, path) {
    const K = this.K, x0 = 70, y0 = 210;
    if (lane < 0 || s < path.sortS) return {x: x0 + s * K, y: y0};
    const a = (lane === 0 ? -30 : -6) * Math.PI / 180, d = (s - path.sortS) * K;
    return {x: x0 + path.sortS * K + d * Math.cos(a), y: y0 + d * Math.sin(a), a};
  },

  laneDir(lane) {
    const a = (lane === 0 ? -30 : -6) * Math.PI / 180;
    return {x: Math.cos(a), y: Math.sin(a)};
  },

  arrow(g, x, y, dx, dy, len, color) {
    if (len < 4) return;
    const ex = x + dx * len, ey = y + dy * len, hx = -dy * 6, hy = dx * 6;
    Svg.el('line', {x1: x, y1: y, x2: ex, y2: ey, stroke: color, 'stroke-width': 4, 'stroke-linecap': 'round'}, g);
    Svg.el('polygon', {points: `${ex + dx * 10},${ey + dy * 10} ${ex + hx},${ey + hy} ${ex - hx},${ey - hy}`, fill: color}, g);
  },

  ball(g, x, y, type, d) {
    const r = d / 2 * this.K;
    Svg.el('circle', {cx: x, cy: y, r, fill: type === 'NECTAR' ? '#e53935' : '#fdd835', stroke: '#0006', 'stroke-width': 1.5}, g);
  },

  update(el, s) {
    const w = s.world;
    if (!w || !w.intake || !w.intake.path) return;
    const it = w.intake, fw = w.flywheels, path = it.path, K = this.K;
    if (!this.drawnPaths) {
      this.drawnPaths = true;
      const a = this.point(-1, path.mouthS - 1.5, path), b = this.point(-1, path.sortS, path);
      Svg.el('line', {x1: a.x, y1: a.y + 31, x2: b.x, y2: b.y + 31, stroke: 'var(--metal)', 'stroke-width': 3}, this.paths);
      for (const lane of [0, 1]) {
        const e = this.point(lane, path.gateS[lane] + path.gapIn, path), d = this.laneDir(lane);
        Svg.el('line', {x1: b.x, y1: b.y + 31, x2: e.x - d.y * 31, y2: e.y + d.x * 31, stroke: 'var(--metal)', 'stroke-width': 3}, this.paths);
        Svg.text(this.paths, e.x + 50, e.y + (lane === 0 ? -70 : 75), lane === 0 ? 'FRONT launcher (NECTAR)' : 'BACK launcher (POLLEN)', {'font-weight': 700, 'font-size': 12});
      }
      Svg.el('polygon', {points: `${b.x - 6},${b.y + 30} ${b.x + 26},${b.y - 2} ${b.x + 26},${b.y + 30}`, fill: 'var(--part)'}, this.paths);
      Svg.text(this.paths, b.x - 20, b.y + 50, 'sorter', {fill: 'var(--muted)', 'font-size': 11});
    }
    const g = this.dynamic;
    g.innerHTML = '';

    // intake roller above the mouth; its bottom surface touches the element
    const rx = 70, ry = 210 - 1.8 * K - K, rr = K;
    Svg.el('circle', {cx: rx, cy: ry, r: rr, fill: '#8d6e63'}, g);
    const ra = it.deg * Math.PI / 180;
    for (const off of [0, Math.PI / 2]) {
      Svg.el('line', {x1: rx - Math.cos(ra + off) * rr, y1: ry - Math.sin(ra + off) * rr, x2: rx + Math.cos(ra + off) * rr, y2: ry + Math.sin(ra + off) * rr, stroke: '#d7ccc8', 'stroke-width': 3}, g);
    }
    Svg.text(g, rx - 20, ry - rr - 8, 'intake roller', {'font-size': 11, fill: 'var(--muted)'});
    const sIn = Math.min(1, Math.abs(it.surfaceIn) / 30);
    this.arrow(g, rx, ry + rr + 4, Math.sign(it.surfaceIn), 0, 34 * sIn, 'var(--accent)');

    // gates and flywheels
    for (const lane of [0, 1]) {
      const d = this.laneDir(lane), n = {x: d.y, y: -d.x};   // n points up from the lane
      const gp = this.point(lane, path.gateS[lane], path);
      const open = Math.max(0, Math.min(1, it.gateOpen[lane]));
      const ga = (1 - open) * Math.PI / 2;                   // closed: hangs across the lane; open: folded forward
      const px = gp.x + n.x * 34, py = gp.y + n.y * 34;
      const fx = -n.x * Math.sin(ga) + d.x * Math.cos(ga), fy = -n.y * Math.sin(ga) + d.y * Math.cos(ga);
      Svg.el('line', {x1: px, y1: py, x2: px + fx * 62, y2: py + fy * 62, stroke: it.gateRubs[lane] ? 'var(--bad)' : 'var(--metal)', 'stroke-width': 6, 'stroke-linecap': 'round'}, g);
      Svg.el('circle', {cx: px, cy: py, r: 4, fill: 'var(--ink)'}, g);
      Svg.text(g, px - 12, py - 8, 'gate', {'font-size': 11, fill: 'var(--muted)'});

      const c = this.point(lane, path.gateS[lane] + path.gapIn, path), wr = 1.9 * K;
      const ballR = (lane === 0 ? 3.6 : 2.8) / 2 * K;
      const wx = c.x + n.x * (wr + ballR), wy = c.y + n.y * (wr + ballR);
      Svg.el('circle', {cx: wx, cy: wy, r: wr, fill: '#455a64'}, g);
      const fa = (fw.deg[lane] || 0) * Math.PI / 180;
      for (const off of [0, Math.PI / 3, 2 * Math.PI / 3]) {
        Svg.el('line', {x1: wx - Math.cos(fa + off) * wr, y1: wy - Math.sin(fa + off) * wr, x2: wx + Math.cos(fa + off) * wr, y2: wy + Math.sin(fa + off) * wr, stroke: '#eceff1', 'stroke-width': 3}, g);
      }
      // the fixed guide arc and rollers, and the way out
      const ox = wx + d.x * wr * 1.1, oy = wy + d.y * wr * 1.1;
      Svg.el('path', {d: `M ${c.x - n.x * ballR} ${c.y - n.y * ballR} Q ${ox + d.x * 30 - n.x * 10} ${oy + d.y * 30 - n.y * 10} ${ox + d.x * 26 + n.x * 40} ${oy + d.y * 26 + n.y * 40}`, fill: 'none', stroke: 'var(--part)', 'stroke-width': 5}, g);
      Svg.text(g, ox + d.x * 30 + n.x * 48, oy + d.y * 30 + n.y * 48, 'OUT', {'font-size': 11, 'font-weight': 700, fill: 'var(--muted)'});
      // surface direction where the wheel touches the element (its side facing the lane)
      const rpm = fw.rpmOut[lane] || 0, len = 46 * Math.min(1, Math.abs(rpm) / 1500);
      const tx = wx - n.x * wr, ty = wy - n.y * wr;
      this.arrow(g, tx, ty, d.x * Math.sign(rpm), d.y * Math.sign(rpm), len, rpm >= 0 ? 'var(--good)' : 'var(--bad)');
      if (fw.contact[lane]) this.ball(g, c.x, c.y, lane === 0 ? 'NECTAR' : 'POLLEN', lane === 0 ? 3.6 : 2.8);
      // a shot: the element leaves along the guide
      if (this.lastLaunched && fw.launched[lane] > this.lastLaunched[lane]) this.flash[lane] = 8;
      if (this.lastBounced && fw.bouncedBack[lane] > this.lastBounced[lane]) this.bounceFlash[lane] = 30;
      if (this.flash[lane] > 0) {
        const k = 8 - this.flash[lane]--;
        this.ball(g, ox + d.x * 26 + n.x * (40 + k * 22), oy + d.y * 26 + n.y * (40 + k * 22), lane === 0 ? 'NECTAR' : 'POLLEN', lane === 0 ? 3.6 : 2.8);
      }
      if (this.bounceFlash[lane] > 0) {
        this.bounceFlash[lane]--;
        Svg.text(g, c.x - 70, c.y + (lane === 0 ? -48 : 56), 'pushed back into the robot!', {fill: 'var(--bad)', 'font-weight': 700, 'font-size': 12});
      }
    }
    this.lastLaunched = fw.launched.slice();
    this.lastBounced = fw.bouncedBack.slice();

    for (const e of it.elements) {
      const p = this.point(e.lane, e.s, path);
      this.ball(g, p.x, p.y, e.type, e.d);
    }
    el.querySelector('#lcHeld').textContent = `Held: ${it.held} of ${it.capacity}` + (it.spatOut ? `, pushed out of the intake: ${it.spatOut}` : '');

    this.sound(fw.rpmOut);
    if (el.querySelector('#lcCoach').checked) this.coach(el, w);
  },

  coach(el, w) {
    const fw = w.flywheels, it = w.intake, f0 = (v, n) => (v ?? 0).toFixed(n ?? 0);
    el.querySelector('#lcKv').innerHTML = ['front', 'back'].map((n, i) =>
      `<span>${n} flywheel</span><b>${f0(fw.rpmOut[i])} rpm = ${f0(fw.ticksOut[i])} ticks/s the throwing way` +
      `${fw.rpmOut[i] < -50 ? ' (WRONG WAY: elements bounce back)' : ''}; last shot: dip ${f0(fw.lastDipTicks[i])} ticks/s, ` +
      `exit ${f0(fw.lastExitMps[i], 2)} m/s; ${fw.launched[i]} launched, ${fw.bouncedBack[i]} pushed back</b>`).join('') +
      `<span>intake</span><b>elements move ${f0(it.surfaceIn, 1)} in/s (+ = in)${it.blocked ? ', roller slipping on ' + it.blocked : ''}</b>` +
      `<span>POLLEN feeder</span><b>POLLEN moves ${f0(it.pollenSurfaceIn, 1)} in/s (+ = toward the back launcher)${it.blockedPollen ? ', wheels slipping on ' + it.blockedPollen : ''}</b>` +
      `<span>gates</span><b>front ${f0(it.gateOpen[0] * 100)}% open${it.gateRubs[0] ? ' RUBS the wheel' : ''}, back ${f0(it.gateOpen[1] * 100)}% open${it.gateRubs[1] ? ' RUBS the wheel' : ''}</b>`;
    for (let i = 0; i < 2; i++) {
      this.trail[i].push(fw.ticksOut[i]);
      if (this.trail[i].length > 120) this.trail[i].shift();
    }
    const svg = el.querySelector('#lcTrace');
    const max = Math.max(500, ...this.trail[0], ...this.trail[1]), min = Math.min(0, ...this.trail[0], ...this.trail[1]);
    svg.innerHTML = '';
    ['#1565c0', '#ef6c00'].forEach((color, i) => {
      const pts = this.trail[i].map((v, k) => `${k * 400 / 119},${85 - (v - min) / (max - min) * 80}`).join(' ');
      Svg.el('polyline', {points: pts, fill: 'none', stroke: color, 'stroke-width': 2}, svg);
    });
    Svg.text(svg, 4, 12, Math.round(max) + ' ticks/s', {'font-size': 10, fill: 'var(--muted)'});
  },

  // a hum per flywheel: pitch follows the wheel speed, like the whine you hear on the real robot
  toggleSound(btn) {
    if (!this.audio) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) { App.toast('This browser has no Web Audio.'); return; }
      this.audio = new Ctx();
      this.voices = [0, 1].map(() => {
        const o = this.audio.createOscillator(), gain = this.audio.createGain();
        o.type = 'sawtooth';
        gain.gain.value = 0;
        o.connect(gain).connect(this.audio.destination);
        o.start();
        return {o, gain};
      });
      this.soundOn = false;
    }
    this.soundOn = !this.soundOn;
    if (this.soundOn) this.audio.resume();
    btn.textContent = 'Sound: ' + (this.soundOn ? 'on' : 'off');
    if (!this.soundOn) this.voices.forEach(v => v.gain.gain.setTargetAtTime(0, this.audio.currentTime, 0.05));
  },

  sound(rpm) {
    if (!this.soundOn) return;
    const t = this.audio.currentTime;
    this.voices.forEach((v, i) => {
      const rps = Math.abs(rpm[i] || 0) / 60;
      v.o.frequency.setTargetAtTime(30 + rps * 4, t, 0.05);
      v.gain.gain.setTargetAtTime(Math.min(1, rps / 60) * 0.05, t, 0.05);
    });
  },
});
