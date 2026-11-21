package org.ivanivorontsov.stroka.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView

class SelfTutorActivity : AppCompatActivity() {
    private data class Lesson(val title: String, val goal: String, val example: String, val question: String, val expected: List<String>, val hint: String, val why: String)
    private val lessons = listOf(
        Lesson("1. Переменные", "Переменная хранит значение под понятным именем.",
            "name = \"Иван\"\nprint(name)", "Что выведет программа?", listOf("иван"), "Посмотри, какое значение присвоено name.", "Сначала выполняется присваивание, затем print читает сохранённое значение."),
        Lesson("2. Условия", "if выбирает ветку по результату логического выражения.",
            "age = 18\nif age >= 18:\n    print(\"Можно\")\nelse:\n    print(\"Рано\")", "Какая ветка выполнится при age = 16?", listOf("else", "иначе", "рано"), "Сравни 16 и 18.", "16 >= 18 ложно, поэтому выполняется ветка else."),
        Lesson("3. Циклы", "Цикл повторяет блок кода, пока выполняется условие или не закончится последовательность.",
            "for n in range(3):\n    print(n)", "Сколько раз выполнится print?", listOf("3", "три"), "range(3) создаёт значения 0, 1, 2.", "Это три значения: ноль, один и два. Верхняя граница range не включается."),
        Lesson("4. Функции", "Функция объединяет действия и может возвращать результат через return.",
            "def square(x):\n    return x * x\nprint(square(4))", "Что напечатает программа?", listOf("16"), "Подставь 4 вместо x и умножь число само на себя.", "Функция возвращает 4 * 4, то есть 16."),
        Lesson("5. Списки", "Список хранит несколько значений; индексация Python начинается с нуля.",
            "colors = [\"red\", \"green\", \"blue\"]\nprint(colors[1])", "Какой элемент получим по индексу 1?", listOf("green"), "Нулевой индекс — первый элемент.", "Индекс 1 указывает на второй элемент списка — green."),
        Lesson("6. Отладка", "Читай сообщение об ошибке, находи строку и проверяй типы и границы данных.",
            "numbers = [2, 4, 6]\nprint(numbers[3])", "Почему этот код вызывает IndexError?", listOf("индекс", "границ", "три элемента", "выход"), "У списка три элемента. Какие индексы допустимы?", "Допустимые индексы: 0, 1 и 2. Индекса 3 нет; нужно исправить индекс или проверить длину.")
    )
    private var index = 0
    private lateinit var content: LinearLayout
    private lateinit var answer: AppCompatEditText
    private lateinit var feedback: TextView
    private lateinit var example: TextView
    private lateinit var hint: TextView
    private var exampleVisible = true
    private var hintVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Самоучитель программирования"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 20) }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(Button(this).apply { text = "Подсказка"; setOnClickListener { hintVisible = !hintVisible; render() } })
        actions.addView(Button(this).apply { text = "Пример"; setOnClickListener { exampleVisible = !exampleVisible; render() } })
        actions.addView(Button(this).apply { text = "Проверить"; setOnClickListener { checkAnswer() } })
        root.addView(actions)
        root.addView(Button(this).apply { text = "Следующий урок →"; setOnClickListener { index = (index + 1) % lessons.size; exampleVisible = true; hintVisible = false; render() } })
        setContentView(root)
        render()
    }

    private fun render() {
        val lesson = lessons[index]
        content.removeAllViews()
        fun text(value: String, size: Float = 16f, bold: Boolean = false) {
            content.addView(AppCompatTextView(this).apply { text = value; textSize = size; setTextIsSelectable(true); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, 8, 0, 8) })
        }
        text("Урок ${index + 1} из ${lessons.size}", 13f)
        text(lesson.title, 24f, true)
        text(lesson.goal)
        if (exampleVisible) {
            text("Разобранный пример", 17f, true)
            example = AppCompatTextView(this).apply { text = lesson.example; textSize = 15f; setPadding(14, 14, 14, 14); setBackgroundColor(0xFFEFF3FA.toInt()); typeface = android.graphics.Typeface.MONOSPACE }
            content.addView(example)
            text("Сначала объясни себе: что делает каждая строка и почему?", 14f)
        } else text("Пример скрыт — попробуй восстановить решение по памяти.", 14f)
        text(lesson.question, 18f, true)
        answer = AppCompatEditText(this).apply { hint = "Напиши ответ своими словами"; minLines = 2; setPadding(12, 8, 12, 8) }
        content.addView(answer, LinearLayout.LayoutParams(-1, -2))
        hint = AppCompatTextView(this).apply { text = if (hintVisible) "Подсказка: ${lesson.hint}" else ""; textSize = 14f; setPadding(0, 8, 0, 8) }
        content.addView(hint)
        feedback = AppCompatTextView(this).apply { text = ""; textSize = 15f; setPadding(0, 12, 0, 12) }
        content.addView(feedback)
        text("Метод: изучи пример → закрой его → ответь по памяти → проверь объяснение → попробуй изменить код.")
    }

    private fun checkAnswer() {
        val lesson = lessons[index]
        val response = answer.text?.toString()?.trim()?.lowercase().orEmpty()
        if (response.isBlank()) { Toast.makeText(this, "Сначала попробуй ответить самостоятельно", Toast.LENGTH_SHORT).show(); return }
        val correct = lesson.expected.any { response.contains(it) }
        feedback.text = if (correct) "Верно! Теперь объясни, почему это работает.\n\n${lesson.why}" else "Пока не совсем. Это не провал, а полезная ошибка.\n\nПодсказка: ${lesson.hint}\n\nПочему: ${lesson.why}"
        getSharedPreferences("self_tutor", MODE_PRIVATE).edit().putInt("attempts_$index", getSharedPreferences("self_tutor", MODE_PRIVATE).getInt("attempts_$index", 0) + 1).apply()
    }
}
