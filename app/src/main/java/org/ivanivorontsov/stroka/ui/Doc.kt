package org.ivanivorontsov.stroka.ui

import org.ivanivorontsov.stroka.core.Lang
import org.ivanivorontsov.stroka.core.UndoStack
import org.ivanivorontsov.stroka.files.DocRef

class Doc(val ref: DocRef, var text: String) {
    var lang: Lang = Lang.forName(ref.name)
    var selStart = 0
    var selEnd = 0
    var scrollY = 0
    val undo = UndoStack()
    val name: String get() = ref.name
    var dirty = false
}
