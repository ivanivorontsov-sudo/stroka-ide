#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
ZIP="${1:?Нет архива проекта}"
TARGET="${2:-android-apk}"
DOWNLOADS="$HOME/storage/downloads/StrokaBuild"
SDK="$HOME/android-sdk"
WORK="$HOME/.cache/stroka-build/current"
mkdir -p "$DOWNLOADS/output"
LOG="$DOWNLOADS/output/build.log"
exec > >(tee "$LOG") 2>&1
fail() { echo; echo "ОШИБКА: $*"; echo "Подробный журнал: $LOG"; exit 1; }
[ -f "$ZIP" ] || fail "Архив проекта не найден: $ZIP"
[ -d "$SDK/platforms/android-36" ] || fail "Android SDK не установлен. Один раз выполните в Termux: bash ~/storage/downloads/StrokaBuild/setup-termux.sh"
command -v java >/dev/null || fail "Не найден Java. Выполните setup-termux.sh"
command -v aapt2 >/dev/null || fail "Не найден ARM64 AAPT2. Выполните setup-termux.sh"
rm -rf "$WORK"
mkdir -p "$WORK"
unzip -q "$ZIP" -d "$WORK"
ROOT=""
while IFS= read -r -d '' f; do ROOT="$(dirname "$f")"; break; done < <(find "$WORK" -maxdepth 4 \( -name settings.gradle -o -name settings.gradle.kts \) -print0)
if [ -z "$ROOT" ]; then
  while IFS= read -r -d '' f; do ROOT="$(dirname "$f")"; break; done < <(find "$WORK" -maxdepth 4 \( -name build.gradle -o -name build.gradle.kts \) -print0)
fi
if [ -z "$ROOT" ]; then
  PYFILE="$(find "$WORK" -maxdepth 3 -type f -name 'main.py' | head -n 1 || true)"
  if [ -z "$PYFILE" ]; then
    PYFILE="$(find "$WORK" -maxdepth 3 -type f -name '*.py' ! -name 'stroka_runner.py' ! -name 'stroka_tkinter.py' | head -n 1 || true)"
  fi
  [ -n "$PYFILE" ] || fail "Не найден Android Gradle-проект или Python-файл."
  echo "Обнаружен Python-проект без файлов сборки: $PYFILE"
  APP="$HOME/.cache/stroka-build/python-app"
  rm -rf "$APP"
  PYROOT="$(dirname "$PYFILE")"
  APP_SLUG="$(basename "$PYFILE" .py | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9_]/_/g')"
  if [[ ! "$APP_SLUG" =~ ^[a-z] ]]; then APP_SLUG="app_$APP_SLUG"; fi
  mkdir -p "$APP/app/src/main/java/com/example/strokabuild" "$APP/app/src/main/python" "$APP/app/src/main/assets"
  cp -R "$PYROOT"/. "$APP/app/src/main/python/"
  cp "$PYFILE" "$APP/app/src/main/python/main.py"
  cp -R "$PYROOT"/. "$APP/app/src/main/assets/"
  cp "$PYFILE" "$APP/app/src/main/assets/main.py"
  if [ -f "$PYROOT/requirements.txt" ]; then cp "$PYROOT/requirements.txt" "$APP/requirements.txt"; else : > "$APP/requirements.txt"; fi
  [ -f "$DOWNLOADS/stroka_runner.py" ] || fail "Не найден stroka_runner.py. Обновите приложение «Строка»."
  [ -f "$DOWNLOADS/stroka_tkinter.py" ] || fail "Не найден stroka_tkinter.py. Обновите приложение «Строка»."
  cp "$DOWNLOADS/stroka_runner.py" "$APP/app/src/main/python/"
  cp "$DOWNLOADS/stroka_tkinter.py" "$APP/app/src/main/python/"
  cp "$PYFILE" "$APP/app/src/main/assets/main.py"
  cat > "$APP/settings.gradle.kts" <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "PythonAndroidApp"
include(":app")
EOF
  cat > "$APP/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.chaquo.python") version "16.1.0" apply false
}
EOF
  cat > "$APP/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF
  cat > "$APP/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}
