"""Small Tkinter-compatible UI shim for Android preview in Stroka.
It renders common Tk widgets as responsive HTML instead of importing desktop Tcl/Tk.
"""
import html
import sys
import types

_callback = None


def install(callback):
    global _callback
    _callback = callback
    tk = types.ModuleType("tkinter")
    ttk = types.ModuleType("tkinter.ttk")
    messagebox = types.ModuleType("tkinter.messagebox")

    class Variable:
        def __init__(self, master=None, value="", **kwargs): self.value = value
        def get(self): return self.value
        def set(self, value): self.value = value
        def __str__(self): return str(self.value)

    class Widget:
        tag = "div"
        def __init__(self, master=None, **kwargs):
            self.master = master
            self.kwargs = kwargs
            self.children = []
            self.text = kwargs.get("text", "")
            self.value = kwargs.get("textvariable").get() if kwargs.get("textvariable") else kwargs.get("value", "")
            self.command = kwargs.get("command")
            self.options = kwargs.get("values", [])
            self.kind = kwargs.get("show", "")
            self.state = kwargs.get("state", "normal")
            if master is not None and hasattr(master, "children"): master.children.append(self)
        def pack(self, *a, **k): return self
        def grid(self, *a, **k): return self
        def place(self, *a, **k): return self
        def pack_forget(self): return self
        def grid_forget(self): return self
        def configure(self, **kwargs): self.kwargs.update(kwargs); self.text = kwargs.get("text", self.text); return self
        config = configure
        def bind(self, *a, **k): return self
        def focus_set(self): return self
        def insert(self, index, value): self.value = str(self.value) + str(value)
        def delete(self, *a): self.value = ""
        def get(self): return str(self.value)
        def set(self, value): self.value = value
        def _render(self):
            children = "".join(x._render() for x in self.children)
            esc = html.escape(str(self.text))
            if isinstance(self, Label): return '<div class="label">' + esc + '</div>' + children
            if isinstance(self, Entry): return '<input class="entry" placeholder="' + esc + '" value="' + html.escape(str(self.value)) + '" type="' + ("password" if self.kind else "text") + '">' + children
            if isinstance(self, Text): return '<textarea class="entry" rows="' + str(self.kwargs.get("height", 4)) + '">' + html.escape(str(self.value)) + '</textarea>' + children
            if isinstance(self, Button): return '<button type="button" onclick="alert(' + repr(str(self.text)).replace("'", '"') + ')">' + esc + '</button>' + children
            if isinstance(self, Checkbutton): return '<label class="check"><input type="checkbox"> ' + esc + '</label>' + children
            if isinstance(self, Radiobutton): return '<label class="check"><input type="radio" name="choice"> ' + esc + '</label>' + children
            if isinstance(self, Listbox): return '<select class="entry">' + "".join('<option>' + html.escape(str(x)) + '</option>' for x in self.options) + '</select>' + children
            if isinstance(self, (Frame, Labelframe)): return '<section>' + (('<h3>' + esc + '</h3>') if esc else '') + children + '</section>'
            if isinstance(self, (Scale, Spinbox)): return '<input class="entry" type="number" value="' + html.escape(str(self.value)) + '">' + children
            return '<div>' + esc + children + '</div>'

    class Tk(Widget):
        def __init__(self, *a, **k): super().__init__(None, **k); self.window_title = "Программа"; self.geometry_value = ""
        def title(self, value): self.window_title = str(value)
        def geometry(self, value): self.geometry_value = str(value)
        def resizable(self, *a): pass
        def minsize(self, *a): pass
        def maxsize(self, *a): pass
        def protocol(self, *a): pass
        def mainloop(self):
            body = "".join(x._render() for x in self.children)
            page = """<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8">
<style>*{box-sizing:border-box}body{margin:0;padding:18px;background:#f5f5f8;color:#20212a;font:16px system-ui}main{max-width:560px;margin:4vh auto;background:white;border-radius:18px;padding:22px;box-shadow:0 8px 32px #0002}h2{margin:0 0 18px;font-size:22px}.label{margin:10px 0 6px}.entry{display:block;width:100%;padding:12px;margin:8px 0 14px;border:1px solid #b8bdc9;border-radius:10px;font:inherit}textarea{min-height:100px}button{padding:12px 18px;margin:6px 6px 6px 0;border:0;border-radius:10px;background:#dce5ff;color:#172d63;font:inherit}.check{display:block;margin:12px 0}section{margin:12px 0;padding:12px;border:1px solid #e1e3e9;border-radius:12px}</style>
<main><h2>""" + html.escape(self.window_title) + """</h2>""" + body + """</main></html>"""
            if _callback is not None: _callback.showPreview(page)
        def destroy(self): pass
        def quit(self): pass

    class Toplevel(Tk):
        pass
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

    def askstring(title, prompt, **kwargs): return ""
    def showinfo(title, message, **kwargs): return None
    def showwarning(title, message, **kwargs): return None
    def showerror(title, message, **kwargs): return None
    def askyesno(title, message, **kwargs): return False
    messagebox.askstring = askstring
    messagebox.showinfo = showinfo
    messagebox.showwarning = showwarning
    messagebox.showerror = showerror
    messagebox.askyesno = askyesno

    for name, value in {
        "Tk": Tk, "Toplevel": Toplevel, "Frame": Frame, "LabelFrame": Labelframe,
        "Label": Label, "Entry": Entry, "Text": Text, "Button": Button,
        "Checkbutton": Checkbutton, "Radiobutton": Radiobutton, "Listbox": Listbox,
        "Scale": Scale, "Spinbox": Spinbox, "Menu": Menu, "StringVar": Variable,
        "IntVar": Variable, "BooleanVar": Variable, "DoubleVar": Variable,
        "END": "end", "LEFT": "left", "RIGHT": "right", "TOP": "top", "BOTTOM": "bottom",
        "BOTH": "both", "X": "x", "Y": "y", "YES": True, "NO": False, "DISABLED": "disabled",
        "NORMAL": "normal", "WORD": "word"
    }.items(): setattr(tk, name, value)
    ttk.Frame, ttk.LabelFrame, ttk.Label, ttk.Entry, ttk.Button = Frame, Labelframe, Label, Entry, Button
    ttk.Checkbutton, ttk.Radiobutton, ttk.Combobox, ttk.Separator = Checkbutton, Radiobutton, Listbox, Frame
    tk.ttk = ttk
    tk.messagebox = messagebox
    sys.modules["tkinter"] = tk
    sys.modules["tkinter.ttk"] = ttk
    sys.modules["tkinter.messagebox"] = messagebox
