#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

echo "== Подготовка локального сборщика Строки =="

case "$(uname -m)" in
  aarch64|arm64) ;;
  *) echo "Ошибка: локальная сборка поддерживает только ARM64 (aarch64)."; exit 2 ;;
esac

pkg update -y
pkg install -y openjdk-17 gradle aapt2 aapt apksigner unzip zip curl coreutils nodejs-lts

SDK="$HOME/android-sdk"
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  TMP="$TMPDIR/stroka-cmdline-tools"
  mkdir -p "$TMP/unpack"
  echo "Скачиваю официальные Android command-line tools..."
  curl -fL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -o "$TMP/tools.zip"
  echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  $TMP/tools.zip" | sha256sum -c -
  unzip -q "$TMP/tools.zip" -d "$TMP/unpack"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TMP/unpack/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
echo "Устанавливаю Android SDK API 36 и Build Tools 36.0.0..."
yes | bash "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
bash "$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-36" "build-tools;36.0.0"

mkdir -p "$HOME/.gradle" "$HOME/.termux"
touch "$HOME/.gradle/gradle.properties"
if ! grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then
  printf '\nandroid.aapt2FromMavenOverride=%s/bin/aapt2\n' "$PREFIX" >> "$HOME/.gradle/gradle.properties"
fi
if ! grep -q 'ANDROID_HOME=.*android-sdk' "$HOME/.bashrc" 2>/dev/null; then
  printf '\nexport ANDROID_HOME="$HOME/android-sdk"\nexport ANDROID_SDK_ROOT="$ANDROID_HOME"\n' >> "$HOME/.bashrc"
fi

# Run this script once from the Termux terminal to enable the bridge.
if ! grep -q '^allow-external-apps=true$' "$HOME/.termux/termux.properties" 2>/dev/null; then
  printf '\nallow-external-apps=true\n' >> "$HOME/.termux/termux.properties"
fi
termux-reload-settings || true
termux-setup-storage || true

echo
echo "ГОТОВО. Разрешите Android разрешение «Запуск команд в Termux» для приложения «Строка»."
echo "Затем вернитесь в Строку и нажмите «Собрать APK / AAB прямо на телефоне»."
echo "Проверка: java -version; gradle --version; aapt2 version"