android {
    namespace = "com.example.strokabuild"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.strokabuild"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
chaquopy { defaultConfig { version = "3.12"; pip { install("-r", "requirements.txt") } } }
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
EOF
  sed -i "s|applicationId = \"com.example.strokabuild\"|applicationId = \"com.example.stroka.$APP_SLUG\"|" "$APP/app/build.gradle.kts"
  cat > "$APP/app/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="Python app">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF
  cat > "$APP/app/src/main/java/com/example/strokabuild/MainActivity.kt" <<'EOF'
package com.example.strokabuild

import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val events = LinkedBlockingQueue<String>()
    private val bridge = PythonBridge()

    inner class PythonBridge {
        fun out(value: String, error: Boolean) { android.util.Log.d("PythonApp", value) }
        fun showPreview(html: String) { runOnUiThread { web.loadDataWithBaseURL("https://app.local/", html, "text/html", "utf-8", null) } }
        fun waitUiEvent(): String = events.take()
        fun postUiEvent(value: String) { events.offer(value) }
        fun readLine(prompt: String): String = ""
    }

    inner class WebBridge {
        @JavascriptInterface fun onUiEvent(value: String) { events.offer(value) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(WebBridge(), "Stroka")
        }
        setContentView(web)
        web.loadData("<meta name='viewport' content='width=device-width,initial-scale=1'><body style='font:16px sans-serif;padding:24px'>Запуск приложения…</body>", "text/html", "utf-8")
        Thread({
            try {
                copyAssetTree("", filesDir)
                if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
                val code = File(filesDir, "main.py").readText()
                Python.getInstance().getModule("stroka_runner").callAttr("run", code, "main.py", filesDir.absolutePath, bridge)
            } catch (failure: Throwable) {
                runOnUiThread {
                    val message = failure.message ?: failure.javaClass.simpleName
                    web.loadData("<meta name='viewport' content='width=device-width,initial-scale=1'><pre style='white-space:pre-wrap;color:#b00020;padding:20px'>" + message + "</pre>", "text/html", "utf-8")
                }
            }
        }, "python-main").apply { isDaemon = true }.start()
    }

    private fun copyAssetTree(path: String, destination: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(path).use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            val childPath = if (path.isEmpty()) child else "$path/$child"
            copyAssetTree(childPath, File(destination, child))
        }
    }

    override fun onDestroy() {
        events.offer("__stop__")
        web.destroy()
        super.onDestroy()
    }
}
EOF
  ROOT="$APP"
fi
[ -n "$ROOT" ] || fail "Не найден Gradle Android-проект (settings.gradle или build.gradle)."
echo "Проект: $ROOT"
echo "Цель: $TARGET"
printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/local.properties"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
if [ -x "$ROOT/gradlew" ]; then
  chmod +x "$ROOT/gradlew"
  GRADLE="$ROOT/gradlew"
else
  # AGP 8.7.x needs Gradle 8.9; Termux's rolling package may be newer.
  GRADLE_HOME_PINNED="$HOME/.cache/stroka/gradle-8.9"
  if [ ! -x "$GRADLE_HOME_PINNED/bin/gradle" ]; then
    mkdir -p "$HOME/.cache/stroka"
    TMP_GRADLE="$HOME/.cache/stroka/gradle-8.9.zip"
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$TMP_GRADLE" || fail "Не удалось скачать Gradle 8.9."
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256" -o "$TMP_GRADLE.sha256" || fail "Не удалось получить контрольную сумму Gradle."
    EXPECTED_SHA="$(tr -d '[:space:]' < "$TMP_GRADLE.sha256")"
    echo "$EXPECTED_SHA  $TMP_GRADLE" | sha256sum -c - || fail "Контрольная сумма Gradle 8.9 не совпала."
    unzip -q "$TMP_GRADLE" -d "$HOME/.cache/stroka"
    rm -f "$TMP_GRADLE" "$TMP_GRADLE.sha256"
  fi
  GRADLE="$GRADLE_HOME_PINNED/bin/gradle"
fi
cd "$ROOT"
case "$TARGET" in
  android-apk)
    "$GRADLE" --no-daemon --max-workers=2 --stacktrace assembleDebug
    ;;
  android-aab)
    "$GRADLE" --no-daemon --max-workers=2 --stacktrace bundleDebug
    ;;
  *) fail "Неподдерживаемая локальная цель: $TARGET" ;;
esac
mkdir -p "$DOWNLOADS/output"
if [ "$TARGET" = "android-apk" ]; then
  mapfile -d '' FILES < <(find . -path '*/build/outputs/apk/debug/*.apk' -type f -print0)
else
  mapfile -d '' FILES < <(find . -path '*/build/outputs/bundle/debug/*.aab' -type f -print0)
fi
[ "${#FILES[@]}" -gt 0 ] || fail "Gradle завершился без ожидаемого файла результата."
for f in "${FILES[@]}"; do
  cp "$f" "$DOWNLOADS/output/"
  echo "РЕЗУЛЬТАТ: $DOWNLOADS/output/$(basename "$f")"
  sha256sum "$DOWNLOADS/output/$(basename "$f")"
done
echo "ЛОКАЛЬНАЯ СБОРКА УСПЕШНО ЗАВЕРШЕНА."
