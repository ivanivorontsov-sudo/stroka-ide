package org.ivanivorontsov.stroka.core

/**
 * Turns natural-language requests for common code blocks into language-aware source snippets.
 * This is deliberately deterministic: it inserts known templates and never pretends to generate
 * arbitrary application logic or install dependencies.
 */
data class VoiceCodeBlock(val title: String, val language: Lang, val code: String)

object VoiceCodeTemplates {
    private enum class Intent { NAME_INPUT, WEB_FORM, WINDOW, HTML_PAGE, CSS_STYLES, FUNCTION, PROGRAM, LIBRARY }

    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}+#.-]+"), " ").replace(Regex("\\s+"), " ").trim()

    private val requestWords = listOf(
        "нужна", "нужен", "нужно", "хочу", "создай", "создать", "сделай", "сделать",
        "сгенерируй", "сгенерировать", "напиши", "написать", "добавь", "добавить", "вставь", "вставить",
        "программа", "часть кода", "кусок кода", "блок кода", "фрагмент кода", "сниппет",
        "need", "i need", "i want", "create", "make", "generate", "write", "add", "insert", "build",
        "program for", "piece of code", "code snippet", "block of code", "component"
    )

    private fun hasRequest(text: String): Boolean = requestWords.any { text.contains(it) }

    private fun languageFrom(text: String, fallback: Lang): Lang {
        return when {
            Regex("\\b(python|питон|питоне)\\b").containsMatchIn(text) -> Lang.PYTHON
            Regex("\\b(typescript|ts)\\b").containsMatchIn(text) -> Lang.TYPESCRIPT
            Regex("\\b(javascript|java script|джаваскрипт|\\bjs\\b)\\b").containsMatchIn(text) -> Lang.JAVASCRIPT
            Regex("\\b(html|хтмл)\\b").containsMatchIn(text) -> Lang.HTML
            Regex("\\b(css|цсс)\\b").containsMatchIn(text) -> Lang.CSS
            Regex("\\b(kotlin|котлин)\\b").containsMatchIn(text) -> Lang.KOTLIN
            Regex("\\b(java|джава)\\b").containsMatchIn(text) -> Lang.JAVA
            Regex("\\b(c\\+\\+|cpp)\\b").containsMatchIn(text) -> Lang.CPP
            Regex("\\b(c sharp|c#)\\b").containsMatchIn(text) -> Lang.C
            fallback != Lang.PLAIN -> fallback
            else -> Lang.PYTHON
        }
    }

    private fun detectIntent(text: String, lang: Lang): Intent? {
        val libraryNames = listOf(
            "requests", "numpy", "pandas", "flask", "fastapi", "tkinter", "math", "random", "json",
            "axios", "react", "express", "lodash", "date-fns"
        )
        if (text.contains("библиотек") || text.contains("library") || text.contains("пакет") ||
            text.contains("подключ") || text.contains("импорт") || text.contains("import ") ||
            libraryNames.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
        ) return Intent.LIBRARY

        if (lang == Lang.CSS || text.contains("css") || text.contains("стил") ||
            text.contains("оформлен") || text.contains("style") || text.contains("stylesheet")
        ) return Intent.CSS_STYLES

        if (text.contains("форм") || text.contains("form") || text.contains("регистрац") ||
            text.contains("обратн") && text.contains("связ") || text.contains("contact form") ||
            text.contains("login form") || text.contains("web form")
        ) return Intent.WEB_FORM

        if (text.contains("окн") || text.contains("диалог") || text.contains("модаль") ||
            text.contains("window") || text.contains("dialog") || text.contains("modal") ||
            text.contains("popup") || text.contains("попап")
        ) return Intent.WINDOW

        if (text.contains("имя") || text.contains("имени") || text.contains("name") ||
            text.contains("имя пользователя") || text.contains("user name") ||
            text.contains("username") || text.contains("get name") || text.contains("ask name")
        ) return Intent.NAME_INPUT

        if (text.contains("html") || text.contains("веб-страниц") || text.contains("веб страниц") ||
            text.contains("web page") || text.contains("website") || text.contains("страниц")
        ) return Intent.HTML_PAGE

        if (text.contains("функц") || text.contains("function") || text.contains("метод") || text.contains("method"))
            return Intent.FUNCTION

        if (text.contains("программ") || text.contains("program") || text.contains("код") ||
            text.contains("snippet") || text.contains("component")
        ) return Intent.PROGRAM
        return null
    }

    fun generate(utterance: String, preferredLanguage: Lang = Lang.PLAIN): VoiceCodeBlock? {
        val text = normalize(utterance)
        if (!hasRequest(text)) return null
        val language = languageFrom(text, preferredLanguage)
        val intent = detectIntent(text, language) ?: return null
        val code = when (intent) {
            Intent.NAME_INPUT -> nameInput(language)
            Intent.WEB_FORM -> webForm(language)
            Intent.WINDOW -> window(language)
            Intent.HTML_PAGE -> htmlPage(language)
            Intent.CSS_STYLES -> cssStyles(language)
            Intent.FUNCTION -> function(language)
            Intent.PROGRAM -> program(language)
            Intent.LIBRARY -> library(text, language)
        }
        return VoiceCodeBlock(title(intent), language, code.trimIndent().trim())
    }

    private fun title(intent: Intent): String = when (intent) {
        Intent.NAME_INPUT -> "Ввод имени"
        Intent.WEB_FORM -> "Веб-форма"
        Intent.WINDOW -> "Окно / диалог"
        Intent.HTML_PAGE -> "HTML-страница"
        Intent.CSS_STYLES -> "CSS-оформление"
        Intent.FUNCTION -> "Функция"
        Intent.PROGRAM -> "Старт программы"
        Intent.LIBRARY -> "Подключение библиотеки"
    }

    private fun nameInput(lang: Lang): String = when (lang) {
        Lang.PYTHON, Lang.PLAIN -> """
            name = input("Как вас зовут? ").strip()
            if name:
                print(f"Привет, {name}!")
        """
        Lang.JAVASCRIPT -> """
            const name = window.prompt("Как вас зовут?")?.trim() ?? "";
            if (name) {
                console.log("Привет, " + name + "!");
            }
        """
        Lang.TYPESCRIPT -> """
            const name: string = window.prompt("Как вас зовут?")?.trim() ?? "";
            if (name.length > 0) {
                console.log("Привет, " + name + "!");
            }
        """
        Lang.HTML -> """
            <form id="name-form">
                <label for="user-name">Как вас зовут?</label>
                <input id="user-name" name="name" autocomplete="name" required>
                <button type="submit">Продолжить</button>
            </form>
            <p id="name-result" aria-live="polite"></p>
            <script>
                document.getElementById("name-form").addEventListener("submit", (event) => {
                    event.preventDefault();
                    const name = document.getElementById("user-name").value.trim();
                    document.getElementById("name-result").textContent = name ? "Привет, " + name + "!" : "";
                });
            </script>
        """
        Lang.CSS -> """
            .name-form {
                display: grid;
                gap: 12px;
                max-width: 360px;
                padding: 20px;
                border-radius: 14px;
                background: #ffffff;
                box-shadow: 0 8px 28px rgba(0, 0, 0, 0.10);
            }
            .name-form input, .name-form button {
                padding: 12px 14px;
                border: 1px solid #cbd5e1;
                border-radius: 8px;
                font: inherit;
            }
        """
        Lang.KOTLIN -> """
            print("Как вас зовут? ")
            val name = readLine()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                println("Привет, ${'
            }
        """
        Lang.JAVA -> """
            System.out.print("Как вас зовут? ");
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            String name = scanner.nextLine().trim();
            if (!name.isEmpty()) {
                System.out.println("Привет, " + name + "!");
            }
        """
        else -> """
            // Ask for a name and validate the input.
            const name = prompt("What is your name?")?.trim() ?? "";
            if (name) {
                console.log("Hello, " + name + "!");
            }
        """
    }

    private fun webForm(lang: Lang): String = when (lang) {
        Lang.CSS -> cssStyles(Lang.CSS)
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            const form = document.createElement("form");
            form.className = "contact-form";
            form.innerHTML = '<label>Name <input name="name" required></label>' +
                '<label>Email <input name="email" type="email" required></label>' +
                '<label>Message <textarea name="message" required></textarea></label>' +
                '<button type="submit">Send</button>';
            form.addEventListener("submit", (event) => {
                event.preventDefault();
                const data = Object.fromEntries(new FormData(form).entries());
                console.log("Form submitted:", data);
            });
            document.body.appendChild(form);
        """
        Lang.PYTHON -> """
            import tkinter as tk
            from tkinter import messagebox

            def submit_form():
                name = name_entry.get().strip()
                email = email_entry.get().strip()
                message = message_entry.get("1.0", tk.END).strip()
                if not name or not email or not message:
                    messagebox.showwarning("Проверка", "Заполните все поля")
                    return
                messagebox.showinfo("Форма отправлена", f"Спасибо, {name}!")
            
            root = tk.Tk()
            root.title("Форма обратной связи")
            tk.Label(root, text="Имя").pack(anchor="w", padx=12, pady=(12, 0))
            name_entry = tk.Entry(root)
            name_entry.pack(fill="x", padx=12)
            tk.Label(root, text="Email").pack(anchor="w", padx=12, pady=(8, 0))
            email_entry = tk.Entry(root)
            email_entry.pack(fill="x", padx=12)
            tk.Label(root, text="Сообщение").pack(anchor="w", padx=12, pady=(8, 0))
            message_entry = tk.Text(root, height=5)
            message_entry.pack(fill="both", padx=12, pady=4)
            tk.Button(root, text="Отправить", command=submit_form).pack(pady=12)
            root.mainloop()
        """
        else -> """
            <form class="contact-form" id="contact-form">
                <label for="contact-name">Имя</label>
                <input id="contact-name" name="name" autocomplete="name" required>
                <label for="contact-email">Email</label>
                <input id="contact-email" name="email" type="email" autocomplete="email" required>
                <label for="contact-message">Сообщение</label>
                <textarea id="contact-message" name="message" rows="4" required></textarea>
                <button type="submit">Отправить</button>
                <p id="form-status" role="status" aria-live="polite"></p>
            </form>
            <script>
                document.getElementById("contact-form").addEventListener("submit", (event) => {
                    event.preventDefault();
                    const data = Object.fromEntries(new FormData(event.currentTarget).entries());
                    document.getElementById("form-status").textContent = "Спасибо, " + data.name + "!";
                    console.log("Форма:", data);
                });
            </script>
        """
    }

    private fun window(lang: Lang): String = when (lang) {
        Lang.HTML -> """
            <dialog id="app-dialog" aria-labelledby="dialog-title">
                <h2 id="dialog-title">Новое окно</h2>
                <p>Содержимое диалога</p>
                <button type="button" id="dialog-close">Закрыть</button>
            </dialog>
            <button type="button" id="dialog-open">Открыть окно</button>
            <script>
                const dialog = document.getElementById("app-dialog");
                document.getElementById("dialog-open").addEventListener("click", () => dialog.showModal());
                document.getElementById("dialog-close").addEventListener("click", () => dialog.close());
            </script>
        """
        Lang.CSS -> """
            .app-window {
                width: min(92vw, 480px);
                padding: 24px;
                border: 0;
                border-radius: 16px;
                background: #ffffff;
                color: #172033;
                box-shadow: 0 24px 80px rgba(0, 0, 0, 0.25);
            }
            .app-window::backdrop {
                background: rgba(15, 23, 42, 0.55);
            }
        """
        Lang.PYTHON -> """
            import tkinter as tk

            def open_window():
                window = tk.Toplevel(root)
                window.title("Новое окно")
                window.geometry("360x220")
                tk.Label(window, text="Содержимое окна", font=("Arial", 16)).pack(pady=24)
                tk.Button(window, text="Закрыть", command=window.destroy).pack()
            
            root = tk.Tk()
            root.title("Главное окно")
            tk.Button(root, text="Открыть окно", command=open_window).pack(padx=24, pady=24)
            root.mainloop()
        """
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            const dialog = document.createElement("dialog");
            dialog.className = "app-window";
            dialog.innerHTML = '<h2>Новое окно</h2><p>Содержимое диалога</p>' +
                '<button type="button">Закрыть</button>';
            dialog.querySelector("button").addEventListener("click", () => dialog.close());
            document.body.appendChild(dialog);
            dialog.showModal();
        """
        else -> """
            // Desktop window example (Java Swing).
            javax.swing.JFrame frame = new javax.swing.JFrame("Новое окно");
            frame.setSize(480, 320);
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        """
    }

    private fun htmlPage(lang: Lang): String = when (lang) {
        Lang.CSS -> cssStyles(Lang.CSS)
        else -> """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Новая страница</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <main class="page">
                    <h1>Заголовок страницы</h1>
                    <p>Описание страницы</p>
                </main>
            </body>
            </html>
        """
    }

    private fun cssStyles(lang: Lang): String = """
        :root {
            color-scheme: light;
            font-family: system-ui, sans-serif;
        }
        * {
            box-sizing: border-box;
        }
        body {
            margin: 0;
            padding: 24px;
            color: #172033;
            background: #f4f6fb;
        }
        .page, .contact-form, .app-window {
            width: min(100%, 560px);
            margin: 24px auto;
            padding: 24px;
            border-radius: 16px;
            background: #ffffff;
            box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
        }
        input, textarea, button {
            width: 100%;
            padding: 12px 14px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font: inherit;
        }
        button {
            border: 0;
            color: #ffffff;
            background: #2563eb;
            cursor: pointer;
        }
    """

    private fun function(lang: Lang): String = when (lang) {
        Lang.PYTHON -> """
            def process_data(value):
                """ + """"Обработать значение и вернуть результат."""" + """
                result = value
                return result
        """
        Lang.KOTLIN -> """
            fun processData(value: String): String {
                return value.trim()
            }
        """
        Lang.JAVA -> """
            private static String processData(String value) {
                return value == null ? "" : value.trim();
            }
        """
        Lang.HTML -> """
            <script>
                function processData(value) {
                    return String(value ?? "").trim();
                }
            </script>
        """
        else -> """
            function processData(value) {
                const result = value;
                return result;
            }
        """
    }

    private fun program(lang: Lang): String = when (lang) {
        Lang.PYTHON, Lang.PLAIN -> """
            def main():
                print("Программа запущена")

            if __name__ == "__main__":
                main()
        """
        Lang.HTML -> htmlPage(Lang.HTML)
        Lang.CSS -> cssStyles(Lang.CSS)
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            function main() {
                console.log("Программа запущена");
            }

            main();
        """
        Lang.KOTLIN -> """
            fun main() {
                println("Программа запущена")
            }
        """
        else -> function(lang)
    }

    private fun library(text: String, lang: Lang): String {
        val names = listOf("requests", "numpy", "pandas", "flask", "fastapi", "tkinter", "math", "random", "json", "axios", "react", "express", "lodash", "date-fns")
        val library = names.firstOrNull { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
        return when (library) {
            "requests" -> "# Установка один раз: python -m pip install requests\nimport requests"
            "numpy" -> "# Установка один раз: python -m pip install numpy\nimport numpy as np"
            "pandas" -> "# Установка один раз: python -m pip install pandas\nimport pandas as pd"
            "flask" -> "# Установка один раз: python -m pip install flask\nfrom flask import Flask, request, jsonify"
            "fastapi" -> "# Установка один раз: python -m pip install fastapi uvicorn\nfrom fastapi import FastAPI"
            "tkinter" -> "import tkinter as tk\nfrom tkinter import messagebox"
            "math" -> "import math"
            "random" -> "import random"
            "json" -> "import json"
            "axios" -> "// Установка один раз: npm install axios\nimport axios from \"axios\";"
            "react" -> "// Установка один раз: npm install react react-dom\nimport React from \"react\";"
            "express" -> "// Установка один раз: npm install express\nimport express from \"express\";"
            "lodash" -> "// Установка один раз: npm install lodash\nimport _ from \"lodash\";"
            "date-fns" -> "// Установка один раз: npm install date-fns\nimport { format } from \"date-fns\";"
            else -> when (lang) {
                Lang.JAVASCRIPT, Lang.TYPESCRIPT -> "// Установите пакет в проекте: npm install package-name\nimport packageName from \"package-name\";"
                else -> "# Установите библиотеку в окружении: python -m pip install package_name\nimport package_name"
            }
        }
    }
}
}name!")
            }
        """
        Lang.JAVA -> """
            System.out.print("Как вас зовут? ");
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            String name = scanner.nextLine().trim();
            if (!name.isEmpty()) {
                System.out.println("Привет, " + name + "!");
            }
        """
        else -> """
            // Ask for a name and validate the input.
            const name = prompt("What is your name?")?.trim() ?? "";
            if (name) {
                console.log("Hello, " + name + "!");
            }
        """
    }

    private fun webForm(lang: Lang): String = when (lang) {
        Lang.CSS -> cssStyles(Lang.CSS)
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            const form = document.createElement("form");
            form.className = "contact-form";
            form.innerHTML = '<label>Name <input name="name" required></label>' +
                '<label>Email <input name="email" type="email" required></label>' +
                '<label>Message <textarea name="message" required></textarea></label>' +
                '<button type="submit">Send</button>';
            form.addEventListener("submit", (event) => {
                event.preventDefault();
                const data = Object.fromEntries(new FormData(form).entries());
                console.log("Form submitted:", data);
            });
            document.body.appendChild(form);
        """
        Lang.PYTHON -> """
            import tkinter as tk
            from tkinter import messagebox

            def submit_form():
                name = name_entry.get().strip()
                email = email_entry.get().strip()
                message = message_entry.get("1.0", tk.END).strip()
                if not name or not email or not message:
                    messagebox.showwarning("Проверка", "Заполните все поля")
                    return
                messagebox.showinfo("Форма отправлена", f"Спасибо, {name}!")
            
            root = tk.Tk()
            root.title("Форма обратной связи")
            tk.Label(root, text="Имя").pack(anchor="w", padx=12, pady=(12, 0))
            name_entry = tk.Entry(root)
            name_entry.pack(fill="x", padx=12)
            tk.Label(root, text="Email").pack(anchor="w", padx=12, pady=(8, 0))
            email_entry = tk.Entry(root)
            email_entry.pack(fill="x", padx=12)
            tk.Label(root, text="Сообщение").pack(anchor="w", padx=12, pady=(8, 0))
            message_entry = tk.Text(root, height=5)
            message_entry.pack(fill="both", padx=12, pady=4)
            tk.Button(root, text="Отправить", command=submit_form).pack(pady=12)
            root.mainloop()
        """
        else -> """
            <form class="contact-form" id="contact-form">
                <label for="contact-name">Имя</label>
                <input id="contact-name" name="name" autocomplete="name" required>
                <label for="contact-email">Email</label>
                <input id="contact-email" name="email" type="email" autocomplete="email" required>
                <label for="contact-message">Сообщение</label>
                <textarea id="contact-message" name="message" rows="4" required></textarea>
                <button type="submit">Отправить</button>
                <p id="form-status" role="status" aria-live="polite"></p>
            </form>
            <script>
                document.getElementById("contact-form").addEventListener("submit", (event) => {
                    event.preventDefault();
                    const data = Object.fromEntries(new FormData(event.currentTarget).entries());
                    document.getElementById("form-status").textContent = "Спасибо, " + data.name + "!";
                    console.log("Форма:", data);
                });
            </script>
        """
    }

    private fun window(lang: Lang): String = when (lang) {
        Lang.HTML -> """
            <dialog id="app-dialog" aria-labelledby="dialog-title">
                <h2 id="dialog-title">Новое окно</h2>
                <p>Содержимое диалога</p>
                <button type="button" id="dialog-close">Закрыть</button>
            </dialog>
            <button type="button" id="dialog-open">Открыть окно</button>
            <script>
                const dialog = document.getElementById("app-dialog");
                document.getElementById("dialog-open").addEventListener("click", () => dialog.showModal());
                document.getElementById("dialog-close").addEventListener("click", () => dialog.close());
            </script>
        """
        Lang.CSS -> """
            .app-window {
                width: min(92vw, 480px);
                padding: 24px;
                border: 0;
                border-radius: 16px;
                background: #ffffff;
                color: #172033;
                box-shadow: 0 24px 80px rgba(0, 0, 0, 0.25);
            }
            .app-window::backdrop {
                background: rgba(15, 23, 42, 0.55);
            }
        """
        Lang.PYTHON -> """
            import tkinter as tk

            def open_window():
                window = tk.Toplevel(root)
                window.title("Новое окно")
                window.geometry("360x220")
                tk.Label(window, text="Содержимое окна", font=("Arial", 16)).pack(pady=24)
                tk.Button(window, text="Закрыть", command=window.destroy).pack()
            
            root = tk.Tk()
            root.title("Главное окно")
            tk.Button(root, text="Открыть окно", command=open_window).pack(padx=24, pady=24)
            root.mainloop()
        """
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            const dialog = document.createElement("dialog");
            dialog.className = "app-window";
            dialog.innerHTML = '<h2>Новое окно</h2><p>Содержимое диалога</p>' +
                '<button type="button">Закрыть</button>';
            dialog.querySelector("button").addEventListener("click", () => dialog.close());
            document.body.appendChild(dialog);
            dialog.showModal();
        """
        else -> """
            // Desktop window example (Java Swing).
            javax.swing.JFrame frame = new javax.swing.JFrame("Новое окно");
            frame.setSize(480, 320);
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        """
    }

    private fun htmlPage(lang: Lang): String = when (lang) {
        Lang.CSS -> cssStyles(Lang.CSS)
        else -> """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Новая страница</title>
                <link rel="stylesheet" href="style.css">
            </head>
            <body>
                <main class="page">
                    <h1>Заголовок страницы</h1>
                    <p>Описание страницы</p>
                </main>
            </body>
            </html>
        """
    }

    private fun cssStyles(lang: Lang): String = """
        :root {
            color-scheme: light;
            font-family: system-ui, sans-serif;
        }
        * {
            box-sizing: border-box;
        }
        body {
            margin: 0;
            padding: 24px;
            color: #172033;
            background: #f4f6fb;
        }
        .page, .contact-form, .app-window {
            width: min(100%, 560px);
            margin: 24px auto;
            padding: 24px;
            border-radius: 16px;
            background: #ffffff;
            box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
        }
        input, textarea, button {
            width: 100%;
            padding: 12px 14px;
            border: 1px solid #cbd5e1;
            border-radius: 8px;
            font: inherit;
        }
        button {
            border: 0;
            color: #ffffff;
            background: #2563eb;
            cursor: pointer;
        }
    """

    private fun function(lang: Lang): String = when (lang) {
        Lang.PYTHON -> """
            def process_data(value):
                """ + """"Обработать значение и вернуть результат."""" + """
                result = value
                return result
        """
        Lang.KOTLIN -> """
            fun processData(value: String): String {
                return value.trim()
            }
        """
        Lang.JAVA -> """
            private static String processData(String value) {
                return value == null ? "" : value.trim();
            }
        """
        Lang.HTML -> """
            <script>
                function processData(value) {
                    return String(value ?? "").trim();
                }
            </script>
        """
        else -> """
            function processData(value) {
                const result = value;
                return result;
            }
        """
    }

    private fun program(lang: Lang): String = when (lang) {
        Lang.PYTHON, Lang.PLAIN -> """
            def main():
                print("Программа запущена")

            if __name__ == "__main__":
                main()
        """
        Lang.HTML -> htmlPage(Lang.HTML)
        Lang.CSS -> cssStyles(Lang.CSS)
        Lang.JAVASCRIPT, Lang.TYPESCRIPT -> """
            function main() {
                console.log("Программа запущена");
            }

            main();
        """
        Lang.KOTLIN -> """
            fun main() {
                println("Программа запущена")
            }
        """
        else -> function(lang)
    }

    private fun library(text: String, lang: Lang): String {
        val names = listOf("requests", "numpy", "pandas", "flask", "fastapi", "tkinter", "math", "random", "json", "axios", "react", "express", "lodash", "date-fns")
        val library = names.firstOrNull { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(text) }
        return when (library) {
            "requests" -> "# Установка один раз: python -m pip install requests\nimport requests"
            "numpy" -> "# Установка один раз: python -m pip install numpy\nimport numpy as np"
            "pandas" -> "# Установка один раз: python -m pip install pandas\nimport pandas as pd"
            "flask" -> "# Установка один раз: python -m pip install flask\nfrom flask import Flask, request, jsonify"
            "fastapi" -> "# Установка один раз: python -m pip install fastapi uvicorn\nfrom fastapi import FastAPI"
            "tkinter" -> "import tkinter as tk\nfrom tkinter import messagebox"
            "math" -> "import math"
            "random" -> "import random"
            "json" -> "import json"
            "axios" -> "// Установка один раз: npm install axios\nimport axios from \"axios\";"
            "react" -> "// Установка один раз: npm install react react-dom\nimport React from \"react\";"
            "express" -> "// Установка один раз: npm install express\nimport express from \"express\";"
            "lodash" -> "// Установка один раз: npm install lodash\nimport _ from \"lodash\";"
            "date-fns" -> "// Установка один раз: npm install date-fns\nimport { format } from \"date-fns\";"
            else -> when (lang) {
                Lang.JAVASCRIPT, Lang.TYPESCRIPT -> "// Установите пакет в проекте: npm install package-name\nimport packageName from \"package-name\";"
                else -> "# Установите библиотеку в окружении: python -m pip install package_name\nimport package_name"
            }
        }
    }
}
