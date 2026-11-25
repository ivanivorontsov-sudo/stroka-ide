#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <shlobj.h>
#include <shellapi.h>
#include <objbase.h>
#include <shobjidl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

static const unsigned char MAGIC[8] = {'S','T','R','S','E','T','0','1'};

typedef struct { char *name; unsigned char *data; unsigned int size; } PFile;

static unsigned char *self_buf = NULL;
static DWORD self_size = 0;
static unsigned int stub_size = 0;
static PFile *files = NULL;
static int nfiles = 0;

static wchar_t m_title[256] = L"Программа";
static wchar_t m_version[64] = L"1.0";
static wchar_t m_publisher[256] = L"";
static wchar_t m_id[128] = L"stroka-app";
static wchar_t m_exe[260] = L"";
static wchar_t m_license[260] = L"";
static wchar_t m_banner[260] = L"";
static wchar_t m_icon[260] = L"";
static wchar_t m_website[512] = L"";
static int opt_desktop = 1, opt_startmenu = 1, opt_run = 1;
static int silent = 0;
static wchar_t install_dir[MAX_PATH * 2] = L"";

static HWND hwnd_main, h_dir, h_desk, h_menu, h_run, h_accept, h_progress, h_install, h_cancel, h_browse, h_status, h_license;
static HFONT font_normal, font_title;
static BITMAPINFO *banner_bi = NULL;
static unsigned char *banner_bits = NULL;
static int banner_w = 0, banner_h = 0;

#define ID_INSTALL 101
#define ID_CANCEL 102
#define ID_BROWSE 103
#define ID_ACCEPT 104

static unsigned int ru32(const unsigned char *p) { return (unsigned)p[0] | ((unsigned)p[1] << 8) | ((unsigned)p[2] << 16) | ((unsigned)p[3] << 24); }
static unsigned int ru16(const unsigned char *p) { return (unsigned)p[0] | ((unsigned)p[1] << 8); }

static void utf8w(const char *s, int n, wchar_t *out, int cap) {
    int k = MultiByteToWideChar(CP_UTF8, 0, s, n, out, cap - 1);
    if (k < 0) k = 0;
    out[k] = 0;
}

static wchar_t *utf8w_alloc(const char *s, int n) {
    int k = MultiByteToWideChar(CP_UTF8, 0, s, n, NULL, 0);
    wchar_t *w = (wchar_t *)calloc((size_t)k + 1, sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, s, n, w, k);
    w[k] = 0;
    return w;
}

static PFile *find_file(const char *name) {
    for (int i = 0; i < nfiles; i++) if (strcmp(files[i].name, name) == 0) return &files[i];
    return NULL;
}

static PFile *find_filew(const wchar_t *wname) {
    char name[1024];
    WideCharToMultiByte(CP_UTF8, 0, wname, -1, name, sizeof(name), NULL, NULL);
    return find_file(name);
}

static int read_self(void) {
    wchar_t path[MAX_PATH * 2];
    GetModuleFileNameW(NULL, path, MAX_PATH * 2);
    HANDLE h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    self_size = GetFileSize(h, NULL);
    self_buf = (unsigned char *)malloc(self_size);
    DWORD got = 0;
    ReadFile(h, self_buf, self_size, &got, NULL);
    CloseHandle(h);
    if (got != self_size || self_size < 16) return 0;
    unsigned char *t = self_buf + self_size - 16;
    if (memcmp(t, MAGIC, 8) != 0) return 0;
    stub_size = ru32(t + 8);
    if (stub_size >= self_size - 16) return 0;
    unsigned char *p = self_buf + stub_size, *end = self_buf + self_size - 16;
    if (p + 4 > end) return 0;
    unsigned int count = ru32(p); p += 4;
    if (count > 100000) return 0;
    files = (PFile *)calloc(count ? count : 1, sizeof(PFile));
    for (unsigned int i = 0; i < count; i++) {
        if (p + 2 > end) return 0;
        unsigned int nl = ru16(p); p += 2;
        if (p + nl + 4 > end) return 0;
        files[i].name = (char *)calloc(nl + 1, 1);
        memcpy(files[i].name, p, nl); p += nl;
        unsigned int dl = ru32(p); p += 4;
        if (p + dl > end) return 0;
        files[i].data = p; files[i].size = dl; p += dl;
        nfiles++;
    }
    return 1;
}

