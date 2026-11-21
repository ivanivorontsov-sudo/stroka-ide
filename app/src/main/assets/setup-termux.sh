#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "== Подготовка локального сборщика Строки =="
pkg update -y
pkg install -y openjdk-17 gradle aapt2 aapt apksigner unzip zip curl

SDK="$HOME/android-sdk"
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  TMP="$TMPDIR/stroka-cmdline-tools"
  mkdir -p "$TMP"
  echo "Скачиваю официальные Android command-line tools..."
  curl -fL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -o "$TMP/tools.zip"\n  echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  $TMP/tools.zip" | sha256sum -c -
  mkdir -p "$TMP/unpack"
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

mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
if ! grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties"; then
  printf '\nandroid.aapt2FromMavenOverride=%s/bin/aapt2\n' "$PREFIX" >> "$HOME/.gradle/gradle.properties"
fi
if ! grep -q 'ANDROID_HOME=.*android-sdk' "$HOME/.bashrc" 2>/dev/null; then
  printf '\nexport ANDROID_HOME="$HOME/android-sdk"\nexport ANDROID_SDK_ROOT="$ANDROID_HOME"\n' >> "$HOME/.bashrc"
fi
mkdir -p "$HOME/storage"
termux-setup-storage || true

echo
echo "ГОТОВО. Закройте и снова откройте Termux, затем вернитесь в Строку."
echo "Проверка: java -version; gradle --version; aapt2 version"
