import re

activity = None
_native = False

_NATIVE_RE = re.compile(
    r"^\s*(from\s+(java|android|com\.chaquo|stroka)\b|import\s+(java|android|stroka)\b)"
    r"|\bjclass\s*\(|\bdynamic_proxy\s*\(|\bstatic_proxy\s*\(",
    re.MULTILINE,
)


def uses_native_ui(source):
    return bool(_NATIVE_RE.search(source or ""))


def _bind(act, native):
    global activity, _native
    activity = act
    _native = bool(native)


def is_native():
    return _native


def run_on_ui_thread(fn, *args, **kwargs):
    from java import dynamic_proxy, jclass

    Runnable = jclass("java.lang.Runnable")

    class _Task(dynamic_proxy(Runnable)):
        def run(self):
            fn(*args, **kwargs)

    task = _Task()
    activity.runOnUiThread(task)
    return task


def click(fn):
    from java import dynamic_proxy, jclass

    Listener = jclass("android.view.View$OnClickListener")

    class _Click(dynamic_proxy(Listener)):
        def onClick(self, view):
            fn(view)

    return _Click()


def dp(value):
    return int(value * activity.getResources().getDisplayMetrics().density + 0.5)
