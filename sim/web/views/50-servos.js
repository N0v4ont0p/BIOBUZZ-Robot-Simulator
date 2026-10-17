// Ramp and gates from the side, the way you look at them during programs 2 and 4 (no hoods: team 2026-10-17). The ramp
// hangs on strings from a spool its two continuous servos turn: watch it go out and in, see if it creeps down, hear the
// servos buzz when they strain or fight. The back gate has a servo each side of one arm. Hold an element against a
// gate. "Coach" shows what you can't see on a real robot (angles, gaps, current, heat, the good range of each step).
const ServoDraw = (() => {
  const state = {coach: false, sound: true};
  try {
    state.coach = localStorage.getItem('servoCoach') === '1';
    state.sound = localStorage.getItem('servoSound') !== '0';
  } catch (e) { /* storage off */ }

  const style = document.createElement('style');
  style.textContent = `
    .srv-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 12px; }
    .srv-card { border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; min-width: 0; }
    .srv-card h4 { margin: 0 0 4px; display: flex; gap: 8px; align-items: baseline; }
    .srv-card h4 .feel { font-weight: 400; font-size: 12px; color: var(--muted); margin-left: auto; }
    .srv-card .controls { margin: 4px 0; }
    .srv-card .say { font-size: 12px; min-height: 1.3em; color: var(--ink); }
    .srv-card .coach { font-size: 12px; color: var(--warn); font-variant-numeric: tabular-nums; min-height: 1.3em; }
    .srv-card.wide { grid-column: 1 / -1; }
    .srv-coach-on { color: var(--warn); font-weight: 600; }
    @keyframes srvShake { 0% { transform: translate(0, 0); } 25% { transform: translate(1.2px, -0.8px); }
      50% { transform: translate(-1px, 0.9px); } 75% { transform: translate(0.8px, 1px); } 100% { transform: translate(0, 0); } }
    .srv-shake { animation: srvShake 0.06s linear infinite; }
  `;
  document.head.appendChild(style);

  // inches -> SVG pixels for a window x0..x1, y0..y1 (y up)
  function frame(svg, x0, y0, x1, y1, px) {
    svg.setAttribute('viewBox', `0 0 ${(x1 - x0) * px} ${(y1 - y0) * px}`);
    return {X: (x) => (x - x0) * px, Y: (y) => (y1 - y) * px, S: (v) => v * px};
  }

  const rad = (d) => d * Math.PI / 180;

  function servoBody(parent, w, h) {
    const g = Svg.el('g', {}, parent);
    const box = Svg.el('rect', {width: w, height: h, rx: 3, fill: 'var(--part)', stroke: 'var(--metal)'}, g);
    const hot = Svg.el('rect', {width: w, height: h, rx: 3, fill: 'var(--hot)', opacity: 0}, g);
    const label = Svg.text(g, w / 2, h / 2 + 4, 'servo', {'text-anchor': 'middle', 'font-size': 10, fill: '#263238'});
    return {
      g,
      at(x, y) { g.setAttribute('transform', `translate(${x - w / 2},${y - h / 2})`); },
      update(st) {
        hot.setAttribute('opacity', st.burnedOut ? 0 : Math.min(0.8, st.heat * 0.9));
        box.setAttribute('fill', st.burnedOut ? '#3e2723' : 'var(--part)');
        label.textContent = st.burnedOut ? 'burnt' : 'servo';
        label.setAttribute('fill', st.burnedOut ? '#ffab91' : '#263238');
      },
    };
  }

  // a gate, the flywheel and (maybe) an element, from the side; feed runs left to right into the wheel
  function gateScene(parent, f, id) {
    const g = Svg.el('g', {id}, parent);
    Svg.el('line', {x1: f.X(-8), y1: f.Y(0), x2: f.X(2.6), y2: f.Y(0), stroke: 'var(--metal)', 'stroke-width': 4}, g);
    Svg.text(g, f.X(-7.8), f.Y(-0.5), 'feed lane →', {'font-size': 11, fill: 'var(--muted)'});
    const wheel = Svg.el('circle', {fill: 'var(--part)', 'fill-opacity': 0.35, stroke: 'var(--metal)', 'stroke-width': 1.5, 'vector-effect': 'non-scaling-stroke'}, g);
    const spoke = Svg.el('circle', {r: 4, fill: 'var(--metal)'}, g);
    const wlabel = Svg.text(g, 0, 0, 'flywheel', {'text-anchor': 'middle', 'font-size': 11, fill: 'var(--muted)'});
    const element = Svg.el('circle', {fill: '#f9a825', stroke: '#8d6e00', 'stroke-width': 1, 'vector-effect': 'non-scaling-stroke'}, g);
    const arm = Svg.el('line', {stroke: 'var(--accent)', 'stroke-width': f.S(0.22), 'stroke-linecap': 'round'}, g);
    const body = servoBody(g, 30, 20);
    const pin = Svg.el('circle', {r: 3.5, fill: 'var(--ink)'}, g);
    return {
      g,
      update(st, geo, theta, elementX) {
        wheel.setAttribute('cx', f.X(0)); wheel.setAttribute('cy', f.Y(geo.wheelY)); wheel.setAttribute('r', f.S(geo.wheelR));
        spoke.setAttribute('cx', f.X(0)); spoke.setAttribute('cy', f.Y(geo.wheelY));
        wlabel.setAttribute('x', f.X(0)); wlabel.setAttribute('y', f.Y(geo.wheelY + geo.wheelR) - 6);
        const tx = st.pivotX - st.arm * Math.sin(rad(theta)), ty = st.pivotY - st.arm * Math.cos(rad(theta));
        arm.setAttribute('x1', f.X(st.pivotX)); arm.setAttribute('y1', f.Y(st.pivotY));
        arm.setAttribute('x2', f.X(tx)); arm.setAttribute('y2', f.Y(ty));
        body.at(f.X(st.pivotX), f.Y(st.pivotY) - 14);
        body.update(st);
        pin.setAttribute('cx', f.X(st.pivotX)); pin.setAttribute('cy', f.Y(st.pivotY));
        element.setAttribute('visibility', elementX == null ? 'hidden' : 'visible');
        if (elementX != null) {
          element.setAttribute('cx', f.X(elementX)); element.setAttribute('cy', f.Y(geo.elementR)); element.setAttribute('r', f.S(geo.elementR));
        }
      },
    };
  }

  function rampScene(parent, f) {
    const g = Svg.el('g', {}, parent);
    const box = Svg.el('rect', {x: f.X(-12), y: f.Y(0), width: f.S(6), height: f.S(2), fill: '#8d6e63', opacity: 0.6}, g);
    const boxLabel = Svg.text(g, f.X(-9), f.Y(-1.3), 'box', {'font-size': 11, fill: 'var(--ink)', 'text-anchor': 'middle'});
    const ground = Svg.el('line', {x1: f.X(-20), x2: f.X(10), y1: f.Y(-2), y2: f.Y(-2), stroke: 'var(--metal)', 'stroke-width': 2}, g);
    const floor = Svg.el('line', {x1: f.X(-20), x2: f.X(10), y1: f.Y(0), y2: f.Y(0), stroke: 'var(--metal)', 'stroke-width': 2}, g);
    const floorLabel = Svg.text(g, f.X(-19.6), f.Y(0) + 13, '', {'font-size': 11, fill: 'var(--muted)'});
    Svg.el('rect', {x: f.X(-18), y: f.Y(18), width: f.S(18), height: f.S(18), fill: 'none', stroke: 'var(--good)', 'stroke-width': 2, 'stroke-dasharray': '8 5'}, g);
    Svg.text(g, f.X(-17.6), f.Y(18) + 14, '18 in starting size', {'font-size': 11, fill: 'var(--good)'});
    Svg.el('rect', {x: f.X(-18), y: f.Y(17), width: f.S(24), height: f.S(17), fill: 'none', stroke: 'var(--warn)', 'stroke-width': 2, 'stroke-dasharray': '3 4'}, g);
    Svg.text(g, f.X(6) - 4, f.Y(17) + 14, '24 in when out', {'font-size': 11, fill: 'var(--warn)', 'text-anchor': 'end'});
    // chassis, wheels
    Svg.el('rect', {x: f.X(-17.5), y: f.Y(5), width: f.S(17.3), height: f.S(3.2), rx: 4, fill: 'var(--part)', opacity: 0.55}, g);
    for (const x of [-14.5, -3.5]) Svg.el('circle', {cx: f.X(x), cy: f.Y(2), r: f.S(2), fill: 'var(--wheel)'}, g);
    // FLOWER with its retrieval opening at the bottom
    const flower = Svg.el('path', {fill: 'var(--good)', opacity: 0.25, stroke: 'var(--good)'}, g);
    Svg.text(g, f.X(5), f.Y(8.5), 'FLOWER', {'font-size': 12, 'font-weight': 700, fill: 'var(--good)', 'text-anchor': 'middle'});
    Svg.el('line', {x1: f.X(9.6), x2: f.X(9.6), y1: f.Y(0), y2: f.Y(3.55), stroke: 'var(--good)', 'stroke-width': 1.5}, g);
    Svg.text(g, f.X(9.4), f.Y(2.6), 'opening', {'font-size': 10, fill: 'var(--good)', 'text-anchor': 'end'});
    Svg.text(g, f.X(9.4), f.Y(2.6) + 12, '3.55 in', {'font-size': 10, fill: 'var(--good)', 'text-anchor': 'end'});
    const ramp = Svg.el('line', {stroke: 'var(--accent)', 'stroke-width': f.S(0.4), 'stroke-linecap': 'round'}, g);
    const pin = Svg.el('circle', {r: 4, fill: 'var(--ink)'}, g);
    // the strings run from a spool on the frame down to the ramp's middle
    const spoolX = -1.2, spoolY = 12.5;
    const string = Svg.el('line', {stroke: 'var(--ink)', 'stroke-width': 1.2, 'stroke-dasharray': '2 2'}, g);
    Svg.el('circle', {cx: f.X(spoolX), cy: f.Y(spoolY), r: f.S(0.5), fill: 'var(--metal)'}, g);
    const body = servoBody(g, 30, 20);
    body.at(f.X(spoolX) - 22, f.Y(spoolY));
    return {
      update(st, geo, onBox, servoSt) {
        const face = geo.flowerFaceIn, top = geo.flowerOpeningIn;
        flower.setAttribute('d', `M${f.X(face)},${f.Y(0)} L${f.X(face)},${f.Y(0.15)} L${f.X(face + 0.3)},${f.Y(0.15)} L${f.X(face + 0.3)},${f.Y(0)} Z ` +
          `M${f.X(face)},${f.Y(top)} L${f.X(10)},${f.Y(top)} L${f.X(10)},${f.Y(12)} L${f.X(face)},${f.Y(12)} Z`);
        for (const e of [box, boxLabel, ground]) e.setAttribute('visibility', onBox ? 'visible' : 'hidden');
        floor.setAttribute('stroke-dasharray', onBox ? '4 4' : '');
        floorLabel.textContent = onBox ? 'field floor level (robot is up on a box)' : 'floor';
        ramp.setAttribute('x1', f.X(st.pivotX)); ramp.setAttribute('y1', f.Y(st.pivotY));
        ramp.setAttribute('x2', f.X(st.tipX)); ramp.setAttribute('y2', f.Y(st.tipY));
        pin.setAttribute('cx', f.X(st.pivotX)); pin.setAttribute('cy', f.Y(st.pivotY));
        const mx = (st.pivotX + st.tipX) / 2, my = (st.pivotY + st.tipY) / 2;
        string.setAttribute('x1', f.X(spoolX)); string.setAttribute('y1', f.Y(spoolY));
        string.setAttribute('x2', f.X(mx)); string.setAttribute('y2', f.Y(my));
        string.setAttribute('stroke-dasharray', st.slack > 0.02 ? '1 5' : '2 2');
        body.update(servoSt);
      },
    };
  }

  // stalled servo noise: a 50 Hz hum (the pulse frame) over a gear whine, louder the harder it pushes
  const buzz = (() => {
    let ctx = null, out = null;
    function start() {
      if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
      out = ctx.createGain();
      out.gain.value = 0;
      out.connect(ctx.destination);
      const am = ctx.createGain();
      am.gain.value = 0.5;
      am.connect(out);
      const tones = [['square', 150, 0.6], ['sawtooth', 410, 0.35], ['triangle', 1230, 0.2]];
      for (const [type, hz, level] of tones) {
        const o = ctx.createOscillator(), gg = ctx.createGain();
        o.type = type; o.frequency.value = hz; gg.gain.value = level;
        o.connect(gg); gg.connect(am); o.start();
      }
      const lfo = ctx.createOscillator(), depth = ctx.createGain();
      lfo.frequency.value = 50; depth.gain.value = 0.5;
      lfo.connect(depth); depth.connect(am.gain); lfo.start();
    }
    function level(v) {
      if (ctx && out) out.gain.setTargetAtTime(v, ctx.currentTime, 0.03);
    }
    return {start, level};
  })();
  document.addEventListener('pointerdown', () => { if (state.sound) buzz.start(); });

  App.onState((s) => {
    const sv = s.world && s.world.servos;
    if (!sv) return;
    let loud = 0;
    for (const id of ['ramp.left', 'ramp.right', 'gate.front', 'gate.back', 'gate.back2']) {
      if (sv[id] && sv[id].buzz > 0.12) loud = Math.max(loud, sv[id].buzz);
    }
    buzz.level(state.sound && loud > 0 ? 0.03 + 0.12 * loud : 0);
  });

  let expected = null, expectedAt = 0;
  function coachExpected() {
    if (!state.coach || Date.now() - expectedAt < 2000) return expected;
    expectedAt = Date.now();
    App.api('expected').then((e) => { expected = e; }).catch(() => {});
    return expected;
  }

  function band(key) {
    const e = expected && expected[key];
    if (!e || e.value == null) return key + ' ?';
    return key === 'gateTravelS' || key === 'rampOutS' || key === 'rampInS'
      ? `${key} ${(e.value * 240).toFixed(1)} ± ${(e.tolerance * 240).toFixed(1)} frames`
      : `${key} ${(e.value - e.tolerance).toFixed(3)}–${(e.value + e.tolerance).toFixed(3)}`;
  }

  return {state, frame, gateScene, rampScene, buzz, coachExpected, band};
})();

