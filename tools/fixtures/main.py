print("Привет, «Строка» — это Python на телефоне!")


def square(x):
    return x * x


for i in range(1, 4):
    print(i, "→", square(i))

name = input("Как тебя зовут? ")
print(f"Рад знакомству, {name}! Квадрат 12 — {square(12)}.")
