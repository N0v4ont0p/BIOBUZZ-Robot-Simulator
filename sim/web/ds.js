// Driver Station: OpMode lists, INIT / START / STOP, telemetry, gamepads (browser Gamepad API, PS layout).
const DS = (() => {
  const $ = (id) => document.getElementById(id);
  const STOP = '$Stop$Robot$';
  let ds = null;
  let listOpen = null;

  // ---- telemetry: the same small HTML subset the real Driver Station shows
  const ALLOWED = /^<\/?(b|i|u|big|small|tt|br|h[1-6]|sup|sub|strong|em)\s*\/?>$|^<font(\s+color\s*=\s*('[^']*'|"[^"]*"|#?\w+))?\s*>$|^<\/font>$/i;
  function html(line) {
    return line.replace(/<[^>]*>|[^<]+/g, (t) => {
      if (t.startsWith('<')) return ALLOWED.test(t) ? t : App.esc(t);
      return t.replace(/&(?!(amp|lt|gt|quot|#\d+);)/g, '&amp;');
    });
  }

  function render(s, local) {
    ds = s.ds;
    const robot = ds.robot || {};
    $('dsRc').textContent = 'RC: ' + (robot.status || 'not running');
    $('dsRc').className = 'pill ' + (robot.status && robot.status.startsWith('running') ? 'good' : 'bad');
    $('dsBattery').textContent = ds.battery == null ? '--' : ds.battery.toFixed(2) + ' V';

    const err = ds.error || (s.sim.buildError ? 'Build failed:\n' + s.sim.buildError : '');
    $('dsError').hidden = !err;
    $('dsError').textContent = err;
    const warns = [ds.warning, ...(robot.warnings || [])].filter(Boolean);
    $('dsWarning').hidden = warns.length === 0;
    $('dsWarning').textContent = warns.join('\n');

    const tele = $('telemetry');
    if (tele.dataset.seq !== String(ds.telemetrySeq) || tele.dataset.fmt !== ds.displayFormat) {
      tele.dataset.seq = ds.telemetrySeq;
      tele.dataset.fmt = ds.displayFormat;
      tele.className = 'telemetry' + (ds.displayFormat === 'MONOSPACE' ? ' mono' : '');
      tele.innerHTML = ds.telemetry.map(l => '<div class="line">' + (ds.displayFormat === 'HTML' ? html(l) : App.esc(l)) + '</div>').join('');
    }

    const sel = ds.selected;
    $('dsSelected').textContent = ds.state !== 'STOPPED' ? ds.active : (sel || 'No OpMode selected');
    const main = $('mainBtn'), stop = $('stopBtn');
    if (ds.state === 'STOPPED') {
      main.textContent = 'INIT'; main.className = 'main-btn'; main.disabled = !sel; stop.hidden = true;
    } else if (ds.state === 'INIT') {
      main.innerHTML = '&#9654;'; main.className = 'main-btn start'; main.disabled = false; stop.hidden = false;
    } else {
      main.innerHTML = '&#9632;'; main.className = 'main-btn stop'; main.disabled = false; stop.hidden = true;
    }
    $('autoTimer').checked = ds.autoTimer;
    if (ds.state === 'RUNNING') {
      const t = ds.runningFlavor === 'AUTONOMOUS' && ds.autoTimer ? Math.max(0, 30 - ds.runSeconds) : ds.runSeconds;
      $('dsClock').textContent = Math.floor(t / 60) + ':' + String(Math.floor(t % 60)).padStart(2, '0');
    } else {
      $('dsClock').textContent = '';
    }
    if (!local) for (const e of ds.events) handleEvent(e);
    if (listOpen) buildList(listOpen);
  }

  function handleEvent(e) {
    if (e.type === 'toast' || e.type === 'dialog') App.toast(typeof e.data === 'string' ? e.data : JSON.stringify(e.data));
    else if (e.type === 'speech' && window.speechSynthesis) speechSynthesis.speak(new SpeechSynthesisUtterance(String(e.data)));
    else if (e.type === 'rumble') Pads.rumble(e.data);
    else if (e.type === 'led') Pads.led(e.data);
  }

  function buildList(flavor) {
    const box = $('opList');
    const ops = ((ds && ds.robot && ds.robot.opModes) || []).filter(o => flavor === 'AUTONOMOUS' ? o.flavor === 'AUTONOMOUS' : o.flavor === 'TELEOP');
    const groups = {};
    for (const o of ops) (groups[o.group] = groups[o.group] || []).push(o);
    const names = Object.keys(groups).sort((a, b) => a === '' ? -1 : b === '' ? 1 : a.localeCompare(b));
    const sig = flavor + JSON.stringify(ops.map(o => o.name)) + (ds && ds.selected);
    if (box.dataset.sig === sig) return;
    box.dataset.sig = sig;
    box.innerHTML = names.map(g => (g ? `<div class="group">${App.esc(g)}</div>` : '') +
      groups[g].sort((a, b) => a.name.localeCompare(b.name, undefined, {numeric: true}))
        .map(o => `<button data-name="${App.esc(o.name)}" class="${ds.selected === o.name ? 'sel' : ''}">${App.esc(o.name)}</button>`).join('')).join('')
      || '<div class="group">No OpModes</div>';
  }

  function openList(flavor) {
    if (ds && ds.state !== 'STOPPED') { App.toast('Stop the current OpMode first'); return; }
    if (listOpen === flavor) { closeList(); return; }
    listOpen = flavor;
    $('opList').dataset.sig = '';
    buildList(flavor);
    $('opList').hidden = false;
  }

  function closeList() { listOpen = null; $('opList').hidden = true; }

  function init() {
    $('pickAuto').onclick = () => openList('AUTONOMOUS');
    $('pickTele').onclick = () => openList('TELEOP');
    $('opList').onclick = async (ev) => {
      const b = ev.target.closest('button[data-name]');
      if (!b) return;
      await App.api('ds/select', {name: b.dataset.name});
      closeList();
    };
    $('mainBtn').onclick = () => {
      if (!ds) return;
      // show the next state at once; the next frame from the robot confirms it
      if (ds.state === 'STOPPED') { if (!ds.selected) return; App.api('ds/init', {}); ds.state = 'INIT'; ds.active = ds.selected; }
      else if (ds.state === 'INIT') { App.api('ds/start', {}); ds.state = 'RUNNING'; ds.runSeconds = 0; }
      else { App.api('ds/stop', {}); ds.state = 'STOPPED'; }
      render({ds, sim: App.last.sim}, true);
    };
    $('stopBtn').onclick = () => { App.api('ds/stop', {}); if (ds) { ds.state = 'STOPPED'; render({ds, sim: App.last.sim}, true); } };
    $('autoTimer').onchange = (e) => App.api('ds/autoTimer', {on: e.target.checked});
    $('dsMenuBtn').onclick = () => { $('dsMenu').hidden = !$('dsMenu').hidden; };
    $('dsMenu').onclick = async (ev) => {
      const act = ev.target.dataset.act;
      $('dsMenu').hidden = true;
      if (act === 'config') Config.open();
      else if (act === 'restart') { App.toast('Restarting Robot...'); App.api('robot/restart', {}).catch(e => alert(e.message)); }
      else if (act === 'keyboard') Pads.toggleKeyboard();
      else if (act === 'help') $('padHelp').hidden = !$('padHelp').hidden;
    };
    document.addEventListener('click', (ev) => {
      if (!ev.target.closest('#opList, .pick')) closeList();
      if (!ev.target.closest('#dsMenu, #dsMenuBtn')) $('dsMenu').hidden = true;
    });
    App.onState(render);
  }

  window.addEventListener('DOMContentLoaded', init);
  return {STOP};
})();

// Gamepads, the way the real Driver Station handles them: a controller only drives the robot after it is
// assigned (options + cross = gamepad 1, options + circle = gamepad 2). Every change goes to the robot at once.
const Pads = (() => {
  const users = {1: null, 2: null};   // browser gamepad index, or 'kb' (keyboard and the on-screen controller)
  const KB = {}, TAPPED = {};
  const PB = {}, PT = {}, STICK = {left: [0, 0], right: [0, 0]};   // on-screen controller: held, tapped, sticks   // TAPPED: pressed since the last send, so a quick tap is never lost
  let keyboard = false;
  const KEYMAP = {x: 'cross', o: 'circle', z: 'square', t: 'triangle', '1': 'left_bumper', '2': 'right_bumper',
    '5': 'share', '6': 'options', '7': 'ps', '8': 'touchpad', arrowup: 'dpad_up', arrowdown: 'dpad_down',
    arrowleft: 'dpad_left', arrowright: 'dpad_right'};
  const BTN = ['cross', 'circle', 'square', 'triangle', 'left_bumper', 'right_bumper', null, null, 'share', 'options',
    'left_stick_button', 'right_stick_button', 'dpad_up', 'dpad_down', 'dpad_left', 'dpad_right', 'ps', 'touchpad'];

  function fromGamepad(g) {
    const p = {};
    BTN.forEach((n, i) => { if (n && g.buttons[i]) p[n] = g.buttons[i].pressed; });
    const dz = (v) => Math.abs(v) < 0.06 ? 0 : v;
    p.left_stick_x = dz(g.axes[0] || 0); p.left_stick_y = dz(g.axes[1] || 0);
    p.right_stick_x = dz(g.axes[2] || 0); p.right_stick_y = dz(g.axes[3] || 0);
    p.left_trigger = g.buttons[6] ? g.buttons[6].value : 0;
    p.right_trigger = g.buttons[7] ? g.buttons[7].value : 0;
    return p;
  }

  function fromKeyboard() {
    const p = {}, down = (k) => !!(KB[k] || TAPPED[k]);
    for (const [k, n] of Object.entries(KEYMAP)) p[n] = down(k);
    p.left_stick_x = (down('d') ? 1 : 0) - (down('a') ? 1 : 0);
    p.left_stick_y = (down('s') ? 1 : 0) - (down('w') ? 1 : 0);
    p.right_stick_x = (down('l') ? 1 : 0) - (down('j') ? 1 : 0);
    p.right_stick_y = (down('k') ? 1 : 0) - (down('i') ? 1 : 0);
    p.left_trigger = down('3') ? 1 : 0;
    p.right_trigger = down('4') ? 1 : 0;
    for (const k in TAPPED) delete TAPPED[k];
    for (const n of new Set([...Object.keys(PB), ...Object.keys(PT)])) {
      if (!PB[n] && !PT[n]) continue;
      if (n.endsWith('_trigger')) p[n] = 1; else p[n] = true;
    }
    for (const k in PT) delete PT[k];
    for (const side of ['left', 'right']) {
      const [x, y] = STICK[side];
      if (x || y) { p[side + '_stick_x'] = x; p[side + '_stick_y'] = y; }
    }
    return p;
  }

  function anyInput(p) {
    return Object.entries(p).some(([k, v]) => v === true || (typeof v === 'number' && Math.abs(v) > 0.2));
  }

  function tick() {
    const pads = navigator.getGamepads ? Array.from(navigator.getGamepads()).filter(Boolean) : [];
    for (const g of pads) {
      const b = g.buttons;
      if (b[9] && b[9].pressed && b[0] && b[0].pressed) assign(1, g.index);
      if (b[9] && b[9].pressed && b[1] && b[1].pressed) assign(2, g.index);
    }
    const out = {user1: null, user2: null};
    for (const u of [1, 2]) {
      const who = users[u];
      let p = null;
      if (who === 'kb') p = fromKeyboard();
      else if (who != null) { const g = pads.find(x => x.index === who); if (g) p = fromGamepad(g); }
      out['user' + u] = p;
      const badge = document.getElementById('pad' + u);
      badge.classList.toggle('on', p !== null);
      badge.classList.toggle('active', p !== null && anyInput(p));
    }
    // like the real Driver Station: send every change at once, and the state again now and then
    const json = JSON.stringify(out), now = Date.now();
    if (json !== tick.last || now - tick.sent > 100) {
      tick.last = json;
      tick.sent = now;
      App.api('gamepad', out).catch(() => {});
    }
  }

  function assign(user, index) {
    const other = user === 1 ? 2 : 1;
    if (users[user] === index) return;
    if (users[other] === index) users[other] = null;
    users[user] = index;
    App.toast('Gamepad ' + user + ' assigned');
  }

  // on-screen controller: using it makes it (with the keyboard) the chosen gamepad
  function claimPanel() {
    const u = Number(document.getElementById('ppUser').value), other = u === 1 ? 2 : 1;
    if (users[u] === 'kb') return;
    if (users[other] === 'kb') users[other] = null;
    users[u] = 'kb';
    keyboard = u === 1;
    App.toast('On-screen controller is gamepad ' + u);
  }

  function initPanel() {
    const panel = document.getElementById('padPanel');
    document.getElementById('ppUser').onchange = () => { for (const u of [1, 2]) if (users[u] === 'kb') users[u] = null; claimPanel(); };
    panel.querySelectorAll('button[data-b]').forEach(b => {
      const n = b.dataset.b;
      const up = () => { if (b.classList.contains('latched')) return; PB[n] = false; b.classList.remove('down'); };
      b.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        claimPanel();
        if (e.shiftKey || b.classList.contains('latched')) {
          const on = !b.classList.contains('latched');
          b.classList.toggle('latched', on);
          b.classList.toggle('down', on);
          PB[n] = on;
          if (on) PT[n] = true;
          return;
        }
        b.setPointerCapture(e.pointerId);
        PB[n] = true; PT[n] = true;
        b.classList.add('down');
      });
      b.addEventListener('pointerup', up);
      b.addEventListener('pointercancel', up);
    });
    panel.querySelectorAll('.pp-stick').forEach(el => {
      const side = el.dataset.s, knob = el.querySelector('.knob');
      const set = (x, y) => {
        STICK[side] = [Math.round(x * 100) / 100, Math.round(y * 100) / 100];
        knob.style.transform = `translate(${x * 30}px, ${y * 30}px)`;
      };
      const at = (e) => {
        const r = el.getBoundingClientRect();
        let x = (e.clientX - r.left - r.width / 2) / (r.width / 2 - 18), y = (e.clientY - r.top - r.height / 2) / (r.height / 2 - 18);
        const m = Math.hypot(x, y);
        if (m > 1) { x /= m; y /= m; }
        set(x, y);   // screen down = +y, same as a real stick
      };
      el.addEventListener('pointerdown', (e) => { e.preventDefault(); claimPanel(); el.setPointerCapture(e.pointerId); el.classList.add('on'); at(e); });
      el.addEventListener('pointermove', (e) => { if (el.hasPointerCapture(e.pointerId)) at(e); });
      const release = () => { el.classList.remove('on'); set(0, 0); };
      el.addEventListener('pointerup', release);
      el.addEventListener('pointercancel', release);
    });
  }

  function toggleKeyboard() {
    keyboard = !keyboard;
    users[1] = keyboard ? 'kb' : null;
    App.toast(keyboard ? 'Keyboard is gamepad 1 (see Gamepad help)' : 'Keyboard released');
  }

  function rumble(d) {
    const who = users[d.user];
    if (who == null || who === 'kb') return;
    const g = (navigator.getGamepads() || [])[who];
    if (!g || !g.vibrationActuator) return;
    let delay = 0;
    for (const [large, small, ms] of d.steps) {
      setTimeout(() => g.vibrationActuator.playEffect('dual-rumble', {duration: ms, strongMagnitude: large, weakMagnitude: small}).catch(() => {}), delay);
      delay += ms;
    }
  }

  function led(d) {
    const badge = document.getElementById('pad' + d.user);
    const s = d.steps[0];
    if (s) badge.style.outline = `3px solid rgb(${s[0]},${s[1]},${s[2]})`;
  }

  function keyName(e) { return e.key.length === 1 ? e.key.toLowerCase() : e.key.toLowerCase(); }

  window.addEventListener('keydown', (e) => {
    if (!keyboard || e.target.closest('input, select, textarea')) return;
    KB[keyName(e)] = true;
    TAPPED[keyName(e)] = true;
    if (e.key.startsWith('Arrow')) e.preventDefault();
  });
  window.addEventListener('keyup', (e) => { KB[keyName(e)] = false; });
  window.addEventListener('blur', () => { for (const k in KB) KB[k] = false; });
  window.addEventListener('gamepadconnected', (e) => App.toast('Controller found: ' + e.gamepad.id.slice(0, 40) + '. Press options + cross for gamepad 1.'));
  tick.sent = 0;
  setInterval(tick, 20);
  window.addEventListener('DOMContentLoaded', initPanel);
  return {rumble, led, toggleKeyboard};
})();
