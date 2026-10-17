// Turret close-up for program 3. Both turrets from above with the robot's edges; each has its own yaw servo. You
// judge "straight" by eye, optionally with a straightedge laid along an edge. While a servo has no signal its turret
// turns by hand (drag around it); once program 3 moves it, the servo holds it. The angles stay hidden unless the
// Coach readout is on.
Bench.register({
  id: 'turret', title: 'Turret', order: 30,
  mount(el) {
    el.innerHTML = `<style>
        .tu-svg { width: 100%; max-width: 620px; display: block; touch-action: none; }
        .tu-warn { color: var(--bad); font-weight: 700; }
        .tu-coach { border: 1px dashed var(--warn); padding: 6px 8px; margin-top: 6px; }
        .tu-coach b { color: var(--warn); }
        .tu-coach[hidden] { display: none; }
      </style>
      <h3>Turrets close-up (top view, front is up)</h3>
      <p class="hint">"Straight left" = barrel parallel to the robot's FRONT edge (front turret) or BACK edge (back turret);
        "forward" = parallel to the side edges. Judge it by eye, or lay the straightedge along an edge and slide it over the
        barrel. The cable turns orange when it is just tight, red when the servo is pulling on it. A turret with no servo
        signal yet turns by hand (drag around it).</p>
      <div class="controls">
        <span>Straightedge:</span>
        <button class="btn secondary" data-ruler="off">off</button>
        <button class="btn secondary" data-ruler="h">along front / back edge</button>
        <button class="btn secondary" data-ruler="v">along a side edge</button>
      </div>
      <div class="controls">
        <button class="btn secondary" data-fix="front">Re-route the front cable</button>
        <button class="btn secondary" data-fix="back">Re-route the back cable</button>
        <label><input type="checkbox" id="tuCoach"> Coach readout (shows hidden numbers)</label>
      </div>
      <div class="controls" id="tuTurn">${['front', 'back'].map(t => `<span>${t} turret:</span>
        <button class="btn secondary" data-turn="${t}" data-deg="10">&#10226; 10&deg;</button>
        <button class="btn secondary" data-turn="${t}" data-deg="1">&#10226; 1&deg;</button>
        <button class="btn secondary" data-turn="${t}" data-deg="-1">1&deg; &#10227;</button>
        <button class="btn secondary" data-turn="${t}" data-deg="-10">10&deg; &#10227;</button>`).join('<span style="width:12px"></span>')}
        <span class="hint">by hand, only while that servo has no signal; hold to keep turning</span></div>
      <div id="tuMsg"></div>
      <svg viewBox="0 0 600 640" class="tu-svg" id="tuSvg"></svg>
      <div class="kv tu-coach" id="tuCoachKv" hidden></div>`;
    this.build(el);
    // turn by hand in steps: a press turns once, holding keeps turning 5 times a second
    el.querySelectorAll('[data-turn]').forEach(b => {
      let timer = null;
      const turn = () => App.hand('turret', 'turn', {deg: Number(b.dataset.deg), back: b.dataset.turn === 'back'});
      const end = () => { if (!timer) return; clearInterval(timer); timer = null; setTimeout(() => App.hand('turret', 'release'), 80); };
      b.addEventListener('pointerdown', (e) => { e.preventDefault(); b.setPointerCapture(e.pointerId); turn(); timer = setInterval(turn, 200); });
      b.addEventListener('pointerup', end);
      b.addEventListener('pointercancel', end);
    });
  },

  build(el) {
    const svg = el.querySelector('#tuSvg');
    const S = 30, cx = 300, cy = 320;
    Svg.el('rect', {x: 0, y: 0, width: 600, height: 640, fill: 'var(--panel)'}, svg);
    Svg.el('rect', {x: cx - 9 * S, y: cy - 9 * S, width: 18 * S, height: 18 * S, fill: 'none', stroke: 'var(--metal)', 'stroke-width': 4}, svg);
    Svg.text(svg, cx, cy - 9 * S - 10, 'FRONT edge', {'text-anchor': 'middle', 'font-weight': 700});
    Svg.text(svg, cx, cy + 9 * S + 22, 'BACK edge', {'text-anchor': 'middle', 'font-weight': 700});
    Svg.text(svg, cx - 9 * S - 8, cy, 'LEFT', {'text-anchor': 'middle', transform: `rotate(-90 ${cx - 9 * S - 8} ${cy})`});
    Svg.text(svg, cx + 9 * S + 16, cy, 'RIGHT', {'text-anchor': 'middle', transform: `rotate(90 ${cx + 9 * S + 16} ${cy})`});
    this.turrets = [['front', -3], ['back', 4.5]].map(([name, y], i) => {
      const t = {x: cx, y: cy + y * S, back: i === 1};
      const anchor = [t.x + 150, t.y + (i ? -60 : 60)];
      t.cable = Svg.el('path', {fill: 'none', 'stroke-width': 5, 'stroke-linecap': 'round'}, svg);
      t.anchor = anchor;
      Svg.el('circle', {cx: anchor[0], cy: anchor[1], r: 6, fill: 'var(--metal)'}, svg);
      t.ring = Svg.el('circle', {cx: t.x, cy: t.y, r: 58, fill: 'var(--panel)', stroke: 'var(--part)', 'stroke-width': 8, class: 'grab'}, svg);
      t.body = Svg.el('g', {'pointer-events': 'none'}, svg);
      Svg.el('rect', {x: t.x - 9, y: t.y - 112, width: 18, height: 112, rx: 4, fill: 'var(--accent)'}, t.body);
      Svg.el('circle', {cx: t.x, cy: t.y, r: 16, fill: 'var(--metal)'}, t.body);
      t.label = Svg.text(svg, t.x - 150, t.y + 4, name + ' turret', {'text-anchor': 'middle'});
      let last = null;
      const batch = Bench.handBatcher('turret', 'turn', 'deg');
      const angleAt = (m) => {
        const p = this.toSvg(svg, m);
        return Math.atan2(-(p.x - t.x), -(p.y - t.y)) * 180 / Math.PI;
      };
      Bench.drag(t.ring, (dx, dy, m) => {
        const a = angleAt(m);
        if (last != null) { let d = a - last; if (d > 180) d -= 360; if (d < -180) d += 360; batch.add(d, {back: t.back}); }
        last = a;
      }, () => { last = null; setTimeout(() => App.hand('turret', 'release'), 80); }, (e) => { last = angleAt(e); });
      return t;
    });
    // straightedge: lies along the chosen edge direction; drag it across to compare with a barrel
    this.ruler = {mode: 'off', h: this.turrets[0].y, v: cx};
    const rg = Svg.el('g', {class: 'grab', opacity: 0.55}, svg);
    this.rulerG = rg;
    Svg.el('rect', {x: -280, y: -9, width: 560, height: 18, fill: 'var(--warn)'}, rg);
    for (let k = -27; k <= 27; k++) Svg.el('line', {x1: k * 10, x2: k * 10, y1: -9, y2: k % 5 ? -3 : 1, stroke: 'var(--ink)', 'stroke-width': 1}, rg);
    Bench.drag(rg, (dx, dy, m) => {
      const p = this.toSvg(svg, m);
      if (this.ruler.mode === 'h') this.ruler.h = p.y; else this.ruler.v = p.x;
      this.placeRuler();
    });
    el.querySelectorAll('[data-ruler]').forEach(b => b.onclick = () => { this.ruler.mode = b.dataset.ruler; this.placeRuler(); });
    this.placeRuler();
    el.querySelectorAll('[data-fix]').forEach(b => b.onclick = () => App.hand('turret', 'fixCable', {back: b.dataset.fix === 'back'}));
    el.querySelector('#tuCoach').onchange = (e) => { el.querySelector('#tuCoachKv').hidden = !e.target.checked; };
  },

  update(el, s) {
    const tu = s.world && s.world.turret;
    if (!tu || !tu.front) return;
    const msg = [];
    let damaged = '';
    this.turrets.forEach((t, i) => {
      const one = i ? tu.back : tu.front, name = i ? 'back' : 'front', deg = one.deg, a = deg * Math.PI / 180;
      t.body.setAttribute('transform', `rotate(${-deg} ${t.x} ${t.y})`);
      const tension = one.cableTension;
      const ex = t.x + Math.sin(a) * 58, ey = t.y + Math.cos(a) * 58;
      const sag = 45 * (1 - tension);
      const mx = (ex + t.anchor[0]) / 2, my = (ey + t.anchor[1]) / 2 + sag;
      t.cable.setAttribute('d', `M${ex},${ey} Q${mx},${my} ${t.anchor[0]},${t.anchor[1]}`);
      t.cable.setAttribute('stroke', one.cableDamaged || tension > 0.5 ? 'var(--bad)' : tension > 0 ? 'var(--warn)' : 'var(--part)');
      t.ring.setAttribute('stroke', one.atStop || one.buzz > 0.12 ? 'var(--bad)' : one.powered ? 'var(--accent)' : 'var(--part)');
      if (one.atStop) msg.push(`The ${name} turret is against its ${one.atStop} end` + (one.buzz > 0.12 ? ': the servo is buzzing, pressing into it.' : '.'));
      if (one.cableTension > 0 && !one.cableDamaged) msg.push(`The ${name} cable is tight (turning ${one.cableSide}).` + (one.cableTension > 0.5 ? ' It is being pulled: turn back!' : ''));
      if (one.cableDamaged) damaged += `<p class="tu-warn">${name} cable damaged: it was pulled past its slack. Re-route / replace it before running the turret again.</p>`;
    });
    el.querySelector('#tuMsg').innerHTML = damaged + msg.map(m => `<p class="hint">${App.esc(m)}</p>`).join('');
    if (!el.querySelector('#tuCoachKv').hidden) {
      const off = (d) => { const r = ((d % 90) + 135) % 90 - 45; return r.toFixed(1); };
      const ends = (o) => `${o.leftLimitDeg.toFixed(1)}&deg; (${o.leftIs}) / ${o.rightLimitDeg.toFixed(1)}&deg; (${o.rightIs})`;
      el.querySelector('#tuCoachKv').innerHTML =
        `<span>Front / back turret angle</span><b>${tu.front.deg.toFixed(1)}&deg; / ${tu.back.deg.toFixed(1)}&deg;</b>` +
        `<span>Off the nearest straight line</span><b>front ${off(tu.front.deg)}&deg;, back ${off(tu.back.deg)}&deg;</b>` +
        `<span>Front left / right end of travel</span><b>${ends(tu.front)}</b>` +
        `<span>Back left / right end of travel</span><b>${ends(tu.back)}</b>`;
    }
  },

  toSvg(svg, m) {
    const p = svg.createSVGPoint();
    p.x = m.clientX;
    p.y = m.clientY;
    return p.matrixTransform(svg.getScreenCTM().inverse());
  },

  placeRuler() {
    const r = this.ruler;
    this.rulerG.style.display = r.mode === 'off' ? 'none' : '';
    this.rulerG.setAttribute('transform', r.mode === 'h' ? `translate(300 ${r.h})` : `translate(${r.v} 320) rotate(90)`);
  },
});