static void parse_meta(void) {
    PFile *m = find_file("meta.txt");
    if (!m) return;
    char *s = (char *)calloc(m->size + 1, 1);
    memcpy(s, m->data, m->size);
    char *line = s;
    while (line && *line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = 0;
        size_t L = strlen(line);
        if (L && line[L - 1] == '\r') line[L - 1] = 0;
        char *eq = strchr(line, '=');
        if (eq) {
            *eq = 0;
            const char *k = line, *v = eq + 1;
            if (!strcmp(k, "title")) utf8w(v, -1, m_title, 256);
            else if (!strcmp(k, "version")) utf8w(v, -1, m_version, 64);
            else if (!strcmp(k, "publisher")) utf8w(v, -1, m_publisher, 256);
            else if (!strcmp(k, "id")) utf8w(v, -1, m_id, 128);
            else if (!strcmp(k, "exe")) utf8w(v, -1, m_exe, 260);
            else if (!strcmp(k, "license")) utf8w(v, -1, m_license, 260);
            else if (!strcmp(k, "banner")) utf8w(v, -1, m_banner, 260);
            else if (!strcmp(k, "icon")) utf8w(v, -1, m_icon, 260);
            else if (!strcmp(k, "website")) utf8w(v, -1, m_website, 512);
            else if (!strcmp(k, "desktop")) opt_desktop = atoi(v);
            else if (!strcmp(k, "startmenu")) opt_startmenu = atoi(v);
            else if (!strcmp(k, "run")) opt_run = atoi(v);
        }
        line = nl ? nl + 1 : NULL;
    }
    free(s);
}

static void safe_name(const wchar_t *in, wchar_t *out, int cap) {
    int j = 0;
    for (int i = 0; in[i] && j < cap - 1; i++) {
        wchar_t c = in[i];
        if (c < 32 || wcschr(L"\\/:*?\"<>|", c)) c = L'_';
        out[j++] = c;
    }
    while (j > 0 && (out[j - 1] == L' ' || out[j - 1] == L'.')) j--;
    out[j] = 0;
    if (!j) wcscpy(out, L"Программа");
}

static void default_dir(void) {
    wchar_t base[MAX_PATH], nm[256];
    if (FAILED(SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA, NULL, 0, base))) GetTempPathW(MAX_PATH, base);
    safe_name(m_title, nm, 256);
    swprintf(install_dir, MAX_PATH * 2, L"%ls\\Programs\\%ls", base, nm);
}

static int ensure_dir(const wchar_t *dir) {
    int r = SHCreateDirectoryExW(NULL, dir, NULL);
    return r == ERROR_SUCCESS || r == ERROR_ALREADY_EXISTS || r == ERROR_FILE_EXISTS;
}

static int write_file(const wchar_t *path, const unsigned char *data, DWORD n) {
    wchar_t dir[MAX_PATH * 2];
    wcsncpy(dir, path, MAX_PATH * 2 - 1); dir[MAX_PATH * 2 - 1] = 0;
    wchar_t *sl = wcsrchr(dir, L'\\');
    if (sl) { *sl = 0; ensure_dir(dir); }
    HANDLE h = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    DWORD w = 0;
    BOOL ok = WriteFile(h, data, n, &w, NULL);
    CloseHandle(h);
    return ok && w == n;
}

static int make_link(const wchar_t *lnk, const wchar_t *target, const wchar_t *args, const wchar_t *workdir, const wchar_t *icon, const wchar_t *desc) {
    IShellLinkW *sl = NULL;
    IPersistFile *pf = NULL;
    int ok = 0;
    if (FAILED(CoCreateInstance(&CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, &IID_IShellLinkW, (void **)&sl))) return 0;
    sl->lpVtbl->SetPath(sl, target);
    if (args) sl->lpVtbl->SetArguments(sl, args);
    if (workdir) sl->lpVtbl->SetWorkingDirectory(sl, workdir);
    if (icon && *icon) sl->lpVtbl->SetIconLocation(sl, icon, 0);
    if (desc) sl->lpVtbl->SetDescription(sl, desc);
    if (SUCCEEDED(sl->lpVtbl->QueryInterface(sl, &IID_IPersistFile, (void **)&pf))) {
        ok = SUCCEEDED(pf->lpVtbl->Save(pf, lnk, TRUE));
        pf->lpVtbl->Release(pf);
    }
    sl->lpVtbl->Release(sl);
    return ok;
}

