// The playground page: an editor for the app's files, compiled by
// server.py, and a simulator frame (sim.html) that runs the result.

const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { const v = localStorage.getItem("ecsim:" + key); return v === null ? fallback : JSON.parse(v); } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem("ecsim:" + key, JSON.stringify(value)); } catch { /* private mode */ } },
  del(key) { try { localStorage.removeItem("ecsim:" + key); } catch { /* private mode */ } },
};

let examples = [];
let example = null;     // the current example
let files = {};         // its files as edited
let active = null;      // the file in the editor
let diagnostics = [];   // from the last compile
let src = null;         // the app module in the frame
let compiling = false;
let again = false;      // Run while compiling: once more after

// ------------------------------------------------------------------ editor

const docs = {};  // file -> CodeMirror.Doc
const cm = window.CodeMirror
  ? CodeMirror($("editor"), {
      mode: "text/x-kotlin", lineNumbers: true, indentUnit: 4, tabSize: 4, indentWithTabs: false,
      matchBrackets: true, autoCloseBrackets: true, gutters: ["diag-gutter", "CodeMirror-linenumbers"],
      extraKeys: { Tab: (c) => c.execCommand(c.somethingSelected() ? "indentMore" : "insertSoftTab") },
    })
  : null;
const textarea = cm ? null : Object.assign(document.createElement("textarea"), { spellcheck: false });
if (textarea) $("editor").append(textarea);

function showFile(name) {
  if (active && textarea) files[active] = textarea.value;
  active = name;
  if (cm) {
    if (!docs[name]) {
      docs[name] = CodeMirror.Doc(files[name], "text/x-kotlin");
      docs[name].on("change", (d) => { files[name] = d.getValue(); saveEdits(); });
    }
    cm.swapDoc(docs[name]);
    markDiagnostics();
    cm.refresh();
  } else {
    textarea.value = files[name];
    textarea.oninput = () => { files[name] = textarea.value; saveEdits(); };
  }
  renderTabs();
}

function renderTabs() {
  const tabs = $("tabs");
  tabs.replaceChildren();
  for (const name of Object.keys(files)) {
    const tab = document.createElement("button");
    tab.className = "tab" + (name === active ? " active" : "");
    tab.textContent = name;
    if (diagnostics.some((d) => d.file === name && d.severity === "error")) {
      tab.prepend(Object.assign(document.createElement("span"), { className: "bad" }));
    }
    tab.onclick = () => showFile(name);
    if (Object.keys(files).length > 1) {
      const x = Object.assign(document.createElement("span"), { className: "x", textContent: "×", title: `Remove ${name}` });
      x.onclick = (e) => {
        e.stopPropagation();
        if (!confirm(`Remove ${name}?`)) return;
        delete files[name];
        delete docs[name];
        saveEdits();
        showFile(name === active ? Object.keys(files)[0] : active);
      };
      tab.append(x);
    }
    tabs.append(tab);
  }
  const add = Object.assign(document.createElement("button"), { className: "tab add", textContent: "+", title: "New file" });
  add.onclick = () => {
    let name = prompt("File name", "Util.kt");
    if (!name) return;
    if (!name.endsWith(".kt")) name += ".kt";
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,63}\.kt$/.test(name)) return alert("A Kotlin file name: letters, digits and _, ending in .kt");
    if (!(name in files)) files[name] = "import androidx.compose.runtime.*\nimport ui.*\n\n";
    saveEdits();
    showFile(name);
  };
  tabs.append(add);
}

function markDiagnostics() {
  if (!cm) return;
  cm.clearGutter("diag-gutter");
  for (let i = 0; i < cm.lineCount(); i++) {
    cm.removeLineClass(i, "background", "line-error");
    cm.removeLineClass(i, "background", "line-warning");
  }
  for (const d of diagnostics) {
    if (d.file !== active) continue;
    const line = Math.min(d.line - 1, cm.lineCount() - 1);
    const marker = Object.assign(document.createElement("span"), { className: `diag-marker ${d.severity}`, title: d.message });
    cm.setGutterMarker(line, "diag-gutter", marker);
    cm.addLineClass(line, "background", d.severity === "error" ? "line-error" : "line-warning");
  }
}

