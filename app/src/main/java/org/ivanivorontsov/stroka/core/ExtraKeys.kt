package org.ivanivorontsov.stroka.core

sealed class KeyAction {
    data class Insert(val text: String) : KeyAction()
    object Tab : KeyAction()
    object Left : KeyAction()
    object Right : KeyAction()
    object Up : KeyAction()
    object Down : KeyAction()
    object Home : KeyAction()
    object End : KeyAction()
    object Undo : KeyAction()
    object Redo : KeyAction()
    object Comment : KeyAction()
    object Outdent : KeyAction()
    object Select : KeyAction()
    object Voice : KeyAction()
}

data class ExtraKey(val label: String, val action: KeyAction)

object ExtraKeys {
    const val DEFAULT = "( ) { } [ ] \" ' ; : = < > + - * / \\ | & ! ? # _ , . $ % ^ ~ @ ` => -> // Undo Redo Comment Outdent"

    fun parse(config: String): List<ExtraKey> {
        val res = ArrayList<ExtraKey>()
        for (raw in config.trim().split(Regex("\\s+"))) {
            if (raw.isEmpty()) continue
            val key = when (raw.lowercase()) {
                "tab" -> ExtraKey("Tab", KeyAction.Tab)
                "left", "←" -> ExtraKey("←", KeyAction.Left)
                "right", "→" -> ExtraKey("→", KeyAction.Right)
                "up", "↑" -> ExtraKey("↑", KeyAction.Up)
                "down", "↓" -> ExtraKey("↓", KeyAction.Down)
                "home" -> ExtraKey("⇤", KeyAction.Home)
                "end" -> ExtraKey("⇥", KeyAction.End)
                "undo" -> ExtraKey("↶", KeyAction.Undo)
                "redo" -> ExtraKey("↷", KeyAction.Redo)
                "comment" -> ExtraKey("//±", KeyAction.Comment)
                "outdent" -> ExtraKey("⇤Tab", KeyAction.Outdent)
                "select" -> ExtraKey("Выд", KeyAction.Select)
                "voice", "mic" -> ExtraKey("🎤", KeyAction.Voice)
                "space", "пробел" -> ExtraKey("␣", KeyAction.Insert(" "))
                "\\n", "enter" -> ExtraKey("⏎", KeyAction.Insert("\n"))
                else -> ExtraKey(raw, KeyAction.Insert(raw))
            }
            res.add(key)
        }
        return res
    }
}