static void set_status(const wchar_t *s) { if (h_status) SetWindowTextW(h_status, s); }

static void fail(const wchar_t *msg) {
    if (!silent) MessageBoxW(hwnd_main, msg, m_title, MB_ICONERROR | MB_OK);
}

static int do_install(void) {
    wchar_t path[MAX_PATH * 2], icon_path[MAX_PATH * 2] = L"", exe_path[MAX_PATH * 2] = L"", nm[256];
    char *log = (char *)calloc(1, 1);
    size_t loglen = 0;
    #define LOGW(ws) do { char b8[2048]; int k = WideCharToMultiByte(CP_UTF8, 0, ws, -1, b8, sizeof(b8), NULL, NULL); if (k > 0) { log = realloc(log, loglen + k + 1); memcpy(log + loglen, b8, k - 1); loglen += k - 1; log[loglen++] = '\n'; log[loglen] = 0; } } while (0)
    if (!ensure_dir(install_dir)) { fail(L"Не удалось создать папку установки. Выберите другую папку."); return 0; }
    int total = 0, done = 0;
    for (int i = 0; i < nfiles; i++) if (!strncmp(files[i].name, "app/", 4)) total++;
    if (h_progress) SendMessageW(h_progress, PBM_SETRANGE32, 0, total + 3);
    for (int i = 0; i < nfiles; i++) {
        if (strncmp(files[i].name, "app/", 4) != 0) continue;
        wchar_t *rel = utf8w_alloc(files[i].name + 4, -1);
        for (wchar_t *c = rel; *c; c++) if (*c == L'/') *c = L'\\';
        if (wcsstr(rel, L"..")) { free(rel); continue; }
        swprintf(path, MAX_PATH * 2, L"%ls\\%ls", install_dir, rel);
        set_status(rel);
        if (!write_file(path, files[i].data, files[i].size)) {
            wchar_t msg[1024];
            swprintf(msg, 1024, L"Не удалось записать файл:\n%ls\n\nВозможно, программа сейчас запущена.", path);
            fail(msg); free(rel); return 0;
        }
        LOGW(L"F"); log[loglen - 1] = '\t'; LOGW(rel);
        free(rel);
        if (h_progress) SendMessageW(h_progress, PBM_SETPOS, ++done, 0);
    }
    if (m_icon[0]) {
        PFile *ic = find_filew(m_icon);
        if (ic) {
            swprintf(icon_path, MAX_PATH * 2, L"%ls\\app.ico", install_dir);
            if (write_file(icon_path, ic->data, ic->size)) { LOGW(L"F\tapp.ico"); }
            else icon_path[0] = 0;
        }
    }
    {
        wchar_t un[MAX_PATH * 2];
        swprintf(un, MAX_PATH * 2, L"%ls\\uninstall.exe", install_dir);
        if (!write_file(un, self_buf, stub_size)) { fail(L"Не удалось записать программу удаления."); return 0; }
    }
    wchar_t exe_rel[260];
    wcscpy(exe_rel, m_exe);
    for (wchar_t *c = exe_rel; *c; c++) if (*c == L'/') *c = L'\\';
    swprintf(exe_path, MAX_PATH * 2, L"%ls\\%ls", install_dir, exe_rel);
    if (!icon_path[0]) wcscpy(icon_path, exe_path);
    safe_name(m_title, nm, 256);
    CoInitialize(NULL);
    if (opt_desktop) {
        wchar_t d[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_DESKTOPDIRECTORY, NULL, 0, d))) {
            swprintf(path, MAX_PATH * 2, L"%ls\\%ls.lnk", d, nm);
            if (make_link(path, exe_path, NULL, install_dir, icon_path, m_title)) { LOGW(L"L"); log[loglen - 1] = '\t'; LOGW(path); }
        }
    }
    if (opt_startmenu) {
        wchar_t d[MAX_PATH], folder[MAX_PATH * 2], un[MAX_PATH * 2], unl[MAX_PATH * 2];
        if (SUCCEEDED(SHGetFolderPathW(NULL, CSIDL_PROGRAMS, NULL, 0, d))) {
            swprintf(folder, MAX_PATH * 2, L"%ls\\%ls", d, nm);
            ensure_dir(folder);
            swprintf(path, MAX_PATH * 2, L"%ls\\%ls.lnk", folder, nm);
            if (make_link(path, exe_path, NULL, install_dir, icon_path, m_title)) { LOGW(L"L"); log[loglen - 1] = '\t'; LOGW(path); }
            swprintf(un, MAX_PATH * 2, L"%ls\\uninstall.exe", install_dir);
            swprintf(unl, MAX_PATH * 2, L"%ls\\Удалить %ls.lnk", folder, nm);
            if (make_link(unl, un, L"/uninstall", install_dir, icon_path, L"Удалить программу")) { LOGW(L"L"); log[loglen - 1] = '\t'; LOGW(unl); }
            LOGW(L"D"); log[loglen - 1] = '\t'; LOGW(folder);
        }
    }
    CoUninitialize();
    if (h_progress) SendMessageW(h_progress, PBM_SETPOS, ++done, 0);
    {
        wchar_t key[512], un[MAX_PATH * 2], cmd[MAX_PATH * 2 + 32];
        HKEY hk;
        swprintf(key, 512, L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\%ls", m_id);
        if (RegCreateKeyExW(HKEY_CURRENT_USER, key, 0, NULL, 0, KEY_WRITE, NULL, &hk, NULL) == ERROR_SUCCESS) {
            swprintf(un, MAX_PATH * 2, L"%ls\\uninstall.exe", install_dir);
            swprintf(cmd, MAX_PATH * 2 + 32, L"\"%ls\" /uninstall", un);
            RegSetValueExW(hk, L"DisplayName", 0, REG_SZ, (BYTE *)m_title, (DWORD)((wcslen(m_title) + 1) * 2));
            RegSetValueExW(hk, L"DisplayVersion", 0, REG_SZ, (BYTE *)m_version, (DWORD)((wcslen(m_version) + 1) * 2));
            if (m_publisher[0]) RegSetValueExW(hk, L"Publisher", 0, REG_SZ, (BYTE *)m_publisher, (DWORD)((wcslen(m_publisher) + 1) * 2));
            if (m_website[0]) RegSetValueExW(hk, L"URLInfoAbout", 0, REG_SZ, (BYTE *)m_website, (DWORD)((wcslen(m_website) + 1) * 2));
            RegSetValueExW(hk, L"DisplayIcon", 0, REG_SZ, (BYTE *)icon_path, (DWORD)((wcslen(icon_path) + 1) * 2));
            RegSetValueExW(hk, L"InstallLocation", 0, REG_SZ, (BYTE *)install_dir, (DWORD)((wcslen(install_dir) + 1) * 2));
            RegSetValueExW(hk, L"UninstallString", 0, REG_SZ, (BYTE *)cmd, (DWORD)((wcslen(cmd) + 1) * 2));
            DWORD one = 1, kb = (DWORD)((self_size - stub_size) / 1024 + 1);
            RegSetValueExW(hk, L"NoModify", 0, REG_DWORD, (BYTE *)&one, 4);
            RegSetValueExW(hk, L"NoRepair", 0, REG_DWORD, (BYTE *)&one, 4);
            RegSetValueExW(hk, L"EstimatedSize", 0, REG_DWORD, (BYTE *)&kb, 4);
            RegCloseKey(hk);
        }
    }
    {
        char head[600];
        char id8[300], t8[600];
        WideCharToMultiByte(CP_UTF8, 0, m_id, -1, id8, sizeof(id8), NULL, NULL);
        WideCharToMultiByte(CP_UTF8, 0, m_title, -1, t8, sizeof(t8), NULL, NULL);
        snprintf(head, sizeof(head), "STROKA-UNINSTALL 1\nid\t%s\ntitle\t%s\n", id8, t8);
        size_t hl = strlen(head);
        unsigned char *all = (unsigned char *)malloc(hl + loglen);
        memcpy(all, head, hl); memcpy(all + hl, log, loglen);
        swprintf(path, MAX_PATH * 2, L"%ls\\uninstall.dat", install_dir);
        write_file(path, all, (DWORD)(hl + loglen));
        free(all);
    }
    free(log);
    if (h_progress) SendMessageW(h_progress, PBM_SETPOS, total + 3, 0);
    return 1;
}

