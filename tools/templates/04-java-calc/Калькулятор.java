// Калькулятор на Java для Android.
// Меню → «Собрать APK» превращает этот файл в приложение прямо на телефоне.
package org.stroka.app.jcalc;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

// Activity — один экран приложения. Android вызывает onCreate при запуске.
public class Main extends Activity {
    private EditText fieldA, fieldB;
    private TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Элементы складываем сверху вниз в LinearLayout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);

        fieldA = numberField("Первое число");
        fieldB = numberField("Второе число");
        root.addView(fieldA);
        root.addView(fieldB);

        LinearLayout keys = new LinearLayout(this);
        keys.addView(opButton("+", '+'));
        keys.addView(opButton("−", '-'));
        keys.addView(opButton("×", '*'));
        keys.addView(opButton("÷", '/'));
        root.addView(keys);

        result = new TextView(this);
        result.setTextSize(24);
        result.setText("Введите два числа");
        root.addView(result);
        setContentView(root);
    }

    private EditText numberField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    // Лямбда v -> calculate(op) вызывается при нажатии кнопки
    private Button opButton(String label, char op) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> calculate(op));
        return b;
    }

    private void calculate(char op) {
        try {
            double a = Double.parseDouble(fieldA.getText().toString().replace(',', '.'));
            double b = Double.parseDouble(fieldB.getText().toString().replace(',', '.'));
            if (op == '/' && b == 0) { result.setText("На ноль делить нельзя"); return; }
            double r = op == '+' ? a + b : op == '-' ? a - b : op == '*' ? a * b : a / b;
            result.setText("= " + (r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r)));
        } catch (NumberFormatException e) {
            result.setText("Введите два числа");
        }
    }
}
