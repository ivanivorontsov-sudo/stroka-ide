// Считаем нажатия на кнопку и показываем их на странице
let count = 0;
const out = document.getElementById("out");
document.getElementById("btn").addEventListener("click", () => {
    count++;
    out.textContent = `Нажатий: ${count}`;
});
