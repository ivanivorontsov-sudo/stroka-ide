(function () {
  var title = document.getElementById("title");
  var logEl = document.getElementById("log");
  var calc = document.getElementById("calc");
  var screen = document.getElementById("screen");
  var meta = window.__STROKA__ || { title: "Программа", mode: "auto" };
  title.textContent = meta.title || "Программа";
  function log(s, err) {
    var t = document.createTextNode(s + "\n");
    var d = document.createElement("span");
    if (err) d.className = "err";
    d.appendChild(t);
    logEl.appendChild(d);
    logEl.scrollTop = logEl.scrollHeight;
  }
  var Y = window.PonyatnyYadro;
  var io = window.StrokaRuntime.makeIo(log);
  var R = Y ? Y.makeRuntime(io) : null;
  window.__R = R;
  function fn(name) {
    var g = window["$" + name] || window[name];
    if (typeof g === "function") return g;
    return null;
  }
  function hasCalc() {
    return !!(fn("сложить") && fn("вычесть") && fn("умножить") && fn("разделить"));
  }
  try {
    if (typeof window.__strokaMain === "function") window.__strokaMain(R);
  } catch (e) {
    log(window.StrokaRuntime.showErr(e, R), true);
  }
  if (meta.mode === "calc" || (meta.mode === "auto" && hasCalc())) {
    calc.hidden = false;
    var cur = "0", acc = null, op = null, fresh = true;
    function put(v) { cur = String(v); screen.textContent = cur; }
    function num() { return parseFloat(cur.replace(",", ".")) || 0; }
    function apply(a, o, b) {
      var f = ({ "+": "сложить", "-": "вычесть", "*": "умножить", "/": "разделить" })[o];
      var impl = fn(f);
      if (!impl) throw new Error("Нет функции «" + f + "»");
      var r = impl(a, b);
      if (R && R.show) return r;
      return r;
    }
    function pretty(v) {
      if (R && R.show) return R.show(v);
      if (typeof v === "number") return String(Math.round(v * 1e10) / 1e10).replace(".", ",");
      return String(v);
    }
    calc.addEventListener("click", function (ev) {
      var b = ev.target.closest("button");
      if (!b) return;
      var k = b.getAttribute("data-k");
      try {
        if (k === "C") { acc = op = null; fresh = true; put("0"); return; }
        if (k === "⌫") { put(cur.length > 1 ? cur.slice(0, -1) : "0"); fresh = false; return; }
        if (k === "±") { put((-num()).toString().replace(".", ",")); return; }
        if (k === "%") { put((num() / 100).toString().replace(".", ",")); return; }
        if ("0123456789".indexOf(k) >= 0) {
          if (fresh || cur === "0") { put(k); fresh = false; } else put(cur + k);
          return;
        }
        if (k === ".") {
          if (fresh) { put("0,"); fresh = false; return; }
          if (cur.indexOf(",") < 0 && cur.indexOf(".") < 0) put(cur + ",");
          return;
        }
        if (k === "=") {
          if (op && acc != null) { put(pretty(apply(acc, op, num()))); acc = num(); op = null; fresh = true; }
          return;
        }
        if ("+-*/".indexOf(k) >= 0) {
          if (op && acc != null && !fresh) put(pretty(apply(acc, op, num())));
          acc = num(); op = k; fresh = true;
        }
      } catch (e) {
        log(window.StrokaRuntime.showErr(e, R), true);
        put("Ошибка");
        fresh = true;
      }
    });
  }
})();
