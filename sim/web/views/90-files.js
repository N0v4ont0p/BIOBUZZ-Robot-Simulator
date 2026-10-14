// Files on the robot (/sdcard/FIRST): the results the setup programs write, and the configurations.
Bench.register({
  id: 'files', title: 'Robot files', order: 90,
  mount(el) {
    el.innerHTML = `<h3>Files on the robot (/sdcard/FIRST)</h3>
      <p class="hint">The setup programs write their results here. On the real robot you copy them with
        <code>adb pull /sdcard/FIRST/biobuzz_results.csv</code>.</p>
      <div class="controls"><button class="btn secondary" id="flRefresh">Refresh</button>
        <button class="btn secondary" id="flPull">Copy results (adb pull)</button>
        <span class="hint">then the Send results tab enters them</span></div>
      <table class="ports" id="flList"></table><pre class="file" id="flText" hidden></pre>`;
    const load = async () => {
      const files = await App.api('files');
      el.querySelector('#flList').innerHTML = '<tr><th>File</th><th>Size</th><th>Changed</th></tr>' + (files.map(f =>
        `<tr><td><a href="#" data-n="${App.esc(f.name)}">${App.esc(f.name)}</a></td><td>${f.bytes} B</td><td>${new Date(f.modified).toLocaleTimeString()}</td></tr>`).join('')
        || '<tr><td colspan="3" class="empty">No files yet</td></tr>');
      el.querySelectorAll('[data-n]').forEach(a => a.onclick = async (e) => {
        e.preventDefault();
        const f = await App.api('file?name=' + encodeURIComponent(a.dataset.n));
        const pre = el.querySelector('#flText');
        pre.hidden = false;
        pre.textContent = f.text;
      });
    };
    el.querySelector('#flRefresh').onclick = load;
    el.querySelector('#flPull').onclick = async () => {
      const r = await App.api('loop/pull', {}).catch(e => ({files: [], error: e.message}));
      App.toast(r.error || (r.files.length ? 'Copied ' + r.files.join(', ') + ' to the outbox' : 'No results on the robot yet'));
    };
    load();
    this.load = load;
  },
  update() {},
});
