// Workbench: the robot in front of you. Each view (web/views/*.js, loaded in file-name order) registers itself:
//   Bench.register({id, title, mount(el), update(el, state)})
// mount builds the DOM once; update runs on every state frame (20 per second) while the view is visible.
const Bench = (() => {
  const views = [];
  let current = null;
  let mounted = {};

  function register(v) {
    views.push(v);
    views.sort((a, b) => (a.order ?? 50) - (b.order ?? 50));
    renderTabs();
    if (!current) show(views[0].id);
  }

  function renderTabs() {
    const tabs = document.getElementById('benchTabs');
    tabs.innerHTML = views.map(v => `<button data-id="${v.id}" class="${v.id === current ? 'on' : ''}">${App.esc(v.title)}</button>`).join('');
    tabs.querySelectorAll('button').forEach(b => b.onclick = () => show(b.dataset.id));
  }

  function show(id) {
    current = id;
    try { localStorage.setItem('benchView', id); } catch (e) { /* storage off */ }
    renderTabs();
    const bench = document.getElementById('bench');
    for (const el of bench.children) el.hidden = el.dataset.id !== id;
    if (!mounted[id]) {
      const v = views.find(x => x.id === id);
      const el = document.createElement('div');
      el.className = 'view';
      el.dataset.id = id;
      bench.appendChild(el);
      v.mount(el);
      mounted[id] = el;
    }
    if (App.last) update(App.last);
  }

  function update(s) {
    const v = views.find(x => x.id === current);
    if (v && mounted[current] && v.update) v.update(mounted[current], s);
  }

  // drag helper for SVG parts: calls onMove(dx, dy) in screen pixels, onEnd() on release
  function drag(el, onMove, onEnd, onStart) {
    el.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      el.setPointerCapture(e.pointerId);
      let x = e.clientX, y = e.clientY;
      if (onStart) onStart(e);
      const move = (m) => { onMove(m.clientX - x, m.clientY - y, m); x = m.clientX; y = m.clientY; };
      const up = () => { el.removeEventListener('pointermove', move); el.removeEventListener('pointerup', up); el.removeEventListener('pointercancel', up); if (onEnd) onEnd(); };
      el.addEventListener('pointermove', move);
      el.addEventListener('pointerup', up);
      el.addEventListener('pointercancel', up);
    });
  }

  // batches hand moves so dragging sends at most ~30 requests a second
  function handBatcher(part, action, key) {
    let pending = 0, timer = null, extra = {};
    return {
      add(amount, args) {
        pending += amount;
        extra = args || {};
        if (!timer) timer = setTimeout(() => {
          const a = Object.assign({}, extra);
          a[key] = pending;
          pending = 0;
          timer = null;
          App.hand(part, action, a);
        }, 33);
      },
    };
  }

  async function loadViews() {
    const files = await App.api('views');
    for (const f of files) {
      await new Promise((res) => {
        const s = document.createElement('script');
        s.src = f;
        s.onload = res;
        s.onerror = res;
        document.body.appendChild(s);
      });
    }
    let saved = null;
    try { saved = localStorage.getItem('benchView'); } catch (e) { /* storage off */ }
    if (saved && views.find(v => v.id === saved)) show(saved);
  }

  App.onState(update);
  window.addEventListener('DOMContentLoaded', loadViews);
  return {register, drag, handBatcher, show};
})();

// small SVG helpers for views
const Svg = {
  el(tag, attrs, parent) {
    const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const k in attrs || {}) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  },
  text(parent, x, y, s, attrs) {
    const t = Svg.el('text', Object.assign({x, y, 'font-size': 12, fill: 'currentColor'}, attrs || {}), parent);
    t.textContent = s;
    return t;
  },
};
