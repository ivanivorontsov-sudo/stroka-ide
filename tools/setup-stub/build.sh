#!/bin/sh
set -e
cd "$(dirname "$0")"
x86_64-w64-mingw32-windres setup.rc -O coff -o setup.res.o
x86_64-w64-mingw32-gcc -Os -s -municode -mwindows -Wall -Wno-unused-parameter -o stroka-setup-stub.exe stroka-setup-stub.c setup.res.o -static -lcomctl32 -lshell32 -lole32 -luuid -ladvapi32 -lgdi32 -luser32
ls -l stroka-setup-stub.exe
