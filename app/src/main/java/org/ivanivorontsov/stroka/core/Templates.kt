package org.ivanivorontsov.stroka.core

data class Template(val id: String, val title: String, val files: Map<String, String>, val main: String)

object Templates {
    val all: List<Template> = listOf(
        Template(
            "ponyatny-calc", "Понятный: калькулятор", mapOf(
                "калькулятор.pon" to "функция сложить(а, б)\n    верни а + б\nконец\n\nфункция вычесть(а, б)\n    верни а - б\nконец\n\nфункция умножить(а, б)\n    верни а * б\nконец\n\nфункция разделить(а, б)\n    если б == 0 то\n        верни «Деление на ноль»\n    конец\n    верни а / б\nконец\n"
            ), "калькулятор.pon"
        ),
        Template(
            "java-calc", "Java: калькулятор", mapOf(
                "Калькулятор.java" to "package org.stroka.app.jcalc;\n\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.view.View;\nimport android.widget.Button;\nimport android.widget.EditText;\nimport android.widget.LinearLayout;\nimport android.widget.TextView;\nimport android.widget.Toast;\n\npublic class Main extends Activity {\n    private EditText fieldA;\n    private EditText fieldB;\n    private TextView result;\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        int p = Math.round(16 * getResources().getDisplayMetrics().density);\n        root.setPadding(p, p, p, p);\n\n        TextView title = new TextView(this);\n        title.setText(\"Калькулятор\");\n        title.setTextSize(22);\n        root.addView(title);\n\n        fieldA = new EditText(this);\n        fieldA.setHint(\"Первое число\");\n        fieldA.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);\n        root.addView(fieldA);\n\n        fieldB = new EditText(this);\n        fieldB.setHint(\"Второе число\");\n        fieldB.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);\n        root.addView(fieldB);\n\n        LinearLayout keys = new LinearLayout(this);\n        keys.setOrientation(LinearLayout.HORIZONTAL);\n        keys.addView(opButton(\"Сложить\", 1));\n        keys.addView(opButton(\"Вычесть\", 2));\n        keys.addView(opButton(\"Умножить\", 3));\n        keys.addView(opButton(\"Разделить\", 4));\n        root.addView(keys);\n\n        result = new TextView(this);\n        result.setTextSize(20);\n        result.setPadding(0, p, 0, 0);\n        result.setText(\"Результат\");\n        root.addView(result);\n        setContentView(root);\n    }\n\n    private Button opButton(String label, final int op) {\n        Button b = new Button(this);\n        b.setText(label);\n        b.setOnClickListener(v -> apply(op));\n        return b;\n    }\n\n    private void apply(int op) {\n        Double a = parse(fieldA.getText().toString());\n        Double b = parse(fieldB.getText().toString());\n        if (a == null || b == null) {\n            Toast.makeText(this, \"Введите два числа\", Toast.LENGTH_SHORT).show();\n            return;\n        }\n        if (op == 4 && b.doubleValue() == 0.0) {\n            result.setText(\"Деление на ноль\");\n            return;\n        }\n        double r;\n        if (op == 1) r = a.doubleValue() + b.doubleValue();\n        else if (op == 2) r = a.doubleValue() - b.doubleValue();\n        else if (op == 3) r = a.doubleValue() * b.doubleValue();\n        else r = a.doubleValue() / b.doubleValue();\n        if (r == Math.rint(r) && Math.abs(r) < 1e15) result.setText(String.valueOf((long) r));\n        else result.setText(String.valueOf(r));\n    }\n\n    private Double parse(String s) {\n        if (s == null) return null;\n        String t = s.trim().replace(',', '.');\n        if (t.length() == 0) return null;\n        try {\n            return Double.valueOf(t);\n        } catch (NumberFormatException e) {\n            return null;\n        }\n    }\n}\n"
            ), "Калькулятор.java"
        ),
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
                "Main.kt" to "import android.app.Activity\nimport android.os.Bundle\nimport android.widget.Button\nimport android.widget.LinearLayout\nimport android.widget.TextView\n\nclass Main : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val root = LinearLayout(this)\n        root.orientation = LinearLayout.VERTICAL\n        val pad = (16 * resources.displayMetrics.density).toInt()\n        root.setPadding(pad, pad, pad, pad)\n\n        val message = TextView(this)\n        message.text = \"Привет из Kotlin!\"\n        message.textSize = 24f\n        root.addView(message)\n\n        val button = Button(this)\n        button.text = \"Нажми меня\"\n        button.setOnClickListener { message.text = \"Kotlin работает! Кнопка нажата.\" }\n        root.addView(button)\n        setContentView(root)\n    }\n}\n
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
                "game.asm" to "    processor 6502\n\nVSYNC = \$00\nVBLANK = \$01\nWSYNC = \$02\nCOLUBK = \$09\n\n    seg code\n    org \$F000\n\nReset:\n    sei\n    cld\n    ldx #\$FF\n    txs\n    lda #0\nClear:\n    sta 0,x\n    dex\n    bne Clear\n\nFrame:\n    lda #2\n    sta VSYNC\n    sta WSYNC\n    sta WSYNC\n    sta WSYNC\n    lda #0\n    sta VSYNC\n    ldx #37\nVBlank:\n    sta WSYNC\n    dex\n    bne VBlank\n    lda #0\n    sta VBLANK\n    ldx #192\nKernel:\n    stx COLUBK\n    sta WSYNC\n    dex\n    bne Kernel\n    lda #2\n    sta VBLANK\n    ldx #30\nOverscan:\n    sta WSYNC\n    dex\n    bne Overscan\n    jmp Frame\n\n    org \$FFFC\n    .word Reset\n    .word Reset\n"
            ), "game.asm"
        ),
        Template(
            "md", "Заметка Markdown", mapOf(
                "README.md" to "# Мой проект\n\nКороткое описание.\n\n## Задачи\n\n- [ ] Первая задача\n- [ ] Вторая задача\n\n```python\nprint(\"пример кода\")\n```\n"
            ), "README.md"
        )
    )
}
