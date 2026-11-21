#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "quickjs.h"

static const unsigned char MAGIC[8] = {'S','T','R','Q','J','S','0','1'};

static unsigned int ru32(const unsigned char *p) {
    return (unsigned int)p[0] | ((unsigned int)p[1] << 8) | ((unsigned int)p[2] << 16) | ((unsigned int)p[3] << 24);
}

static unsigned short ru16(const unsigned char *p) {
    return (unsigned short)p[0] | ((unsigned short)p[1] << 8);
}

static wchar_t *utf8_to_wide(const char *s) {
    if (!s) s = "";
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    wchar_t *w = (wchar_t *)malloc((size_t)n * sizeof(wchar_t));
    if (!w) return NULL;
    MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n);
    return w;
}

static char *wide_to_utf8(const wchar_t *w) {
    if (!w) w = L"";
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    char *s = (char *)malloc((size_t)n);
    if (!s) return NULL;
    WideCharToMultiByte(CP_UTF8, 0, w, -1, s, n, NULL, NULL);
    return s;
}

typedef struct {
    char *name;
    unsigned char *data;
    unsigned int size;
} PFile;

static PFile *files = NULL;
static int nfiles = 0;
static char title_utf8[512] = "Программа";
static char mode_utf8[64] = "auto";
static JSRuntime *rt = NULL;
static JSContext *js = NULL;
static HWND editA, editB, outBox, logBox;
static HFONT font;

static PFile *find_file(const char *name) {
    int i;
    for (i = 0; i < nfiles; i++) {
        if (strcmp(files[i].name, name) == 0) return &files[i];
    }
    return NULL;
}

static JSValue js_print(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    int i;
    for (i = 0; i < argc; i++) {
        const char *s = JS_ToCString(ctx, argv[i]);
        if (s) {
            wchar_t *w = utf8_to_wide(s);
            if (w) {
                if (logBox) {
                    int len = GetWindowTextLengthW(logBox);
                    SendMessageW(logBox, EM_SETSEL, (WPARAM)len, (LPARAM)len);
                    SendMessageW(logBox, EM_REPLACESEL, FALSE, (LPARAM)w);
                    SendMessageW(logBox, EM_REPLACESEL, FALSE, (LPARAM)L"\r\n");
                } else {
                    fputs(s, stdout);
                    fputc('\n', stdout);
                }
                free(w);
            }
            JS_FreeCString(ctx, s);
        }
    }
    return JS_UNDEFINED;
}

static JSValue js_ask(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    const char *q = argc > 0 ? JS_ToCString(ctx, argv[0]) : "Ввод";
    wchar_t *wq = utf8_to_wide(q ? q : "Ввод");
    wchar_t buf[1024];
    buf[0] = 0;
    if (wq) {
        HWND parent = GetActiveWindow();
        (void)parent;
        free(wq);
    }
    if (q) JS_FreeCString(ctx, q);
    return JS_NewString(ctx, "");
}

static int eval_buf(const char *name, const char *code, size_t len) {
    JSValue ret = JS_Eval(js, code, len, name, JS_EVAL_TYPE_GLOBAL);
    int ok = 1;
    if (JS_IsException(ret)) {
        JSValue ex = JS_GetException(js);
        const char *s = JS_ToCString(js, ex);
        wchar_t *w = utf8_to_wide(s ? s : "Ошибка JavaScript");
        MessageBoxW(NULL, w ? w : L"Ошибка JavaScript", L"Строка", MB_ICONERROR);
        free(w);
        if (s) JS_FreeCString(js, s);
        JS_FreeValue(js, ex);
        ok = 0;
    }
    JS_FreeValue(js, ret);
    return ok;
}

static char *call_fn(const char *name, const char *a, const char *b) {
    JSValue global = JS_GetGlobalObject(js);
    JSValue fn = JS_GetPropertyStr(js, global, name);
    if (!JS_IsFunction(js, fn)) {
        JS_FreeValue(js, fn);
        JS_FreeValue(js, global);
        return NULL;
    }
    JSValue args[2];
    args[0] = JS_NewString(js, a ? a : "");
    args[1] = JS_NewString(js, b ? b : "");
    JSValue ret = JS_Call(js, fn, global, 2, args);
    JS_FreeValue(js, args[0]);
    JS_FreeValue(js, args[1]);
    JS_FreeValue(js, fn);
    JS_FreeValue(js, global);
    if (JS_IsException(ret)) {
        JSValue ex = JS_GetException(js);
        const char *s = JS_ToCString(js, ex);
        char *out = s ? _strdup(s) : _strdup("Ошибка");
        if (s) JS_FreeCString(js, s);
        JS_FreeValue(js, ex);
        JS_FreeValue(js, ret);
        return out;
    }
    const char *s = JS_ToCString(js, ret);
    char *out = s ? _strdup(s) : _strdup("");
    if (s) JS_FreeCString(js, s);
    JS_FreeValue(js, ret);
    return out;
}

