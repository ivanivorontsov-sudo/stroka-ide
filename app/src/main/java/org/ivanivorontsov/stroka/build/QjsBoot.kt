package org.ivanivorontsov.stroka.build

object QjsBoot {
    private val common = """
var __strokaShow = function (v) {
  if (__R && __R.show) return __R.show(v);
  if (typeof v === "number") return String(Math.round(v * 1e10) / 1e10).replace(".", ",");
  return String(v);
};
var __strokaError = function (e) {
  if (e && e.isExit) return "";
  var m = e && e.isPon ? e.message : (__R && __R.explain ? __R.explain(e) : String(e && e.message || e));
  return (__R && __R.L ? "Строка " + __R.L + ": " : "") + m;
};
var __strokaCalc = function (name, a, b) {
  var ops = { "+": "${'$'}сложить", "-": "${'$'}вычесть", "*": "${'$'}умножить", "/": "${'$'}разделить" };
  var key = ops[name] || name;
  var f = globalThis[key] || globalThis[String(key).replace(/^\${'$'}/, "")];
  if (typeof f !== "function") return "Нет функции «" + name + "»";
  var num = function (s) { var t = String(s).trim().replace(",", "."); var v = Number(t); return t !== "" && !isNaN(v) ? v : String(s); };
  try { return __strokaShow(f(num(a), num(b))); } catch (e) { return "Ошибка: " + __strokaError(e); }
};
"""

    fun windows(): String = """
var __R = typeof PonyatnyYadro !== "undefined" ? PonyatnyYadro.makeRuntime({
  write: function (s) { print(s); },
  writeErr: function (s) { print(s); },
  ask: function (q) { return ask(q === undefined ? "" : String(q)); },
  maxSteps: Infinity,
  args: []
}) : null;
""" + common

    fun linuxMain(mode: String): String {
        val calc = if (mode == "calc") """
(function () {
  std.out.puts("Калькулятор. Пустая строка — выход.\n");
  for (;;) {
    std.out.puts("Первое число: "); std.out.flush();
    var a = std.in.getline(); if (a === null || a.trim() === "") break;
    std.out.puts("Действие (+ - * /): "); std.out.flush();
    var o = std.in.getline(); if (o === null) break;
    std.out.puts("Второе число: "); std.out.flush();
    var b = std.in.getline(); if (b === null) break;
    std.out.puts("= " + __strokaCalc(o.trim(), a, b) + "\n");
  }
})();
""" else ""
        return """
var __dir = String(scriptArgs[0] || "").replace(/\/[^\/]*${'$'}/, "") || ".";
var __args = scriptArgs.slice(1);
if (__args.length === 4 && __args[0] === "--stroka-check") {
  var __check = __args.slice(1);
}
std.evalScript(std.loadFile(__dir + "/yadro.js"));
var __R = PonyatnyYadro.makeRuntime({
  write: function (s) { std.out.puts(String(s) + "\n"); std.out.flush(); },
  writeErr: function (s) { std.err.puts(String(s) + "\n"); },
  ask: function (q) { if (q) { std.out.puts(String(q) + " "); std.out.flush(); } var l = std.in.getline(); return l === null ? "" : l; },
  maxSteps: Infinity,
  args: __args
});
""" + common + """
try {
  std.evalScript(std.loadFile(__dir + "/program.js"));
} catch (e) {
  var __m = __strokaError(e);
  if (__m) { std.err.puts("Ошибка. " + __m + "\n"); std.exit(1); }
}
if (typeof __check !== "undefined") {
  std.out.puts(__strokaCalc(__check[0], __check[1], __check[2]) + "\n");
  std.exit(0);
}
""" + calc
    }
}
