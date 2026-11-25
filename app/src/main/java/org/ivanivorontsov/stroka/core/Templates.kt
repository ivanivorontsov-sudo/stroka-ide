package org.ivanivorontsov.stroka.core

// Файл создан tools/templates/gen.py из папок tools/templates. Правьте примеры там.

data class Template(val id: String, val title: String, val files: Map<String, String>, val main: String)

object Templates {
    val all: List<Template> = listOf(
        Template(
            "ponyatny-start", "Понятный: первая программа", linkedMapOf(
                "привет.pon" to "# Первая программа на «Понятном».\n# Нажмите «Старт» и ответьте на вопрос в окне ввода.\n\n# спроси() показывает окно ввода и возвращает ответ\nпусть имя = спроси(\"Как тебя зовут?\")\nпиши \"Привет, \" + имя + \"!\"\n\n# Функция получает число и возвращает его квадрат\nфункция квадрат(н)\n    верни н * н\nконец\n\n# Цикл: н принимает значения 1, 2, 3, 4, 5\nдля н от 1 до 5\n    пиши н, \"в квадрате =\", квадрат(н)\nконец\n\n# Список чисел и встроенные функции сумма() и длина()\nпусть оценки = [5, 4, 5, 3]\nпиши \"Средняя оценка:\", округли(сумма(оценки) / длина(оценки), 2)\n"
            ), "привет.pon"
        ),
        Template(
            "ponyatny-calc", "Понятный: калькулятор с кнопками", linkedMapOf(
                "калькулятор.pon" to "# Калькулятор на «Понятном».\n# Если в программе есть функции сложить, вычесть, умножить и разделить,\n# Строка сама рисует окно с двумя полями и кнопками «+ − × ÷».\n# Нажмите «Старт» или соберите APK/EXE — получится настоящее приложение.\n\nфункция сложить(а, б)\n    верни а + б\nконец\n\nфункция вычесть(а, б)\n    верни а - б\nконец\n\nфункция умножить(а, б)\n    верни а * б\nконец\n\nфункция разделить(а, б)\n    # На ноль делить нельзя — вернём понятное сообщение вместо ошибки\n    если б == 0 то\n        верни \"Деление на ноль\"\n    конец\n    верни а / б\nконец\n"
            ), "калькулятор.pon"
        ),
        Template(
            "ponyatny-game", "Понятный: мини-игра «Поймай мяч»", linkedMapOf(
                "игра.pon" to "# Мини-игра: двигайте платформу стрелками ← → (или пальцем) и ловите мяч.\n# Работает в Строке, в собранном APK и в песочнице «Понятного».\n\nокно(\"Поймай мяч\", 320, 240)\n\nпусть х = 140          # левый край платформы\nпусть мяч_х = 160\nпусть мяч_у = 0\nпусть счёт = 0\n\n# Функция кадр рисует один кадр игры; дт — секунды с прошлого кадра\nфункция кадр(дт)\n    если клавиша(\"влево\") то\n        х = х - 200 * дт\n    конец\n    если клавиша(\"вправо\") то\n        х = х + 200 * дт\n    конец\n    если мышь()[\"нажата\"] то\n        х = мышь()[\"x\"] - 20\n    конец\n    мяч_у = мяч_у + 120 * дт\n    # Мяч коснулся платформы?\n    если пересекаются(х, 220, 40, 8, мяч_х - 6, мяч_у - 6, 12, 12) то\n        счёт = счёт + 1\n        мяч_у = 0\n        мяч_х = 20 + (счёт * 70) % 280\n    иначе если мяч_у > 240 то\n        мяч_у = 0\n    конец\n    залей(\"чёрный\")\n    прямоугольник(х, 220, 40, 8, \"белый\")\n    круг(мяч_х, мяч_у, 6, \"жёлтый\")\n    рисуй_текст(10, 10, \"Счёт: {счёт}\", \"зелёный\", 16)\nконец\n\n# каждый_кадр вызывает функцию примерно 60 раз в секунду\nкаждый_кадр(кадр)\n"
            ), "игра.pon"
        ),
        Template(
            "java-calc", "Java: калькулятор (APK)", linkedMapOf(
                "Калькулятор.java" to "// Калькулятор на Java для Android.\n// Меню → «Собрать APK» превращает этот файл в приложение прямо на телефоне.\npackage org.stroka.app.jcalc;\n\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.text.InputType;\nimport android.widget.Button;\nimport android.widget.EditText;\nimport android.widget.LinearLayout;\nimport android.widget.TextView;\n\n// Activity — один экран приложения. Android вызывает onCreate при запуске.\npublic class Main extends Activity {\n    private EditText fieldA, fieldB;\n    private TextView result;\n\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        // Элементы складываем сверху вниз в LinearLayout\n        LinearLayout root = new LinearLayout(this);\n        root.setOrientation(LinearLayout.VERTICAL);\n        int p = Math.round(16 * getResources().getDisplayMetrics().density);\n        root.setPadding(p, p, p, p);\n\n        fieldA = numberField(\"Первое число\");\n        fieldB = numberField(\"Второе число\");\n        root.addView(fieldA);\n        root.addView(fieldB);\n\n        LinearLayout keys = new LinearLayout(this);\n        keys.addView(opButton(\"+\", '+'));\n        keys.addView(opButton(\"−\", '-'));\n        keys.addView(opButton(\"×\", '*'));\n        keys.addView(opButton(\"÷\", '/'));\n        root.addView(keys);\n\n        result = new TextView(this);\n        result.setTextSize(24);\n        result.setText(\"Введите два числа\");\n        root.addView(result);\n        setContentView(root);\n    }\n\n    private EditText numberField(String hint) {\n        EditText e = new EditText(this);\n        e.setHint(hint);\n        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);\n        return e;\n    }\n\n    // Лямбда v -> calculate(op) вызывается при нажатии кнопки\n    private Button opButton(String label, char op) {\n        Button b = new Button(this);\n        b.setText(label);\n        b.setOnClickListener(v -> calculate(op));\n        return b;\n    }\n\n    private void calculate(char op) {\n        try {\n            double a = Double.parseDouble(fieldA.getText().toString().replace(',', '.'));\n            double b = Double.parseDouble(fieldB.getText().toString().replace(',', '.'));\n            if (op == '/' && b == 0) { result.setText(\"На ноль делить нельзя\"); return; }\n            double r = op == '+' ? a + b : op == '-' ? a - b : op == '*' ? a * b : a / b;\n            result.setText(\"= \" + (r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r)));\n        } catch (NumberFormatException e) {\n            result.setText(\"Введите два числа\");\n        }\n    }\n}\n"
            ), "Калькулятор.java"
        ),
        Template(
            "kotlin", "Kotlin: счётчик (APK)", linkedMapOf(
                "Main.kt" to "// Приложение-счётчик на Kotlin для Android.\n// Меню → «Собрать APK»: Строка скомпилирует Kotlin прямо на телефоне.\nimport android.app.Activity\nimport android.os.Bundle\nimport android.widget.Button\nimport android.widget.LinearLayout\nimport android.widget.TextView\n\n// Activity — один экран приложения\nclass Main : Activity() {\n    private var count = 0   // var — переменную можно менять\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val root = LinearLayout(this)\n        root.orientation = LinearLayout.VERTICAL\n        val pad = (16 * resources.displayMetrics.density).toInt()\n        root.setPadding(pad, pad, pad, pad)\n\n        val label = TextView(this)\n        label.textSize = 28f\n        label.text = \"Нажато: 0\"\n        root.addView(label)\n\n        // Лямбда в фигурных скобках выполняется при каждом нажатии\n        val plus = Button(this)\n        plus.text = \"+1\"\n        plus.setOnClickListener {\n            count++\n            label.text = \"Нажато: \$count\"   // \$count подставляет значение в строку\n        }\n        root.addView(plus)\n\n        val reset = Button(this)\n        reset.text = \"Сбросить\"\n        reset.setOnClickListener {\n            count = 0\n            label.text = \"Нажато: 0\"\n        }\n        root.addView(reset)\n        setContentView(root)\n    }\n}\n"
            ), "Main.kt"
        ),
        Template(
            "python", "Python: программа", linkedMapOf(
                "main.py" to "# Первая программа на Python. Нажмите «Старт».\n\n# input() спрашивает в консоли, print() печатает\nname = input(\"Как тебя зовут? \")\nprint(f\"Привет, {name}!\")   # f-строка подставляет значения в {}\n\n\ndef square(n):\n    \"\"\"Функция возвращает квадрат числа.\"\"\"\n    return n * n\n\n\n# range(1, 6) — числа от 1 до 5\nfor i in range(1, 6):\n    print(i, \"в квадрате =\", square(i))\n\n# Список и встроенные функции\nmarks = [5, 4, 5, 3]\nprint(\"Средняя оценка:\", round(sum(marks) / len(marks), 2))\n"
            ), "main.py"
        ),
        Template(
            "web", "Сайт: HTML + CSS + JS", linkedMapOf(
                "index.html" to "<!DOCTYPE html>\n<!-- Страница сайта. Нажмите «Старт», чтобы открыть её в просмотрщике. -->\n<html lang=\"ru\">\n<head>\n    <meta charset=\"utf-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n    <title>Мой сайт</title>\n    <!-- Оформление лежит в отдельном файле style.css -->\n    <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n    <h1>Привет, мир!</h1>\n    <button id=\"btn\">Нажми меня</button>\n    <p id=\"out\">Нажатий: 0</p>\n    <!-- Поведение страницы — в app.js -->\n    <script src=\"app.js\"></script>\n</body>\n</html>\n",
                "app.js" to "// Считаем нажатия на кнопку и показываем их на странице\nlet count = 0;\nconst out = document.getElementById(\"out\");\ndocument.getElementById(\"btn\").addEventListener(\"click\", () => {\n    count++;\n    out.textContent = `Нажатий: \${count}`;\n});\n",
                "style.css" to "/* Оформление страницы: шрифт, отступы, цвета */\nbody {\n    font-family: sans-serif;\n    margin: 24px;\n    background: #f4f6fb;\n    color: #222;\n}\n\nbutton {\n    padding: 12px 20px;\n    font-size: 18px;\n    border-radius: 10px;\n}\n"
            ), "index.html"
        ),
        Template(
            "js", "JavaScript: скрипт", linkedMapOf(
                "main.js" to "// Скрипт на JavaScript. Нажмите «Старт» — результат появится в консоли.\n\n// Функция: числа Фибоначчи (каждое — сумма двух предыдущих)\nfunction fib(n) {\n    let a = 0, b = 1;\n    for (let i = 0; i < n; i++) [a, b] = [b, a + b];\n    return a;\n}\n\nfor (let i = 0; i <= 10; i++) {\n    console.log(`fib(\${i}) = \${fib(i)}`);\n}\n\n// Массив и его методы\nconst marks = [5, 4, 5, 3];\nconst avg = marks.reduce((s, x) => s + x, 0) / marks.length;\nconsole.log(\"Средняя оценка:\", avg.toFixed(2));\n"
            ), "main.js"
        ),
        Template(
            "c", "C: программа (для компьютера)", linkedMapOf(
                "main.c" to "/* Программа на C. Строка подсвечивает C, но не компилирует его на телефоне:\n   соберите на компьютере командой  gcc main.c -o main  и запустите ./main */\n#include <stdio.h>\n\n/* Функция возвращает квадрат числа */\nint square(int n) {\n    return n * n;\n}\n\nint main(void) {\n    for (int i = 1; i <= 5; i++) {\n        printf(\"%d в квадрате = %d\\n\", i, square(i));\n    }\n    return 0;\n}\n"
            ), "main.c"
        ),
        Template(
            "gdscript", "Godot: скрипт игрока (GDScript)", linkedMapOf(
                "player.gd" to "# Скрипт для Godot 4: персонаж ходит стрелками.\n# В Godot создайте сцену с узлом CharacterBody2D и прикрепите этот скрипт.\nextends CharacterBody2D\n\n# @export — значение можно менять в инспекторе Godot\n@export var speed: float = 300.0\n\n# Вызывается каждый кадр физики; delta — время с прошлого кадра\nfunc _physics_process(delta: float) -> void:\n\tvar direction := Input.get_vector(\"ui_left\", \"ui_right\", \"ui_up\", \"ui_down\")\n\tvelocity = direction * speed\n\tmove_and_slide()\n"
            ), "player.gd"
        ),
        Template(
            "atari", "Atari 2600: радуга (ассемблер 6502)", linkedMapOf(
                "game.asm" to "; Atari 2600: рисует радугу на экране. Ассемблер 6502 (синтаксис DASM).\n; Соберите на компьютере:  dasm game.asm -f3 -ogame.bin  и откройте game.bin в эмуляторе Stella.\n    processor 6502\n\nVSYNC  = \$00        ; регистры видеочипа TIA\nVBLANK = \$01\nWSYNC  = \$02\nCOLUBK = \$09        ; цвет фона\n\n    seg code\n    org \$F000       ; картридж начинается с адреса \$F000\n\nReset:\n    sei             ; запретить прерывания\n    cld             ; обычная (не десятичная) арифметика\n    ldx #\$FF\n    txs             ; стек\n    lda #0\nClear:\n    sta 0,x         ; обнулить память и регистры\n    dex\n    bne Clear\n\nFrame:              ; один кадр телевизора\n    lda #2\n    sta VSYNC       ; 3 строки синхроимпульса\n    sta WSYNC\n    sta WSYNC\n    sta WSYNC\n    lda #0\n    sta VSYNC\n    ldx #37         ; 37 строк паузы сверху\nVBlank:\n    sta WSYNC\n    dex\n    bne VBlank\n    lda #0\n    sta VBLANK\n    ldx #192        ; 192 видимые строки: номер строки = цвет\nKernel:\n    stx COLUBK\n    sta WSYNC\n    dex\n    bne Kernel\n    lda #2\n    sta VBLANK\n    ldx #30         ; 30 строк снизу\nOverscan:\n    sta WSYNC\n    dex\n    bne Overscan\n    jmp Frame\n\n    org \$FFFC\n    .word Reset     ; адрес запуска\n    .word Reset\n"
            ), "game.asm"
        ),
        Template(
            "md", "Заметка Markdown", linkedMapOf(
                "README.md" to "# Мой проект\n\nMarkdown — простая разметка. Нажмите «Старт», чтобы увидеть оформленный текст.\n\n## Как писать\n\n- `#` в начале строки — заголовок\n- `-` — пункт списка, `- [ ]` — задача\n- `**жирный**` и `*курсив*`\n\n## Задачи\n\n- [ ] Первая задача\n- [x] Сделанная задача\n\n```python\nprint(\"блок кода\")\n```\n"
            ), "README.md"
        )
    )
}
