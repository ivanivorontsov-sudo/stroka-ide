import importlib.util
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
shim = root / "app/src/main/python/stroka_tkinter.py"
src = (root / "tools/fixtures/калькулятор-tkinter.py").read_text(encoding="utf-8")
spec = importlib.util.spec_from_file_location("stroka_tkinter", shim)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class Cb:
    def __init__(self):
        self.html = ""
        self.step = 0
        self.ids = {}

    def showPreview(self, html):
        self.html = html
        for m in re.finditer(r"onUiEvent\('c:(\d+)'\)[^>]*>([^<]+)</button>", html):
            self.ids[m.group(2).strip()] = m.group(1)
        vm = re.search(r"<input[^>]*value='([^']*)'", html)
        self.value = vm.group(1) if vm else ""

    def waitUiEvent(self, timeout=200):
        self.step += 1
        if self.step == 1:
            missing = [x for x in ("7", "+", "8", "=") if x not in self.ids]
            if missing:
                raise SystemExit("no buttons " + ",".join(missing) + " in " + self.html)
            return "c:" + self.ids["7"]
        if self.step == 2:
            return "c:" + self.ids["+"]
        if self.step == 3:
            return "c:" + self.ids["8"]
        if self.step == 4:
            return "c:" + self.ids["="]
        return "__stop__"

    def postUiEvent(self, e):
        pass

cb = Cb()
mod.install(cb)
g = {"__name__": "__main__", "__file__": "калькулятор-tkinter.py"}
exec(compile(src, "калькулятор-tkinter.py", "exec"), g)
if "Калькулятор" not in cb.html:
    raise SystemExit("title missing: " + cb.html[:400])
if cb.value != "15":
    raise SystemExit("expected 15, got %r html=%s" % (getattr(cb, "value", None), cb.html[:800]))
print("OK 15")
