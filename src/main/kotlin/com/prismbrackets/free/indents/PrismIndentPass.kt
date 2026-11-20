package com.prismbrackets.free.indents

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePassFilter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.text.CharArrayUtil
import com.prismbrackets.free.colors.PrismColors
import com.prismbrackets.free.settings.PrismSettings

/**
 * 彩虹缩进线：
 * - [PrismIndentPassFilter] 注册到平台 `com.intellij.daemon.indentGuidePassFilter` EP，
 *   在本插件缩进线开关开启时禁用平台原生灰色缩进线（避免双线叠加）；
 * - [PrismIndentPass] 是我们自己的 TextEditorHighlightingPass，复刻平台的
 *   描述符算法（见 PrismIndentCalculator），用 [PrismIndentRenderer]
 *   按深度绘制彩色竖线。
 */
internal object PrismIndentSupport {

    /** 每编辑器的缩进线 highlighter 列表，随 pass 重算时清理旧线。 */
    val HIGHLIGHTERS_KEY: Key<List<RangeHighlighter>> = Key.create("rainbow.free.indent.highlighters")

    fun indentSizeOf(project: Project, file: PsiFile): Int {
        val options = CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(file)
        val size = options.INDENT_SIZE
        return if (size in 1..32) size else 4
    }

    fun colorIndexOf(indentLevel: Int, indentSize: Int, colorCount: Int): Int {
        // indentLevel 是列数（如 4/8/12）；映射到 0-based 深度索引
        val depth = (indentLevel + indentSize - 1) / indentSize - 1
        return PrismColors.levelOf(depth.coerceAtLeast(0), colorCount)
    }
}

/** 平台缩进线过滤器：我们的功能开启时让平台原生线让路。 */
class PrismIndentPassFilter : IndentGuidePassFilter {
    override fun shouldRunIndentsPass(editor: Editor): Boolean =
        !PrismSettings.getInstance().indentGuideEnabled

    override fun shouldShowIndentGuide(editor: Editor, descriptor: IndentGuideDescriptor): Boolean = true
}

/**
 * 缩进线渲染器：从 highlighter 范围推导起止行与缩进列，按深度取色画竖线。
 * 坐标处理与平台 IndentGuideRenderer 相同（offsetToXY/visualPositionToXY）。
 */
class PrismIndentRenderer(
    private val indentSize: Int,
    private val colorCount: Int,
    private val palette: List<java.awt.Color>,
) : CustomHighlighterRenderer {

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: java.awt.Graphics) {
        val doc = editor.document
        val startOffset = highlighter.startOffset
        val endOffset = highlighter.endOffset
        if (startOffset >= doc.textLength) return

        // highlighter 范围 = [lineStart(startLine), lineStart(endLine))。
        // 起点行向上跳过空行找第一个非空行——其缩进列即引导线的列。
        val chars = doc.charsSequence
        var line = doc.getLineNumber(startOffset)
        var off: Int
        do {
            val ls = doc.getLineStartOffset(line)
            val le = doc.getLineEndOffset(line)
            off = ls
            while (off < le && (chars[off] == ' ' || chars[off] == '\t')) off++
            line--
        } while (line > 0 && off < chars.length && chars[off] == '\n')

        if (off >= chars.length || chars[off] == '\n') return // 全空
        val indentColumn = editor.offsetToVisualPosition(off).column
        if (indentColumn <= 0) return

        val startPos = editor.offsetToVisualPosition(off)
        val endPos = editor.offsetToVisualPosition(minOf(endOffset, doc.textLength))
        val lineHeight = editor.lineHeight
        val startPt = editor.visualPositionToXY(startPos)
        val endPt = editor.visualPositionToXY(endPos)
        // 平台从起始行的下一行开始画（start.y + lineHeight），避免盖住块首行文字
        var y1 = startPt.y + lineHeight
        var y2 = endPt.y
        if (endPos.line >= editor.offsetToVisualPosition(doc.textLength).line) {
            y2 += lineHeight
        }
        if (y2 <= y1) return

        val clip = g.clipBounds
        if (clip != null) {
            if (clip.y >= y2 || clip.y + clip.height <= y1) return
            y2 = minOf(y2, clip.y + clip.height)
            y1 = maxOf(y1, clip.y)
        }

        val level = indentColumn
        val colorIdx = PrismIndentSupport.colorIndexOf(level, indentSize, colorCount)
        val color = palette[colorIdx % palette.size]

        val g2 = g as? java.awt.Graphics2D ?: return
        // 左移 1px：offsetToXY 给出的是字符 cell 左缘，平台原生缩进线画在 cell 左缘内侧
        val x = startPt.x - 1
        val oldColor = g2.color
        try {
            g2.color = color
            g2.drawLine(x, y1, x, y2 - 1)
        } finally {
            g2.color = oldColor
        }
    }
}

