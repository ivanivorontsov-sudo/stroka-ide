package org.ivanivorontsov.stroka.core

import org.junit.Assert.*
import org.junit.Test

class VoiceCodeTemplatesTest {
    @Test fun phpFileReceivesPhpFormInsteadOfPythonTkinter() {
        val block = VoiceCodeTemplates.generate("создай веб-форму", Lang.PLAIN, "index.php")
        assertNotNull(block)
        assertTrue(block!!.code.contains("\${'$'}_POST"))
        assertTrue(block.code.contains("htmlspecialchars"))
        assertFalse(block.code.contains("import tkinter"))
        assertFalse(block.code.contains("def submit_form"))
    }
    @Test fun pythonFileKeepsPythonTemplate() {
        val block = VoiceCodeTemplates.generate("создай веб-форму", Lang.PYTHON, "main.py")
        assertNotNull(block)
        assertTrue(block!!.code.contains("import tkinter"))
        assertFalse(block.code.contains("<?php"))
    }
    @Test fun unknownPlainTextFileDoesNotGuessPython() {
        assertNull(VoiceCodeTemplates.generate("создай веб-форму", Lang.PLAIN, "notes.txt"))
    }
    @Test fun explicitLanguageOverridesFileExtension() {
        val block = VoiceCodeTemplates.generate("создай веб-форму на Python", Lang.PLAIN, "index.php")
        assertNotNull(block)
        assertTrue(block!!.code.contains("import tkinter"))
    }
}