function jumpTo(d) {
  if (d.file in files) showFile(d.file);
  if (!cm) return;
  cm.focus();
  cm.setCursor({ line: d.line - 1, ch: d.column - 1 });
  cm.scrollIntoView(null, 120);
}

// ---------------------------------------------------------------- examples

function saveEdits() {
  store.set("files:" + example.id, files);
}

function openExample(id) {
  example = examples.find((e) => e.id === id) || examples[0];
  store.set("example", example.id);
  $("example").value = example.id;
  files = structuredClone(store.get("files:" + example.id, null) || example.files);
  for (const k of Object.keys(docs)) delete docs[k];
  diagnostics = [];
  active = null;
  showFile(Object.keys(files)[0]);
}

$("example").onchange = () => { openExample($("example").value); run(); };
$("reset").onclick = () => {
  if (!confirm("Undo your edits to this example?")) return;
  store.del("files:" + example.id);
  openExample(example.id);
  run();
};

// ----------------------------------------------------------------- console

function say(text, kind = "", onClick = null) {
  const out = $("console");
  const stick = out.scrollTop + out.clientHeight >= out.scrollHeight - 4;
  const line = Object.assign(document.createElement("div"), { className: `line ${kind}`, textContent: text });
  if (onClick) { line.classList.add("link"); line.onclick = onClick; }
  out.append(line);
  while (out.childElementCount > 2000) out.firstChild.remove();
  if (stick) out.scrollTop = out.scrollHeight;
}
$("clear").onclick = () => $("console").replaceChildren();

function status(text, kind = "") {
  $("status").textContent = text;
  $("status").className = `status ${kind}`;
}

function overlay(text, kind = "") {
  $("overlay").hidden = !text;
  $("overlay").textContent = text || "";
  $("overlay").className = `overlay ${kind}`;
}

// --------------------------------------------------------------- simulator

// The panels, as embedded-compose drives them: HUB75 (RGB, rpi-hub75) and
// MAX7219 modules (red LEDs, on or off; rpi-max7219). Sizes in LEDs.
const PANELS = {
  hub75: {
    size: "64x32",
    sizes: [["32x16", "32 × 16"], ["64x32", "64 × 32"], ["64x64", "64 × 64"], ["128x32", "128 × 32 (two chained)"], ["128x64", "128 × 64"]],
  },
  max7219: {
    size: "32x8",
    sizes: [["32x8", "32 × 8 (4-in-1)"], ["64x8", "64 × 8 (two 4-in-1)"]],
  },
};

function panelSize() {
  const [w, h] = $("size").value.split("x").map(Number);
  return { w, h, mono: $("panel").value === "max7219" };
}

// The size list for the chosen panel, with the size last used on it.
function fillSizes() {
  const panel = PANELS[$("panel").value];
  $("size").replaceChildren(...panel.sizes.map(([value, text]) => new Option(text, value)));
  const saved = store.get(`size:${$("panel").value}`, panel.size);
  $("size").value = panel.sizes.some(([value]) => value === saved) ? saved : panel.size;
}

function startSim() {
  const { w, h, mono } = panelSize();
  $("bezel").style.aspectRatio = `${w} / ${h}`;
  overlay(src.startsWith("blob:") ? "starting… (the first time, the Kotlin runtime downloads from JetBrains)" : null);
  $("sim").src = `sim.html?w=${w}&h=${h}${mono ? "&mono=1" : ""}&src=${encodeURIComponent(src)}`;
}

addEventListener("message", (e) => {
  if (e.origin !== location.origin || e.data?.source !== "ec-sim") return;
  const m = e.data;
  if (m.type === "log") say(m.text, "app");
  else if (m.type === "crash") { say(m.text, "error"); overlay(`The app stopped:\n${m.text}`, "crash"); }
  else if (m.type === "frame") overlay(null);
  else if (m.type === "fps") $("fps").textContent = `${m.fps} fps · ${m.frames} frames`;
  else if (m.type === "started") say(`— started on ${m.width}×${m.height} —`);
  else if (m.type === "snapshot") {
    Object.assign(document.createElement("a"), { href: m.url, download: `${example.id}-${Date.now()}.png` }).click();
  }
});
const toSim = (msg) => $("sim").contentWindow?.postMessage({ source: "ec-page", ...msg }, location.origin);