/**
 * 彩虹缩进线 highlighting pass：后台算描述符，EDT 建 highlighter。
 * 平台自身的 indentGuide pass 已由 [PrismIndentPassFilter] 挡掉。
 */
class PrismIndentPass(
    project: Project,
    private val editor: Editor,
    private val psiFile: PsiFile,
) : TextEditorHighlightingPass(project, editor.document, false), DumbAware {

    private var myGuides: List<PrismIndentCalculator.Guide> = emptyList()
    private var myTabSize: Int = 4
    private var myIndentSize: Int = 4

    override fun doCollectInformation(progress: ProgressIndicator) {
        val settings = PrismSettings.getInstance()
        if (!settings.indentGuideEnabled || !editor.settings.isIndentGuidesShown) {
            myGuides = emptyList()
            return
        }
        myTabSize = EditorUtil.getTabSize(editor)
        myIndentSize = PrismIndentSupport.indentSizeOf(myProject, psiFile)

        val doc = editor.document
        val lineCount = doc.lineCount
        val raw = IntArray(lineCount)
        val chars = doc.charsSequence
        for (line in 0 until lineCount) {
            ProgressManager.checkCanceled()
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            raw[line] = PrismIndentCalculator.indentOfLine(chars.subSequence(start, end), myTabSize)
        }
        // 平台同款：空行下方若是右括号/块结束标记，空白行继承 topIndent（线画到块底）
        val fileType = psiFile.fileType
        val isBlockEndLine: (Int) -> Boolean = { line ->
            if (line >= lineCount) {
                false
            } else {
                val ls = doc.getLineStartOffset(line)
                val le = doc.getLineEndOffset(line)
                val off = CharArrayUtil.shiftForward(chars, ls, le, " \t")
                if (off >= le) {
                    false
                } else {
                    val it = editor.highlighter.createIterator(off)
                    BraceMatchingUtil.isRBraceToken(it, chars, fileType) ||
                            (it.tokenType != null &&
                                    CodeBlockSupportHandler.findMarkersRanges(psiFile, it.tokenType.language, off).isNotEmpty())
                }
            }
        }
        myGuides = PrismIndentCalculator.calculate(raw, isBlockEndLine)
    }

    override fun doApplyInformationToEditor() {
        // 清掉上一轮的彩虹缩进线
        val old = editor.getUserData(PrismIndentSupport.HIGHLIGHTERS_KEY)
        old?.forEach { if (it.isValid) it.dispose() }
        editor.putUserData(PrismIndentSupport.HIGHLIGHTERS_KEY, emptyList())

        if (myGuides.isEmpty()) return

        val settings = PrismSettings.getInstance()
        val doc = editor.document
        val palette = PrismColors.INDENT_KEYS.mapIndexed { i, key ->
            editor.colorsScheme.getAttributes(key).foregroundColor ?: PrismColors.INDENT_DEFAULTS[i]
        }
        val renderer = PrismIndentRenderer(myIndentSize, settings.colorCount, palette)
        val lineCount = doc.lineCount
        val created = ArrayList<RangeHighlighter>(myGuides.size)

        for (guide in myGuides) {
            if (guide.startLine >= lineCount) continue
            val start = doc.getLineStartOffset(guide.startLine)
            val end = if (guide.endLine < lineCount) doc.getLineStartOffset(guide.endLine) else doc.textLength
            if (end <= start) continue
            val hl = editor.markupModel.addRangeHighlighter(
                start,
                end,
                0,
                null,
                HighlighterTargetArea.EXACT_RANGE,
            )
            hl.setCustomRenderer(renderer)
            created.add(hl)
        }
        editor.putUserData(PrismIndentSupport.HIGHLIGHTERS_KEY, created)
    }
}

/**
 * 注册到 `com.intellij.highlightingPassFactory` EP。
 * 顺序与平台 indentGuide pass 一致（BEFORE UPDATE_FOLDING）。
 */
class PrismIndentPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, DumbAware {

    override fun registerHighlightingPassFactory(
        registrar: TextEditorHighlightingPassRegistrar,
        project: Project,
    ) {
        registrar.registerTextEditorHighlightingPass(
            this,
            TextEditorHighlightingPassRegistrar.Anchor.BEFORE,
            com.intellij.codeHighlighting.Pass.UPDATE_FOLDING,
            false,
            false,
        )
    }

    override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!PrismSettings.getInstance().indentGuideEnabled) return null
        if (editor.project == null) return null
        return PrismIndentPass(psiFile.project, editor, psiFile)
    }
}
