import tkinter as tk

root = tk.Tk()
root.title("Калькулятор")
expr = []
display = tk.Entry(root, font=("sans-serif", 22))
display.pack(fill=tk.X, padx=8, pady=8)

def show():
    display.delete(0, tk.END)
    display.insert(0, "".join(expr))

def press(s):
    expr.append(s)
    show()

def equal():
    try:
        v = str(eval("".join(expr), {"__builtins__": {}}, {}))
        expr.clear()
        expr.append(v)
        show()
    except Exception:
        display.delete(0, tk.END)
        display.insert(0, "ошибка")

def clear():
    expr.clear()
    show()

rows = (("7", "8", "9", "/"), ("4", "5", "6", "*"), ("1", "2", "3", "-"), ("0", ".", "=", "+"))
for row in rows:
    bar = tk.Frame(root)
    bar.pack(fill=tk.X)
    for lab in row:
        def cmd(x=lab):
            if x == "=":
                equal()
            else:
                press(x)
        tk.Button(bar, text=lab, width=4, command=cmd).pack(side=tk.LEFT, expand=True, fill=tk.X)
tk.Button(root, text="C", command=clear).pack(fill=tk.X, padx=8, pady=8)
root.mainloop()
