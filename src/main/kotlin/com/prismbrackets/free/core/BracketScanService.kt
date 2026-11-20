package com.prismbrackets.free.core

import com.intellij.lang.LanguageBraceMatching
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * 文件级括号扫描：lexer 遍历 + 语言 PairedBraceMatcher + 字符级兜底，
 * 跳过字符串/注释，尖括号做泛型上下文验证，栈扫描计算深度与配对。
 *
 * 结果按 PsiFile 缓存，文档修改后自动失效（PsiModificationTracker）。
 */
object BracketScanService {

    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(BracketScanService::class.java)

    private val SCAN_KEY: Key<CachedValue<BracketScanResult>> =
        Key.create("rainbow.free.bracket.scan")

    private val CLOSE_BRACKETS = charArrayOf(')', ']', '}', '>')
    private val OPEN_BRACKETS = charArrayOf('(', '[', '{', '<')

    /** 尖括号预验证窗口：泛型参数列表不会太长 */
    private const val ANGLE_SEARCH_LIMIT = 500

    /** 语法高亮器标记为关键字的 token（着色时按出现顺序循环取色） */
    data class KeywordToken(val offset: Int, val length: Int, val ordinal: Int)

    /** 标签名（HTML/Vue/JSX）。
     *  @param depth 标签嵌套层级（0-based）：最外层 0，内部逐层 +1；开闭同层
     */
    data class TagNameToken(val offset: Int, val length: Int, val depth: Int, val openOffset: Int)

    /** 字符串 token（覆盖方案里的灰暗字符串色） */
    data class StringToken(val offset: Int, val length: Int)

    /** markup 标签的尖括号片段（<、/、>各自独立一条，共享同一标签层级） */
    data class TagBracket(val offset: Int, val length: Int, val level: Int)

    data class BracketScanResult(
        val tokens: List<BracketToken>,
        val keywords: List<KeywordToken> = emptyList(),
        val tagNames: List<TagNameToken> = emptyList(),
        val strings: List<StringToken> = emptyList(),
        /** markup 文件：非标签括号（圆括号等）深度相对缩进层级偏移 -1 */
        val depthShift: Int = 0,
        /** 尖括号字符 offset → 片段（markup 彩虹标签；不依赖括号栈配对） */
        val tagBracketsByOffset: Map<Int, TagBracket> = emptyMap(),
    ) {
        /** offset → token 索引，供 O(1) 查找 */
        val byOffset: Map<Int, BracketToken> = tokens.associateBy { it.offset }
        val keywordsByOffset: Map<Int, KeywordToken> = keywords.associateBy { it.offset }
        val tagNamesByOffset: Map<Int, TagNameToken> = tagNames.associateBy { it.offset }
        /** 开 '<' offset → 标签名：括号着色时顺带给标签名上同色，不依赖 PSI 叶子结构 */
        val tagByOpenOffset: Map<Int, TagNameToken> = tagNames.associateBy { it.openOffset }
        val stringsByOffset: Map<Int, StringToken> = strings.associateBy { it.offset }
    }

    fun scan(file: PsiFile): BracketScanResult {
        val cached = CachedValuesManager.getCachedValue(file, SCAN_KEY) {
            CachedValueProvider.Result.create(computeScan(file), PsiModificationTracker.MODIFICATION_COUNT)
        }
        return cached
    }

    private fun computeScan(file: PsiFile): BracketScanResult {
        val project = file.project
        val text = file.viewProvider.contents ?: return BracketScanResult(emptyList())
        if (text.length == 0) return BracketScanResult(emptyList())

        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            file.language, project, file.virtualFile
        ) ?: return BracketScanResult(emptyList())
        val lexer = highlighter.highlightingLexer
        val markup = looksMarkup(file)

        // 语言注册的括号对（token type 级精确匹配）
        val matcher = LanguageBraceMatching.INSTANCE.forLanguage(file.language)
        val pairs = matcher?.pairs
        val leftTypes = pairs?.map { it.leftBraceType }?.toSet()
        val rightTypes = pairs?.map { it.rightBraceType }?.toSet()