Bench.register({
  id: 'servos', title: 'Servos', order: 50,
  mount(el) {
    const D = ServoDraw;
    el.innerHTML = `<h3>Ramp and gates (side views)</h3>
      <p class="hint">Program 4 moves one gate servo at a time. Watch where it goes and listen: a servo told to push past its
        stop, or against the other back-gate servo, buzzes and gets hot. For a gate, put an element against it: closed must
        hold it clear of the flywheel, open must let it roll through without touching. The ramp only knows time: watch it
        go out and come up, and whether it creeps down when it should stay up.</p>
      <div class="controls">
        <label><input type="checkbox" id="srvSound"> Sound (buzzing)</label>
        <label><input type="checkbox" id="srvCoach"> <span class="srv-coach-on">Coach</span>: show the hidden numbers</label>
      </div>
      <div class="srv-grid">
        <div class="srv-card wide" data-id="ramp"><h4>Ramp (front of the robot is right) <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card" data-id="gate.front"><h4>Front gate <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card" data-id="gate.back"><h4>Back gate (2 servos) <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
      </div>`;
    const sound = el.querySelector('#srvSound'), coach = el.querySelector('#srvCoach');
    sound.checked = D.state.sound;
    coach.checked = D.state.coach;
    sound.onchange = () => {
      D.state.sound = sound.checked;
      try { localStorage.setItem('servoSound', sound.checked ? '1' : '0'); } catch (e) { /* storage off */ }
      if (sound.checked) D.buzz.start();
    };
    coach.onchange = () => {
      D.state.coach = coach.checked;
      try { localStorage.setItem('servoCoach', coach.checked ? '1' : '0'); } catch (e) { /* storage off */ }
    };
    this.cards = {};
    for (const card of el.querySelectorAll('.srv-card')) {
      const id = card.dataset.id, svg = card.querySelector('svg'), controls = card.querySelector('.controls');
      const c = {card, svg, feel: card.querySelector('.feel'), say: card.querySelector('.say'), coach: card.querySelector('.coach')};
      const mech = Svg.el('g', {}, svg);
      c.mech = mech;
      if (id === 'ramp') {
        c.f = D.frame(svg, -20, -2.6, 10, 19.5, 21);
        c.scene = D.rampScene(mech, c.f);
        controls.innerHTML = `<button class="btn secondary" data-a="stowRamp">Fold the ramp up by hand</button>
          <button class="btn secondary" data-a="remountRampRight" hidden>Turn the right ramp servo round on its mount</button>`;
        controls.querySelectorAll('[data-a]').forEach((b) => { b.onclick = () => App.hand('servos', b.dataset.a, {}); });
        c.remount = controls.querySelector('[data-a="remountRampRight"]');
      } else {
        const side = id === 'gate.front' ? 0 : 1;
        c.f = D.frame(svg, -8, -1, 2.8, 7.2, 30);
        c.scene = D.gateScene(mech, c.f, 'srvGateScene' + side);
        // magnifier on the element / flywheel gap
        const defs = Svg.el('defs', {}, svg);
        const clip = Svg.el('clipPath', {id: 'srvGateClip' + side}, defs);
        c.lensR = 52;
        c.lensX = c.f.X(-6.1); c.lensY = c.f.Y(4.9);
        Svg.el('circle', {cx: c.lensX, cy: c.lensY, r: c.lensR}, clip);
        c.lens = Svg.el('g', {'clip-path': `url(#srvGateClip${side})`}, svg);
        Svg.el('circle', {cx: c.lensX, cy: c.lensY, r: c.lensR, fill: 'var(--panel)'}, c.lens);
        c.lensUse = Svg.el('use', {href: '#srvGateScene' + side}, c.lens);
        c.lensRing = Svg.el('circle', {cx: c.lensX, cy: c.lensY, r: c.lensR, fill: 'none', stroke: 'var(--muted)', 'stroke-width': 2}, svg);
        c.lensText = Svg.text(svg, c.lensX, c.lensY + c.lensR + 14, 'close-up (6×)', {'text-anchor': 'middle', 'font-size': 11, fill: 'var(--muted)'});
        controls.innerHTML = `<button class="btn secondary" data-a="placeElement">Hold an element against the gate</button>
          <button class="btn secondary" data-a="removeElement">Take it out</button>`;
        controls.querySelectorAll('[data-a]').forEach((b) => { b.onclick = () => App.hand('servos', b.dataset.a, {gate: side}); });
      }
      const rep = document.createElement('button');
      rep.className = 'btn danger';
      rep.textContent = 'Replace the burnt servo';
      rep.hidden = true;
      // the ramp and the back gate have two servos: replace whichever is burnt
      rep.onclick = () => {
        const ids = id === 'ramp' ? ['ramp.left', 'ramp.right'] : id === 'gate.back' ? ['gate.back', 'gate.back2'] : [id];
        const sv = (App.last && App.last.world && App.last.world.servos) || {};
        for (const k of ids) if (sv[k] && sv[k].burnedOut) App.hand('servos', 'replaceServo', {id: k});
      };
      controls.appendChild(rep);
      c.replace = rep;
      this.cards[id] = c;
    }
  },
  update(el, s) {
    const sv = s.world && s.world.servos;
    if (!sv) return;
    const D = ServoDraw, geo = sv.geometry, coach = D.state.coach;
    D.coachExpected();
    const n = (v, d = 1) => (v == null ? '?' : Number(v).toFixed(d));
    for (const id in this.cards) {
      const c = this.cards[id];
      // the servos behind this card: two for the ramp and the back gate
      const parts = id === 'ramp' ? [sv['ramp.left'], sv['ramp.right']] : id === 'gate.back' ? [sv['gate.back'], sv['gate.back2']] : [sv[id]];
      if (parts.some((p) => !p)) continue;
      const st = id === 'ramp' ? sv.ramp : parts[0];
      const loud = Math.max(...parts.map((p) => p.buzz));
      const hot = parts.reduce((a, p) => (p.heat > a.heat ? p : a));
      c.mech.classList.toggle('srv-shake', loud > 0.12);
      c.feel.textContent = 'touch: ' + hot.feel;
      c.replace.hidden = !parts.some((p) => p.burnedOut);
      const common = parts.map((p, k) => (parts.length > 1 ? `servo ${k + 1}: ` : '') + `${n(p.amps, 2)} A, push ${Math.round(p.buzz * 100)}%, heat ${Math.round(p.heat * 100)}%`).join(' · ');
      let say = parts.some((p) => p.burnedOut) ? 'It went quiet and smells burnt: a servo here is dead. Replace it, then set its positions again.'
        : loud > 0.12 ? 'BZZZZ: it is pushing against something and not moving.' : '';
      let coachText = '';
      if (id === 'ramp') {
        c.scene.update(st, geo, s.world.onBox, hot);
        c.remount.hidden = !st.fighting;
        if (st.fighting) say = 'The two ramp servos turn against each other: one is mounted the same way as the other, not mirrored.';
        else if (!say && !parts.some((p) => p.powered)) say = st.share > 0.01 ? 'No signal: the servos are limp.' : 'No signal: the servos are limp. The ramp is folded up.';
        else if (!say && st.slack > 0.02) say = 'It ran past fully out: the strings hang slack. They wind in first before the ramp lifts.';
        coachText = `out ${Math.round(st.share * 100)}% (${n(st.outDeg)}° from upright) · tip ${n(st.tipX, 2)} in forward, ${n(st.tipY, 2)} in up · inside 18 in: ${st.inside ? 'yes' : 'no'} · at the FLOWER: ${st.atFlower ? 'yes' : 'no'} · slack ${n(st.slack, 2)} · drooped ${n(st.droopSeconds)} s while driving · power ${n(parts[0].power, 2)} / ${n(parts[1].power, 2)} · ${common} · good: ${D.band('rampOutS')}, ${D.band('rampInS')}`;
      } else {
        const side = id === 'gate.front' ? 'front' : 'back';
        if (!say && !parts.some((p) => p.powered)) say = 'No signal: the servo is limp. A program has to send it a position first (program 4: press square on that step).';
        c.scene.update(st, geo, st.out, st.element ? st.elementX : null);
        // close-up on the nearest point between the element (or where it would sit) and the wheel
        const ex = st.element ? st.elementX : -2, ey = geo.elementR, wx = 0, wy = geo.wheelY;
        const d = Math.hypot(wx - ex, wy - ey), gap = Math.max(0.01, d - geo.elementR - geo.wheelR);
        const mid = geo.elementR + gap / 2, px = ex + (wx - ex) / d * mid, py = ey + (wy - ey) / d * mid;
        const k = Math.round(Math.max(2, Math.min(6, 1.4 / gap)));
        c.lensText.textContent = `close-up (${k}×)`;
        c.lensUse.setAttribute('transform', `translate(${c.lensX},${c.lensY}) scale(${k}) translate(${-c.f.X(px)},${-c.f.Y(py)})`);
        const vis = st.element ? 'visible' : 'hidden';
        c.lens.setAttribute('visibility', vis); c.lensRing.setAttribute('visibility', vis); c.lensText.setAttribute('visibility', vis);
        if (!say) say = st.result || (st.element ? (st.passing ? 'The element is going under the gate...' : 'The element rests against the gate.') : '');
        const goods = side === 'front' ? `${D.band('frontGateClosed')}, ${D.band('frontGateOpen')}`
          : `servo 1 ${D.band('backGateClosed')}, ${D.band('backGateOpen')} · servo 2 ${D.band('backGate2Closed')}, ${D.band('backGate2Open')}`;
        coachText = `arm ${n(st.out)}° (stops ${n(st.outStopLow)}–${n(st.outStopHigh)}°) · element to wheel ${n(st.clearanceIn, 2)} in · gap under arm vs element ${n(st.gapIn, 2)} in · open ${n(st.open, 2)} · ${common} · good: ${goods}` +
          (st.lastOpenS != null ? ` · last opening ${(st.lastOpenS * 240).toFixed(1)} frames` : '');
      }
      c.say.textContent = say;
      c.coach.textContent = coach ? coachText : '';
      c.coach.hidden = !coach;
    }
  },
});
