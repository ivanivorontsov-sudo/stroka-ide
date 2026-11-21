package org.ivanivorontsov.stroka.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMapperTest {
    private fun text(s: String): String = VoiceMapper.map(s).joinToString("") {
        when (it) {
            is VoiceAction.Insert -> it.text
            VoiceAction.NewLine -> "⏎"
            VoiceAction.Indent -> "⇥"
            else -> "[$it]"
        }
    }

    @Test
    fun russianBracketsAndQuotes() {
        assertEquals("print(\"привет мир\")", text("print скобка открыть кавычка привет мир кавычка скобка закрыть"))
    }

    @Test
    fun englishSymbols() {
        assertEquals("print(\"hello\")", text("print open paren quote hello quote close paren"))
    }

    @Test
    fun assignmentAndSemicolon() {
        assertEquals("x = 5;", text("икс равно пять точка с запятой"))
        assertEquals("let total = 25;", text("let total equals twenty five semicolon"))
    }

    @Test
    fun comparisonAndColon() {
        assertEquals("if x == 10:", text("if x двойное равно десять двоеточие"))
        assertEquals("if a != b:", text("if a не равно b двоеточие"))
    }

    @Test
    fun newLineAndIndentAreActions() {
        val actions = VoiceMapper.map("def main скобка открыть скобка закрыть двоеточие новая строка отступ return")
        assertEquals(
            listOf(VoiceAction.Insert("def main():"), VoiceAction.NewLine, VoiceAction.Indent, VoiceAction.Insert("return")),
            actions
        )
        assertEquals(listOf(VoiceAction.NewLine), VoiceMapper.map("new line"))
    }

    @Test
    fun dotAndCall() {
        assertEquals("console.log(x)", text("console точка log скобка икс закрыть скобку"))
    }

    @Test
    fun bracesAndArrow() {
        assertEquals("{ return a => b }", text("фигурная скобка открыть return a жирная стрелка b фигурная скобка закрыть"))
    }

    @Test
    fun caseModifiers() {
        assertEquals("userName = 1", text("кэмел user name равно один"))
        assertEquals("max_speed", text("snake max speed"))
        assertEquals("MAX_SPEED", text("капс max speed"))
        assertEquals("MyClass", text("pascal my class"))
    }

    @Test
    fun recognizerPunctuationPassesThrough() {
        assertEquals("print(x)", text("print(x)"))
        assertEquals("a, b", text("a, b"))
    }

    @Test
    fun commandsRussian() {
        assertEquals(listOf(VoiceAction.Save), VoiceMapper.map("Сохранить"))
        assertEquals(listOf(VoiceAction.Run), VoiceMapper.map("запусти"))
        assertEquals(listOf(VoiceAction.Undo), VoiceMapper.map("отменить."))
        assertEquals(listOf(VoiceAction.Redo), VoiceMapper.map("повторить"))
        assertEquals(listOf(VoiceAction.GoToLine(25)), VoiceMapper.map("перейти к строке двадцать пять"))
        assertEquals(listOf(VoiceAction.GoToLine(120)), VoiceMapper.map("строка 120"))
        assertEquals(listOf(VoiceAction.Find("main")), VoiceMapper.map("найти main"))
        assertEquals(listOf(VoiceAction.StopListening), VoiceMapper.map("стоп"))
    }

    @Test
    fun commandsEnglish() {
        assertEquals(listOf(VoiceAction.Save), VoiceMapper.map("Save"))
        assertEquals(listOf(VoiceAction.Run), VoiceMapper.map("run"))
        assertEquals(listOf(VoiceAction.Undo), VoiceMapper.map("undo"))
        assertEquals(listOf(VoiceAction.GoToLine(42)), VoiceMapper.map("go to line forty two"))
        assertEquals(listOf(VoiceAction.Find("hello world")), VoiceMapper.map("search for hello world"))
        assertEquals(listOf(VoiceAction.Save), VoiceMapper.map("команда сохранить"))
    }

    @Test
    fun capitalizationOfFirstWordIsFixed() {
        assertEquals("import os", text("Import os"))
        assertEquals("NASA", text("NASA"))
    }

    @Test
    fun literalEscapesSymbolWords() {
        assertEquals("точка", text("буквально точка"))
    }

    @Test
    fun numberWords() {
        assertEquals(25L, NumberWords.parseAll("двадцать пять"))
        assertEquals(1200L, NumberWords.parseAll("тысяча двести"))
        assertEquals(342L, NumberWords.parseAll("three hundred forty two"))
        assertEquals(7L, NumberWords.parseAll("7"))
        assertEquals(null, NumberWords.parseAll("пять пять"))
    }

    @Test
    fun tolerantRussianCommands() {
        assertEquals(listOf(VoiceAction.Save), VoiceMapper.map("сахранить"))
        assertEquals(listOf(VoiceAction.Run), VoiceMapper.map("запусти код"))
        assertEquals(listOf(VoiceAction.Undo), VoiceMapper.map("отмени последнее"))
        assertEquals(listOf(VoiceAction.StopListening), VoiceMapper.map("стоп запись"))
    }

    @Test
    fun alternateRecognitionHypothesisCanRecoverCommand() {
        assertEquals("сохранить", VoiceMapper.bestCandidate(listOf("сохрани это", "сохранить")))
        assertEquals(
            listOf(VoiceAction.Save),
            VoiceMapper.map(VoiceMapper.bestCandidate(listOf("сохрани это", "сохранить")))
        )
    }

    @Test
    fun commandHintsContainCommonEditorActions() {
        val hints = VoiceMapper.recognitionHints()
        assert(hints.contains("сохранить"))
        assert(hints.contains("запусти код"))
        assert(hints.contains("open paren"))
    }
    @Test
    fun englishProgrammingKeywordsAndSymbols() {
        assertEquals(
            listOf(VoiceAction.Insert("function greet(name):"), VoiceAction.NewLine, VoiceAction.Insert("return name")),
            VoiceMapper.map("function greet open paren name close paren colon new line return name")
        )
        assertEquals("console.log(\"hello\")", text("console log open paren quote hello quote close paren"))
        assertEquals("x ** 2", text("x to the power of two"))
    }

    @Test
    fun russianEnglishMixedProgrammingPhrases() {
        assertEquals(
            listOf(VoiceAction.Insert("function greet(name):"), VoiceAction.NewLine, VoiceAction.Insert("return name")),
            VoiceMapper.map("функция greet open paren name close paren двоеточие новая строка возврат name")
        )
        assertEquals("if ready == true:", text("если ready double equals истина двоеточие"))
    }

    @Test
    fun fullIdentifierStylesAndSpelledLetters() {
        assertEquals("userProfileName", text("camel case user profile name"))
        assertEquals("UserProfileName", text("pascal case user profile name"))
        assertEquals("user_profile_name", text("snake case user profile name"))
        assertEquals("user-profile-name", text("kebab case user profile name"))
        assertEquals("userprofilename", text("flat case user profile name"))
        assertEquals("x", text("letter ex"))
    }

    @Test
    fun englishNumbersAndHyphenatedNumbers() {
        assertEquals(123L, NumberWords.parseAll("one hundred twenty three"))
        assertEquals(2024L, NumberWords.parseAll("two thousand twenty four"))
        assertEquals(42L, NumberWords.parseAll("forty-two"))
        assertEquals(2_000_000L, NumberWords.parseAll("two million"))
    }

    @Test
    fun commonTranslatedLanguageKeywords() {
        assertEquals("if ready: return true", text("если ready двоеточие возврат истина"))
        assertEquals("class User", text("класс User"))
        assertEquals("import os", text("импорт os"))
        assertEquals("const total = 25;", text("константа total equals twenty five semicolon"))
    }

}
