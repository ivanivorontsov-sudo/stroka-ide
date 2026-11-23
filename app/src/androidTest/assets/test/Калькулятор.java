package org.stroka.app.jcalc;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity {
    private EditText fieldA;
    private EditText fieldB;
    private TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("""
            Калькулятор
            """.trim());
        title.setTextSize(22);
        root.addView(title);

        fieldA = new EditText(this);
        fieldA.setHint("Первое число");
        fieldA.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(fieldA);

        fieldB = new EditText(this);
        fieldB.setHint("Второе число");
        fieldB.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(fieldB);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(opButton("Сложить", 1));
        keys.addView(opButton("Вычесть", 2));
        keys.addView(opButton("Умножить", 3));
        keys.addView(opButton("Разделить", 4));
        root.addView(keys);

        result = new TextView(this);
        result.setTextSize(20);
        result.setPadding(0, p, 0, 0);
        result.setText("Результат");
        root.addView(result);
        setContentView(root);
    }

    private Button opButton(String label, final int op) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> apply(op));
        return b;
    }

    private void apply(int op) {
        Double a = parse(fieldA.getText().toString());
        Double b = parse(fieldB.getText().toString());
        if (a == null || b == null) {
            Toast.makeText(this, "Введите два числа", Toast.LENGTH_SHORT).show();
            return;
        }
        if (op == 4 && b.doubleValue() == 0.0) {
            result.setText("Деление на ноль");
            return;
        }
        double r;
        if (op == 1) r = a.doubleValue() + b.doubleValue();
        else if (op == 2) r = a.doubleValue() - b.doubleValue();
        else if (op == 3) r = a.doubleValue() * b.doubleValue();
        else r = a.doubleValue() / b.doubleValue();
        if (r == Math.rint(r) && Math.abs(r) < 1e15) result.setText(String.valueOf((long) r));
        else result.setText(String.valueOf(r));
    }

    private Double parse(String s) {
        if (s == null) return null;
        String t = s.trim().replace(',', '.');
        if (t.length() == 0) return null;
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