const savedPanel = store.get("panel", "hub75");
$("panel").value = savedPanel in PANELS ? savedPanel : "hub75";
fillSizes();
$("panel").onchange = () => { store.set("panel", $("panel").value); fillSizes(); if (src) startSim(); };
$("size").onchange = () => { store.set(`size:${$("panel").value}`, $("size").value); if (src) startSim(); };
$("restart").onclick = () => src && startSim();
$("snapshot").onclick = () => toSim({ type: "snapshot" });
for (const b of document.querySelectorAll("#keys button")) {
  const code = +b.dataset.key;
  const down = (e) => { e.preventDefault(); b.setPointerCapture?.(e.pointerId); toSim({ type: "key", code, value: 1 }); };
  const up = () => toSim({ type: "key", code, value: 0 });
  b.addEventListener("pointerdown", down);
  b.addEventListener("pointerup", up);
  b.addEventListener("pointercancel", up);
}

// ------------------------------------------------------------------ build

// Where code is compiled: server.py on this machine, or, on a static host
// (GitHub Pages, made by pages.py), JetBrains' Kotlin Playground server, which
// other sites may use too. There the examples' own code runs prebuilt.

const local = {
  name: "this machine",
  examples: async () => {
    const res = await fetch("api/examples");
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  },
  // The last build, if it is of these files.
  async lastBuild(fs) {
    const state = await (await fetch("api/status")).json();
    return state.ready && state.hash === (await filesHash(fs)) ? `build/playground/playground.mjs?v=${state.build}` : null;
  },
  async compile(fs) {
    const res = await fetch("api/compile", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ files: fs }),
    });
    const answer = await res.json();
    if (answer.ok) answer.src = `build/playground/playground.mjs?v=${answer.build}`;
    return answer;
  },
};

const KCS = "https://api.kotlinlang.org";

// fetch, saying which request failed and how; a network failure is tried once more.
async function request(what, url, options) {
  for (let attempt = 1; ; attempt++) {
    try {
      return await fetch(url, options);
    } catch (e) {
      if (attempt === 2) throw new Error(`${what} failed: ${e.message} (${url})`);
      say(`${what}: ${e.message}; trying again`, "warning");
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
}
const kotlinPlayground = {
  name: "api.kotlinlang.org",
  toolkit: null,  // toolkit.json: the toolkit's sources, sent with every compile
  version: null,  // the newest Kotlin the server has Compose for
  blob: null,
  examples: async () => (await fetch("examples.json")).json(),
  lastBuild: async (fs) => (sameFiles(fs, example.files) ? `build/examples/${example.id}/playground.mjs` : null),
  async compile(fs) {
    const prebuilt = await this.lastBuild(fs);
    if (prebuilt) return { ok: true, src: prebuilt, diagnostics: [], seconds: 0 };
    const t0 = performance.now();
    this.toolkit ??= await (await request("loading toolkit.json", "toolkit.json")).json();
    this.version ??= (await (await request("asking which Kotlin versions it has", `${KCS}/versions`)).json())
      .find((v) => v.hasComposeAssets)?.version ?? "2.4.0";
    const all = { ...this.toolkit, ...fs };
    const res = await request("compiling", `${KCS}/api/${this.version}/compiler/translate?compiler=compose-wasm`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ args: "", confType: "compose-wasm", files: Object.entries(all).map(([name, text]) => ({ name, text, publicId: "" })) }),
    });
    if (!res.ok) return { ok: false, diagnostics: [], log: `${KCS}: HTTP ${res.status}` };
    const r = await res.json();
    const diagnostics = [], log = [];
    for (const [file, list] of Object.entries(r.errors || {})) {
      for (const e of list) {
        if (e.severity !== "ERROR" && e.severity !== "WARNING") continue;
        const d = { severity: e.severity.toLowerCase(), file, line: e.interval.start.line + 1, column: e.interval.start.ch + 1, message: e.message };
        if (!(file in this.toolkit)) diagnostics.push(d);
        else if (d.severity === "error") log.push(`toolkit ${file}:${d.line}: ${d.message}`);  // its warnings aren't yours
      }
    }
    if (r.exception) log.push(`${r.exception.fullName || "exception"}: ${r.exception.message}`);
    const ok = !!r.jsCode && !diagnostics.some((d) => d.severity === "error");
    const answer = { ok, diagnostics, log: log.join("\n"), seconds: ((performance.now() - t0) / 1000).toFixed(1) };
    if (ok) {
      if (this.blob) URL.revokeObjectURL(this.blob);
      this.blob = answer.src = URL.createObjectURL(new Blob([r.jsCode], { type: "text/javascript" }));
    }
    return answer;
  },
};

