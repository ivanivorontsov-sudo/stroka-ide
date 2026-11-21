Встроенные библиотеки Строки

Текст подготовлен с помощью ИИ.

Язык «Понятный» — стандартная библиотека (стд/математика, стд/строки, стд/списки, стд/случай, стд/время, стд/консоль, стд/файлы)
Источник: официальный репозиторий «Понятный» (ivanivorontsov-sudo/ponyatny). Лицензия проекта «Понятный».

Java (подключаются в собираемое приложение, если программа их импортирует)
- Apache Commons Lang 3.17.0 (org.apache.commons.lang3) — Apache License 2.0
  https://commons.apache.org/proper/commons-lang/
- Apache Commons Text 1.13.0 (org.apache.commons.text) — Apache License 2.0
  https://commons.apache.org/proper/commons-text/
- Apache Commons Math 3.6.1 (org.apache.commons.math3) — Apache License 2.0
  https://commons.apache.org/proper/commons-math/
- Google Gson 2.11.0 (com.google.gson) — Apache License 2.0
  https://github.com/google/gson
- org.json входит в android.jar и отдельно не встраивается.

JavaScript / EXE
- Day.js 1.11.13 — MIT License
  https://github.com/iamkun/dayjs

По запросу Строка может скачать другие библиотеки только с официальных источников
(Maven Central, реестр npm, репозиторий «Понятный»), проверить контрольную сумму
и положить в кэш. Скачиваются только пакеты со свободной совместимой лицензией
(Apache 2.0, MIT, BSD, ISC, MPL 2.0, Eclipse Public License). Если сети нет и
библиотеки нет в кэше, сборка останавливается с понятной ошибкой.

Инструменты сборки APK (aapt2, zipalign) — Android Open Source, Apache License 2.0
(см. assets/toolchain/NOTICE.txt). QuickJS в заготовке EXE — лицензия MIT.

Python
- Chaquopy 16.1.0 и встроенный CPython 3.12 — лицензия MIT
  Copyright 2017-2025 Chaquo Ltd and contributors
  https://github.com/chaquo/chaquopy
  Среда копируется в отдельный Python-APK из установленной Строки (обычно только ABI телефона).
  Размер такого APK заметно больше обычного (десятки мегабайт из-за libpython и стандартной библиотеки).
- stroka_tkinter — совместимый модуль tkinter для панели «Просмотр» и Python-APK, написан для Строки.

Совместимость компилятора Java на Android
- Минимальная заглушка javax.lang.model.SourceVersion (без Runtime.Version) собирается в jar
  и встраивается в Строку, чтобы ECJ мог загрузиться на ART, где нет модуля java.compiler.