static void run_program(void) {
    wchar_t exe_rel[260], exe_path[MAX_PATH * 2];
    wcscpy(exe_rel, m_exe);
    for (wchar_t *c = exe_rel; *c; c++) if (*c == L'/') *c = L'\\';
    swprintf(exe_path, MAX_PATH * 2, L"%ls\\%ls", install_dir, exe_rel);
    ShellExecuteW(NULL, L"open", exe_path, NULL, install_dir, SW_SHOWNORMAL);
}

static int do_uninstall(void) {
    wchar_t me[MAX_PATH * 2], dir[MAX_PATH * 2], datp[MAX_PATH * 2], title[256] = L"программу", id[128] = L"";
    GetModuleFileNameW(NULL, me, MAX_PATH * 2);
    wcscpy(dir, me);
    wchar_t *sl = wcsrchr(dir, L'\\');
    if (sl) *sl = 0;
    swprintf(datp, MAX_PATH * 2, L"%ls\\uninstall.dat", dir);
    HANDLE h = CreateFileW(datp, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (h == INVALID_HANDLE_VALUE) { if (!silent) MessageBoxW(NULL, L"Не найден список установленных файлов (uninstall.dat).", L"Удаление", MB_ICONERROR); return 1; }
    DWORD n = GetFileSize(h, NULL), got = 0;
    char *s = (char *)calloc(n + 1, 1);
    ReadFile(h, s, n, &got, NULL);
    CloseHandle(h);
    if (strncmp(s, "STROKA-UNINSTALL", 16) != 0) { if (!silent) MessageBoxW(NULL, L"Файл uninstall.dat повреждён.", L"Удаление", MB_ICONERROR); return 1; }
    for (char *p = s; p && *p;) {
        char *nl = strchr(p, '\n'); if (nl) *nl = 0;
        char *tab = strchr(p, '\t');
        if (tab) {
            *tab = 0;
            if (!strcmp(p, "title")) utf8w(tab + 1, -1, title, 256);
            else if (!strcmp(p, "id")) utf8w(tab + 1, -1, id, 128);
            *tab = '\t';
        }
        if (nl) *nl = '\n';
        p = nl ? nl + 1 : NULL;
    }
    if (!silent) {
        wchar_t q[512];
        swprintf(q, 512, L"Удалить «%ls» с этого компьютера?", title);
        if (MessageBoxW(NULL, q, L"Удаление программы", MB_ICONQUESTION | MB_YESNO) != IDYES) return 0;
    }
    for (char *p = s; p && *p;) {
        char *nl = strchr(p, '\n'); if (nl) *nl = 0;
        if ((p[0] == 'F' || p[0] == 'L' || p[0] == 'D') && p[1] == '\t') {
            wchar_t w[MAX_PATH * 2], full[MAX_PATH * 2];
            utf8w(p + 2, -1, w, MAX_PATH * 2);
            if (p[0] == 'F' && !wcsstr(w, L"..")) { swprintf(full, MAX_PATH * 2, L"%ls\\%ls", dir, w); DeleteFileW(full); }
            else if (p[0] == 'L') DeleteFileW(w);
            else if (p[0] == 'D') RemoveDirectoryW(w);
        }
        p = nl ? nl + 1 : NULL;
    }
    for (char *p = s; p && *p;) {
        char *nl = strchr(p, '\n'); if (nl) *nl = 0;
        if (p[0] == 'F' && p[1] == '\t') {
            wchar_t w[MAX_PATH * 2], full[MAX_PATH * 2];
            utf8w(p + 2, -1, w, MAX_PATH * 2);
            swprintf(full, MAX_PATH * 2, L"%ls\\%ls", dir, w);
            wchar_t *cut;
            while ((cut = wcsrchr(full, L'\\')) && (size_t)(cut - full) > wcslen(dir)) { *cut = 0; if (!RemoveDirectoryW(full)) break; }
        }
        p = nl ? nl + 1 : NULL;
    }
    DeleteFileW(datp);
    if (id[0]) {
        wchar_t key[512];
        swprintf(key, 512, L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\%ls", id);
        RegDeleteKeyW(HKEY_CURRENT_USER, key);
    }
    {
        wchar_t cmd[MAX_PATH * 6], sys[MAX_PATH];
        GetSystemDirectoryW(sys, MAX_PATH);
        swprintf(cmd, MAX_PATH * 6, L"\"%ls\\cmd.exe\" /c ping -n 3 127.0.0.1 >nul & del /f /q \"%ls\" & rmdir \"%ls\"", sys, me, dir);
        STARTUPINFOW si; PROCESS_INFORMATION pi;
        ZeroMemory(&si, sizeof(si)); si.cb = sizeof(si); si.dwFlags = STARTF_USESHOWWINDOW; si.wShowWindow = SW_HIDE;
        if (CreateProcessW(NULL, cmd, NULL, NULL, FALSE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) { CloseHandle(pi.hThread); CloseHandle(pi.hProcess); }
        else MoveFileExW(me, NULL, MOVEFILE_DELAY_UNTIL_REBOOT);
    }
    if (!silent) {
        wchar_t q[512];
        swprintf(q, 512, L"Программа «%ls» удалена.", title);
        MessageBoxW(NULL, q, L"Удаление программы", MB_ICONINFORMATION);
    }
    free(s);
    return 0;
}

static void load_banner(void) {
    if (!m_banner[0]) return;
    PFile *b = find_filew(m_banner);
    if (!b || b->size < 54 || b->data[0] != 'B' || b->data[1] != 'M') return;
    unsigned int off = ru32(b->data + 10);
    BITMAPINFOHEADER *bih = (BITMAPINFOHEADER *)(b->data + 14);
    if (bih->biBitCount != 32 && bih->biBitCount != 24) return;
    if (off >= b->size) return;
    banner_bi = (BITMAPINFO *)bih;
    banner_bits = b->data + off;
    banner_w = bih->biWidth;
    banner_h = bih->biHeight < 0 ? -bih->biHeight : bih->biHeight;
}

static HWND mk(const wchar_t *cls, const wchar_t *text, DWORD style, int x, int y, int w, int h, int id, HFONT f) {
    HWND c = CreateWindowExW(0, cls, text, WS_CHILD | WS_VISIBLE | style, x, y, w, h, hwnd_main, (HMENU)(INT_PTR)id, GetModuleHandleW(NULL), NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)f, TRUE);
    return c;
}

static void update_install_enabled(void) {
    if (h_accept) EnableWindow(h_install, SendMessageW(h_accept, BM_GETCHECK, 0, 0) == BST_CHECKED);
}

static int CALLBACK browse_cb(HWND h, UINT msg, LPARAM l, LPARAM data) {
    if (msg == BFFM_INITIALIZED) SendMessageW(h, BFFM_SETSELECTIONW, TRUE, data);
    return 0;
}

static int installed_ok = 0;

static LRESULT CALLBACK wndproc(HWND h, UINT msg, WPARAM w, LPARAM l) {
    switch (msg) {
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC dc = BeginPaint(h, &ps);
        RECT r = {0, 0, 170, 1000};
        HBRUSH br = CreateSolidBrush(RGB(36, 99, 235));
        FillRect(dc, &r, br);
        DeleteObject(br);
        if (banner_bi) {
            SetStretchBltMode(dc, HALFTONE);
            int side = 150;
            StretchDIBits(dc, 10, 20, side, side * banner_h / (banner_w ? banner_w : 1), 0, 0, banner_w, banner_h, banner_bits, banner_bi, DIB_RGB_COLORS, SRCCOPY);
        }
        EndPaint(h, &ps);
        return 0;
    }
    case WM_COMMAND:
        switch (LOWORD(w)) {
        case ID_ACCEPT: update_install_enabled(); break;
        case ID_BROWSE: {
            BROWSEINFOW bi;
            wchar_t sel[MAX_PATH];
            ZeroMemory(&bi, sizeof(bi));
            bi.hwndOwner = h;
            bi.lpszTitle = L"Куда установить программу?";
            bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
            GetWindowTextW(h_dir, install_dir, MAX_PATH * 2);
            bi.lpfn = browse_cb; bi.lParam = (LPARAM)install_dir;
            CoInitialize(NULL);
            LPITEMIDLIST pidl = SHBrowseForFolderW(&bi);
            if (pidl && SHGetPathFromIDListW(pidl, sel)) {
                wchar_t nm[256], full[MAX_PATH * 2];
                safe_name(m_title, nm, 256);
                size_t L = wcslen(sel);
                swprintf(full, MAX_PATH * 2, L"%ls%ls%ls", sel, (L && sel[L - 1] == L'\\') ? L"" : L"\\", nm);
                SetWindowTextW(h_dir, full);
                CoTaskMemFree(pidl);
            }
            CoUninitialize();
            break;
        }
        case ID_CANCEL:
            if (installed_ok) { DestroyWindow(h); break; }
            if (MessageBoxW(h, L"Прервать установку?", m_title, MB_ICONQUESTION | MB_YESNO) == IDYES) DestroyWindow(h);
            break;
        case ID_INSTALL:
            if (installed_ok) { if (SendMessageW(h_run, BM_GETCHECK, 0, 0) == BST_CHECKED) run_program(); DestroyWindow(h); break; }
            GetWindowTextW(h_dir, install_dir, MAX_PATH * 2);
            if (!install_dir[0]) { MessageBoxW(h, L"Укажите папку установки.", m_title, MB_ICONWARNING); break; }
            opt_desktop = SendMessageW(h_desk, BM_GETCHECK, 0, 0) == BST_CHECKED;
            opt_startmenu = SendMessageW(h_menu, BM_GETCHECK, 0, 0) == BST_CHECKED;
            EnableWindow(h_install, FALSE); EnableWindow(h_browse, FALSE); EnableWindow(h_dir, FALSE);
            EnableWindow(h_desk, FALSE); EnableWindow(h_menu, FALSE); if (h_accept) EnableWindow(h_accept, FALSE);
            if (do_install()) {
                installed_ok = 1;
                wchar_t done[512];
                swprintf(done, 512, L"Готово! «%ls» установлена.", m_title);
                set_status(done);
                SetWindowTextW(h_install, L"Готово");
                SetWindowTextW(h_cancel, L"Закрыть");
                EnableWindow(h_install, TRUE);
            } else {
                EnableWindow(h_install, TRUE); EnableWindow(h_browse, TRUE); EnableWindow(h_dir, TRUE);
                EnableWindow(h_desk, TRUE); EnableWindow(h_menu, TRUE); if (h_accept) EnableWindow(h_accept, TRUE);
                set_status(L"Установка не выполнена.");
            }
            break;
        }
        return 0;
    case WM_CTLCOLORSTATIC: {
        HDC dc = (HDC)w;
        SetBkMode(dc, TRANSPARENT);
        return (LRESULT)GetSysColorBrush(COLOR_WINDOW);
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, w, l);
}

static void build_ui(HINSTANCE inst) {
    INITCOMMONCONTROLSEX icc = { sizeof(icc), ICC_PROGRESS_CLASS | ICC_STANDARD_CLASSES };
    InitCommonControlsEx(&icc);
    WNDCLASSEXW wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = wndproc;
    wc.hInstance = inst;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = GetSysColorBrush(COLOR_WINDOW);
    wc.lpszClassName = L"StrokaSetup";
    wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(1));
    wc.hIconSm = wc.hIcon;
    RegisterClassExW(&wc);
    wchar_t cap[400];
    swprintf(cap, 400, L"Установка — %ls", m_title);
    int W = 620, H = m_license[0] ? 500 : 400;
    RECT r = {0, 0, W, H};
    AdjustWindowRect(&r, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX, FALSE);
    hwnd_main = CreateWindowExW(0, L"StrokaSetup", cap, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left, r.bottom - r.top, NULL, NULL, inst, NULL);
    NONCLIENTMETRICSW ncm;
    ncm.cbSize = sizeof(ncm);
    SystemParametersInfoW(SPI_GETNONCLIENTMETRICS, sizeof(ncm), &ncm, 0);
    font_normal = CreateFontIndirectW(&ncm.lfMessageFont);
    LOGFONTW lf = ncm.lfMessageFont;
    lf.lfHeight = lf.lfHeight * 3 / 2; lf.lfWeight = FW_BOLD;
    font_title = CreateFontIndirectW(&lf);
    int x = 190, y = 18, cw = W - x - 20;
    wchar_t t[400];
    swprintf(t, 400, L"Установка «%ls»", m_title);
    mk(L"STATIC", t, 0, x, y, cw, 30, 0, font_title); y += 36;
    if (m_publisher[0]) swprintf(t, 400, L"Версия %ls · %ls", m_version, m_publisher);
    else swprintf(t, 400, L"Версия %ls", m_version);
    mk(L"STATIC", t, 0, x, y, cw, 20, 0, font_normal); y += 30;
    if (m_license[0]) {
        PFile *lic = find_filew(m_license);
        wchar_t *lt = lic ? utf8w_alloc((const char *)lic->data, (int)lic->size) : utf8w_alloc("", 0);
        size_t n = wcslen(lt), k = 0;
        wchar_t *crlf = (wchar_t *)calloc(n * 2 + 1, sizeof(wchar_t));
        for (size_t i = 0; i < n; i++) { if (lt[i] == L'\n' && (i == 0 || lt[i - 1] != L'\r')) crlf[k++] = L'\r'; crlf[k++] = lt[i]; }
        h_license = mk(L"EDIT", crlf, WS_BORDER | WS_VSCROLL | ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL, x, y, cw, 110, 0, font_normal); y += 116;
        h_accept = mk(L"BUTTON", L"Я принимаю условия лицензионного соглашения", BS_AUTOCHECKBOX, x, y, cw, 22, ID_ACCEPT, font_normal); y += 30;
        free(lt); free(crlf);
    }
    mk(L"STATIC", L"Папка установки:", 0, x, y, cw, 20, 0, font_normal); y += 22;
    h_dir = mk(L"EDIT", install_dir, WS_BORDER | ES_AUTOHSCROLL, x, y, cw - 96, 24, 0, font_normal);
    h_browse = mk(L"BUTTON", L"Обзор…", BS_PUSHBUTTON, x + cw - 88, y - 1, 88, 26, ID_BROWSE, font_normal); y += 36;
    h_desk = mk(L"BUTTON", L"Создать ярлык на рабочем столе", BS_AUTOCHECKBOX, x, y, cw, 22, 0, font_normal); y += 26;
    h_menu = mk(L"BUTTON", L"Добавить в меню «Пуск»", BS_AUTOCHECKBOX, x, y, cw, 22, 0, font_normal); y += 26;
    h_run = mk(L"BUTTON", L"Запустить программу после установки", BS_AUTOCHECKBOX, x, y, cw, 22, 0, font_normal); y += 34;
    SendMessageW(h_desk, BM_SETCHECK, opt_desktop ? BST_CHECKED : BST_UNCHECKED, 0);
    SendMessageW(h_menu, BM_SETCHECK, opt_startmenu ? BST_CHECKED : BST_UNCHECKED, 0);
    SendMessageW(h_run, BM_SETCHECK, opt_run ? BST_CHECKED : BST_UNCHECKED, 0);
    h_progress = CreateWindowExW(0, PROGRESS_CLASSW, NULL, WS_CHILD | WS_VISIBLE, x, y, cw, 18, hwnd_main, NULL, inst, NULL); y += 24;
    h_status = mk(L"STATIC", L"Нажмите «Установить», чтобы начать.", SS_ENDELLIPSIS, x, y, cw, 20, 0, font_normal);
    h_install = mk(L"BUTTON", L"Установить", BS_DEFPUSHBUTTON, W - 230, H - 46, 110, 30, ID_INSTALL, font_normal);
    h_cancel = mk(L"BUTTON", L"Отмена", BS_PUSHBUTTON, W - 110, H - 46, 90, 30, ID_CANCEL, font_normal);
    update_install_enabled();
    ShowWindow(hwnd_main, SW_SHOW);
    UpdateWindow(hwnd_main);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, LPWSTR cmd, int show) {
    int argc = 0, uninstall = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    wchar_t forced_dir[MAX_PATH * 2] = L"";
    for (int i = 1; i < argc; i++) {
        if (!_wcsicmp(argv[i], L"/S") || !_wcsicmp(argv[i], L"/SILENT")) silent = 1;
        else if (!_wcsicmp(argv[i], L"/uninstall")) uninstall = 1;
        else if (!_wcsnicmp(argv[i], L"/D=", 3)) wcsncpy(forced_dir, argv[i] + 3, MAX_PATH * 2 - 1);
        else if (!_wcsicmp(argv[i], L"/norun")) opt_run = -1;
    }
    int has_payload = read_self();
    if (uninstall || !has_payload) {
        if (!has_payload && !uninstall) {
            MessageBoxW(NULL, L"Этот файл — программа удаления. Запустите его из меню «Пуск» или из «Приложения и возможности».", L"Удаление", MB_ICONINFORMATION);
            return 0;
        }
        return do_uninstall();
    }
    int norun = opt_run == -1;
    parse_meta();
    if (norun) opt_run = 0;
    if (forced_dir[0]) wcscpy(install_dir, forced_dir); else default_dir();
    if (silent) {
        int ok = do_install();
        return ok ? 0 : 2;
    }
    load_banner();
    build_ui(inst);
    MSG m;
    while (GetMessageW(&m, NULL, 0, 0) > 0) {
        if (!IsDialogMessageW(hwnd_main, &m)) { TranslateMessage(&m); DispatchMessageW(&m); }
    }
    return 0;
}
