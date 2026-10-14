// Shared plumbing: the server event stream, API calls, and a tiny publish/subscribe for every panel.
const App = (() => {
  const listeners = [];
  let last = null;
  let since = 0;

  async function api(path, body) {
    const r = await fetch('/api/' + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: {'Content-Type': 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const j = await r.json().catch(() => null);
    if (!r.ok) throw new Error((j && j.error) || ('HTTP ' + r.status));
    return j;
  }

  function connect() {
    const es = new EventSource('/api/stream?since=' + since);
    es.onmessage = (m) => {
      const s = JSON.parse(m.data);
      for (const e of s.ds.events) since = Math.max(since, e.seq);
      last = s;
      for (const f of listeners) {
        try { f(s); } catch (err) { console.error(err); }
      }
    };
    es.onerror = () => { es.close(); setTimeout(connect, 1000); };
  }

  function onState(f) { listeners.push(f); if (last) f(last); }

  function esc(s) {
    return String(s ?? '').replace(/[&<>"]/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
  }

  function hand(part, action, args) { return api('hand', {part, action, args: args || {}}).catch(err => console.warn(err)); }

  function toast(text) {
    const t = document.getElementById('toast');
    t.textContent = text;
    t.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => { t.hidden = true; }, 3500);
  }

  async function initScenarios() {
    const sel = document.getElementById('scenarioSelect');
    const list = await api('scenarios');
    sel.innerHTML = list.map(s => `<option value="${esc(s.file)}" title="${esc(s.description)}">${esc(s.name)}</option>`).join('');
    onState(s => {
      if (s.sim.scenario && document.activeElement !== sel && !initScenarios.picked) sel.value = s.sim.scenario;
      const seed = document.getElementById('seedInput');
      if (document.activeElement !== seed && !seed.value) seed.placeholder = 'seed ' + s.sim.seed;
    });
    sel.addEventListener('change', () => { initScenarios.picked = true; });
    document.getElementById('newRobot').addEventListener('click', async () => {
      const seedText = document.getElementById('seedInput').value;
      if (!confirm('Start "' + sel.options[sel.selectedIndex].text + '" with a fresh robot? Results entered in the sandbox are thrown away.')) return;
      document.getElementById('simStatus').textContent = 'Building a new robot...';
      try {
        await api('scenario', {file: sel.value, seed: seedText === '' ? null : Number(seedText), fresh: true});
        initScenarios.picked = false;
      } catch (e) { alert(e.message); }
    });
  }

  // Test mode (simulator only): a fully tuned robot at one click; ending it restores your saved work, nothing fake left
  function initTestMode() {
    const btn = document.getElementById('testMode'), banner = document.getElementById('testModeBanner');
    let on = false;
    let armed = 0;
    onState(s => {
      const t = s.sim.testMode;
      on = !!t;
      if (Date.now() - armed > 10000) btn.textContent = on ? 'End test mode' : 'Test mode';
      banner.hidden = !on;
      if (on) banner.textContent = 'TEST MODE: ' + (t.state === 'ready' ? 'fully tuned test robot (fake numbers)' : t.state) + '. Your real work is saved.';
    });
    // two clicks instead of a confirm() box: embedded browsers silently answer "no" to confirm()
    btn.addEventListener('click', async () => {
      if (Date.now() - armed > 10000) {
        armed = Date.now();
        btn.textContent = on ? 'Click again to END test mode (your work comes back, test data deleted)'
          : 'Click again to START test mode (your work is saved first)';
        setTimeout(() => { if (Date.now() - armed >= 10000) btn.textContent = on ? 'End test mode' : 'Test mode'; }, 10100);
        return;
      }
      armed = 0;
      btn.disabled = true;
      const status = document.getElementById('simStatus');
      status.textContent = on ? 'Ending test mode...' : 'Starting test mode...';
      try {
        await api(on ? 'testmode/end' : 'testmode/start', {});
      } catch (e) {
        status.textContent = 'Test mode: ' + e.message;
        toast('Test mode: ' + e.message);
      }
      btn.disabled = false;
    });
  }

  onState(s => {
    const log = s.sim.log || [];
    document.getElementById('simStatus').textContent =
      (s.sim.buildError ? 'BUILD FAILED (robot keeps the old build). ' : '') + (log.length ? log[log.length - 1] : '');
  });

  window.addEventListener('DOMContentLoaded', () => { connect(); initScenarios(); initTestMode(); });
  return {api, onState, esc, hand, toast, get last() { return last; }};
})();
