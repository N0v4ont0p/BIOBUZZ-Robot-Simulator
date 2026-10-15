// Hoods, ramp and gates from the side, the way you look at them during program 4: watch a servo move, hear it buzz
// against a stop, see the ramp against the size outlines and the FLOWER, hold an element against a gate. "Coach"
// shows what you can't see on a real robot (angles, gaps, current, heat, the good range of each step).
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

  function hoodScene(parent, f) {
    const g = Svg.el('g', {}, parent);
    const wheel = Svg.el('circle', {cx: f.X(0), cy: f.Y(0), fill: 'var(--part)', 'fill-opacity': 0.35, stroke: 'var(--metal)', 'stroke-width': 2}, g);
    Svg.el('circle', {cx: f.X(0), cy: f.Y(0), r: 4, fill: 'var(--metal)'}, g);
    const lowBlock = Svg.el('rect', {width: 14, height: 14, rx: 2, fill: 'var(--metal)'}, g);
    const highBlock = Svg.el('rect', {width: 14, height: 14, rx: 2, fill: 'var(--metal)'}, g);
    const plate = Svg.el('polyline', {fill: 'none', stroke: 'var(--accent)', 'stroke-width': 7, 'stroke-linecap': 'round'}, g);
    const tab = Svg.el('circle', {r: 6, fill: 'var(--accent)'}, g);
    const shot = Svg.el('line', {stroke: 'var(--muted)', 'stroke-width': 2, 'stroke-dasharray': '6 5'}, g);
    const body = servoBody(g, 30, 20);
    body.at(f.X(-4.4), f.Y(-2.4));
    Svg.text(g, f.X(0), f.Y(-2.6), 'flywheel', {'text-anchor': 'middle', 'font-size': 11, fill: 'var(--muted)'});
    const p = (r, psi) => [f.X(r * Math.cos(rad(psi))), f.Y(r * Math.sin(rad(psi)))];
    const place = (el, r, psi) => { const [x, y] = p(r, psi); el.setAttribute('x', x - 7); el.setAttribute('y', y - 7); };
    return {
      update(st, geo) {
        const rw = geo.wheelR, rh = geo.wheelY, exit = 90 + st.out;
        wheel.setAttribute('r', f.S(rw));
        const pts = [];
        for (let k = 0; k <= 16; k++) pts.push(p(rh, exit + k * 75 / 16).join(','));
        plate.setAttribute('points', pts.join(' '));
        const [tx, ty] = p(rh + 0.35, exit + 75);
        tab.setAttribute('cx', tx); tab.setAttribute('cy', ty);
        place(lowBlock, rh + 0.35, 165 + st.outStopLow - 5);
        place(highBlock, rh + 0.35, 165 + st.outStopHigh + 5);
        const [ex, ey] = p(rh, exit);
        shot.setAttribute('x1', ex); shot.setAttribute('y1', ey);
        shot.setAttribute('x2', ex + f.S(3.2 * Math.cos(rad(st.out)))); shot.setAttribute('y2', ey - f.S(3.2 * Math.sin(rad(st.out))));
        body.update(st);
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
    const body = servoBody(g, 30, 20);
    return {
      update(st, geo, onBox) {
        const face = geo.flowerFaceIn, top = geo.flowerOpeningIn;
        flower.setAttribute('d', `M${f.X(face)},${f.Y(0)} L${f.X(face)},${f.Y(0.15)} L${f.X(face + 0.3)},${f.Y(0.15)} L${f.X(face + 0.3)},${f.Y(0)} Z ` +
          `M${f.X(face)},${f.Y(top)} L${f.X(10)},${f.Y(top)} L${f.X(10)},${f.Y(12)} L${f.X(face)},${f.Y(12)} Z`);
        for (const e of [box, boxLabel, ground]) e.setAttribute('visibility', onBox ? 'visible' : 'hidden');
        floor.setAttribute('stroke-dasharray', onBox ? '4 4' : '');
        floorLabel.textContent = onBox ? 'field floor level (robot is up on a box)' : 'floor';
        ramp.setAttribute('x1', f.X(st.pivotX)); ramp.setAttribute('y1', f.Y(st.pivotY));
        ramp.setAttribute('x2', f.X(st.tipX)); ramp.setAttribute('y2', f.Y(st.tipY));
        pin.setAttribute('cx', f.X(st.pivotX)); pin.setAttribute('cy', f.Y(st.pivotY));
        body.at(f.X(st.pivotX) - 26, f.Y(st.pivotY));
        body.update(st);
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
    for (const id of ['hood.front', 'hood.back', 'ramp', 'gate.front', 'gate.back']) {
      if (sv[id] && sv[id].stalled) loud = Math.max(loud, sv[id].buzz);
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
    return key === 'gateTravelS' || key === 'rampTravelS'
      ? `${key} ${(e.value * 240).toFixed(1)} ± ${(e.tolerance * 240).toFixed(1)} frames`
      : `${key} ${(e.value - e.tolerance).toFixed(3)}–${(e.value + e.tolerance).toFixed(3)}`;
  }

  return {state, frame, gateScene, hoodScene, rampScene, buzz, coachExpected, band};
})();

Bench.register({
  id: 'servos', title: 'Servos', order: 50,
  mount(el) {
    const D = ServoDraw;
    el.innerHTML = `<h3>Hoods, ramp and gates (side views)</h3>
      <p class="hint">Program 4 moves one servo at a time. Watch where it goes and listen: a servo told to push past its stop
        buzzes and gets hot. For a gate, put an element against it: closed must hold it clear of the flywheel, open must
        let it roll through without touching.</p>
      <div class="controls">
        <label><input type="checkbox" id="srvSound"> Sound (buzzing)</label>
        <label><input type="checkbox" id="srvCoach"> <span class="srv-coach-on">Coach</span>: show the hidden numbers</label>
      </div>
      <div class="srv-grid">
        <div class="srv-card" data-id="hood.front"><h4>Front hood <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card" data-id="hood.back"><h4>Back hood <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card wide" data-id="ramp"><h4>Ramp (front of the robot is right) <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card" data-id="gate.front"><h4>Front gate <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
        <div class="srv-card" data-id="gate.back"><h4>Back gate <span class="feel"></span></h4><svg></svg><div class="controls"></div><div class="say"></div><div class="coach"></div></div>
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
      if (id.startsWith('hood')) {
        c.f = D.frame(svg, -6, -3, 6, 6.6, 26);
        c.scene = D.hoodScene(mech, c.f);
      } else if (id === 'ramp') {
        c.f = D.frame(svg, -20, -2.6, 10, 19.5, 21);
        c.scene = D.rampScene(mech, c.f);
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
      rep.onclick = () => App.hand('servos', 'replaceServo', {id});
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
    for (const id in this.cards) {
      const c = this.cards[id], st = sv[id];
      if (!st) continue;
      c.mech.classList.toggle('srv-shake', st.stalled);
      c.feel.textContent = 'touch: ' + st.feel;
      c.replace.hidden = !st.burnedOut;
      let say = st.burnedOut ? 'It went quiet and smells burnt: this servo is dead. Replace it, then set its positions again.'
        : st.stalled ? 'BZZZZ: it is pushing against something and not moving.' : !st.powered ? 'No signal: the servo is limp. A program has to send it a position first (program 4: press square on that step).' : '';
      let coachText = '';
      const n = (v, d = 1) => (v == null ? '?' : Number(v).toFixed(d));
      const common = `horn ${n(st.deg)}° · ${n(st.amps, 2)} A · push ${Math.round(st.buzz * 100)}% · heat ${Math.round(st.heat * 100)}%`;
      if (id.startsWith('hood')) {
        c.scene.update(st, geo);
        const side = id === 'hood.front' ? 'front' : 'back';
        coachText = `launch ${n(st.out)}° (stops ${n(st.outStopLow)}–${n(st.outStopHigh)}°) · ${common} · good: ${D.band(side + 'HoodMin')}, ${D.band(side + 'HoodMax')}`;
      } else if (id === 'ramp') {
        c.scene.update(st, geo, s.world.onBox);
        coachText = `tip ${n(st.tipX, 2)} in forward, ${n(st.tipY, 2)} in up · inside 18 in: ${st.inside ? 'yes' : 'no'} · at the FLOWER: ${st.atFlower ? 'yes' : 'no'} · ${common} · good: ${D.band('rampStowed')}, ${D.band('rampDeployed')}`;
      } else {
        const side = id === 'gate.front' ? 'front' : 'back';
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
        coachText = `arm ${n(st.out)}° (stops ${n(st.outStopLow)}–${n(st.outStopHigh)}°) · element to wheel ${n(st.clearanceIn, 2)} in · gap under arm vs element ${n(st.gapIn, 2)} in · open ${n(st.open, 2)} · ${common} · good: ${D.band(side + 'GateClosed')}, ${D.band(side + 'GateOpen')}` +
          (st.lastOpenS != null ? ` · last opening ${(st.lastOpenS * 240).toFixed(1)} frames` : '');
      }
      c.say.textContent = say;
      c.coach.textContent = coach ? coachText : '';
      c.coach.hidden = !coach;
    }
  },
});
