package org.ivanivorontsov.stroka.core

class LangSpec(
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val builtins: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComment: Pair<String, String>? = null,
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    val preprocessor: Boolean = false,
    val decorators: Boolean = false,
    val caseInsensitive: Boolean = false,
    val extraIdentChars: String = "_"
) {
    val allWords: Set<String> by lazy { keywords + types + builtins + constants }

    companion object {
        private fun w(s: String): Set<String> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

        private val pyKeywords = w("False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case")
        private val pyBuiltins = w("print input len range int str float bool list dict set tuple open abs min max sum sorted reversed enumerate zip map filter any all isinstance type super object round divmod pow repr chr ord hex bin oct id hash iter next format vars dir help getattr setattr hasattr delattr callable staticmethod classmethod property self cls Exception ValueError TypeError KeyError IndexError RuntimeError StopIteration ZeroDivisionError")
        private val jsKeywords = w("break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield async await of static get set from as")
        private val jsBuiltins = w("console window document Math JSON Object Array String Number Boolean Promise Map Set Date RegExp Error Symbol parseInt parseFloat setTimeout setInterval clearTimeout clearInterval fetch alert prompt confirm localStorage require module exports globalThis")
        private val jsConstants = w("true false null undefined NaN Infinity")
        private val tsExtra = w("interface type enum implements namespace declare abstract readonly private protected public keyof infer is asserts satisfies")
        private val tsTypes = w("string number boolean any unknown never void object bigint symbol Record Partial Readonly Array Promise")
        private val ktKeywords = w("as break class continue do else for fun if in interface is null object package return super this throw try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set setparam where actual abstract annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg it")
        private val ktTypes = w("Int Long Short Byte Double Float Boolean Char String Unit Any Nothing List MutableList Map MutableMap Set MutableSet Array IntArray ByteArray Pair Triple")
        private val ktBuiltins = w("println print listOf mutableListOf mapOf mutableMapOf setOf arrayOf lazy require check error repeat run let apply also with takeIf")
        private val javaKeywords = w("abstract assert break case catch class const continue default do else enum extends final finally for goto if implements import instanceof interface native new package private protected public return static strictfp super switch synchronized this throw throws transient try volatile while var record yield sealed permits")
        private val javaTypes = w("int long short byte double float boolean char void String Integer Long Double Float Boolean Character Object List ArrayList Map HashMap Set HashSet System")
        private val cKeywords = w("auto break case const continue default do else enum extern for goto if inline register restrict return sizeof static struct switch typedef union volatile while")
        private val cTypes = w("int long short char float double void signed unsigned bool size_t uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t FILE")
        private val cBuiltins = w("printf scanf malloc free calloc realloc memcpy memset strlen strcpy strcmp puts gets fopen fclose fprintf exit main")
        private val cppExtra = w("class namespace template typename public private protected virtual override final new delete this throw try catch using operator friend explicit constexpr nullptr noexcept static_cast dynamic_cast reinterpret_cast const_cast auto decltype mutable")
        private val cppTypes = w("string vector map set unordered_map unique_ptr shared_ptr std cout cin endl wchar_t char16_t char32_t")
        private val gdKeywords = w("if elif else for while match break continue pass return class class_name extends is in as self signal func static const enum var onready export setget tool breakpoint preload await yield assert void super and or not await")
        private val gdTypes = w("int float bool String Vector2 Vector2i Vector3 Vector3i Color Rect2 Transform2D Transform3D Basis Quaternion Array Dictionary Node Node2D Node3D Object Resource PackedScene Callable Signal StringName NodePath")
        private val gdBuiltins = w("print push_error push_warning len range str int float abs min max clamp lerp randf randi randf_range randi_range sin cos sqrt floor ceil round get_node queue_free emit_signal connect load instance instantiate _ready _process _physics_process _input _init")
        private val gdConstants = w("true false null PI TAU INF NAN")
        private val asmOps = w("adc and asl bcc bcs beq bit bmi bne bpl brk bvc bvs clc cld cli clv cmp cpx cpy dec dex dey eor inc inx iny jmp jsr lda ldx ldy lsr nop ora pha php pla plp rol ror rti rts sbc sec sed sei sta stx sty tax tay tsx txa txs tya")
        private val asmDirectives = w("processor org rorg include incbin seg seg.u equ = byte word dc dc.b dc.w ds ds.b ds.w hex align echo if else endif ifconst ifnconst mac endm repeat repend subroutine end db dw .byte .word .org .include .segment .proc .endproc .macro .endmacro .res .align .if .else .endif .db .dw")
        private val asmRegs = w("a x y")
        private val cssKeywords = w("important inherit initial unset auto none block inline flex grid absolute relative fixed sticky solid dashed bold normal center left right top bottom")

        fun of(lang: Lang): LangSpec = when (lang) {
            Lang.PYTHON -> LangSpec(keywords = pyKeywords - w("True False None"), constants = w("True False None"), builtins = pyBuiltins, lineComments = listOf("#"), tripleQuotes = true, decorators = true)
            Lang.JAVASCRIPT -> LangSpec(keywords = jsKeywords, builtins = jsBuiltins, constants = jsConstants, lineComments = listOf("//"), blockComment = "/*" to "*/", quotes = "\"'`", extraIdentChars = "_$")
            Lang.TYPESCRIPT -> LangSpec(keywords = jsKeywords + tsExtra, types = tsTypes, builtins = jsBuiltins, constants = jsConstants, lineComments = listOf("//"), blockComment = "/*" to "*/", quotes = "\"'`", decorators = true, extraIdentChars = "_$")
            Lang.KOTLIN -> LangSpec(keywords = ktKeywords - setOf("null"), types = ktTypes, builtins = ktBuiltins, constants = w("true false null"), lineComments = listOf("//"), blockComment = "/*" to "*/", tripleQuotes = true, quotes = "\"'", decorators = true)
            Lang.JAVA -> LangSpec(keywords = javaKeywords, types = javaTypes, constants = w("true false null"), lineComments = listOf("//"), blockComment = "/*" to "*/", decorators = true)
            Lang.C -> LangSpec(keywords = cKeywords, types = cTypes, builtins = cBuiltins, constants = w("NULL true false EOF"), lineComments = listOf("//"), blockComment = "/*" to "*/", preprocessor = true)
            Lang.CPP -> LangSpec(keywords = cKeywords + cppExtra, types = cTypes + cppTypes, builtins = cBuiltins, constants = w("NULL true false nullptr EOF"), lineComments = listOf("//"), blockComment = "/*" to "*/", preprocessor = true)
            Lang.GDSCRIPT -> LangSpec(keywords = gdKeywords, types = gdTypes, builtins = gdBuiltins, constants = gdConstants, lineComments = listOf("#"), tripleQuotes = true, decorators = true)
            Lang.ASM6502 -> LangSpec(keywords = asmOps, builtins = asmRegs, types = asmDirectives, lineComments = listOf(";"), caseInsensitive = true, extraIdentChars = "_.")
            Lang.CSS -> LangSpec(keywords = cssKeywords, blockComment = "/*" to "*/", extraIdentChars = "_-")
            Lang.JSON -> LangSpec(constants = w("true false null"), quotes = "\"")
            Lang.HTML -> LangSpec(keywords = w("html head body div span p a img script style link meta title ul ol li table tr td th form input button label select option textarea canvas section header footer nav main article h1 h2 h3 h4 h5 h6 br hr"))
            else -> LangSpec()
        }
    }
}