let compiler = local;

function sameFiles(a, b) {
  const ka = Object.keys(a).sort(), kb = Object.keys(b).sort();
  return ka.length === kb.length && ka.every((k, i) => k === kb[i] && a[k] === b[k]);
}

async function filesHash(fs) {
  const text = Object.keys(fs).sort().map((n) => `${n}\0${fs[n]}\0`).join("");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}

async function run() {
  if (compiling) { again = true; return; }
  if (textarea && active) files[active] = textarea.value;
  compiling = true;
  status(`compiling on ${compiler.name}…`, "busy");
  overlay(src ? null : "compiling…");
  try {
    const answer = await compiler.compile(structuredClone(files));
    diagnostics = answer.diagnostics || [];
    markDiagnostics();
    renderTabs();
    const errors = diagnostics.filter((d) => d.severity === "error");
    for (const d of diagnostics) {
      say(`${d.file}:${d.line}:${d.column}  ${d.message}`, `${d.severity}`, () => jumpTo(d));
    }
    if (answer.ok) {
      src = answer.src;
      status(answer.seconds ? `built in ${answer.seconds} s` : "ready", "ok");
      if (answer.seconds) say(`built on ${compiler.name} in ${answer.seconds} s`, "ok");
      startSim();
    } else {
      if (answer.log) say(answer.log, "error");
      status(errors.length ? `${errors.length} error${errors.length > 1 ? "s" : ""}` : "build failed", "error");
      if (errors.length) jumpTo(errors[0]);
      if (!src) overlay("fix the errors and Run");
    }
  } catch (e) {
    const network = e instanceof TypeError || /Failed to fetch|NetworkError|Load failed/.test(e.message);
    status(network ? `can't reach ${compiler.name}` : "compile failed: see the console", "error");
    say(`${e.message || e}${compiler === local ? ". Is ./server.py running?" : ""}`, "error");
    if (network && compiler !== local) {
      say("Your browser couldn't connect to api.kotlinlang.org. Open https://api.kotlinlang.org/versions in a new tab: " +
          "if that fails too, a network filter, VPN or ad/privacy blocker is stopping it.", "error");
    }
  } finally {
    compiling = false;
    if (again) { again = false; run(); }
  }
}

$("run").onclick = run;
addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && (e.key === "Enter" || e.key === "s")) {
    e.preventDefault();
    run();
  }
});

// ------------------------------------------------------------------- start

(async () => {
  try {
    examples = await local.examples();
  } catch {
    try {
      examples = await kotlinPlayground.examples();
      compiler = kotlinPlayground;
      say("Edited code is compiled by JetBrains' Kotlin Playground server (api.kotlinlang.org); the examples as they are run prebuilt.");
    } catch {
      status("no examples", "error");
      say("Can't load the examples: start ./server.py and open the address it prints.", "error");
      return;
    }
  }
  for (const e of examples) $("example").append(new Option(e.title, e.id));
  openExample(store.get("example", "matrix"));
  // Show the last build at once if it is of these files; otherwise build them.
  const last = await compiler.lastBuild(files);
  if (last) {
    src = last;
    status("ready", "ok");
    startSim();
  } else {
    run();
  }
})();
