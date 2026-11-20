package com.prismbrackets.free.scope

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

/**
 * 作用域渲染：边框 = 当前括号颜色，内部 = 同色按透明度减弱。
 * 画在背景之后、文字之前（AFTER_BACKGROUND），填充不遮挡文字。
 *
 * 坐标系与平台 IndentGuideRenderer 相同：paint 通道已做 y 平移，
 * 直接使用 editor.offsetToXY 的坐标即可。
 */
class ScopeHighlightRenderer(
    private val borderColor: Color,
    private val fillColor: Color,
) : CustomHighlighterRenderer {

    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_BACKGROUND

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        val start = highlighter.startOffset
        val end = highlighter.endOffset
        if (start >= end) return
        val textLength = editor.document.textLength
        if (start >= textLength) return

        val openPt = editor.offsetToXY(start)
        val closePt = editor.offsetToXY(minOf(end - 1, textLength - 1))
        // 闭括号右缘：end 处字符的起点 x（覆盖闭括号自身宽度）
        val closeRightX = editor.offsetToXY(minOf(end, textLength)).x
        val lineHeight = editor.lineHeight

        // 右缘必须盖住作用域内每一行的行末：闭括号常在行首（col 0），
        // 若只取括号 x，右边界会缩到开括号那一列。
        val document = editor.document
        val firstLine = document.getLineNumber(start)
        val lastLine = document.getLineNumber(minOf(end - 1, textLength - 1))
        var contentRightX = maxOf(openPt.x, closeRightX)
        for (line in firstLine..lastLine) {
            val lineEndX = editor.offsetToXY(document.getLineEndOffset(line)).x
            if (lineEndX > contentRightX) contentRightX = lineEndX
        }

        val x1 = minOf(openPt.x, closePt.x)
        val x2 = contentRightX
        val y1 = openPt.y
        val y2 = closePt.y + lineHeight
        if (x2 <= x1 || y2 <= y1) return

        val g2 = g as? Graphics2D ?: return
        val oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        val oldStroke = g2.stroke
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val arc = (lineHeight / 3).coerceAtMost(8)
            val rect = RoundRectangle2D.Float(x1.toFloat(), y1.toFloat(), (x2 - x1).toFloat(), (y2 - y1).toFloat(), arc.toFloat(), arc.toFloat())

            g2.color = fillColor
            g2.fill(rect)

            g2.color = borderColor
            g2.stroke = BasicStroke(1.5f)
            g2.draw(rect)
        } finally {
            g2.stroke = oldStroke
            if (oldHint != null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint)
        }
    }
}