        val candidates = ArrayList<CandidateBracket>()
        val keywords = ArrayList<KeywordToken>()
        val strings = ArrayList<StringToken>()
        lexer.start(text)
        while (lexer.tokenType != null) {
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            if (end > start) {
                val tokenType = lexer.tokenType
                val tokenText = text.subSequence(start, end)

                if (isKeyword(highlighter, tokenType)) {
                    keywords.add(KeywordToken(start, end - start, keywords.size))
                } else if (isStringToken(highlighter, tokenType)) {
                    strings.add(StringToken(start, end - start))
                }

                // markup 文件的尖括号交给 scanMarkupTags 按标签语义处理，
                // 不进字符栈（栈配对会被属性值/表达式干扰导致层级错乱）
                val angleLike = tokenText.isNotEmpty() &&
                        (tokenText[0] == '<' || tokenText[0] == '>') &&
                        tokenText.all { it == '<' || it == '>' }
                if (markup && angleLike) {
                    lexer.advance()
                    continue
                }

                val fromMatcher = tokenType != null &&
                        ((leftTypes?.contains(tokenType) == true) || (rightTypes?.contains(tokenType) == true))

                val singleCharBracket = tokenText.length == 1 && isBracketChar(tokenText[0])
                val repeatedAngle = tokenText.length >= 2 &&
                        tokenText.all { it == '<' || it == '>' } // Java >>、<< 多字符 token 拆分

                if ((fromMatcher || singleCharBracket || repeatedAngle) &&
                    !isStringOrComment(highlighter, tokenType)
                ) {
                    if (repeatedAngle && !fromMatcher) {
                        // 拆成单字符候选，交由栈扫描 + 尖括号验证
                        for (i in 0 until tokenText.length) {
                            addCandidate(candidates, start + i, tokenText[i], text)
                        }
                    } else if (tokenText.length == 1) {
                        addCandidate(candidates, start, tokenText[0], text)
                    } else if (fromMatcher) {
                        // 多字符但被语言 matcher 认定为括号（罕见）：取首字符，范围整 token
                        candidates.add(CandidateBracket(start, tokenText.length, tokenText[0]))
                    }
                }
            }
            lexer.advance()
        }

        // PSI 注释区间兜底跳过（防某些语言注释被 lexer 高亮器误判）
        val commentRanges = collectCommentRanges(file)
        val filtered = if (commentRanges.isEmpty()) candidates else candidates.filter { c ->
            commentRanges.none { r -> c.offset >= r.first && c.offset < r.second }
        }

        val tokens = BracketScanner.scan(filtered)
        // 后处理：未配对的闭括号不着色；未配对的开括号 ()[]{} 保留着色
        val visible = tokens.filter { t ->
            when {
                t.pairOffset >= 0 -> true
                t.char == '<' || t.char == '>' -> false
                !t.isOpen -> false
                else -> true
            }
        }

