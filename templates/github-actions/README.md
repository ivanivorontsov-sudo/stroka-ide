# Шаблоны GitHub Actions для Строки

Скопируйте нужный YAML-файл в `.github/workflows/` своего репозитория.

- `gradle-multiplatform.yml` запускает настоящий Gradle Wrapper, собирает включённые subprojects/composite builds и настроенные Kotlin Multiplatform targets на Linux, Windows и macOS runners. Это облачная сборка, а не локальный Android-компилятор.
- `apk-decompile.yml` запускает JADX в изолированном Ubuntu runner. APK должен уже находиться в репозитории; путь задаётся через Actions → Decompile APK with JADX → Run workflow. Результат доступен как artifact.

JADX не восстанавливает исходный проект один в один. Если декомпиляция неполная или код обфусцирован, анализируйте также DEX/Smali, manifest и ресурсы. Не загружайте APK, на которые у вас нет прав.
