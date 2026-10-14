// Configure Robot, like the real Driver Station screen: pick a configuration, or make a new one by scanning the
// hubs and typing a name on every port that has something plugged in. Saved files live on the robot
// (/sdcard/FIRST/<name>.xml). Activating one restarts the robot.
const Config = (() => {
  const $ = (id) => document.getElementById(id);
  let types = null;
  let editing = null;

  function show(on) {
    $('dsConfig').hidden = !on;
    $('dsMain').hidden = on;
  }

  async function open() {
    const info = await App.api('config');
    types = info.types;
    show(true);
    const box = $('dsConfig');
    box.innerHTML = `<div class="cfg">
      <h3>Configure Robot</h3>
      <p class="hint">Active configuration: <b>${App.esc(info.active || 'none')}</b>. Activating a configuration restarts the robot.</p>
      <div class="list">${info.names.map(n => `<div class="row">
          <button class="name" data-edit="${App.esc(n)}">${App.esc(n)}</button>${n === info.active ? '<span class="active">active</span>' : ''}
          <span style="flex:1"></span>
          <button class="btn secondary" data-activate="${App.esc(n)}">Activate</button>
          <button class="btn secondary" data-edit="${App.esc(n)}">Edit</button>
          <button class="btn danger" data-delete="${App.esc(n)}">Delete</button>
        </div>`).join('') || '<p class="hint">No configurations on this robot yet.</p>'}</div>
      <div class="actions">
        <button class="btn" id="cfgNew">New (scan hubs)</button>
        <button class="btn secondary" id="cfgDone">Done</button>
      </div></div>`;
    box.querySelector('#cfgDone').onclick = () => show(false);
    box.querySelector('#cfgNew').onclick = async () => edit(await App.api('config/scan', {name: ''}), true);
    box.querySelectorAll('[data-edit]').forEach(b => b.onclick = async () =>
      edit(await App.api('config/get?name=' + encodeURIComponent(b.dataset.edit)), false));
    box.querySelectorAll('[data-activate]').forEach(b => b.onclick = async () => {
      try { await App.api('config/activate', {name: b.dataset.activate}); App.toast('Activated ' + b.dataset.activate + ': robot restarting'); show(false); }
      catch (e) { alert(e.message); }
    });
    box.querySelectorAll('[data-delete]').forEach(b => b.onclick = async () => {
      if (!confirm('Delete configuration ' + b.dataset.delete + '?')) return;
      await App.api('config/delete', {name: b.dataset.delete});
      open();
    });
  }

  function typeSelect(list, value, cls) {
    return `<select class="${cls}"><option value="Nothing">Nothing</option>` +
      list.map(t => `<option value="${App.esc(t.tag)}" ${t.tag === value ? 'selected' : ''}>${App.esc(t.name)}</option>`).join('') + '</select>';
  }

  function find(list, port, bus) {
    return (list || []).find(d => d.port === port && (bus === undefined || d.bus === bus));
  }

  function edit(cfg, isNew) {
    editing = cfg;
    const box = $('dsConfig');
    const mods = cfg.modules.map((m, mi) => {
      const motors = [0, 1, 2, 3].map(p => {
        const d = find(m.motors, p);
        return `<div class="row" data-kind="motors" data-port="${p}"><span class="port">Motor ${p}</span>${typeSelect(types.motor, d && d.type, 't')}<input class="n" value="${App.esc(d ? d.name : '')}" placeholder="name"></div>`;
      }).join('');
      const servos = [0, 1, 2, 3, 4, 5].map(p => {
        const d = find(m.servos, p);
        return `<div class="row" data-kind="servos" data-port="${p}"><span class="port">Servo ${p}</span>${typeSelect(types.servo, d && d.type, 't')}<input class="n" value="${App.esc(d ? d.name : '')}" placeholder="name"></div>`;
      }).join('');
      const i2c = [0, 1, 2, 3].map(bus => {
        const d = find(m.i2c, 0, bus) || (m.i2c || []).find(x => x.bus === bus);
        return `<div class="row" data-kind="i2c" data-port="0" data-bus="${bus}"><span class="port">I2C bus ${bus}</span>${typeSelect(types.i2c, d && d.type, 't')}<input class="n" value="${App.esc(d ? d.name : '')}" placeholder="name"></div>`;
      }).join('');
      const digital = [0, 1, 2, 3, 4, 5, 6, 7].map(p => {
        const d = find(m.digital, p);
        return `<div class="row" data-kind="digital" data-port="${p}"><span class="port">Digital ${p}</span>${typeSelect(types.digital || [], d && d.type, 't')}<input class="n" value="${App.esc(d ? d.name : '')}" placeholder="name"></div>`;
      }).join('');
      return `<fieldset data-mod="${mi}"><legend>${App.esc(m.name)} (address ${m.address})</legend>
        <fieldset><legend>Motors</legend>${motors}</fieldset>
        <fieldset><legend>Servos</legend>${servos}</fieldset>
        <fieldset><legend>I2C buses</legend>${i2c}</fieldset>
        <fieldset><legend>Digital devices</legend>${digital}</fieldset></fieldset>`;
    }).join('');
    box.innerHTML = `<div class="cfg">
      <h3>${isNew ? 'New configuration' : 'Edit ' + App.esc(cfg.name)}</h3>
      <p class="hint">${isNew ? 'Scan found these hubs. ' : ''}For each port with a cable, pick the device type and type its name exactly.
        Not sure what is plugged where? Look at the Workbench "Wiring" tab.</p>
      <div class="row"><span class="port">Name</span><input id="cfgName" value="${App.esc(cfg.name)}" placeholder="configuration name"></div>
      ${mods}
      <div class="actions"><button class="btn" id="cfgSave">Save</button><button class="btn secondary" id="cfgBack">Cancel</button></div></div>`;
    box.querySelector('#cfgBack').onclick = open;
    box.querySelector('#cfgSave').onclick = save;
  }

  async function save() {
    const name = $('cfgName').value.trim();
    if (!name) { alert('Give the configuration a name'); return; }
    const out = {name, modules: editing.modules.map((m, mi) => {
      const fs = document.querySelector(`fieldset[data-mod="${mi}"]`);
      const mod = {name: m.name, address: m.address, motors: [], servos: [], i2c: [], digital: []};
      fs.querySelectorAll('.row[data-kind]').forEach(r => {
        const type = r.querySelector('.t').value, n = r.querySelector('.n').value.trim();
        if (type === 'Nothing' || !n) return;
        const d = {type, name: n, port: Number(r.dataset.port)};
        if (r.dataset.bus !== undefined) d.bus = Number(r.dataset.bus);
        mod[r.dataset.kind].push(d);
      });
      return mod;
    })};
    try {
      await App.api('config/save', out);
      App.toast('Saved ' + name + '. Activate it to use it.');
      open();
    } catch (e) { alert(e.message); }
  }

  return {open};
})();
