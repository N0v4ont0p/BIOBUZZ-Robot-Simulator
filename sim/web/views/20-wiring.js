// The hubs and what is plugged into every port: what the students trace by following the cables, so they can
// name each port in Configure Robot. Live port readings help check a program is really driving something.
Bench.register({
  id: 'wiring', title: 'Wiring', order: 20,
  mount(el) {
    el.innerHTML = `<h3>Hubs and cables</h3>
      <p class="hint">Follow the cables: this is what is physically plugged into each port. Configure Robot must give each
        port the name the code uses (setup step D-1).</p><div class="hubs" id="wrHubs"></div>`;
  },
  update(el, s) {
    const w = s.world;
    if (!w) return;
    const part = (id) => PARTS[id] || id;
    el.querySelector('#wrHubs').innerHTML = w.hubs.map(h => `<div class="hub"><h4>${App.esc(h.name)} <small>(${h.volts.toFixed(2)} V)</small></h4>
      <table class="ports"><tr><th>Motor port</th><th>Power cable from</th><th>Encoder cable from</th><th>Mode</th><th>Power</th><th>Ticks</th><th>Ticks/s</th></tr>
      ${h.motors.map(m => `<tr><td>${m.port}</td><td>${m.part ? App.esc(part(m.part)) : '<span class="empty">empty</span>'}</td>
        <td>${m.encoderFrom ? App.esc(part(m.encoderFrom)) : '<span class="empty">none</span>'}</td><td>${m.mode.replace(/_/g, ' ').toLowerCase()}</td>
        <td>${m.enabled ? m.power.toFixed(2) : 'off'}</td><td>${m.position}</td><td>${Math.round(m.velocity)}</td></tr>`).join('')}</table>
      <table class="ports"><tr><th>Servo port</th><th>Cable from</th><th>Signal</th><th>Position</th></tr>
      ${h.servos.map(sv => `<tr><td>${sv.port}</td><td>${sv.part ? App.esc(part(sv.part)) : '<span class="empty">empty</span>'}</td>
        <td>${sv.pwm ? 'on' : 'off'}</td><td>${sv.position == null ? '-' : sv.position.toFixed(3)}</td></tr>`).join('')}</table>
      <table class="ports"><tr><th>I2C bus</th><th>Device</th></tr>
      ${h.i2c.map((d, i) => `<tr><td>${i}</td><td>${d ? App.esc(part(d)) : '<span class="empty">empty</span>'}</td></tr>`).join('')}</table>
      ${(h.digital || []).some(d => d) ? `<table class="ports"><tr><th>Digital port</th><th>Device</th></tr>
      ${h.digital.map((d, i) => d ? `<tr><td>${i}</td><td>${App.esc(part(d))}</td></tr>` : '').join('')}</table>` : ''}</div>`).join('');
  },
});
const PARTS = {
  'drive.leftFront': 'left-front wheel motor', 'drive.leftBack': 'left-back wheel motor',
  'drive.rightFront': 'right-front wheel motor', 'drive.rightBack': 'right-back wheel motor',
  'intake': 'intake motor (roller + NECTAR feed)', 'pollenFeed': 'POLLEN feeder motor (2 wheels)', 'flywheel.front': 'front flywheel motor', 'flywheel.back': 'back flywheel motor',
  'ramp.left': 'left ramp servo (continuous rotation)', 'ramp.right': 'right ramp servo (continuous rotation)', 'gate.front': 'front gate servo',
  'gate.back': 'back gate servo 1', 'gate.back2': 'back gate servo 2', 'yaw.front': 'front turret servo', 'yaw.back': 'back turret servo', 'pinpoint': 'goBILDA Pinpoint (odometry)',
};
