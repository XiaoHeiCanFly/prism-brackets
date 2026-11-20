package com.prismbrackets.free.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.prismbrackets.free.colors.PrismColors
import com.prismbrackets.free.settings.PrismSettings
import com.prismbrackets.free.core.BracketScanService

/**
 * 彩虹括号 + 彩虹变量/类型 高亮访问器。
 *
 * 直接实现 HighlightVisitor 接口（不继承平台 RainbowVisitor——后者受
 * 内置 rainbow 开关门控，插件需要用自己的设置开关）。
 *
 * - 括号：analyze 中取全文件扫描缓存；visit 对叶子按 offset 查表着色。
 * - 变量：字段/局部/参数等，按声明身份分配稳定颜色（斜体）。
 * - 类型：类名、import 包路径等 resolve 到类/包的标识符，独立色组。
 */
class PrismHighlightVisitor : HighlightVisitor {

    private var myHolder: HighlightInfoHolder? = null
    private var myScan: BracketScanService.BracketScanResult? = null
    private var myColorCount: Int = PrismColors.MAX_LEVELS
    private val myVariableColors = HashMap<String, Int>()
    private val myTypeColors = HashMap<String, Int>()
    private val myMethodColors = HashMap<String, Int>()

    // 预建 HighlightInfoType（severity INFORMATION + 自定义 key，纯着色无 tooltip）
    private val bracketTypes = PrismColors.BRACKET_KEYS.map {
        HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, it)
    }
    private val variableTypes = PrismColors.VARIABLE_KEYS.map {
        HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, it)
    }
    private val typeTypes = PrismColors.TYPE_KEYS.map {
        HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, it)
    }
    private val methodTypes = PrismColors.METHOD_KEYS.map {
        HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, it)
    }
    private val keywordTypes = PrismColors.KEYWORD_KEYS.map {
        HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, it)
    }
    private val stringType = HighlightInfoType.HighlightInfoTypeImpl(
        HighlightSeverity.INFORMATION, PrismColors.STRING_KEY,
    )

    override fun suitableForFile(psiFile: PsiFile): Boolean {
        val settings = PrismSettings.getInstance()
        if (!settings.rainbowEnabled && !settings.rainbowVariablesEnabled &&
            !settings.keywordColoringEnabled && !settings.stringColoringEnabled
        ) return false
        if (psiFile.fileType.isBinary) return false
        if (psiFile.viewProvider.document?.textLength?.let { it > MAX_FILE_LENGTH } == true) return false
        return true
    }

    override fun isDumbAware(): Boolean = true

    override fun analyze(
        psiFile: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable,
    ): Boolean {
        myHolder = holder
        val settings = PrismSettings.getInstance()
        myColorCount = settings.colorCount
        myVariableColors.clear()
        myTypeColors.clear()
        myMethodColors.clear()
        myScan = if (settings.rainbowEnabled || settings.keywordColoringEnabled || settings.stringColoringEnabled) {
            BracketScanService.scan(psiFile)
        } else {
            null
        }
        myMarkup = myScan?.depthShift != 0
        try {
            action.run()
        } finally {
            myHolder = null
            myScan = null
        }
        return true
    }

    override fun visit(element: PsiElement) {
        val holder = myHolder ?: return
        val settings = PrismSettings.getInstance()

        // 只处理叶子：括号字符与标识符都在叶子层
        if (element.firstChild != null) return
        val text = element.text
        if (text.isEmpty()) return

        // 1) 彩虹括号：按 offset 查扫描表
        if (settings.rainbowEnabled) {
            val scan = myScan
            if (scan != null) {
                val start = element.textRange.startOffset
                val end = start + text.length
                var offset = start
                while (offset < end) {
                    val token = scan.byOffset[offset]
                    if (token != null) {
                        // 圆括号等：栈深度 + markup 偏移
                        val level = PrismColors.levelOf(token.depth + scan.depthShift, myColorCount)
                        val info = HighlightInfo.newHighlightInfo(bracketTypes[level])
                            .range(offset, offset + token.length)
                            .create()
                        if (info != null) holder.add(info)
                        offset += token.length
                        continue
                    }
                    // markup 标签尖括号（< / >）：纯文本扫描表，同一标签的所有片段同 level
                    val tb = scan.tagBracketsByOffset[offset]
                    if (tb != null) {
                        val level = PrismColors.levelOf(tb.level - 1, myColorCount)
                        val info = HighlightInfo.newHighlightInfo(bracketTypes[level])
                            .range(tb.offset, tb.offset + tb.length)
                            .create()
                        if (info != null) holder.add(info)
                        // 开 '<' 顺带给标签名上同 level 颜色
                        val tag = scan.tagByOpenOffset[tb.offset]
                        if (tag != null) {
                            val tagInfo = HighlightInfo.newHighlightInfo(bracketTypes[level])
                                .range(tag.offset, tag.offset + tag.length)
                                .create()
                            if (tagInfo != null) holder.add(tagInfo)
                        } else {
                            LOG.info("rainbow.tag miss openOffset=${tb.offset} text=${text.take(40)}")
                        }
                        offset += tb.length
                        continue
                    }
                    offset++
                }
                // 括号叶子不会是标识符，直接返回
                if (text.length == 1 && isBracketChar(text[0])) return
            }
        }

        // 2) 关键字：扫描表命中则覆盖方案色，并跳过后续标识符逻辑
        var isKeywordToken = false
        val scan = myScan
        if (settings.keywordColoringEnabled) {
            val kw = scan?.keywordsByOffset?.get(element.textRange.startOffset)
            if (kw != null && kw.length == text.length) {
                val level = kw.ordinal % myColorCount.coerceIn(2, PrismColors.MAX_LEVELS)
                val info = HighlightInfo.newHighlightInfo(keywordTypes[level])
                    .range(kw.offset, kw.offset + kw.length)
                    .create()
                if (info != null) holder.add(info)
                return
            }
            if (kw != null) isKeywordToken = true
        }

        // 3) 标签名已由括号分支统一着色（随 '<' 同 level）。这里防止标识符分支
        //    把标签名/组件名抢去涂成变量色：leaf 只要与任何标签名区间重叠就跳过。
        if (scan != null) {
            val s = element.textRange.startOffset
            val e = s + text.length
            if (scan.tagNames.any { it.offset < e && it.offset + it.length > s }) return
        }

        // 4) 字符串：覆盖方案里的灰绿，统一鲜艳色
        if (settings.stringColoringEnabled) {
            val str = scan?.stringsByOffset?.get(element.textRange.startOffset)
            if (str != null) {
                // 字符串叶子可能带引号整体一个 token；按扫描表 range 着色
                val info = HighlightInfo.newHighlightInfo(stringType)
                    .range(str.offset, str.offset + str.length)
                    .create()
                if (info != null) holder.add(info)
                return
            }
        }

        // 5) 彩虹变量/类型/方法：仅标识符样叶子
        if (!isKeywordToken && settings.rainbowVariablesEnabled && isIdentifierLike(text)) {
            colorIdentifier(element, holder)
        }
    }

    private fun colorIdentifier(leaf: PsiElement, holder: HighlightInfoHolder) {
        val text = leaf.text

        // 声明位点：标识符叶子通常包在 PsiIdentifier 里（Java: leaf→PsiIdentifier→PsiField），
        // 向上最多 3 层找命名元素，名字必须就是该叶子，且排除引用表达式（其 references 非空）
        var ancestor: PsiElement? = leaf.parent
        var hops = 0
        while (ancestor != null && hops < 3 && ancestor !is PsiFile) {
            if (ancestor is PsiNamedElement && ancestor.name == text &&
                (ancestor.references.isEmpty() ||
                        // XML/HTML/CSS 属性的 references 常非空，仍然按声明处理
                        ancestor.javaClass.simpleName.contains("Attribute") ||
                        ancestor.javaClass.simpleName.contains("Property"))
            ) {
                when (classify(ancestor)) {
                    IdentKind.VAR -> {
                        val range = leaf.textRange
                        addIdentifierInfo(
                            range.startOffset, range.endOffset,
                            identityKey(ancestor), IdentKind.VAR, holder,
                        )
                        return
                    }
                    IdentKind.TYPE -> {
                        val range = leaf.textRange
                        addIdentifierInfo(
                            range.startOffset, range.endOffset,
                            typeIdentityKey(ancestor), IdentKind.TYPE, holder,
                        )
                        return
                    }
                    IdentKind.METHOD -> {
                        val range = leaf.textRange
                        addIdentifierInfo(
                            range.startOffset, range.endOffset,
                            typeIdentityKey(ancestor), IdentKind.METHOD, holder,
                        )
                        return
                    }
                    IdentKind.SKIP -> return
                }
            }
            ancestor = ancestor.parent
            hops++
        }

        // 引用位点：文件级解析（变量引用 / 类型引用 / import 包段）
        val file = leaf.containingFile ?: return
        val start = leaf.textRange.startOffset
        val ref: PsiReference? = file.findReferenceAt(start)
        val resolved = ref?.let {
            try {
                it.resolve()
            } catch (_: Exception) {
                null
            }
        }
        if (resolved is PsiNamedElement && resolved.name == text) {
            when (classify(resolved)) {
                IdentKind.VAR -> {
                    addIdentifierInfo(start, start + text.length, identityKey(resolved), IdentKind.VAR, holder)
                    return
                }
                IdentKind.TYPE -> {
                    addIdentifierInfo(start, start + text.length, typeIdentityKey(resolved), IdentKind.TYPE, holder)
                    return
                }
                IdentKind.METHOD -> {
                    addIdentifierInfo(start, start + text.length, typeIdentityKey(resolved), IdentKind.METHOD, holder)
                    return
                }
                IdentKind.SKIP -> return
            }
        }

        // 兜底：PSI 解析不到的属性名——CSS 中划线属性（font-size）、Vue 指令等。
        // markup 模板区（非 CSS 上下文）的含 - 名字多半是自定义组件标签，交给 tag 逻辑，不走属性兜底。
        val langId = leaf.language.id.lowercase()
        val inCss = langId.contains("css") || langId.contains("scss") || langId.contains("less") ||
                langId.contains("stylus")
        if (looksLikeAttributeName(text) && (inCss || !scanIsMarkup())) {
            addIdentifierInfo(start, start + text.length, "ATTR|$text", IdentKind.VAR, holder)
        }
    }

    private var myMarkup: Boolean = false

    private fun scanIsMarkup(): Boolean = myMarkup

    /** 中划线属性名 / 冒号@#开头的指令名（排除含 . 的选择器） */
    private fun looksLikeAttributeName(text: String): Boolean {
        if (text.isEmpty()) return false
        val first = text[0]
        if (first == ':' || first == '@' || first == '#') return true
        return text.contains('-') && !text.contains('.')
    }

    private fun addIdentifierInfo(
        start: Int,
        end: Int,
        identity: String,
        kind: IdentKind,
        holder: HighlightInfoHolder,
    ) {
        val colorCount = myColorCount
        val map = when (kind) {
            IdentKind.TYPE -> myTypeColors
            IdentKind.METHOD -> myMethodColors
            IdentKind.VAR -> myVariableColors
            IdentKind.SKIP -> return
        }
        val level = map.getOrPut(identity) { map.size % colorCount }
        val types = when (kind) {
            IdentKind.TYPE -> typeTypes
            IdentKind.METHOD -> methodTypes
            IdentKind.VAR -> variableTypes
            IdentKind.SKIP -> return
        }
        val info = HighlightInfo.newHighlightInfo(types[level])
            .range(start, end)
            .create()
        if (info != null) holder.add(info)
    }

    /**
     * 声明身份：文件路径 + 最多 3 层祖先命名链 + 自身名。
     * 同一变量所有位点同 key → 同色；跨作用域/文件不同 key。
     */
    private fun identityKey(target: PsiNamedElement): String {
        val name = target.name ?: "?"
        val chain = StringBuilder()
        var e: PsiElement? = target.parent
        var depth = 0
        while (e != null && e !is PsiFile && depth < 3) {
            if (e is PsiNamedElement) {
                val n = e.name
                if (n != null) {
                    if (chain.isNotEmpty()) chain.append('/')
                    chain.append(n)
                }
            }
            e = e.parent
            depth++
        }
        val path = target.containingFile?.virtualFile?.path
            ?: target.containingFile?.name
            ?: "?"
        return if (chain.isEmpty()) "$path|$name" else "$path|$chain|$name"
    }

    /**
     * 类型身份：跨文件同名类必须同色，用限定名/文件路径 + 名字。
     * 优先 qualifiedName（Java 类/包），否则退回文件路径。
     */
    private fun typeIdentityKey(target: PsiNamedElement): String {
        val name = target.name ?: "?"
        val qualified = try {
            // PsiClass / PsiPackage 都有 qualifiedName；其他语言没有则走 catch
            val method = target.javaClass.methods.firstOrNull { it.name == "getQualifiedName" && it.parameterCount == 0 }
            method?.invoke(target) as? String
        } catch (_: Exception) {
            null
        }
        if (!qualified.isNullOrEmpty()) return "T|$qualified"
        val path = target.containingFile?.virtualFile?.path
            ?: target.containingFile?.name
            ?: "?"
        return "T|$path|$name"
    }

    private enum class IdentKind { VAR, TYPE, METHOD, SKIP }

    /** 按声明/解析目标的 PSI 实现类简单名分类 */
    private fun classify(target: PsiNamedElement): IdentKind {
        val n = target.javaClass.simpleName
        return when {
            SKIP_DECL.matches(n) -> IdentKind.SKIP
            METHOD_DECL.matches(n) -> IdentKind.METHOD
            TYPE_DECL.matches(n) -> IdentKind.TYPE
            else -> IdentKind.VAR
        }
    }

    private fun isIdentifierLike(text: String): Boolean {
        if (text.isEmpty() || text.length > 128) return false
        if (Character.isDigit(text[0])) return false
        // 标准标识符
        if (Character.isJavaIdentifierStart(text[0]) && text.all { Character.isJavaIdentifierPart(it) }) return true
        // 属性/指令名：font-size、v-if、data-foo 等含中划线的名字
        //（注意不能先走 Java 严格校验——首字符合法但含 '-' 的名字会被误杀）
        if (text.all { c ->
                Character.isLetterOrDigit(c) || c == '-' || c == ':' || c == '@' ||
                        c == '#' || c == '.' || c == '_' || c == '!' || c == '$'
            }
        ) {
            // 纯符号串不算
            return text.any { Character.isLetterOrDigit(it) }
        }
        return false
    }

    private fun isBracketChar(c: Char): Boolean =
        c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == '<' || c == '>'

    override fun clone(): HighlightVisitor = PrismHighlightVisitor()

    companion object {
        private const val MAX_FILE_LENGTH = 1_500_000

        private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(PrismHighlightVisitor::class.java)

        /** 类型/包：类名、接口、枚举、类型参数、包名（import 路径）等 */
        private val TYPE_DECL = Regex(
            ".*(Class|Interface|Enum|Trait|TypeParameter|TypeAlias|TypeSpec|TypeDef|" +
                    "Package|Namespace|Protocol).*"
        )

        /** 方法/函数/构造器：调用名与声明名 */
        private val METHOD_DECL = Regex(".*(Method|Function|FuncDecl|Constructor).*")

        /** 不着色：文件/模块/纯语法构件等 */
        private val SKIP_DECL = Regex(
            ".*(File|Module|Object|Record|Label|Keyword|Token|Def).*"
        )
    }
}
