# Локальная сборка Android-проектов на смартфоне

Строка умеет передавать проект в установленный Termux и запускать Gradle на ARM64 Android-устройстве. Это отдельный локальный путь, не GitHub Actions и не эмуляция Windows.

## Поддержка

- Android APK: `assembleDebug`
- Android App Bundle: `bundleDebug`
- Результаты: `Download/StrokaBuild/output/`
- Журнал: `Download/StrokaBuild/output/build.log`

Локальная сборка требует Android 10+ и ARM64 Termux. В текущем варианте не собираются Windows EXE, Linux ELF и произвольные не-Gradle проекты. Для них остаётся облачный workflow.

## Однократная настройка

1. Установить Termux из F-Droid или официальной страницы релизов: https://github.com/termux/termux-app
2. Открыть Termux вручную и выполнить `termux-setup-storage`, затем разрешить доступ к файлам.
3. В Termux выполнить:
   ```sh
   mkdir -p ~/.termux
   printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
   termux-reload-settings
   ```
4. В системных настройках Android открыть приложения → Строка → разрешения → дополнительные разрешения и разрешить «Запуск команд в Termux» (`com.termux.permission.RUN_COMMAND`).
5. В Строке открыть «Собрать проект» и нажать «Подготовить локальную сборку (Termux)». Скрипт установит JDK 17, Gradle, ARM64 AAPT2, apksigner и официальные Android command-line tools / SDK API 36.
6. После сообщения «ГОТОВО» вернуться в Строку, выбрать APK или AAB и нажать «Собрать APK / AAB прямо на телефоне».

## Как это работает

Строка архивирует исходную папку проекта (исключая .git, build, .gradle, node_modules и IDE caches), публикует архив и скрипт в общую папку Downloads/StrokaBuild через MediaStore, а затем вызывает Termux RunCommandService. Termux распаковывает проект в собственный cache, прописывает локальный SDK path и запускает Gradle. APK/AAB и лог сохраняются в общей папке Downloads.

В ARM64-среде используется нативный Termux AAPT2 вместо стандартного Linux x86_64 AAPT2, который не может выполняться на ARM64 Android. Остальные SDK-инструменты запускаются в поддерживаемом Termux окружении. SDK скачивается только с официального домена Google; SHA-256 command-line tools проверяется до распаковки.

## Ограничения и безопасность

- Termux должен быть установлен отдельно: Android не позволяет приложению Строка читать приватную папку другого приложения или самостоятельно устанавливать его rootfs.
- `allow-external-apps=true` разрешает сторонним приложениям запускать команды в Termux. Включайте это только если доверяете приложению-отправителю. Строка передаёт только собственные build scripts и архив выбранного проекта.
- Для установки обновлений поверх существующего приложения нужна та же подпись. Локальная сборка сейчас использует стандартную debug-подпись; это не заменяет приватный release keystore.
- Первый запуск загружает SDK и зависимости и может занять несколько гигабайт и несколько минут. Оставьте свободное место и не закрывайте Termux во время сборки.
- Android может ограничивать длительные фоновые процессы энергосбережением; Termux запускает сборку в foreground-сессии.
- Проекты, которым нужны специфичные NDK host tools, проприетарные плагины или неподдерживаемые native executables, могут не собраться. Смотрите полный `build.log`.

## Источники

- Termux RunCommand Intent: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- ARM64 build stack example: https://github.com/Xtratter/termux-android-build
- Android command-line tools: https://developer.android.com/studio
- Android AAPT2: https://developer.android.com/tools/aapt2
