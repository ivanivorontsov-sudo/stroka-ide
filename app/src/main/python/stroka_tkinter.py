"""Tkinter-compatible responsive HTML preview for Android."""
import html
import sys
import types
from urllib.parse import unquote

_callback = None
_widgets = {}
_next_id = 0


def install(callback):
    global _callback
    _callback = callback
    tk = types.ModuleType("tkinter")
    tk.__path__ = []
    ttk = types.ModuleType("tkinter.ttk")
    messagebox = types.ModuleType("tkinter.messagebox")

    class Variable:
        def __init__(self, master=None, value="", **kwargs): self.value = value
        def get(self): return self.value
        def set(self, value): self.value = value
        def __str__(self): return str(self.value)

    class Widget:
        def __init__(self, master=None, **kwargs):
            global _next_id
            _next_id += 1
            self.widget_id = _next_id
            _widgets[self.widget_id] = self
            self.master = master
            self.kwargs = kwargs
            self.children = []
            self.text = kwargs.get("text", "")
            variable = kwargs.get("textvariable")
            self.variable = variable
            self.value = variable.get() if variable else kwargs.get("value", "")
            self.command = kwargs.get("command")
            self.options = kwargs.get("values", [])
            self.kind = kwargs.get("show", "")
            if master is not None and hasattr(master, "children"): master.children.append(self)
        def pack(self, *a, **k): return self
        def grid(self, *a, **k): return self
        def place(self, *a, **k): return self
        def pack_forget(self): return self
        def grid_forget(self): return self
        def configure(self, **kwargs):
            self.kwargs.update(kwargs)
            self.text = kwargs.get("text", self.text)
            return self
        config = configure
        def bind(self, *a, **k): return self
        def focus_set(self): return self
        def insert(self, index, value): self.value = str(self.value) + str(value)
        def delete(self, *a): self.value = ""
        def get(self, *a): return str(self.value)
        def set(self, value): self.value = value
        def _render(self):
            children = "".join(x._render() for x in self.children)
            label_text = self.variable.get() if isinstance(self, Label) and self.variable is not None else self.text
            esc = html.escape(str(label_text))
            wid = str(self.widget_id)
            if isinstance(self, Label): return '<div class="label">' + esc + '</div>' + children
            if isinstance(self, Entry):
                return "<input class='entry' data-wid='" + wid + "' oninput=\"window.Stroka&&Stroka.onUiEvent('i:" + wid + "|'+encodeURIComponent(this.value))\" placeholder='" + esc + "' value='" + html.escape(str(self.value)) + "' type='" + ("password" if self.kind else "text") + "'>" + children
            if isinstance(self, Text):
                return "<textarea class='entry' data-wid='" + wid + "' oninput=\"window.Stroka&&Stroka.onUiEvent('i:" + wid + "|'+encodeURIComponent(this.value))\" rows='" + str(self.kwargs.get("height", 4)) + "'>" + html.escape(str(self.value)) + "</textarea>" + children
            if isinstance(self, Button):
                return "<button type='button' onclick=\"window.Stroka&&Stroka.onUiEvent('c:" + wid + "')\">" + esc + "</button>" + children
            if isinstance(self, Checkbutton): return '<label class="check"><input type="checkbox"> ' + esc + '</label>' + children
            if isinstance(self, Radiobutton): return '<label class="check"><input type="radio" name="choice"> ' + esc + '</label>' + children
            if isinstance(self, Listbox):
                return '<select class="entry">' + "".join('<option>' + html.escape(str(x)) + '</option>' for x in self.options) + '</select>' + children
            if isinstance(self, (Frame, Labelframe)): return '<section>' + (('<h3>' + esc + '</h3>') if esc else '') + children + '</section>'
            if isinstance(self, (Scale, Spinbox)): return '<input class="entry" type="number" value="' + html.escape(str(self.value)) + '">' + children
            return '<div>' + esc + children + '</div>'

    class Tk(Widget):
        def __init__(self, *a, **k):
            super().__init__(None, **k)
            self.window_title = "Программа"
        def title(self, value): self.window_title = str(value)
        def geometry(self, value): pass
        def resizable(self, *a): pass
        def minsize(self, *a): pass
        def maxsize(self, *a): pass
        def protocol(self, *a): pass
        def _page(self):
            body = "".join(x._render() for x in self.children)
            return """<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8">
<style>*{box-sizing:border-box}body{margin:0;padding:18px;background:#f5f5f8;color:#20212a;font:16px system-ui}main{max-width:560px;margin:4vh auto;background:white;border-radius:18px;padding:22px;box-shadow:0 8px 32px #0002}h2{margin:0 0 18px;font-size:22px}.label{margin:10px 0 6px}.entry{display:block;width:100%;padding:12px;margin:8px 0 14px;border:1px solid #b8bdc9;border-radius:10px;font:inherit}textarea{min-height:100px}button{padding:12px 18px;margin:6px 6px 6px 0;border:0;border-radius:10px;background:#dce5ff;color:#172d63;font:inherit}.check{display:block;margin:12px 0}section{margin:12px 0;padding:12px;border:1px solid #e1e3e9;border-radius:12px}</style>
<main><h2>""" + html.escape(self.window_title) + """</h2>""" + body + """</main></html>"""
        def mainloop(self):
            if _callback is None: return
            _callback.showPreview(self._page())
            while True:
                event = _callback.waitUiEvent()
                if event == "__stop__": break
                if not event: continue
                kind, _, payload = event.partition(":")
                widget_id, sep, value = payload.partition("|")
                try: widget = _widgets.get(int(widget_id))
                except Exception: widget = None
                if widget is None: continue
                if kind == "i":
                    widget.value = unquote(value) if sep else ""
                    if widget.variable is not None: widget.variable.set(widget.value)
                elif kind == "c" and callable(widget.command):
                    try: widget.command()
                    except Exception as error: print("Ошибка обработчика кнопки:", error)
                _callback.showPreview(self._page())
        def destroy(self): pass
        def quit(self):
            if _callback is not None: _callback.postUiEvent("__stop__")

    class Toplevel(Tk): pass
    class Frame(Widget): pass
    class Labelframe(Widget): pass
    class Label(Widget): pass
    class Entry(Widget): pass
    class Text(Widget): pass
    class Button(Widget): pass
    class Checkbutton(Widget): pass
    class Radiobutton(Widget): pass
    class Listbox(Widget): pass
    class Scale(Widget): pass
    class Spinbox(Widget): pass
    class Menu(Widget): pass

    def showinfo(title, message, **kwargs):
        if _callback is not None:
            page = '<html><meta name="viewport" content="width=device-width,initial-scale=1"><body style="font:16px system-ui;padding:24px"><h2>' + html.escape(str(title)) + '</h2><p>' + html.escape(str(message)) + '</p></body></html>'
            _callback.showPreview(page)
    def showwarning(title, message, **kwargs): return showinfo(title, message, **kwargs)
    def showerror(title, message, **kwargs): return showinfo(title, message, **kwargs)
    def askyesno(title, message, **kwargs): showinfo(title, message, **kwargs); return False
    def askstring(title, prompt, **kwargs): showinfo(title, prompt, **kwargs); return ""

    messagebox.showinfo, messagebox.showwarning = showinfo, showwarning
    messagebox.showerror, messagebox.askyesno, messagebox.askstring = showerror, askyesno, askstring
    for name, value in {
        "Tk": Tk, "Toplevel": Toplevel, "Frame": Frame, "LabelFrame": Labelframe,
        "Label": Label, "Entry": Entry, "Text": Text, "Button": Button,
        "Checkbutton": Checkbutton, "Radiobutton": Radiobutton, "Listbox": Listbox,
        "Scale": Scale, "Spinbox": Spinbox, "Menu": Menu, "StringVar": Variable,
        "IntVar": Variable, "BooleanVar": Variable, "DoubleVar": Variable,
        "END": "end", "LEFT": "left", "RIGHT": "right", "TOP": "top", "BOTTOM": "bottom",
        "BOTH": "both", "X": "x", "Y": "y", "YES": True, "NO": False,
        "DISABLED": "disabled", "NORMAL": "normal", "WORD": "word"
    }.items(): setattr(tk, name, value)
    ttk.Frame, ttk.LabelFrame, ttk.Label, ttk.Entry, ttk.Button = Frame, Labelframe, Label, Entry, Button
    ttk.Checkbutton, ttk.Radiobutton, ttk.Combobox, ttk.Separator = Checkbutton, Radiobutton, Listbox, Frame
    tk.ttk, tk.messagebox = ttk, messagebox
    sys.modules["tkinter"] = tk
    sys.modules["tkinter.ttk"] = ttk
    sys.modules["tkinter.messagebox"] = messagebox
