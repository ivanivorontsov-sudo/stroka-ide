// Скрипт на JavaScript. Нажмите «Старт» — результат появится в консоли.

// Функция: числа Фибоначчи (каждое — сумма двух предыдущих)
function fib(n) {
    let a = 0, b = 1;
    for (let i = 0; i < n; i++) [a, b] = [b, a + b];
    return a;
}

for (let i = 0; i <= 10; i++) {
    console.log(`fib(${i}) = ${fib(i)}`);
}

// Массив и его методы
const marks = [5, 4, 5, 3];
const avg = marks.reduce((s, x) => s + x, 0) / marks.length;
console.log("Средняя оценка:", avg.toFixed(2));
