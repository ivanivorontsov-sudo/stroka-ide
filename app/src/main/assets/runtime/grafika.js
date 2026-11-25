(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.PonyatnyGrafika = factory();
})(typeof self !== "undefined" ? self : this, function () {
  const COLORS = {
    "красный": "#e53935", "зелёный": "#43a047", "зеленый": "#43a047", "синий": "#1e88e5", "голубой": "#4fc3f7", "жёлтый": "#fdd835", "желтый": "#fdd835",
    "оранжевый": "#fb8c00", "фиолетовый": "#8e24aa", "розовый": "#ec407a", "белый": "#ffffff", "чёрный": "#000000", "черный": "#000000",
    "серый": "#9e9e9e", "коричневый": "#6d4c41", "бирюзовый": "#26a69a", "золотой": "#ffd54f", "небесный": "#87ceeb", "прозрачный": "transparent"
  };
  const KEYS = { ArrowLeft: "влево", ArrowRight: "вправо", ArrowUp: "вверх", ArrowDown: "вниз", " ": "пробел", Enter: "ввод", Escape: "выход", Backspace: "стереть", Shift: "шифт", Control: "контрол", Tab: "таб" };
  const keyName = (e) => KEYS[e.key] || String(e.key || "").toLowerCase();
  const color = (c, d) => (c === null || c === undefined ? d : COLORS[String(c).toLowerCase()] || String(c));

  function create(doc, hooks) {
    hooks = hooks || {};
    const win = doc.defaultView || (typeof window !== "undefined" ? window : null);
    let R0 = null, box = null, cv = null, g = null, panel = null, frameFn = null, raf = 0, last = 0, stopped = false;
    const keys = new Set();
    const mouse = { x: 0, y: 0, "нажата": false };
    const widgets = [];
    const clickFns = [], keyFns = [];
    const images = new Map();
    let audio = null;
    const fail = (m) => { if (R0 && R0.fail) R0.fail(m); throw new Error(m); };
    const num = (v, what) => { if (typeof v !== "number" || !Number.isFinite(v)) fail("«" + what + "» должно быть числом"); return v; };

    function stop() {
      stopped = true;
      frameFn = null;
      if (raf && win) win.cancelAnimationFrame(raf);
      raf = 0;
    }
    function run(f, args) {
      if (stopped) return null;
      try { return R0.callback(f, args); }
      catch (e) {
        stop();
        if (e && e.isExit) { if (hooks.exit) hooks.exit(); return null; }
        if (hooks.error) hooks.error(R0.errorText(e)); else if (win && win.console) win.console.error(e);
        return null;
      }
    }
    function ensureBox() {
      if (box) return;
      box = doc.createElement("div");
      box.style.cssText = "font:15px system-ui,sans-serif;color:#222";
      (hooks.mount || doc.body || doc.documentElement).appendChild(box);
      panel = doc.createElement("div");
      panel.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:6px 0";
      box.appendChild(panel);
      doc.addEventListener("keydown", (e) => {
        const k = keyName(e);
        keys.add(k);
        if (cv && ["влево", "вправо", "вверх", "вниз", "пробел"].includes(k) && doc.activeElement === cv) e.preventDefault();
        for (const f of keyFns) run(f, [k]);
      });
      doc.addEventListener("keyup", (e) => keys.delete(keyName(e)));
      if (hooks.opened) hooks.opened();
    }
    function ensureCanvas(w, h, title) {
      ensureBox();
      if (cv) return;
      if (title) { const t = doc.createElement("div"); t.textContent = String(title); t.style.cssText = "font-weight:600;margin:4px 0"; box.insertBefore(t, panel); }
      cv = doc.createElement("canvas");
      cv.width = w || 640; cv.height = h || 480;
      cv.tabIndex = 0;
      cv.style.cssText = "display:block;max-width:100%;background:#000;border-radius:8px;touch-action:none;outline:none";
      box.insertBefore(cv, panel);
      g = cv.getContext("2d");
      const pos = (e) => { const r = cv.getBoundingClientRect(); const p = e.touches && e.touches[0] ? e.touches[0] : e; mouse.x = ((p.clientX - r.left) * cv.width) / r.width; mouse.y = ((p.clientY - r.top) * cv.height) / r.height; };
      cv.addEventListener("pointermove", pos);
      cv.addEventListener("pointerdown", (e) => { pos(e); mouse["нажата"] = true; cv.focus(); for (const f of clickFns) run(f, [mouse.x, mouse.y]); });
      cv.addEventListener("pointerup", () => { mouse["нажата"] = false; });
      try { cv.focus(); } catch (e) {}
      if (hooks.opened) hooks.opened();
    }
    function loop(t) {
      if (stopped || !frameFn) return;
      const dt = last ? Math.min(0.25, (t - last) / 1000) : 0;
      last = t;
      run(frameFn, [dt]);
      if (!stopped && frameFn) raf = win.requestAnimationFrame(loop);
    }
    function widget(el, kind) { panel.appendChild(el); widgets.push({ el, kind }); return widgets.length; }
    function getW(id) { const w = widgets[Math.floor(num(id, "номер элемента")) - 1]; if (!w) fail("нет элемента окна с номером " + id); return w; }

    const api = {
      "окно": (title, w, h) => { if (typeof title === "number") { h = w; w = title; title = null; } ensureCanvas(w ? num(w, "ширина") : 640, h ? num(h, "высота") : 480, title); return null; },
      "залей": (c) => { ensureCanvas(); g.fillStyle = color(c, "#000"); g.fillRect(0, 0, cv.width, cv.height); return null; },
      "прямоугольник": (x, y, w, h, c, fill) => { ensureCanvas(); if (fill === false) { g.strokeStyle = color(c, "#fff"); g.strokeRect(x, y, w, h); } else { g.fillStyle = color(c, "#fff"); g.fillRect(x, y, w, h); } return null; },
      "круг": (x, y, r, c, fill) => { ensureCanvas(); g.beginPath(); g.arc(x, y, Math.max(0, r), 0, Math.PI * 2); if (fill === false) { g.strokeStyle = color(c, "#fff"); g.stroke(); } else { g.fillStyle = color(c, "#fff"); g.fill(); } return null; },
      "линия": (x1, y1, x2, y2, c, w) => { ensureCanvas(); g.beginPath(); g.moveTo(x1, y1); g.lineTo(x2, y2); g.strokeStyle = color(c, "#fff"); g.lineWidth = w || 1; g.stroke(); return null; },
      "рисуй_текст": (x, y, t, c, size) => { if (typeof x === "string" && typeof y === "number") { const s0 = x; x = y; y = t; t = s0; } ensureCanvas(); g.fillStyle = color(c, "#fff"); g.font = (size || 16) + "px system-ui,sans-serif"; g.textBaseline = "top"; g.fillText(R0.show ? R0.show(t) : String(t), x, y); return null; },
      "картинка": (src, x, y, w, h) => {
        ensureCanvas();
        const key = String(src);
        let img = images.get(key);
        if (!img) { img = new win.Image(); img.src = key; images.set(key, img); }
        if (img.complete && img.naturalWidth) { if (w && h) g.drawImage(img, x, y, w, h); else g.drawImage(img, x, y); }
        return null;
      },
      "каждый_кадр": (f) => { if (typeof f !== "function") fail("в «каждый_кадр» нужно передать функцию"); ensureCanvas(); frameFn = f; stopped = false; last = 0; if (!raf) raf = win.requestAnimationFrame(loop); return null; },
      "клавиша": (k) => keys.has(String(k).toLowerCase()),
      "мышь": () => ({ x: mouse.x, y: mouse.y, "нажата": mouse["нажата"] }),
      "при_клике": (f) => { if (typeof f !== "function") fail("в «при_клике» нужно передать функцию"); ensureCanvas(); clickFns.push(f); return null; },
      "при_клавише": (f) => { if (typeof f !== "function") fail("в «при_клавише» нужно передать функцию"); ensureBox(); keyFns.push(f); return null; },
      "стоп_игра": () => { stop(); if (hooks.exit) hooks.exit(); return null; },
      "кнопка": (t, f) => { if (typeof f !== "function") fail("второе значение у «кнопка» — функция, которая выполнится при нажатии"); ensureBox(); const b = doc.createElement("button"); b.textContent = String(t); b.style.cssText = "border:0;border-radius:8px;padding:8px 14px;background:#2e8b57;color:#fff;font-size:15px;cursor:pointer"; b.onclick = () => run(f, []); return widget(b, "кнопка"); },
      "надпись": (t) => { ensureBox(); const s = doc.createElement("span"); s.textContent = t === undefined ? "" : R0.show(t); return widget(s, "надпись"); },
      "поле_ввода": (hint) => { ensureBox(); const i = doc.createElement("input"); i.placeholder = hint === undefined ? "" : String(hint); i.style.cssText = "padding:7px;border:1px solid #bbb;border-radius:8px;font-size:15px"; return widget(i, "поле"); },
      "значение_поля": (id) => { const w = getW(id); return w.kind === "поле" ? w.el.value : w.el.textContent; },
      "задай_текст": (id, t) => { const w = getW(id); const v = R0.show(t); if (w.kind === "поле") w.el.value = v; else w.el.textContent = v; return null; },
      "пищи": (f, ms) => {
        try {
          const AC = win.AudioContext || win.webkitAudioContext;
          if (!AC) return null;
          audio = audio || new AC();
          const o = audio.createOscillator(), v = audio.createGain();
          o.frequency.value = f || 440; v.gain.value = 0.1;
          o.connect(v); v.connect(audio.destination);
          o.start(); o.stop(audio.currentTime + (ms || 200) / 1000);
        } catch (e) {}
        return null;
      },
      "ширина_экрана": () => { ensureCanvas(); return cv.width; },
      "высота_экрана": () => { ensureCanvas(); return cv.height; },
    };
    return {
      call(name, args, R) {
        R0 = R;
        const f = api[name];
        if (!f) fail("нет такой команды графики: «" + name + "»");
        return f(...args);
      },
      busy() { return !stopped && (!!frameFn || clickFns.length > 0 || keyFns.length > 0 || widgets.some((w) => w.kind === "кнопка")); },
      stop,
    };
  }
  return { create, COLORS };
});
