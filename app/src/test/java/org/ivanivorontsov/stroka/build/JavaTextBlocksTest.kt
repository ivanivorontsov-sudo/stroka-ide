package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertEquals
import org.junit.Test

class JavaTextBlocksTest {
    @Test
    fun desugarsLikeJavac() {
        val src = "class A {\n    String a = \"\"\"\n        Калькулятор\n          отступ\n        \"\"\".trim();\n    String b = \"\"\"\n        x \"q\" \\\n        y\\s\n        z\"\"\";\n    // \"\"\" в комментарии\n    String c = \"\\\"\\\"\\\"\";\n}\n"
        val out = JavaTextBlocks.desugar(src)
        assertEquals(src.count { it == '\n' }, out.count { it == '\n' })
        assertEquals(true, out.contains("\"Калькулятор\\n\" +\n\"  отступ\\n\" +\n\"\".trim()"))
        assertEquals(true, out.contains("\"x \\\"q\\\" \" +\n\"y\\040\\n\" +\n\"z\""))
        assertEquals(true, out.contains("// \"\"\" в комментарии"))
        assertEquals(true, out.contains("String c = \"\\\"\\\"\\\"\";"))
        assertEquals("нет блоков", JavaTextBlocks.desugar("нет блоков"))
    }
}
