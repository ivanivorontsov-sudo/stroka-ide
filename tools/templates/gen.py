#!/usr/bin/env python3
"""Генерирует app/.../core/Templates.kt из папок tools/templates/NN-*/ (meta.txt + файлы)."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "..", "app/src/main/java/org/ivanivorontsov/stroka/core/Templates.kt")

def kt(s):
    r = []
    for ch in s:
        if ch == "\\": r.append("\\\\")
        elif ch == '"': r.append('\\"')
        elif ch == "\n": r.append("\\n")
        elif ch == "\t": r.append("\\t")
        elif ch == "$": r.append("\\$")
        else: r.append(ch)
    return '"' + "".join(r) + '"'

items = []
for d in sorted(os.listdir(here)):
    p = os.path.join(here, d)
    if not os.path.isdir(p): continue
    meta = dict(l.split("=", 1) for l in open(os.path.join(p, "meta.txt"), encoding="utf8").read().splitlines() if "=" in l)
    files = [f for f in sorted(os.listdir(p)) if f != "meta.txt"]
    main = meta["main"]
    assert main in files, (d, main)
    files.remove(main); files.insert(0, main)
    body = ",\n".join("                %s to %s" % (kt(f), kt(open(os.path.join(p, f), encoding="utf8").read())) for f in files)
    items.append('        Template(\n            %s, %s, linkedMapOf(\n%s\n            ), %s\n        )' % (kt(meta["id"]), kt(meta["title"]), body, kt(main)))

src = ("package org.ivanivorontsov.stroka.core\n\n"
       "// Файл создан tools/templates/gen.py из папок tools/templates. Правьте примеры там.\n\n"
       "data class Template(val id: String, val title: String, val files: Map<String, String>, val main: String)\n\n"
       "object Templates {\n    val all: List<Template> = listOf(\n" + ",\n".join(items) + "\n    )\n}\n")
open(out, "w", encoding="utf8").write(src)
print("templates:", len(items))
