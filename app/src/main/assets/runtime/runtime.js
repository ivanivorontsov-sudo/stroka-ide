(function (root) {
  function showErr(e, R) {
    if (e && e.isExit) return;
    var m = e && e.isPon ? e.message : (R && R.explain ? R.explain(e) : String(e && e.message ? e.message : e));
    var line = R && R.L ? "Строка " + R.L + ": " : "";
    return "Ошибка. " + line + m;
  }
  function makeIo(log, errCls) {
    return {
      write: function (s) { log(String(s), false); },
      writeErr: function (s) { log(String(s), true); },
      ask: function (q) { return window.prompt(q || "Ввод", "") || ""; },
      maxSteps: 5000000,
      args: [],
      system: window.StrokaSystem || null
    };
  }
  root.StrokaRuntime = { showErr: showErr, makeIo: makeIo };
})(window);