static char *hwnd_text(HWND h) {
    int n = GetWindowTextLengthW(h) + 1;
    wchar_t *w = (wchar_t *)malloc((size_t)n * sizeof(wchar_t));
    if (!w) return _strdup("");
    GetWindowTextW(h, w, n);
    char *s = wide_to_utf8(w);
    free(w);
    return s ? s : _strdup("");
}

static void set_utf8(HWND h, const char *s) {
    wchar_t *w = utf8_to_wide(s ? s : "");
    if (w) {
        SetWindowTextW(h, w);
        free(w);
    }
}

static void do_op(const char *fn) {
    char *a = hwnd_text(editA);
    char *b = hwnd_text(editB);
    char *r = call_fn(fn, a, b);
    set_utf8(outBox, r ? r : "Нет функции");
    free(a);
    free(b);
    free(r);
}

static LRESULT CALLBACK WndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_COMMAND:
        if (LOWORD(w) == 101) do_op("$сложить");
        else if (LOWORD(w) == 102) do_op("$вычесть");
        else if (LOWORD(w) == 103) do_op("$умножить");
        else if (LOWORD(w) == 104) do_op("$разделить");
        break;
    case WM_DESTROY:
        PostQuitMessage(0);
        break;
    default:
        return DefWindowProcW(h, m, w, l);
    }
    return 0;
}

