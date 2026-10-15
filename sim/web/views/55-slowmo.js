// A phone in 240 fps slow motion, pointed at both gates (program 4, last step). Record, press square on the Driver
// Station, stop, then step through the video frame by frame and count frames from the slower gate's first
// movement until it is fully open.
Bench.register({
  id: 'slowmo', title: 'Slow-mo camera', order: 55,
  mount(el) {
    const D = ServoDraw;
    el.innerHTML = `<h3>Slow-motion camera (240 fps)</h3>
      <p class="hint">Point it at both gates. Press <b>Record</b>, press square on the Driver Station so the gates open,
        then <b>Stop</b>. Step through the video: find the frame where the slower gate first moves and the frame where it
        is fully open. The difference is the frame count program 4 asks for.</p>
      <div class="controls">
        <button class="btn danger" id="smRec">&#9679; Record</button>
        <span id="smStatus" class="hint"></span>
      </div>
      <svg id="smSvg"></svg>
      <div class="controls">
        <input type="range" id="smScrub" min="0" max="0" value="0" style="flex:1;min-width:200px" aria-label="Frame">
      </div>
      <div class="controls">
        <button class="btn secondary" data-step="-10">&#9664;&#9664; 10</button>
        <button class="btn secondary" data-step="-1">&#9664; 1</button>
        <button class="btn secondary" id="smPlay">Play (1/8 speed)</button>
        <button class="btn secondary" data-step="1">1 &#9654;</button>
        <button class="btn secondary" data-step="10">10 &#9654;&#9654;</button>
        <b id="smFrame" style="font-variant-numeric:tabular-nums"></b>
      </div>
      <div class="controls">
        <button class="btn secondary" id="smMarkA">Mark: starts moving</button>
        <button class="btn secondary" id="smMarkB">Mark: fully open</button>
        <span id="smMarks"></span>
      </div>
      <div class="coach" id="smCoach" style="font-size:12px;color:var(--warn)"></div>`;
    const svg = el.querySelector('#smSvg');
    const f = D.frame(svg, -8, -1.4, 2.8 + 11.6, 7.4, 26);
    const shift = (dx) => ({X: (x) => f.X(x + dx), Y: f.Y, S: f.S});
    this.scenes = [D.gateScene(Svg.el('g', {}, svg), shift(0), 'smGate0'), D.gateScene(Svg.el('g', {}, svg), shift(11.6), 'smGate1')];
    Svg.text(svg, f.X(-7.6), f.Y(7) + 4, 'FRONT gate', {'font-weight': 700});
    Svg.text(svg, f.X(4), f.Y(7) + 4, 'BACK gate', {'font-weight': 700});
    this.stamp = Svg.text(svg, f.X(14.2), f.Y(-1.1), '', {'text-anchor': 'end', 'font-family': 'ui-monospace, Menlo, monospace', 'font-size': 13});
    this.rec = Svg.el('circle', {cx: f.X(13.9), cy: f.Y(6.9), r: 7, fill: 'var(--bad)', visibility: 'hidden'}, svg);

    this.clip = null;
    this.i = 0;
    this.marks = [null, null];
    const scrub = el.querySelector('#smScrub');
    const go = (i) => {
      if (!this.clip) return;
      this.i = Math.max(0, Math.min(this.clip.frames.length - 1, i));
      scrub.value = this.i;
      this.draw(el);
    };
    this.go = go;
    scrub.oninput = () => go(Number(scrub.value));
    el.querySelectorAll('[data-step]').forEach((b) => { b.onclick = () => go(this.i + Number(b.dataset.step)); });
    const play = el.querySelector('#smPlay');
    play.onclick = () => {
      if (this.timer) { clearInterval(this.timer); this.timer = null; play.textContent = 'Play (1/8 speed)'; return; }
      play.textContent = 'Pause';
      this.timer = setInterval(() => {
        if (!this.clip || this.i >= this.clip.frames.length - 1) { play.click(); return; }
        go(this.i + 1);
      }, 1000 / 30);
    };
    const markText = () => {
      const [a, b] = this.marks;
      el.querySelector('#smMarks').textContent = (a == null ? '' : `starts: frame ${a}`) + (b == null ? '' : `   open: frame ${b}`) +
        (a != null && b != null ? `   → ${b - a} frames` : '');
    };
    el.querySelector('#smMarkA').onclick = () => { this.marks[0] = this.i; markText(); };
    el.querySelector('#smMarkB').onclick = () => { this.marks[1] = this.i; markText(); };
    this.markText = markText;

    const recBtn = el.querySelector('#smRec');
    recBtn.onclick = async () => {
      const on = !this.recording;
      await App.hand('servos', 'record', {on});
      if (on) this.started();
    };
    this.started = () => {
      this.recStart = Date.now();
      this.clip = null;
      this.marks = [null, null];
      markText();
    };
    this.load = async () => {
      this.clip = await App.api('slowmo');
      scrub.max = Math.max(0, this.clip.frames.length - 1);
      go(0);
    };
    this.load();
  },
  draw(el) {
    const c = this.clip, sv = App.last && App.last.world && App.last.world.servos;
    if (!c || !sv || !c.frames.length) return;
    const fr = c.frames[this.i];
    ['gate.front', 'gate.back'].forEach((id, k) => {
      const ch = c.channels.indexOf(id);
      if (ch >= 0) this.scenes[k].update(sv[id], sv.geometry, fr[ch], null);
    });
    const t = this.i / c.fps;
    this.stamp.textContent = `frame ${String(this.i).padStart(4, '0')}   ${t.toFixed(3)} s`;
    el.querySelector('#smFrame').textContent = `frame ${this.i} of ${c.frames.length - 1}`;
    el.querySelector('#smCoach').textContent = ServoDraw.state.coach
      ? c.channels.map((n, i) => `${n} ${fr[i].toFixed(2)}°`).join(' · ') + ' · ' + ServoDraw.band('gateTravelS') + ' · ' + ServoDraw.band('rampTravelS') : '';
  },
  update(el, s) {
    const sv = s.world && s.world.servos;
    if (!sv) return;
    ServoDraw.coachExpected();
    const was = this.recording;
    this.recording = !!sv.cameraRecording;
    if (this.recording && !was && !this.recStart) this.started();
    if (was && !this.recording) {
      this.recStart = 0;
      this.load();
    }
    const btn = el.querySelector('#smRec');
    btn.innerHTML = this.recording ? '&#9632; Stop' : '&#9679; Record';
    this.rec.setAttribute('visibility', this.recording && Math.floor(Date.now() / 500) % 2 ? 'visible' : 'hidden');
    const status = el.querySelector('#smStatus');
    if (this.recording) {
      const secs = (Date.now() - (this.recStart || Date.now())) / 1000;
      status.textContent = `recording ${secs.toFixed(1)} s (keeps the last 10 s)`;
      ['gate.front', 'gate.back'].forEach((id, k) => this.scenes[k].update(sv[id], sv.geometry, sv[id].out, null));
      this.stamp.textContent = 'REC';
    } else if (this.clip && this.clip.frames.length) {
      status.textContent = `${this.clip.frames.length} frames (${(this.clip.frames.length / this.clip.fps).toFixed(2)} s)`;
      this.draw(el);
    } else {
      status.textContent = 'no video yet';
      ['gate.front', 'gate.back'].forEach((id, k) => this.scenes[k].update(sv[id], sv.geometry, sv[id].out, null));
    }
  },
});
