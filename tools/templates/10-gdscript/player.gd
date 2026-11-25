# Скрипт для Godot 4: персонаж ходит стрелками.
# В Godot создайте сцену с узлом CharacterBody2D и прикрепите этот скрипт.
extends CharacterBody2D

# @export — значение можно менять в инспекторе Godot
@export var speed: float = 300.0

# Вызывается каждый кадр физики; delta — время с прошлого кадра
func _physics_process(delta: float) -> void:
	var direction := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	velocity = direction * speed
	move_and_slide()
