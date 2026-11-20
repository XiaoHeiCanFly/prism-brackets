package com.prismbrackets.free.indents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismIndentCalculatorTest {

    @Test
    fun `flat file has no guides`() {
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 0, 0))
        assertTrue(guides.isEmpty())
    }

    @Test
    fun `single level block matches platform behavior`() {
        // 平台算法：0→4 推入 level 0（不画），单层块无 guide
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 4, 0))
        assertTrue(guides.isEmpty())
    }

    @Test
    fun `nested block produces outer guide`() {
        // def / if / body / close / close
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 4, 8, 4, 0))
        assertEquals(1, guides.size)
        assertEquals(4, guides[0].level)
        assertEquals(1, guides[0].startLine)
        assertEquals(3, guides[0].endLine)
    }

    @Test
    fun `deep nesting produces guides per level`() {
        // 与平台算法独立 Python 实测输出一致：(8,2,4) 和 (4,1,5)
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 4, 8, 12, 8, 4, 0))
        assertEquals(2, guides.size)
        val inner = guides.first { it.level == 8 }
        assertEquals(2, inner.startLine)
        assertEquals(4, inner.endLine)
        val outer = guides.first { it.level == 4 }
        assertEquals(1, outer.startLine)
        assertEquals(5, outer.endLine)
    }

    @Test
    fun `blank lines inherit and do not invent guides`() {
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 4, -1, 4, 0))
        assertTrue(guides.isEmpty())
    }

    @Test
    fun `blank line between nested levels keeps guide`() {
        val guides = PrismIndentCalculator.calculate(intArrayOf(0, 4, 8, -1, 8, 4, 0))
        assertEquals(1, guides.size)
        assertEquals(4, guides[0].level)
    }

    @Test
    fun `blank line before closing brace keeps guide to brace`() {
        // 嵌套块，} 前有空行：[0, 4, 8, -1, 4(}), 0]
        // 平台右括号特判：空行继承 top(8) 而非 min(8,4)，guide 的 endLine 应到 } 行
        val raw = intArrayOf(0, 4, 8, -1, 4, 0)
        val guides = PrismIndentCalculator.calculate(raw) { line -> line == 4 }
        assertEquals(1, guides.size)
        assertEquals(4, guides[0].level)
        assertEquals(1, guides[0].startLine)
        assertEquals(4, guides[0].endLine)
    }

    @Test
    fun `blank line before closing brace without marker truncates`() {
        // 同样布局但下方不是块结束符（如纯表达式行）：空行继承 min(8,4)=4，endLine 停在空行
        val raw = intArrayOf(0, 4, 8, -1, 4, 0)
        val guides = PrismIndentCalculator.calculate(raw) { false }
        assertEquals(1, guides.size)
        assertEquals(4, guides[0].level)
        assertEquals(1, guides[0].startLine)
        assertEquals(3, guides[0].endLine)
    }

    @Test
    fun `indentOfLine expands tabs to stops`() {
        assertEquals(-1, PrismIndentCalculator.indentOfLine("", 4))
        assertEquals(-1, PrismIndentCalculator.indentOfLine("   ", 4))
        assertEquals(4, PrismIndentCalculator.indentOfLine("    x", 4))
        assertEquals(4, PrismIndentCalculator.indentOfLine("\tx", 4))
        assertEquals(8, PrismIndentCalculator.indentOfLine("\t\tx", 4))
        // 2 空格 + tab → tab 推到下一个 4 停靠点
        assertEquals(4, PrismIndentCalculator.indentOfLine("  \tx", 4))
    }
}
