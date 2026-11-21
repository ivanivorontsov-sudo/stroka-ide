package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PonyatnySupportTest {
    @Test fun ponExtensionSelectsNativeLanguage() {
        assertEquals(Lang.PONYATNY, Lang.forName("пример.pon"))
        assertTrue(Lang.PONYATNY.runnable)
    }

    @Test fun syntaxHighlighterRecognizesKeywordsBuiltinsAndComments() {
        val source = "пусть имя = спроси(\"Имя\")\nпиши имя # комментарий"
        val tokens = Highlighter.tokenize(source, Lang.PONYATNY)
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && source.substring(it.start, it.end) == "пусть" })
        assertTrue(tokens.any { it.type == TokenType.BUILTIN && source.substring(it.start, it.end) == "спроси" })
        assertTrue(tokens.any { it.type == TokenType.COMMENT })
    }

    @Test fun voiceMapsSpokenPonyatnySyntaxAndNumbers() {
        assertEquals("пусть число = 5", PonyatnyVoiceMapper.map("переменная число равно пять"))
        assertEquals("пиши \"Привет, мир!\"", PonyatnyVoiceMapper.map("пиши Привет, мир!"))
        assertEquals("пиши 1 + 2", PonyatnyVoiceMapper.map("пиши один плюс два"))
        assertEquals("пиши имя", PonyatnyVoiceMapper.map("пиши переменную имя"))
        assertEquals("пусть имя = 7", PonyatnyVoiceMapper.map("пускай имя равно семь"))
    }

    @Test fun voiceMapperKeepsEditorCommandsAndUsesPonyatnyDialect() {
        assertEquals(listOf(VoiceAction.Save), VoiceMapper.map("сохрани", Lang.PONYATNY, "main.pon"))
        val actions = VoiceMapper.map("переменная число равно пять", Lang.PONYATNY, "main.pon")
        assertEquals(listOf(VoiceAction.Insert("пусть число = 5")), actions)
        val generated = VoiceMapper.map("создай программу", Lang.PONYATNY, "main.pon", "constructs")
        assertTrue(generated.single() is VoiceAction.InsertBlock)
        assertTrue((generated.single() as VoiceAction.InsertBlock).code.contains("пиши"))
    }

    @Test fun completionSuggestsNativeKeywords() {
        assertTrue(Completer.suggest("пу", 2, Lang.PONYATNY).contains("пусть"))
    }

    @Test fun editorAutoIndentsPonyatnyBlocksAndDedentsOnEnd() {
        val opening = "если истина то\n"
        val indent = EditorLogic.afterNewline(opening, opening.lastIndex, Lang.PONYATNY, "    ")
        assertEquals("    ", indent?.text)

        val closing = "если истина то\n    пиши 1\n    конец\n    "
        val dedent = EditorLogic.afterNewline(closing, closing.lastIndexOf('\n'), Lang.PONYATNY, "    ")
        assertEquals("", dedent?.text)
    }

    @Test fun starterTemplateCreatesRunnablePonProject() {
        val template = Templates.all.first { it.id == "ponyatny" }
        assertEquals("main.pon", template.main)
        assertTrue(template.files.getValue("main.pon").contains("подключи \"стд/математика.pon\""))
    }

    @Test fun spokenStopIsCodeButMicrophoneCommandStillStopsListening() {
        assertEquals(listOf(VoiceAction.Insert("стоп")), VoiceMapper.map("стоп", Lang.PONYATNY, "main.pon"))
        assertEquals(listOf(VoiceAction.StopListening), VoiceMapper.map("выключи микрофон", Lang.PONYATNY, "main.pon"))
    }

    @Test fun speechAlternativesPreferRecognizedPonyatnyKeywords() {
        assertEquals(
            "переменная число равно пять",
            VoiceMapper.bestCandidate(listOf("непонятная фраза", "переменная число равно пять"), Lang.PONYATNY)
        )
    }
}
