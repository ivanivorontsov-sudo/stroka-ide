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
[ -n "$ROOT" ] || fail "Не найден Gradle Android-проект (settings.gradle или build.gradle)."
echo "Проект: $ROOT"
echo "Цель: $TARGET"
printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/local.properties"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
if [ -x "$ROOT/gradlew" ]; then
  chmod +x "$ROOT/gradlew"
  GRADLE="$ROOT/gradlew"
else
  GRADLE="$(command -v gradle)" || fail "Gradle не установлен."
fi
cd "$ROOT"
case "$TARGET" in
  android-apk)
    "$GRADLE" --no-daemon --stacktrace assembleDebug
    ;;
  android-aab)
    "$GRADLE" --no-daemon --stacktrace bundleDebug
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
