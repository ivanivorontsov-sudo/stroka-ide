package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraKeysTest {
    @Test
    fun parsesDefaultsAndSpecials() {
        val keys = ExtraKeys.parse("Tab ( ) => undo space enter")
        assertEquals(KeyAction.Tab, keys[0].action)
        assertEquals(KeyAction.Insert("("), keys[1].action)
        assertEquals(KeyAction.Insert("=>"), keys[3].action)
        assertEquals(KeyAction.Undo, keys[4].action)
        assertEquals(KeyAction.Insert(" "), keys[5].action)
        assertEquals(KeyAction.Insert("\n"), keys[6].action)
        assertEquals(true, ExtraKeys.parse(ExtraKeys.DEFAULT).size > 20)
    }

    @Test
    fun templatesHaveMainFiles() {
        for (t in Templates.all) assertEquals(true, t.files.containsKey(t.main))
    }
}
