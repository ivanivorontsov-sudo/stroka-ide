import html
import sys
import time
import types
from urllib.parse import unquote

_callback = None
_widgets = {}
_next_id = 0
_after = []
_running = True


def install(callback):
    global _callback, _widgets, _next_id, _after, _running
    _callback = callback
    _widgets = {}
    _next_id = 0
    _after = []
    _running = True
    tk = types.ModuleType("tkinter")
    tk.__path__ = []
    tk.__file__ = __file__
    ttk = types.ModuleType("tkinter.ttk")
    messagebox = types.ModuleType("tkinter.messagebox")
    constants = types.ModuleType("tkinter.constants")
    dialog = {"html": ""}

    class Variable:
        def __init__(self, master=None, value="", **kwargs):
            self.value = 0 if isinstance(self, IntVar) and value == "" else value
            self._traces = []

        def get(self):
            return self.value

        def set(self, value):
            self.value = value
            for fn in list(self._traces):
                try:
                    fn()
                except Exception:
                    pass

        def trace_add(self, mode, fn):
            self._traces.append(fn)

        def trace(self, mode, fn):
            self._traces.append(fn)

        def __str__(self):
            return str(self.value)

    class StringVar(Variable):
        pass

    class IntVar(Variable):
        def get(self):
            try:
                return int(self.value)
            except Exception:
                return 0

    class DoubleVar(Variable):
        def get(self):
            try:
                return float(self.value)
            except Exception:
                return 0.0

    class BooleanVar(Variable):
        def get(self):
            return bool(self.value)

    def _css_color(v):
        if v is None:
            return None
        s = str(v).strip()
        return s

    def _css_font(v):
        if v is None:
            return None
        if isinstance(v, (tuple, list)):
            family = str(v[0]) if v else "sans-serif"
            size = str(v[1]) + "px" if len(v) > 1 else "16px"
            weight = "bold" if any(str(x).lower() == "bold" for x in v[2:]) else "normal"
            style = "italic" if any(str(x).lower() in ("italic", "oblique") for x in v[2:]) else "normal"
            return "font-family:%s;font-size:%s;font-weight:%s;font-style:%s" % (family, size, weight, style)
        return "font:%s" % v

    def _pad(v):
        if v is None:
            return (0, 0)
        if isinstance(v, (tuple, list)):
            if len(v) == 1:
                return (int(v[0]), int(v[0]))
            return (int(v[0]), int(v[1]))
        return (int(v), int(v))

    class Widget:
        def __init__(self, master=None, **kwargs):
            global _next_id
            _next_id += 1
            self.widget_id = _next_id
            _widgets[self.widget_id] = self
            self.master = master
            self.kwargs = dict(kwargs)
            self.children = []
            self.pack_opts = None
            self.grid_opts = None
            self.place_opts = None
            self.text = kwargs.get("text", "")
            self.variable = kwargs.get("textvariable") or kwargs.get("variable")
            if self.variable is not None:
                self.value = self.variable.get()
            else:
                self.value = kwargs.get("value", "")
            self.command = kwargs.get("command")
            self.bindings = {}
            self.options = list(kwargs.get("values", []) or [])
            self.kind = kwargs.get("show", "")
            self.canvas_items = []
            if master is not None and hasattr(master, "children"):
                master.children.append(self)

        def pack(self, *a, **k):
            self.pack_opts = dict(k)
            return self

        def grid(self, *a, **k):
            self.grid_opts = dict(k)
            return self

        def place(self, *a, **k):
            self.place_opts = dict(k)
            return self

        def pack_forget(self):
            self.pack_opts = None
            return self

        def grid_forget(self):
            self.grid_opts = None
            return self

        def place_forget(self):
            self.place_opts = None
            return self

        def configure(self, **kwargs):
            self.kwargs.update(kwargs)
            if "text" in kwargs:
                self.text = kwargs["text"]
            if "command" in kwargs:
                self.command = kwargs["command"]
            if "textvariable" in kwargs:
                self.variable = kwargs["textvariable"]
            if "bg" in kwargs:
                self.kwargs["background"] = kwargs["bg"]
            if "fg" in kwargs:
                self.kwargs["foreground"] = kwargs["fg"]
            return self

        config = configure

        def cget(self, key):
            if key == "text":
                return self.text
            return self.kwargs.get(key)

        def bind(self, seq, fn=None, **k):
            if fn is not None:
                self.bindings[seq] = fn
            return self

        def focus_set(self):
            return self

        def focus(self):
            return self.focus_set()

        def insert(self, index, value):
            s = str(self.value)
            if index in ("end", "END", tk.END if "tk" in dir() else "end"):
                self.value = s + str(value)
            else:
                try:
                    i = int(index)
                    self.value = s[:i] + str(value) + s[i:]
                except Exception:
                    self.value = s + str(value)
            if self.variable is not None:
                self.variable.set(self.value)

        def delete(self, first=0, last=None):
            if last is None or str(last) in ("end", "END"):
                if str(first) in ("0", "0", 0):
                    self.value = ""
                else:
                    try:
                        self.value = str(self.value)[:int(first)]
                    except Exception:
                        self.value = ""
            else:
                try:
                    a, b = int(first), int(last) if str(last) not in ("end", "END") else len(str(self.value))
                    s = str(self.value)
                    self.value = s[:a] + s[b:]
                except Exception:
                    self.value = ""
            if self.variable is not None:
                self.variable.set(self.value)

        def get(self, *a):
            if self.variable is not None:
                return str(self.variable.get())
            return str(self.value)

        def set(self, value):
            self.value = value
            if self.variable is not None:
                self.variable.set(value)

        def winfo_children(self):
            return list(self.children)

        def destroy(self):
            if self.master is not None and hasattr(self.master, "children") and self in self.master.children:
                self.master.children.remove(self)
            _widgets.pop(self.widget_id, None)

        def after(self, ms, func=None, *args):
            when = time.time() + (float(ms) / 1000.0)
            item = [when, func, args, True]
            _after.append(item)
            return id(item)

        def after_cancel(self, ident):
            for it in _after:
                if id(it) == ident:
                    it[3] = False

        def update(self):
            _flush_after()
            _redraw_root()

        def update_idletasks(self):
            self.update()

        def _style(self):
            kw = self.kwargs
            parts = []
            bg = _css_color(kw.get("bg") or kw.get("background"))
            fg = _css_color(kw.get("fg") or kw.get("foreground"))
            if bg:
                parts.append("background:%s" % bg)
            if fg:
                parts.append("color:%s" % fg)
            font = _css_font(kw.get("font"))
            if font:
                parts.append(font)
            relief = str(kw.get("relief") or "flat").lower()
            if relief == "raised":
                parts.append("border:1px solid #888;box-shadow:1px 1px 0 #ccc")
            elif relief == "sunken":
                parts.append("border:1px solid #888;box-shadow:inset 1px 1px 2px #0003")
            elif relief == "groove":
                parts.append("border:2px groove #888")
            elif relief == "ridge":
                parts.append("border:2px ridge #888")
            elif relief == "solid":
                parts.append("border:1px solid currentColor")
            else:
                parts.append("border:0")
            padx = _pad(kw.get("padx"))
            pady = _pad(kw.get("pady"))
            ipadx = _pad(kw.get("ipadx"))
            ipady = _pad(kw.get("ipady"))
            parts.append("padding:%dpx %dpx %dpx %dpx" % (pady[0] + ipady[0], padx[1] + ipadx[1], pady[1] + ipady[1], padx[0] + ipadx[0]))
            if kw.get("width"):
                try:
                    parts.append("min-width:%dem" % int(kw["width"]))
                except Exception:
                    pass
            return ";".join(parts)

        def _box_style(self, extra=""):
            st = self._pack_css() + self._style()
            if extra:
                st += ";" + extra
            return st

        def _pack_css(self):
            o = self.pack_opts or {}
            parts = []
            fill = str(o.get("fill") or "").lower()
            expand = o.get("expand")
            padx = _pad(o.get("padx"))
            pady = _pad(o.get("pady"))
            if padx[0] or padx[1] or pady[0] or pady[1]:
                parts.append("margin:%dpx %dpx %dpx %dpx" % (pady[0], padx[1], pady[1], padx[0]))
            if fill in ("x", "both"):
                parts.append("width:100%;box-sizing:border-box")
            if fill in ("y", "both") or expand:
                parts.append("flex:1")
            side = str(o.get("side") or "top").lower()
            if side == "right":
                parts.append("margin-left:auto")
            return (";".join(parts) + ";") if parts else ""

        def _label_text(self):
            if self.variable is not None and isinstance(self, (Label, Button, Checkbutton, Radiobutton)):
                return str(self.variable.get())
            return str(self.text)

        def _render(self):
            children = self._render_children()
            esc = html.escape(self._label_text())
            wid = str(self.widget_id)
            st = self._box_style()
            if isinstance(self, Label):
                return '<div class="label" style="%s">%s</div>%s' % (st, esc, children)
            if isinstance(self, Entry):
                typ = "password" if self.kind else "text"
                val = html.escape(str(self.get()))
                return "<input class='entry' style='%s' data-wid='%s' oninput=\"window.Stroka&&Stroka.onUiEvent('i:%s|'+encodeURIComponent(this.value))\" value='%s' type='%s'>%s" % (st, wid, wid, val, typ, children)
            if isinstance(self, Text):
                return "<textarea class='entry' style='%s' data-wid='%s' oninput=\"window.Stroka&&Stroka.onUiEvent('i:%s|'+encodeURIComponent(this.value))\" rows='%s'>%s</textarea>%s" % (st, wid, wid, str(self.kwargs.get("height", 4)), html.escape(str(self.get())), children)
            if isinstance(self, Button):
                active = _css_color(self.kwargs.get("activebackground"))
                ast = ('onmousedown="this.dataset.bg=this.style.background;this.style.background=\'' + active + '\'" onmouseup="this.style.background=this.dataset.bg" ') if active else ""
                return "<button type='button' style='%s' %sonclick=\"window.Stroka&&Stroka.onUiEvent('c:%s')\">%s</button>%s" % (st, ast, wid, esc, children)
            if isinstance(self, Checkbutton):
                return '<label class="check" style="%s"><input type="checkbox" data-wid="%s" onchange="window.Stroka&&Stroka.onUiEvent(\'i:%s|\'+(this.checked?\'1\':\'0\'))"> %s</label>%s' % (st, wid, wid, esc, children)
            if isinstance(self, Radiobutton):
                return '<label class="check" style="%s"><input type="radio" name="r%s" data-wid="%s" onchange="window.Stroka&&Stroka.onUiEvent(\'c:%s\')"> %s</label>%s' % (st, id(self.variable) if self.variable else 0, wid, wid, esc, children)
            if isinstance(self, Listbox):
                opts = "".join('<option>%s</option>' % html.escape(str(x)) for x in self.options)
                return '<select class="entry" style="%s">%s</select>%s' % (st, opts, children)
            if isinstance(self, (Scale, Spinbox)):
                return '<input class="entry" style="%s" type="number" value="%s">%s' % (st, html.escape(str(self.value)), children)
            if isinstance(self, Canvas):
                w = int(self.kwargs.get("width", 300) or 300)
                h = int(self.kwargs.get("height", 200) or 200)
                items = "".join(self.canvas_items)
                return '<svg class="canvas" style="%s;width:%dpx;height:%dpx" viewBox="0 0 %d %d">%s</svg>%s' % (st, w, h, w, h, items, children)
            if isinstance(self, (Frame, Labelframe, Tk, Toplevel)):
                title = ("<h3 style='margin:0 0 8px'>%s</h3>" % esc) if isinstance(self, Labelframe) and esc else ""
                return '<section style="%sdisplay:flex;flex-direction:column">%s%s</section>' % (st, title, children)
            return '<div style="%s">%s%s</div>' % (st, esc, children)

        def _render_children(self):
            if not self.children:
                return ""
            return "".join(ch._render() for ch in self.children if ch.pack_opts is not None or ch.grid_opts is not None or ch.place_opts is not None or (ch.pack_opts is None and ch.grid_opts is None and ch.place_opts is None))

    class Tk(Widget):
        def __init__(self, *a, **k):
            super().__init__(None, **k)
            self.window_title = "Программа"

        def title(self, value=None):
            if value is None:
                return self.window_title
            self.window_title = str(value)

        def geometry(self, value=None):
            return None

        def resizable(self, *a):
            return None

        def minsize(self, *a):
            return None

        def maxsize(self, *a):
            return None

        def protocol(self, *a):
            return None

        def iconbitmap(self, *a, **k):
            return None

        def quit(self):
            global _running
            _running = False
            if _callback is not None:
                try:
                    _callback.postUiEvent("__stop__")
                except Exception:
                    pass

        def destroy(self):
            self.quit()

        def _page(self):
            body = "".join(x._render() for x in self.children)
            return """<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8">
<style>*{box-sizing:border-box}body{margin:0;padding:16px;background:#eceff4;color:#1c1f26;font:16px system-ui,sans-serif}main{max-width:560px;margin:3vh auto;background:#fff;border-radius:16px;padding:18px;box-shadow:0 8px 28px #0002}h2{margin:0 0 14px;font-size:20px}.label{margin:6px 0}.entry{display:block;width:100%%;padding:10px;margin:4px 0;border:1px solid #b8bdc9;border-radius:8px;font:inherit}textarea{min-height:96px}button{padding:10px 14px;margin:3px;border-radius:8px;font:inherit;cursor:pointer}.check{display:block;margin:8px 0}section{margin:6px 0;width:100%%}svg.canvas{background:#fff;border:1px solid #ccc}</style>
<main><h2>%s</h2>%s%s</main></html>""" % (html.escape(self.window_title), body, dialog["html"] or "")

        def mainloop(self, n=0):
            global _running
            _running = True
            _redraw(self)
            while _running:
                _flush_after()
                timeout = _next_timeout_ms()
                ev = _wait(timeout)
                if ev == "__stop__":
                    break
                if ev:
                    _handle(self, ev, dialog)
                    _redraw(self)

    class Toplevel(Tk):
        def __init__(self, master=None, **k):
            Widget.__init__(self, master, **k)
            self.window_title = "Окно"
            self.children = []

    class Frame(Widget):
        pass

    class Labelframe(Widget):
        pass

    class Label(Widget):
        pass

    class Entry(Widget):
        pass

    class Text(Widget):
        pass

    class Button(Widget):
        pass

    class Checkbutton(Widget):
        pass

    class Radiobutton(Widget):
        pass

    class Listbox(Widget):
        pass

    class Scale(Widget):
        pass

    class Spinbox(Widget):
        pass

    class Menu(Widget):
        pass

    class Canvas(Widget):
        def create_line(self, *coords, **k):
            pts = " ".join("%s,%s" % (coords[i], coords[i + 1]) for i in range(0, len(coords) - 1, 2))
            fill = _css_color(k.get("fill") or k.get("fillcolor") or "#000") or "#000"
            w = k.get("width", 1)
            self.canvas_items.append('<polyline fill="none" stroke="%s" stroke-width="%s" points="%s"/>' % (fill, w, pts))
            return len(self.canvas_items)

        def create_rectangle(self, x1, y1, x2, y2, **k):
            fill = _css_color(k.get("fill")) or "none"
            stroke = _css_color(k.get("outline") or "#000") or "#000"
            self.canvas_items.append('<rect x="%s" y="%s" width="%s" height="%s" fill="%s" stroke="%s"/>' % (min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1), fill, stroke))
            return len(self.canvas_items)

        def create_oval(self, x1, y1, x2, y2, **k):
            fill = _css_color(k.get("fill")) or "none"
            stroke = _css_color(k.get("outline") or "#000") or "#000"
            cx, cy = (x1 + x2) / 2.0, (y1 + y2) / 2.0
            rx, ry = abs(x2 - x1) / 2.0, abs(y2 - y1) / 2.0
            self.canvas_items.append('<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="%s" stroke="%s"/>' % (cx, cy, rx, ry, fill, stroke))
            return len(self.canvas_items)

        def create_text(self, x, y, **k):
            fill = _css_color(k.get("fill") or "#000") or "#000"
            t = html.escape(str(k.get("text", "")))
            self.canvas_items.append('<text x="%s" y="%s" fill="%s">%s</text>' % (x, y, fill, t))
            return len(self.canvas_items)

        def create_arc(self, x1, y1, x2, y2, **k):
            return self.create_oval(x1, y1, x2, y2, **k)

        def delete(self, *a):
            self.canvas_items = []

    def showinfo(title, message, **kwargs):
        dialog["html"] = '<div style="position:fixed;inset:0;background:#0006;display:grid;place-items:center;padding:20px;z-index:9"><div style="background:white;color:#20212a;border-radius:14px;padding:20px;max-width:420px;width:100%;box-shadow:0 8px 32px #0004"><h3>%s</h3><p>%s</p><button type="button" onclick="window.Stroka&&Stroka.onUiEvent(\'d\')">OK</button></div></div>' % (html.escape(str(title)), html.escape(str(message)))
        _redraw_root()

    def showwarning(title, message, **kwargs):
        return showinfo(title, message, **kwargs)

    def showerror(title, message, **kwargs):
        return showinfo(title, message, **kwargs)

    def askyesno(title, message, **kwargs):
        showinfo(title, message, **kwargs)
        return False

    def askstring(title, prompt, **kwargs):
        showinfo(title, prompt, **kwargs)
        return ""

    def _wait(timeout_ms):
        if _callback is None:
            time.sleep(max(0, timeout_ms) / 1000.0)
            return ""
        try:
            return _callback.waitUiEvent(int(max(1, timeout_ms)))
        except TypeError:
            try:
                return _callback.waitUiEvent()
            except Exception:
                return ""
        except Exception:
            return ""

    def _redraw(root):
        if _callback is None:
            return
        try:
            _callback.showPreview(root._page())
        except Exception:
            pass

    def _redraw_root():
        for w in list(_widgets.values()):
            if isinstance(w, Tk):
                _redraw(w)
                return

    def _flush_after():
        now = time.time()
        due = [it for it in _after if it[3] and it[0] <= now]
        for it in due:
            it[3] = False
            fn, args = it[1], it[2]
            if callable(fn):
                try:
                    fn(*args)
                except Exception as e:
                    print("Ошибка after:", e)

    def _next_timeout_ms():
        live = [it[0] for it in _after if it[3]]
        if not live:
            return 200
        dt = min(live) - time.time()
        return max(1, min(200, int(dt * 1000)))

    def _handle(root, event, dialog):
        if event == "d":
            dialog["html"] = ""
            return
        kind, _, payload = event.partition(":")
        widget_id, sep, value = payload.partition("|")
        try:
            widget = _widgets.get(int(widget_id))
        except Exception:
            widget = None
        if widget is None:
            return
        if kind == "i":
            widget.value = unquote(value) if sep else ""
            if widget.variable is not None:
                widget.variable.set(widget.value)
        elif kind == "c" and callable(widget.command):
            try:
                widget.command()
            except Exception as error:
                print("Ошибка обработчика кнопки:", error)

    const = {
        "END": "end", "INSERT": "insert", "CURRENT": "current", "ANCHOR": "anchor",
        "LEFT": "left", "RIGHT": "right", "TOP": "top", "BOTTOM": "bottom",
        "BOTH": "both", "X": "x", "Y": "y", "NONE": "none",
        "YES": True, "NO": False, "TRUE": True, "FALSE": False,
        "DISABLED": "disabled", "NORMAL": "normal", "ACTIVE": "active", "HIDDEN": "hidden",
        "WORD": "word", "CHAR": "char",
        "HORIZONTAL": "horizontal", "VERTICAL": "vertical",
        "SINGLE": "single", "BROWSE": "browse", "MULTIPLE": "multiple", "EXTENDED": "extended",
        "W": "w", "E": "e", "N": "n", "S": "s", "NW": "nw", "NE": "ne", "SW": "sw", "SE": "se", "NSEW": "nsew",
        "RAISED": "raised", "SUNKEN": "sunken", "GROOVE": "groove", "RIDGE": "ridge", "FLAT": "flat", "SOLID": "solid",
        "CENTER": "center",
    }
    names = {
        "Tk": Tk, "Toplevel": Toplevel, "Frame": Frame, "LabelFrame": Labelframe, "Labelframe": Labelframe,
        "Label": Label, "Entry": Entry, "Text": Text, "Button": Button,
        "Checkbutton": Checkbutton, "Radiobutton": Radiobutton, "Listbox": Listbox,
        "Scale": Scale, "Spinbox": Spinbox, "Menu": Menu, "Canvas": Canvas,
        "StringVar": StringVar, "IntVar": IntVar, "BooleanVar": BooleanVar, "DoubleVar": DoubleVar,
        "Variable": Variable,
        "ttk": ttk, "messagebox": messagebox, "constants": constants,
    }
    names.update(const)
    public = list(names.keys()) + list(const.keys())
    for k, v in names.items():
        setattr(tk, k, v)
        setattr(constants, k, v)
    ttk.Frame, ttk.LabelFrame, ttk.Labelframe = Frame, Labelframe, Labelframe
    ttk.Label, ttk.Entry, ttk.Button = Label, Entry, Button
    ttk.Checkbutton, ttk.Radiobutton, ttk.Combobox, ttk.Separator = Checkbutton, Radiobutton, Listbox, Frame
    ttk.Style = type("Style", (), {"configure": lambda *a, **k: None, "theme_use": lambda *a, **k: None})
    messagebox.showinfo = showinfo
    messagebox.showwarning = showwarning
    messagebox.showerror = showerror
    messagebox.askyesno = askyesno
    messagebox.askstring = askstring
    tk.__all__ = public
    sys.modules["tkinter"] = tk
    sys.modules["tkinter.ttk"] = ttk
    sys.modules["tkinter.messagebox"] = messagebox
    sys.modules["tkinter.constants"] = constants
    sys.modules["_tkinter"] = types.ModuleType("_tkinter")
    return tk
