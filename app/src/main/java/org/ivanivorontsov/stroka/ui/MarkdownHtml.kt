package org.ivanivorontsov.stroka.ui

object MarkdownHtml {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun inline(s: String): String {
        var r = esc(s)
        r = Regex("`([^`]+)`").replace(r) { "<code>${it.groupValues[1]}</code>" }
        r = Regex("\\*\\*([^*]+)\\*\\*").replace(r) { "<b>${it.groupValues[1]}</b>" }
        r = Regex("(?<![*\\w])\\*([^*]+)\\*(?!\\*)").replace(r) { "<i>${it.groupValues[1]}</i>" }
        r = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)").replace(r) { "<img alt=\"${it.groupValues[1]}\" src=\"${it.groupValues[2]}\">" }
        r = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(r) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        return r
    }

    fun render(md: String, dark: Boolean): String {
        val sb = StringBuilder()
        val bg = if (dark) "#1e1f22" else "#ffffff"
        val fg = if (dark) "#d4d7dd" else "#1f2328"
        val codeBg = if (dark) "#2b2d31" else "#f3f4f6"
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>")
        sb.append("body{font-family:sans-serif;padding:12px 16px;line-height:1.5;background:$bg;color:$fg}pre,code{background:$codeBg;border-radius:6px}pre{padding:10px;overflow:auto}code{padding:1px 4px}blockquote{border-left:4px solid #888;margin:0;padding-left:12px;opacity:.85}img{max-width:100%}a{color:#4a8fe7}")
        sb.append("</style></head><body>")
        var inCode = false
        var inList: String? = null
        var para = StringBuilder()
        fun flushPara() {
            if (para.isNotEmpty()) { sb.append("<p>").append(inline(para.toString().trim())).append("</p>"); para = StringBuilder() }
        }
        fun closeList() {
            if (inList != null) { sb.append("</$inList>"); inList = null }
        }
        for (line in md.split("\n")) {
            if (line.trimStart().startsWith("```")) {
                flushPara(); closeList()
                sb.append(if (inCode) "</code></pre>" else "<pre><code>")
                inCode = !inCode
                continue
            }
            if (inCode) { sb.append(esc(line)).append("\n"); continue }
            val t = line.trim()
            val h = Regex("^(#{1,6})\\s+(.*)").find(t)
            val ul = Regex("^[-*+]\\s+(.*)").find(t)
            val ol = Regex("^\\d+[.)]\\s+(.*)").find(t)
            when {
                t.isEmpty() -> { flushPara(); closeList() }
                h != null -> { flushPara(); closeList(); val n = h.groupValues[1].length; sb.append("<h$n>").append(inline(h.groupValues[2])).append("</h$n>") }
                t.startsWith(">") -> { flushPara(); closeList(); sb.append("<blockquote>").append(inline(t.removePrefix(">").trim())).append("</blockquote>") }
                t == "---" || t == "***" -> { flushPara(); closeList(); sb.append("<hr>") }
                ul != null -> {
                    flushPara()
                    if (inList != "ul") { closeList(); sb.append("<ul>"); inList = "ul" }
                    val item = ul.groupValues[1]
                    val box = Regex("^\\[([ xX])]\\s+(.*)").find(item)
                    if (box != null) sb.append("<li><input type=\"checkbox\" disabled ${if (box.groupValues[1].isNotBlank()) "checked" else ""}> ").append(inline(box.groupValues[2])).append("</li>")
                    else sb.append("<li>").append(inline(item)).append("</li>")
                }
                ol != null -> {
                    flushPara()
                    if (inList != "ol") { closeList(); sb.append("<ol>"); inList = "ol" }
                    sb.append("<li>").append(inline(ol.groupValues[1])).append("</li>")
                }
                else -> { closeList(); para.append(line).append(' ') }
            }
        }
        flushPara(); closeList()
        if (inCode) sb.append("</code></pre>")
        sb.append("</body></html>")
        return sb.toString()
    }
}
