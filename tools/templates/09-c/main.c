/* Программа на C. Строка подсвечивает C, но не компилирует его на телефоне:
   соберите на компьютере командой  gcc main.c -o main  и запустите ./main */
#include <stdio.h>

/* Функция возвращает квадрат числа */
int square(int n) {
    return n * n;
}

int main(void) {
    for (int i = 1; i <= 5; i++) {
        printf("%d в квадрате = %d\n", i, square(i));
    }
    return 0;
}
