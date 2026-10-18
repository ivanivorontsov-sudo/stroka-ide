package org.ivanivorontsov.stroka.core

data class Template(val id: String, val title: String, val files: Map<String, String>, val main: String)

object Templates {
    val all: List<Template> = listOf(
        Template(
            "python", "Python: программа", mapOf(
                "main.py" to "def main():\n    name = input(\"Как тебя зовут? \")\n    print(f\"Привет, {name}!\")\n    for i in range(1, 6):\n        print(i, \"в квадрате =\", i * i)\n\n\nif __name__ == \"__main__\":\n    main()\n"
            ), "main.py"
        ),
        Template(
            "web", "Сайт: HTML + CSS + JS", mapOf(
                "index.html" to "<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n    <title>Моя страница</title>\n    <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n    <h1>Привет, мир!</h1>\n    <button id=\"btn\">Нажми меня</button>\n    <p id=\"out\"></p>\n    <script src=\"app.js\"></script>\n</body>\n</html>\n",
                "style.css" to "body {\n    font-family: sans-serif;\n    margin: 24px;\n    background: #f4f6fb;\n    color: #222;\n}\n\nbutton {\n    padding: 12px 20px;\n    font-size: 18px;\n    border-radius: 10px;\n}\n",
                "app.js" to "let count = 0;\nconst out = document.getElementById(\"out\");\ndocument.getElementById(\"btn\").addEventListener(\"click\", () => {\n    count++;\n    out.textContent = `Нажатий: \${count}`;\n    console.log(\"click\", count);\n});\n"
            ), "index.html"
        ),
        Template(
            "js", "JavaScript: скрипт", mapOf(
                "main.js" to "function fib(n) {\n    return n < 2 ? n : fib(n - 1) + fib(n - 2);\n}\n\nfor (let i = 0; i < 10; i++) {\n    console.log(`fib(\${i}) = \${fib(i)}`);\n}\n"
            ), "main.js"
        ),
        Template(
            "kotlin", "Kotlin: файл", mapOf(
                "Main.kt" to "fun main() {\n    val names = listOf(\"Аня\", \"Борис\", \"Вика\")\n    for (name in names) {\n        println(\"Привет, \$name!\")\n    }\n}\n"
            ), "Main.kt"
        ),
        Template(
            "c", "C: программа", mapOf(
                "main.c" to "#include <stdio.h>\n\nint main(void) {\n    for (int i = 1; i <= 5; i++) {\n        printf(\"%d\\n\", i * i);\n    }\n    return 0;\n}\n"
            ), "main.c"
        ),
        Template(
            "gdscript", "Godot: GDScript", mapOf(
                "player.gd" to "extends CharacterBody2D\n\n@export var speed: float = 300.0\n\nfunc _physics_process(delta: float) -> void:\n    var dir := Input.get_vector(\"ui_left\", \"ui_right\", \"ui_up\", \"ui_down\")\n    velocity = dir * speed\n    move_and_slide()\n"
            ), "player.gd"
        ),
        Template(
            "atari", "Atari 2600: 6502 asm", mapOf(
                "game.asm" to "    processor 6502\n    include \"vcs.h\"\n\n    seg code\n    org \$F000\n\nReset:\n    sei\n    cld\n    ldx #\$FF\n    txs\n    lda #0\nClear:\n    sta 0,x\n    dex\n    bne Clear\n\nFrame:\n    lda #2\n    sta VSYNC\n    sta WSYNC\n    sta WSYNC\n    sta WSYNC\n    lda #0\n    sta VSYNC\n    ldx #37\nVBlank:\n    sta WSYNC\n    dex\n    bne VBlank\n    lda #0\n    sta VBLANK\n    ldx #192\nKernel:\n    stx COLUBK\n    sta WSYNC\n    dex\n    bne Kernel\n    lda #2\n    sta VBLANK\n    ldx #30\nOverscan:\n    sta WSYNC\n    dex\n    bne Overscan\n    jmp Frame\n\n    org \$FFFC\n    .word Reset\n    .word Reset\n"
            ), "game.asm"
        ),
        Template(
            "md", "Заметка Markdown", mapOf(
                "README.md" to "# Мой проект\n\nКороткое описание.\n\n## Задачи\n\n- [ ] Первая задача\n- [ ] Вторая задача\n\n```python\nprint(\"пример кода\")\n```\n"
            ), "README.md"
        )
    )
}