static int run_calc_ui(HINSTANCE inst) {
    WNDCLASSW wc;
    HWND win, lbl;
    MSG msg;
    wchar_t *wt;
    RECT r;
    memset(&wc, 0, sizeof(wc));
    wc.lpfnWndProc = WndProc;
    wc.hInstance = inst;
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.lpszClassName = L"StrokaCalc";
    RegisterClassW(&wc);
    wt = utf8_to_wide(title_utf8);
    win = CreateWindowExW(0, L"StrokaCalc", wt ? wt : L"Калькулятор",
                          WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                          CW_USEDEFAULT, CW_USEDEFAULT, 420, 280, NULL, NULL, inst, NULL);
    free(wt);
    font = CreateFontW(-18, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
    lbl = CreateWindowW(L"STATIC", L"A", WS_CHILD | WS_VISIBLE, 16, 16, 24, 28, win, NULL, inst, NULL);
    SendMessageW(lbl, WM_SETFONT, (WPARAM)font, TRUE);
    editA = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL, 48, 12, 340, 32, win, (HMENU)1, inst, NULL);
    lbl = CreateWindowW(L"STATIC", L"B", WS_CHILD | WS_VISIBLE, 16, 56, 24, 28, win, NULL, inst, NULL);
    SendMessageW(lbl, WM_SETFONT, (WPARAM)font, TRUE);
    editB = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL, 48, 52, 340, 32, win, (HMENU)2, inst, NULL);
    CreateWindowW(L"BUTTON", L"Сложить", WS_CHILD | WS_VISIBLE, 16, 100, 90, 36, win, (HMENU)101, inst, NULL);
    CreateWindowW(L"BUTTON", L"Вычесть", WS_CHILD | WS_VISIBLE, 114, 100, 90, 36, win, (HMENU)102, inst, NULL);
    CreateWindowW(L"BUTTON", L"Умножить", WS_CHILD | WS_VISIBLE, 212, 100, 90, 36, win, (HMENU)103, inst, NULL);
    CreateWindowW(L"BUTTON", L"Разделить", WS_CHILD | WS_VISIBLE, 310, 100, 90, 36, win, (HMENU)104, inst, NULL);
    lbl = CreateWindowW(L"STATIC", L"Результат:", WS_CHILD | WS_VISIBLE, 16, 152, 100, 24, win, NULL, inst, NULL);
    SendMessageW(lbl, WM_SETFONT, (WPARAM)font, TRUE);
    outBox = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_READONLY | ES_AUTOHSCROLL, 120, 148, 268, 32, win, (HMENU)3, inst, NULL);
    SendMessageW(editA, WM_SETFONT, (WPARAM)font, TRUE);
    SendMessageW(editB, WM_SETFONT, (WPARAM)font, TRUE);
    SendMessageW(outBox, WM_SETFONT, (WPARAM)font, TRUE);
    ShowWindow(win, SW_SHOW);
    UpdateWindow(win);
    while (GetMessageW(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    DeleteObject(font);
    (void)r;
    return (int)msg.wParam;
}

static int run_console_ui(HINSTANCE inst) {
    AllocConsole();
    SetConsoleOutputCP(65001);
    SetConsoleCP(65001);
    freopen("CONOUT$", "w", stdout);
    freopen("CONOUT$", "w", stderr);
    freopen("CONIN$", "r", stdin);
    PFile *prog = find_file("program.js");
    if (prog) {
        if (!eval_buf("program.js", (const char *)prog->data, prog->size)) return 1;
    }
    printf("Готово.\n");
    return 0;
}

static int load_payload(void) {
    wchar_t path[MAX_PATH];
    HANDLE h;
    DWORD rd = 0;
    unsigned char *buf;
    DWORD sz;
    unsigned int base, flags, count, off, i, nlen, dlen;
    if (!GetModuleFileNameW(NULL, path, MAX_PATH)) return 0;
    h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    sz = GetFileSize(h, NULL);
    if (sz < 16) { CloseHandle(h); return 0; }
    buf = (unsigned char *)malloc(sz);
    if (!buf) { CloseHandle(h); return 0; }
    if (!ReadFile(h, buf, sz, &rd, NULL) || rd != sz) { free(buf); CloseHandle(h); return 0; }
    CloseHandle(h);
    if (memcmp(buf + sz - 16, MAGIC, 8) != 0) { free(buf); return 0; }
    base = ru32(buf + sz - 8);
    flags = ru32(buf + sz - 4);
    (void)flags;
    if (base >= sz - 16) { free(buf); return 0; }
    off = base;
    if (off + 4 > sz - 16) { free(buf); return 0; }
    count = ru32(buf + off);
    off += 4;
    files = (PFile *)calloc(count, sizeof(PFile));
    nfiles = 0;
    for (i = 0; i < count; i++) {
        if (off + 2 > sz - 16) break;
        nlen = ru16(buf + off); off += 2;
        if (off + nlen + 4 > sz - 16) break;
        files[nfiles].name = (char *)malloc(nlen + 1);
        memcpy(files[nfiles].name, buf + off, nlen);
        files[nfiles].name[nlen] = 0;
        off += nlen;
        dlen = ru32(buf + off); off += 4;
        if (off + dlen > sz - 16) break;
        files[nfiles].data = (unsigned char *)malloc(dlen + 1);
        memcpy(files[nfiles].data, buf + off, dlen);
        files[nfiles].data[dlen] = 0;
        files[nfiles].size = dlen;
        off += dlen;
        nfiles++;
    }
    free(buf);
    {
        PFile *meta = find_file("meta.txt");
        if (meta && meta->size) {
            char *copy = (char *)malloc(meta->size + 1);
            char *line, *ctx = NULL;
            memcpy(copy, meta->data, meta->size);
            copy[meta->size] = 0;
            line = strtok(copy, "\r\n");
            while (line) {
                if (strncmp(line, "title=", 6) == 0) {
                    strncpy(title_utf8, line + 6, sizeof(title_utf8) - 1);
                    title_utf8[sizeof(title_utf8) - 1] = 0;
                } else if (strncmp(line, "mode=", 5) == 0) {
                    strncpy(mode_utf8, line + 5, sizeof(mode_utf8) - 1);
                    mode_utf8[sizeof(mode_utf8) - 1] = 0;
                }
                line = strtok(NULL, "\r\n");
            }
            free(copy);
            (void)ctx;
        }
    }
    return 1;
}

static int setup_js(void) {
    PFile *yadro, *prog, *boot;
    rt = JS_NewRuntime();
    if (!rt) return 0;
    js = JS_NewContext(rt);
    if (!js) return 0;
    {
        JSValue global = JS_GetGlobalObject(js);
        JSValue console = JS_NewObject(js);
        JS_SetPropertyStr(js, console, "log", JS_NewCFunction(js, js_print, "log", 1));
        JS_SetPropertyStr(js, console, "error", JS_NewCFunction(js, js_print, "error", 1));
        JS_SetPropertyStr(js, console, "info", JS_NewCFunction(js, js_print, "info", 1));
        JS_SetPropertyStr(js, global, "console", console);
        JS_SetPropertyStr(js, global, "print", JS_NewCFunction(js, js_print, "print", 1));
        JS_SetPropertyStr(js, global, "ask", JS_NewCFunction(js, js_ask, "ask", 1));
        JS_FreeValue(js, global);
    }
    yadro = find_file("yadro.js");
    if (yadro && !eval_buf("yadro.js", (const char *)yadro->data, yadro->size)) return 0;
    boot = find_file("boot.js");
    if (boot && !eval_buf("boot.js", (const char *)boot->data, boot->size)) return 0;
    prog = find_file("program.js");
    if (prog && !eval_buf("program.js", (const char *)prog->data, prog->size)) return 0;
    return 1;
}

int WINAPI WinMain(HINSTANCE inst, HINSTANCE prev, LPSTR cmd, int show) {
    (void)prev; (void)cmd; (void)show;
    if (!load_payload()) {
        MessageBoxW(NULL, L"В этой программе нет встроенного кода (повреждён файл).", L"Строка", MB_ICONERROR);
        return 1;
    }
    if (!setup_js()) return 1;
    if (strcmp(mode_utf8, "calc") == 0) {
        int rc = run_calc_ui(inst);
        JS_FreeContext(js);
        JS_FreeRuntime(rt);
        return rc;
    }
    {
        int rc = run_console_ui(inst);
        JS_FreeContext(js);
        JS_FreeRuntime(rt);
        return rc;
    }
}
