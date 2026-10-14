// The robot from above, on a box or on the floor. Grab a wheel, the intake roller, a flywheel or a turret and drag
// to turn it by hand, like program 1 asks. Front of the robot is up.
Bench.register({
  id: 'robot', title: 'Robot', order: 10,
  mount(el) {
    el.innerHTML = `<h3>Robot (top view, front is up)</h3>
      <p class="hint">Drag a wheel, the intake roller or a flywheel up/down to turn it by hand. Drag around a turret to turn it.
        Flywheels have few encoder counts per turn: spin them a full turn or more.</p>
      <div class="controls">
        <button class="btn secondary" data-a="onBox">Put on box</button>
        <button class="btn secondary" data-a="onFloor">Put on floor</button>
        <button class="btn secondary" data-a="swapBattery">Swap in a charged battery</button>
        <span id="rbWhere"></span>
      </div>
      <div class="controls" id="rbPush">
        <span>Push (on the floor):</span>
        <button class="btn secondary" data-p='{"forward":12}'>1 ft forward</button>
        <button class="btn secondary" data-p='{"forward":-12}'>1 ft back</button>
        <button class="btn secondary" data-p='{"left":12}'>1 ft left</button>
        <button class="btn secondary" data-p='{"left":-12}'>1 ft right</button>
        <button class="btn secondary" data-p='{"turnDeg":90}'>turn 90&deg; left</button>
        <button class="btn secondary" data-p='{"turnDeg":-90}'>turn 90&deg; right</button>
      </div>
      <svg viewBox="0 0 600 600" id="rbSvg"></svg>
      <div class="kv" id="rbKv"></div>`;
    el.querySelectorAll('[data-a]').forEach(b => b.onclick = () => App.hand('robot', b.dataset.a));
    el.querySelectorAll('[data-p]').forEach(b => b.onclick = () => App.hand('drive', 'push', Object.assign({seconds: 1.2}, JSON.parse(b.dataset.p))));
    const svg = el.querySelector('#rbSvg');
    const S = 20, cx = 300, cy = 300;
    const g = Svg.el('g', {}, svg);
    Svg.el('rect', {x: cx - 9 * S, y: cy - 9 * S, width: 18 * S, height: 18 * S, rx: 6, fill: 'none', stroke: 'var(--metal)', 'stroke-width': 4}, g);
    Svg.text(g, cx, cy - 9 * S - 10, 'FRONT', {'text-anchor': 'middle', 'font-weight': 700});
    const wheels = [[-7, -6.5], [-7, 6.5], [7, -6.5], [7, 6.5]];
    this.wheels = wheels.map(([x, y], i) => {
      const w = Svg.el('g', {class: 'grab'}, g);
      Svg.el('rect', {x: cx + x * S - 22, y: cy + y * S - 40, width: 44, height: 80, rx: 8, fill: 'var(--wheel)'}, w);
      const stripes = Svg.el('g', {}, w);
      const lines = [];
      for (let k = 0; k < 5; k++) lines.push(Svg.el('line', {x1: cx + x * S - 20, x2: cx + x * S + 20, stroke: '#9e9e9e', 'stroke-width': 3}, stripes));
      const label = Svg.text(w, cx + x * S, cy + y * S + 58, '', {'text-anchor': 'middle'});
      const batch = Bench.handBatcher('drive', 'turnWheel', 'deg');
      Bench.drag(w, (dx, dy) => batch.add(-dy * 3, {wheel: i}), () => setTimeout(() => App.hand('drive', 'releaseWheel', {wheel: i}), 60));
      return {lines, y: cy + y * S, label};
    });
    // intake roller across the front
    const intake = Svg.el('g', {class: 'grab'}, g);
    Svg.el('rect', {x: cx - 110, y: cy - 9 * S - 4, width: 220, height: 26, rx: 13, fill: '#8d6e63'}, intake);
    this.intakeLines = [];
    for (let k = 0; k < 6; k++) this.intakeLines.push(Svg.el('line', {y1: cy - 9 * S - 2, y2: cy - 9 * S + 20, stroke: '#d7ccc8', 'stroke-width': 3}, intake));
    this.intakeLabel = Svg.text(g, cx + 120, cy - 9 * S + 14, '', {});
    const ib = Bench.handBatcher('intake', 'turn', 'deg');
    Bench.drag(intake, (dx, dy) => ib.add(-dy * 3), () => setTimeout(() => App.hand('intake', 'release'), 60));
    // turrets with barrels and flywheels
    this.turrets = [[0, -3, 'front'], [0, 4.5, 'back']].map(([x, y, name], i) => {
      const t = Svg.el('g', {}, g);
      const ring = Svg.el('circle', {cx: cx + x * S, cy: cy + y * S, r: 46, fill: 'var(--panel)', stroke: 'var(--part)', 'stroke-width': 6, class: 'grab'}, t);
      const barrel = Svg.el('line', {x1: cx + x * S, y1: cy + y * S, stroke: 'var(--accent)', 'stroke-width': 10, 'stroke-linecap': 'round'}, t);
      const label = Svg.text(t, cx + x * S + 60, cy + y * S + 4, '', {});
      let last = null;
      Bench.drag(ring, (dx, dy, m) => {
        const pt = new DOMPoint(m.clientX, m.clientY).matrixTransform(svg.getScreenCTM().inverse());
        const px = pt.x - (cx + x * S), py = pt.y - (cy + y * S);
        const a = Math.atan2(-px, -py) * 180 / Math.PI;
        if (last != null) { let d = a - last; if (d > 180) d -= 360; if (d < -180) d += 360; App.hand('turret', 'turn', {deg: d, back: i === 1}); }
        last = a;
      }, () => { last = null; setTimeout(() => App.hand('turret', 'release'), 60); });
      const fw = Svg.el('g', {class: 'grab'}, g);
      Svg.el('circle', {cx: cx + x * S - 80, cy: cy + y * S, r: 22, fill: '#455a64'}, fw);
      const spoke = Svg.el('line', {stroke: '#eceff1', 'stroke-width': 4}, fw);
      Svg.text(g, cx + x * S - 80, cy + y * S + 38, name + ' flywheel', {'text-anchor': 'middle', 'font-size': 11});
      const fb = Bench.handBatcher('flywheels', 'turn', 'deg');
      Bench.drag(fw, (dx, dy) => fb.add(-dy * 8, {wheel: i}), () => setTimeout(() => App.hand('flywheels', 'release', {wheel: i}), 60));
      return {barrel, label, x: cx + x * S, y: cy + y * S, spoke, fx: cx + x * S - 80};
    });
  },
  update(el, s) {
    const w = s.world;
    if (!w) return;
    el.querySelector('#rbWhere').textContent = w.onBox ? 'Robot is on the box (wheels in the air).' : 'Robot is on the floor.';
    el.querySelector('#rbPush').style.opacity = w.onBox ? 0.4 : 1;
    this.wheels.forEach((wh, i) => {
      const off = ((w.drive.wheelDeg[i] % 36) + 36) % 36 / 36 * 16;
      wh.lines.forEach((l, k) => { const y = wh.y - 40 + ((k * 16 + off) % 80); l.setAttribute('y1', y); l.setAttribute('y2', y); });
      wh.label.textContent = Math.round(w.drive.wheelRpm[i]) + ' rpm';
    });
    const ioff = ((w.intake.deg % 40) + 40) % 40 / 40 * 36;
    this.intakeLines.forEach((l, k) => { const x = 190 + ((k * 36 + ioff) % 216); l.setAttribute('x1', x); l.setAttribute('x2', x); });
    this.intakeLabel.textContent = Math.round(w.intake.rpmIn) + ' rpm (in)';
    [w.turret.frontDeg, w.turret.backDeg].forEach((deg, i) => {
      const t = this.turrets[i], a = deg * Math.PI / 180;
      t.barrel.setAttribute('x2', t.x - Math.sin(a) * 60);
      t.barrel.setAttribute('y2', t.y - Math.cos(a) * 60);
      t.label.textContent = (i ? 'back' : 'front') + ' turret';
      const f = w.flywheels.deg[i] * Math.PI / 180;
      t.spoke.setAttribute('x1', t.fx - Math.cos(f) * 18); t.spoke.setAttribute('y1', t.y - Math.sin(f) * 18);
      t.spoke.setAttribute('x2', t.fx + Math.cos(f) * 18); t.spoke.setAttribute('y2', t.y + Math.sin(f) * 18);
    });
    el.querySelector('#rbKv').innerHTML =
      `<span>Battery</span><b>${w.battery.volts.toFixed(2)} V, ${(w.battery.charge * 100).toFixed(0)}% charged, ${w.battery.amps.toFixed(1)} A</b>` +
      `<span>Flywheels</span><b>front ${Math.round(w.flywheels.rpmOut[0])} rpm, back ${Math.round(w.flywheels.rpmOut[1])} rpm (+ = throws out)</b>`;
  },
});
