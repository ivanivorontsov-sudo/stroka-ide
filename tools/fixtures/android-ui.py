from java import jclass, dynamic_proxy

Activity = jclass("android.app.Activity")
Gravity = jclass("android.view.Gravity")
Color = jclass("android.graphics.Color")
GradientDrawable = jclass("android.graphics.drawable.GradientDrawable")
LinearLayout = jclass("android.widget.LinearLayout")
Button = jclass("android.widget.Button")
TextView = jclass("android.widget.TextView")
OnClickListener = jclass("android.view.View$OnClickListener")

counter = {"n": 0}
label = TextView(activity)
label.setText("Нажато: 0")
label.setTextSize(22.0)
label.setGravity(Gravity.CENTER)


class Click(dynamic_proxy(OnClickListener)):
    def onClick(self, view):
        counter["n"] += 1
        label.setText(f"Нажато: {counter['n']}")
        print("клик →", counter["n"])


layout = LinearLayout(activity)
layout.setOrientation(LinearLayout.VERTICAL)
layout.setGravity(Gravity.CENTER)
for text, color in (("Плюс один", "#4C6EF5"), ("Ещё раз", "#12B886")):
    bg = GradientDrawable()
    bg.setColor(Color.parseColor(color))
    bg.setCornerRadius(36.0)
    b = Button(activity)
    b.setText(text)
    b.setTextColor(Color.WHITE)
    b.setBackground(bg)
    b.setOnClickListener(Click())
    layout.addView(b)
layout.addView(label)
activity.setContentView(layout)
print("Интерфейс готов — это Activity:", isinstance(activity, Activity))
