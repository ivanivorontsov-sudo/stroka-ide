package org.ivanivorontsov.stroka.core

enum class TokenType { KEYWORD, TYPE, BUILTIN, CONSTANT, STRING, NUMBER, COMMENT, FUNCTION, TAG, ATTR, HEADING, META, LABEL, EMPHASIS, LINK }

data class Token(val start: Int, val end: Int, val type: TokenType)
