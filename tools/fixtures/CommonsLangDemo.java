package org.stroka.app.jlang;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.apache.commons.lang3.StringUtils;

public class Main extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText(StringUtils.capitalize("строка") + " " + StringUtils.reverse("abc"));
        setContentView(t);
    }
}
