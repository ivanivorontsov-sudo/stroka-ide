(function (root) {
  function showErr(e, R) {
    if (e && e.isExit) return;
    if (R && R.featureCatch && R.featureCatch(e)) return;
    var m = e && e.isPon ? e.message : (R && R.explain ? R.explain(e) : String(e && e.message ? e.message : e));
    var line = R && R.L ? "Строка " + R.L + ": " : "";
    return "Ошибка. " + line + m;
  }
  function makeIo(log, errCls) {
    return {
      write: function (s) { log(String(s), false); },
      writeErr: function (s) { log(String(s), true); },
      ask: function (q) { var v = window.prompt(q || "Ввод", ""); return v === null || v === undefined ? null : v; },
      feature: function (s) { log("✔ " + String(s), false); },
      gfx: root.PonyatnyGrafika ? root.PonyatnyGrafika.create(document, { mount: document.getElementById("outwrap") || document.body, error: function (m) { log("Ошибка. " + m, true); } }) : undefined,
      maxSteps: 5000000,
      args: [],
      system: window.StrokaSystem || null
    };
  }
  root.StrokaRuntime = { showErr: showErr, makeIo: makeIo };
})(window);
