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
            "ponyatny", "Понятный: русская программа", mapOf(
                "main.pon" to "# Язык «Понятный» 2.5\nпусть имя = спроси(\"Как тебя зовут? \")\nпиши \"Привет, \" + имя + \"!\"\n\nподключи \"стд/математика.pon\" как мат\nпиши \"Факториал пяти: \" + строка(мат.факториал(5))\n"
            ), "main.pon"
        ),
        Template(
            "web", "Сайт: адаптивный премиум-шаблон", mapOf(
                "index.html" to """<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#111a34">
  <meta name="description" content="Современный сайт вашей компании или личного бренда">
  <title>Ваш бренд — цифровой опыт</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <header class="nav wrap">
    <a class="logo" href="#">YOUR<span>STUDIO</span></a>
    <nav><a href="#about">О нас</a><a href="#services">Возможности</a><a href="#contact">Контакты</a></nav>
    <a class="nav-cta" href="#contact">Обсудить проект ↗</a>
  </header>
  <main>
    <section class="hero wrap">
      <span class="eyebrow">✦ ВАША ИДЕЯ · НАШ ПОДХОД</span>
      <h1>Создаём цифровое, которое впечатляет.</h1>
      <p>Красивые, понятные и работающие решения для людей и бизнеса. Дизайн с характером. Технологии со смыслом.</p>
      <div class="actions"><a class="primary" href="#contact">Начать проект ↗</a><a class="secondary" href="#services">Узнать больше ↓</a></div>
      <div class="metrics"><div><b>01</b><small>Погружаемся в задачу</small></div><div><b>02</b><small>Продумываем детали</small></div><div><b>03</b><small>Создаём результат</small></div></div>
    </section>
    <section class="section wrap" id="about">
      <span class="kicker">МЫСЛИМ ШИРЕ ШАБЛОНОВ</span><h2>Хороший дизайн — это ощущение, которое остаётся.</h2>
      <p class="lead">Каждая деталь работает на общую идею: от первого впечатления до последнего клика. Расскажите здесь о вашем бизнесе, команде или личном бренде.</p>
      <div class="highlight"><div><span class="kicker">ВАШЕ ПРЕИМУЩЕСТВО</span><h2>Не просто красиво. Продуманно.</h2><p>Понятная структура, выразительная типографика и удобство на любом экране — от смартфона до большого монитора.</p></div><div class="art" aria-hidden="true"></div></div>
    </section>
    <section class="section wrap" id="services"><span class="kicker">ЧТО МЫ ДЕЛАЕМ</span><h2>Всё начинается с правильной идеи.</h2>
      <div class="cards"><article><i>✳</i><h3>Стратегия</h3><p>Разбираемся в целях и задачах, чтобы сайт был не только эффектным, но и полезным.</p></article><article><i>◈</i><h3>Визуальный стиль</h3><p>Создаём цельную систему: цвета, типографику, композицию и узнаваемые детали.</p></article><article><i>↗</i><h3>Разработка</h3><p>Превращаем концепцию в быстрый адаптивный сайт, который удобно использовать.</p></article></div>
    </section>
    <section class="section wrap"><blockquote>«Сильный проект начинается не с кнопки. Он начинается с ясной идеи и внимания к каждой мелочи.»<small>ВАША ФИЛОСОФИЯ · ВАШ БРЕНД</small></blockquote></section>
    <section class="contact wrap" id="contact"><span class="kicker">СЛЕДУЮЩИЙ ШАГ</span><h2>Есть идея? Давайте воплотим.</h2><p>Расскажите, что хотите создать. Вместе найдём лучший путь.</p><a class="primary" href="mailto:hello@example.com">Связаться с нами ↗</a></section>
  </main>
  <footer class="footer wrap"><b>YOURSTUDIO · 2026</b><span>Сделано с вниманием к деталям</span><a href="mailto:hello@example.com">hello@example.com</a></footer>
  <script src="app.js"></script>
</body>
</html>
""",
                "style.css" to """*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:#f8f9fd;color:#172033;font:16px/1.6 Inter,ui-sans-serif,system-ui,-apple-system,sans-serif}.wrap{width:min(1120px,calc(100% - 40px));margin-inline:auto}.nav{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:24px 0}.logo{font-weight:900;font-size:20px;letter-spacing:-1px;color:#172033;text-decoration:none}.logo span{color:#6d5dfc}.nav nav{display:flex;gap:24px}.nav nav a,.footer a{color:#65718a;text-decoration:none;font-size:13px;font-weight:650}.nav-cta,.primary{display:inline-flex;padding:12px 19px;border-radius:999px;background:#24243d;color:#fff;text-decoration:none;font-size:13px;font-weight:750;box-shadow:0 9px 24px #24243d22}.hero{position:relative;overflow:hidden;border-radius:30px;padding:clamp(30px,7vw,78px);background:radial-gradient(circle at 84% 18%,#a8a0ff88,transparent 28%),radial-gradient(circle at 10% 100%,#79dfd288,transparent 32%),linear-gradient(130deg,#101a35,#283d68 58%,#514b8e);color:white;box-shadow:0 28px 70px #26365a28}.eyebrow,.kicker{font-size:11px;letter-spacing:2px;font-weight:800}.eyebrow{display:inline-block;border:1px solid #ffffff45;border-radius:999px;padding:8px 12px;color:#d6e4ff}.hero h1{max-width:760px;font-size:clamp(42px,8vw,82px);line-height:.99;letter-spacing:-4px;margin:24px 0 18px}.hero>p{max-width:580px;color:#d5def5;font-size:18px;line-height:1.75}.actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:28px}.primary{background:#c6f4df;color:#102b36}.secondary{color:white;text-decoration:none;padding:12px 18px;font-size:13px}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-width:680px;margin-top:48px}.metrics div{border-left:1px solid #ffffff45;padding-left:15px}.metrics b{display:block;font-size:24px}.metrics small{font-size:11px;color:#c4d1ed}.section{padding:clamp(42px,7vw,78px) 0}.kicker{color:#7167d8}.section h2,.contact h2{font-size:clamp(30px,5vw,48px);line-height:1.08;letter-spacing:-1.8px;margin:12px 0}.lead{max-width:650px;color:#68748a;line-height:1.8}.highlight{display:grid;grid-template-columns:1fr 1fr;gap:24px;align-items:center;margin-top:32px;padding:clamp(24px,5vw,48px);border-radius:26px;background:#f1f3fb}.highlight p,.cards p,.contact p{color:#68748a;line-height:1.8}.art{min-height:240px;border-radius:20px;background:radial-gradient(circle at 70% 30%,#c3b9ff,transparent 25%),linear-gradient(140deg,#c6f1e2,#9eabea 55%,#555a9a);position:relative;overflow:hidden}.art:after{content:'';position:absolute;inset:18%;border:1px solid #ffffffaa;border-radius:24px;transform:rotate(-12deg);box-shadow:0 0 0 18px #ffffff22}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:26px}.cards article{padding:24px;border:1px solid #e5e9f2;border-radius:20px;background:linear-gradient(145deg,#fff,#f8f9ff);box-shadow:0 12px 32px #1b2c4609}.cards i{display:grid;place-items:center;width:42px;height:42px;border-radius:14px;background:#eeedff;color:#6257d9;font-style:normal;font-size:20px}.cards h3{margin:18px 0 8px}blockquote{margin:0;padding:28px;border-radius:22px;background:#161f38;color:white;font-size:18px;line-height:1.7}blockquote small{display:block;color:#b7c3e0;margin-top:16px;font-size:12px}.contact{padding:clamp(30px,6vw,62px);border-radius:28px;background:linear-gradient(120deg,#d8f5e8,#e6e2ff);text-align:center}.contact .primary{margin-top:12px}.footer{display:flex;justify-content:space-between;gap:14px;flex-wrap:wrap;padding:24px 0;color:#7c879b;font-size:12px}@media(max-width:720px){.nav nav{display:none}.cards{grid-template-columns:1fr}.highlight{grid-template-columns:1fr}.hero h1{letter-spacing:-2.5px}.wrap{width:min(100% - 24px,1120px)}}
""",
                "app.js" to """// Здесь можно добавлять интерактивность сайта.
document.querySelectorAll('a[href^="#"]').forEach(link => {
  link.addEventListener('click', event => {
    const target = document.querySelector(link.getAttribute('href'));
    if (target) { event.preventDefault(); target.scrollIntoView({ behavior: 'smooth' }); }
  });
});
"""
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
