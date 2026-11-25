// Приложение-счётчик на Kotlin для Android.
// Меню → «Собрать APK»: Строка скомпилирует Kotlin прямо на телефоне.
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// Activity — один экран приложения
class Main : Activity() {
    private var count = 0   // var — переменную можно менять

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        root.setPadding(pad, pad, pad, pad)

        val label = TextView(this)
        label.textSize = 28f
        label.text = "Нажато: 0"
        root.addView(label)

        // Лямбда в фигурных скобках выполняется при каждом нажатии
        val plus = Button(this)
        plus.text = "+1"
        plus.setOnClickListener {
            count++
            label.text = "Нажато: $count"   // $count подставляет значение в строку
        }
        root.addView(plus)

        val reset = Button(this)
        reset.text = "Сбросить"
        reset.setOnClickListener {
            count = 0
            label.text = "Нажато: 0"
        }
        root.addView(reset)
        setContentView(root)
    }
}