        val (tagNames, tagBrackets) = if (markup) scanMarkupTags(text) else emptyList<TagNameToken>() to emptyList<TagBracket>()
        LOG.info(
            "rainbow.scan file=${file.name} fileType=${file.fileType.name} lang=${file.language.id} " +
                    "markup=$markup candidates=${candidates.size} brackets=${visible.size} " +
                    "tags=${tagNames.size} tagBrackets=${tagBrackets.size} " +
                    "keywords=${keywords.size} strings=${strings.size}"
        )
        return BracketScanResult(
            visible, keywords, tagNames, strings,
            if (markup) -1 else 0,
            tagBrackets.associateBy { it.offset },
        )
    }

    /** HTML/Vue/XML/JSX/TSX 一类的标记文件才做标签名关联（三路判定，任一命中即可） */
    private fun looksMarkup(file: PsiFile): Boolean {
        val ft = file.fileType.name.lowercase()
        if (ft.contains("html") || ft.contains("htm") || ft.contains("vue") ||
            ft.contains("xml") || ft.contains("jsx") || ft.contains("tsx") || ft.contains("markup")
        ) {
            return true
        }
        val lang = file.language.id.lowercase()
        if (lang.contains("html") || lang.contains("vue") || lang.contains("xml") || lang == "jsx") {
            return true
        }
        // 扩展名兜底：FileType 注册名随插件版本可能变化，扩展名最稳定
        val ext = file.virtualFile?.extension?.lowercase()
            ?: file.name.substringAfterLast('.', "").lowercase()
        return ext in MARKUP_EXTENSIONS
    }

    private val MARKUP_EXTENSIONS = setOf(
        "vue", "html", "htm", "xhtml", "xml", "svg", "jsx", "tsx", "vue.html", "twig", "njk", "hbs", "ejs", "pug", "jade",
    )

    /**
     * markup 标签纯文本扫描（彩虹标签核心，不依赖括号栈配对）。
     *
     * 从左到右找标签：`<` → 引号外第一个 `>` 为标签结束。按标签语义计层：
     * - 开标签 `<name…>`：取当前层，然后 +1（自闭合 `/>`、void 元素、script/style 块不加）
     * - 闭标签 `</name>`：先 -1，再取该层
     * 每个标签产出：<、（闭合时的 /）、> 三个尖括号片段 + 标签名，全部共享同一 level。
     * 跳过：注释 `<!-- -->`、`<!doctype`/`<?…?>`、script/style 块内部（防 JS 泛型/比较误判）。
     */
    internal fun scanMarkupTags(text: CharSequence): Pair<List<TagNameToken>, List<TagBracket>> {
        val names = ArrayList<TagNameToken>()
        val brackets = ArrayList<TagBracket>()
        var current = 0
        var i = 0
        val len = text.length
        while (i < len) {
            if (text[i] != '<') {
                i++
                continue
            }
            // 注释
            if (text.startsWith("<!--", i)) {
                val endC = text.indexOf("-->", i + 4)
                i = if (endC >= 0) endC + 3 else len
                continue
            }
            // <!doctype / <?xml 等：跳到引号外 '>'
            if (i + 1 < len && (text[i + 1] == '!' || text[i + 1] == '?')) {
                i = skipToTagEnd(text, i) + 1
                continue
            }
            val isClose = i + 1 < len && text[i + 1] == '/'
            // 找引号外 '>'
            var j = i + 1
            var quote: Char? = null
            var gt = -1
            while (j < len && j < i + 1 + ANGLE_SEARCH_LIMIT) {
                val c = text[j]
                if (quote != null) {
                    if (c == quote) quote = null
                    j++
                    continue
                }
                when (c) {
                    '"', '\'' -> quote = c
                    '>' -> {
                        gt = j
                        break
                    }
                    '{', '}', '(', ')' -> break // 结构异常，放弃此 '<'
                }
                j++
            }
            if (gt < 0) {
                i++
                continue
            }
            val nameStart = if (isClose) i + 2 else i + 1
            val nameEnd = readNameEnd(text, nameStart, gt)
            val name = if (nameEnd > nameStart) text.subSequence(nameStart, nameEnd).toString() else ""
            val selfClosing = gt - 1 > i && text[gt - 1] == '/'

            val level: Int
            if (isClose) {
                current = (current - 1).coerceAtLeast(0)
                level = current
            } else {
                level = current
                val void = name.isNotEmpty() && name.lowercase() in VOID_ELEMENTS
                if (!selfClosing && !void) current++
            }
            brackets.add(TagBracket(i, 1, level))
            if (isClose) brackets.add(TagBracket(i + 1, 1, level))
            brackets.add(TagBracket(gt, 1, level))
            if (name.isNotEmpty()) {
                names.add(TagNameToken(nameStart, nameEnd - nameStart, level, i))
            }
            // script/style 块内部整体跳过（防 JS 泛型/比较运算符误判为标签）
            if (!isClose && (name.equals("script", ignoreCase = true) || name.equals("style", ignoreCase = true))) {
                // 闭合标签 </script> 被跳过，这里补记层级回退
                current = (current - 1).coerceAtLeast(0)
                val closeTag = "</" + name.lowercase()
                val closeIdx = text.indexOf(closeTag, gt + 1, ignoreCase = true)
                i = if (closeIdx >= 0) {
                    val cgt = skipToTagEnd(text, closeIdx)
                    if (cgt >= 0) cgt + 1 else len
                } else {
                    len
                }
                continue
            }
            i = gt + 1
        }
        return names to brackets
    }

    /** 从 off 找引号外的 '>'，返回其 offset；找不到返回 -1 */
    private fun skipToTagEnd(text: CharSequence, off: Int): Int {
        var j = off + 1
        var quote: Char? = null
        val len = text.length
        while (j < len && j < off + 1 + ANGLE_SEARCH_LIMIT) {
            val c = text[j]
            if (quote != null) {
                if (c == quote) quote = null
                j++
                continue
            }
            when (c) {
                '"', '\'' -> quote = c
                '>' -> return j
            }
            j++
        }
        return -1
    }

    /** 标签名结束位置（字母/数字/_ - . :） */
    private fun readNameEnd(text: CharSequence, start: Int, limit: Int): Int {
        if (start >= limit) return start
        val first = text[start]
        if (!first.isLetter() && first != '_') return start
        var e = start + 1
        while (e < limit) {
            val c = text[e]
            if (c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == ':') e++ else break
        }
        return e
    }

    /** HTML void 元素：无闭合标签，不增加嵌套层 */
    private val VOID_ELEMENTS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr", "keygen",
    )

    private fun addCandidate(
        out: ArrayList<CandidateBracket>,
        offset: Int,
        char: Char,
        text: CharSequence,
    ) {
        if (char == '<') {
            if (!isValidAngleOpen(text, offset)) return
            out.add(CandidateBracket(offset, 1, char))
        } else if (char == '>') {
            // '>' 保留进候选：配对成功才着色（filter 阶段丢弃未配对的）
            out.add(CandidateBracket(offset, 1, char))
        } else if (char in OPEN_BRACKETS) {
            out.add(CandidateBracket(offset, 1, char))
        } else if (char in CLOSE_BRACKETS) {
            out.add(CandidateBracket(offset, 1, char))
        }
    }

    /**
     * 尖括号开符预验证：从 '<' 后限定窗口内必须出现引号外的 '>'，且引号外不得出现
     * 语句/运算字符（; ( ) { } & | ! + * % 等）。引号内任意字符合法——
     * 这样带属性的标签 `<div class="a">`、`<a @click="fn(x)">` 能通过，
     * 而 `if (a < b)`、`a < b && c > d` 仍被拒绝。
     */
    internal fun isValidAngleOpen(text: CharSequence, ltOffset: Int): Boolean {
        val limit = minOf(text.length, ltOffset + 1 + ANGLE_SEARCH_LIMIT)
        var i = ltOffset + 1
        var quote: Char? = null
        while (i < limit) {
            val c = text[i]
            if (quote != null) {
                if (c == quote) quote = null
                i++
                continue
            }
            when (c) {
                '"', '\'' -> quote = c
                '>' -> return true
                ';', '(', ')', '{', '}', '&', '|', '!', '+', '*', '%', '$', '`', '~' -> return false
                else -> Unit
            }
            i++
        }
        return false // 窗口内无配对 '>'（或引号未闭合）
    }

    private fun isBracketChar(c: Char): Boolean =
        c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == '<' || c == '>'

    /**
     * 语法高亮器判定 token 是否为字符串（不含注释）：
     * 沿 fallback 链查找名字含 STRING / CHAR_SEQUENCE / QUOTE 的属性 key。
     */
    private fun isStringToken(highlighter: SyntaxHighlighter, tokenType: com.intellij.psi.tree.IElementType?): Boolean {
        if (tokenType == null) return false
        val keys = try {
            highlighter.getTokenHighlights(tokenType)
        } catch (_: Exception) {
            return false
        }
        for (key in keys) {
            var k: TextAttributesKey? = key
            var depth = 0
            while (k != null && depth < 10) {
                val n = k.externalName.uppercase()
                if (n.contains("STRING") || n.contains("CHAR_SEQUENCE") || n.contains("QUOTE")) return true
                k = k.fallbackAttributeKey
                depth++
            }
        }
        return false
    }

    /**
     * 语法高亮器判定 token 是否为关键字：
     * getTokenHighlights → 沿 fallback 链查找名字含 KEYWORD 的属性 key。
     */
    private fun isKeyword(highlighter: SyntaxHighlighter, tokenType: com.intellij.psi.tree.IElementType?): Boolean {
        if (tokenType == null) return false
        val keys = try {
            highlighter.getTokenHighlights(tokenType)
        } catch (_: Exception) {
            return false
        }
        for (key in keys) {
            var k: TextAttributesKey? = key
            var depth = 0
            while (k != null && depth < 10) {
                if (k.externalName.contains("KEYWORD", ignoreCase = true)) return true
                k = k.fallbackAttributeKey
                depth++
            }
        }
        return false
    }

    /**
     * 语法高亮器判定 token 是否为字符串/注释：
     * getTokenHighlights → 沿 fallback 链查找 STRING/COMMENT 类 key，或 externalName 启发式。
     */
    private fun isStringOrComment(highlighter: SyntaxHighlighter, tokenType: com.intellij.psi.tree.IElementType?): Boolean {
        if (tokenType == null) return false
        val keys = try {
            highlighter.getTokenHighlights(tokenType)
        } catch (_: Exception) {
            return false
        }
        for (key in keys) {
            var k: TextAttributesKey? = key
            var depth = 0
            while (k != null && depth < 10) {
                if (isStringOrCommentKeyName(k.externalName)) return true
                k = k.fallbackAttributeKey
                depth++
            }
        }
        return false
    }

    private fun isStringOrCommentKeyName(name: String): Boolean {
        val n = name.uppercase()
        // 只做严格匹配。曾经包含 n == "TEXT" —— Vue/HTML 高亮器的 fallback 链
        // 大量经过 TEXT key，导致所有括号 token 被误判为字符串（brackets=0）。
        return n.contains("STRING") || n.contains("COMMENT") ||
                n.contains("CHAR_SEQUENCE") || n.contains("QUOTE") ||
                n.contains("REGEXP") || n.contains("REGEX") || n.contains("HEREDOC")
    }

    /** PSI 注释区间（兑底，处理高亮器未覆盖的语言） */
    private fun collectCommentRanges(file: PsiFile): List<Pair<Int, Int>> {
        val ranges = ArrayList<Pair<Int, Int>>()
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiComment) {
                    val range = element.textRange
                    ranges.add(range.startOffset to range.endOffset)
                } else {
                    super.visitElement(element)
                }
            }
        })
        return ranges
    }
}
