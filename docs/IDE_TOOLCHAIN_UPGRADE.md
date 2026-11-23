# Строка: план и границы расширения IDE

Этот документ фиксирует архитектуру расширения. Он намеренно разделяет то, что уже реализовано в ветке, то, что запускается только через GitHub Actions, и то, что требует отдельного движка.

## Уже добавлено в этой ветке

- **Kotlin/JVM APK build path**: Android-патченный Kotlin compiler вызывается непосредственно на устройстве; результат Kotlin-компиляции вместе с Kotlin runtime передаётся в D8. Первая поддерживаемая форма — один `.kt`-файл с Android `Activity`. Kotlin Script (`.kts`), Kotlin/Native, Kotlin/JS и Kotlin/Wasm не выдаются за поддерживаемые Android APK targets.
- **Java build path** остаётся на Eclipse ECJ → D8 → apksig.
- **Универсальная вкладка предпросмотра**: если для языка нет исполняемого адаптера, она показывает исходник в безопасно экранированном HTML-представлении и явно сообщает, что это предпросмотр кода, а не запуск программы.
- **AI project context**: чат получает ограниченный текстовый снимок локального проекта (до 80 файлов / 90 000 символов), исключая .git, build, node_modules, бинарные файлы и зависимости. Сейчас кнопка «Применить» по-прежнему применяет один основной блок кода в открытый файл; многофайловое применение должно быть отдельным протоколом патчей с подтверждением пользователя.
- **Android system bars**: базовая WebView-оболочка генерируемого APK учитывает верхний и нижний системные отступы на устройствах с edge-to-edge, включая Android 15.
- **APK build profile**: задаются `versionName` и `versionCode`; можно выбрать фото PNG/JPEG/WebP из галереи или случайный emoji. Фото вписывается в квадратную иконку с сохранением пропорций, создаются density-specific PNG resources.
- **GitHub Actions**: добавлена проверка сборки на PR и workflow для создания стандартного Windows Setup.exe через Inno Setup. Веб-запуск доступен на странице Actions репозитория.

## Матрица компиляции

| Вход | Исполнение/сборка | Реальный путь |
|---|---|---|
| `.pon` | запуск / APK / portable EXE | компилятор «Понятный» → JS runtime / QuickJS |
| `.js` | запуск / APK / portable EXE | WebView JS / QuickJS |
| `.html`, `.htm` | предпросмотр / APK | WebView |
| `.py`, `.pyw` | запуск / APK | Chaquopy 3.12; APK использует Python runtime из установленной Строки |
| `.java` | APK | Eclipse ECJ → D8 → apksig; для однофайловой сборки нужен Android Activity |
| `.kt` | APK | Android-patched Kotlin/JVM compiler → D8 → apksig; нужен Android Activity |
| `.kts` | не является самостоятельным APK target | Gradle Kotlin Script; нужен Gradle project runner |
| C/C++, Rust, Go, Swift, Kotlin/Native и другие native targets | пока не встроены в мобильный локальный toolchain | требуют NDK/LLVM/Go/Rust toolchain или отдельной CI-сборки под целевую ОС |

«Все варианты компиляции» не могут быть одной универсальной командой: JVM, Android DEX, native PE/ELF, JavaScript и Python используют разные toolchains и ABI. Для каждого нового target добавляется отдельный adapter с проверкой toolchain, понятной ошибкой и тестовым проектом.

## Открытые проекты и зачем они нужны

- [kotlinc-android](https://github.com/PranavPurwar/kotlinc-android) — Android-патч компилятора Kotlin/JVM и Java; используется как основа Kotlin adapter. Проверять совместимость версий compiler/runtime при обновлении.
- [CodeAssist](https://github.com/tyron12233/CodeAssist) — хороший ориентир для on-device Android IDE: Kotlin/Java, Compose/XML preview, D8/R8, project model и incremental tasks. Это архитектурный ориентир, не библиотека для слепого копирования.
- [JADX](https://github.com/skylot/jadx) — decompile DEX/APK в Java-подобный код, ресурсы и manifest; декомпиляция не гарантирует восстановление исходного проекта и может быть неполной.
- [Apktool](https://github.com/iBotPeaches/Apktool) — декодирование ресурсов/manifest и сборка APK из Smali. Официальная документация предупреждает, что запуск Apktool на Android официально не поддерживается из-за несовместимости native aapt; его нельзя объявлять встроенным Android-движком без отдельного porting spike.
- [Inno Setup](https://jrsoftware.org/isinfo.php) — открытый Windows installer builder, используется в Windows runner для Setup.exe.
- [WiX Toolset](https://github.com/wixtoolset) — альтернативный Windows Installer/MSI и bundle toolchain; следующий кандидат для MSI.
- [NSIS](https://nsis.sourceforge.io/) — дополнительный вариант EXE installer; требует отдельной проверки runner и лицензий плагинов.

## Следующие этапы

1. **Build profiles — частично готово**: versionName/versionCode, фото и emoji уже доступны; осталось сделать Android adaptive foreground/background icons, ручное кадрирование и живой предпросмотр перед сборкой.
2. **Signing identity — частично готово**: текущий PKCS#12 копируется в `filesDir/toolchain/user-apps.p12` один раз и переиспользуется после обновлений приложения. Следующий шаг — отдельный пользовательский PKCS#12 keystore, импорт/экспорт через SAF, пароль только в Android Keystore/защищённом хранилище, fingerprint и предупреждение, что потеря ключа не позволит обновлять подписанное им приложение. Не менять ключ автоматически.
3. **Multi-file projects**: проектный manifest, несколько Java/Kotlin исходников, локальные JAR/AAR и разрешение зависимостей; incremental build graph.
4. **APK import**: открыть APK, проверить ZIP-структуру, извлечь ресурсы/manifest, запустить JADX для DEX → Java и показать Smali/DEX при неудачной декомпиляции. Любые правки только для приложений, которыми пользователь владеет или имеет право модифицировать; подпись после изменения всегда новая, исходная подпись становится недействительной.
5. **AI multi-file edits**: ИИ возвращает структурированный список файловых операций (create/update/delete + diff), IDE показывает diff по каждому файлу и просит подтверждение до применения. Контекст проекта не должен автоматически давать ИИ право менять файлы.
6. **Preview adapters**: WebView для web, встроенный Python UI/runtime, JS console/DOM preview, Android layout/Compose preview через эмуляцию/интерпретацию; для остальных языков — compile/run через sandbox или честный source preview. Нельзя обещать одинаковое интерактивное исполнение для произвольного бинарного target без соответствующего runtime.
7. **GitHub Actions UI**: подключение GitHub через токен с минимальными permissions или GitHub App; список workflows/runs, dispatch с inputs, просмотр логов и скачивание artifacts. Токен не хранить в проекте и не передавать модели ИИ.
8. **Installer targets**: Inno Setup EXE, WiX MSI/MSIX; создавать их в Windows runner, поскольку Windows installer toolchains не запускаются локально на Android.

## Безопасность

- Не выполнять импортированный APK автоматически.
- Не отправлять исходники в CI или AI без явного действия пользователя.
- Перед массовой AI-правкой показывать diff и список файлов.
- Не загружать приватные signing keys в GitHub, логи, AI prompts или release artifacts.
- Учитывать лицензии compiler, runtime, templates и сторонних библиотек; обновлять `LICENSES.md`.
