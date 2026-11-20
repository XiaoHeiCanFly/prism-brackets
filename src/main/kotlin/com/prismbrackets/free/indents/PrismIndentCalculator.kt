package com.prismbrackets.free.indents

/**
 * 缩进线区间计算器（纯函数，可单测）。
 *
 * 算法与 IntelliJ 平台 IndentGuideCalculator/buildDescriptors 行为对齐：
 * 1) 逐行计算缩进列数（空行标记 -1）；
 * 2) 空行区间用 min(上界, 下界) 继承推导；
 * 3) 单调栈找出"缩进加深又回落"的区间，生成 (level列数, startLine, endLine) 描述符；
 * 4) level=0（编辑器左边缘）不产生描述符——与平台一致。
 *
 * 输入为原始行缩进（-1 表示纯空白行），输出为描述符列表。
 */
object PrismIndentCalculator {

    data class Guide(val level: Int, val startLine: Int, val endLine: Int)

    /**
     * @param rawIndents 每行缩进列数；-1 = 纯空白行
     * @param isBlockEndLine 判断某非空行是否以右括号/块结束标记开头。平台逻辑：
     *        空白行默认继承 min(top, bottom)，但若下方第一行是块结束符
     *        （} 等），则继承 top——否则结束括号前的空行会把缩进线提前截断。
     */
    fun calculate(
        rawIndents: IntArray,
        isBlockEndLine: (Int) -> Boolean = { false },
    ): List<Guide> {
        if (rawIndents.isEmpty()) return emptyList()
        val indents = resolveBlanks(rawIndents, isBlockEndLine)
        val guides = ArrayList<Guide>()
        val lineStack = ArrayDeque<Int>()
        val levelStack = ArrayDeque<Int>()
        lineStack.addLast(0)
        levelStack.addLast(0)

        for (line in 1 until indents.size) {
            val cur = kotlin.math.abs(indents[line])
            while (levelStack.isNotEmpty() && cur <= levelStack.last()) {
                val level = levelStack.removeLast()
                val startLine = lineStack.removeLast()
                if (level > 0) {
                    // 区间内存在与 level 不同的行才画（避免纯平区域画废线）
                    for (i in startLine until line) {
                        if (level != kotlin.math.abs(indents[i])) {
                            guides.add(Guide(level, startLine, line))
                            break
                        }
                    }
                }
            }
            val prev = kotlin.math.abs(indents[line - 1])
            if (cur - prev > 1) {
                lineStack.addLast(line - 1)
                levelStack.addLast(prev)
            }
        }
        while (levelStack.isNotEmpty()) {
            val level = levelStack.removeLast()
            val startLine = lineStack.removeLast()
            if (level > 0) {
                guides.add(Guide(level, startLine, indents.size))
            }
        }
        return guides
    }

    /** 空行区间继承（与平台 IndentGuideCalculator 对齐，含右括号特判）。 */
    private fun resolveBlanks(raw: IntArray, isBlockEndLine: (Int) -> Boolean): IntArray {
        val out = raw.copyOf()
        var top = 0
        var line = 0
        while (line < out.size) {
            if (out[line] >= 0) {
                top = out[line]
            } else {
                val start = line
                while (line < out.size && out[line] < 0) line++
                val bottom = if (line < out.size) out[line] else top
                var indent = minOf(top, bottom)
                if (bottom < top && line < out.size && isBlockEndLine(line)) {
                    indent = top
                }
                for (i in start until line) {
                    out[i] = -minOf(top, indent)
                }
                line-- // 会被循环尾 ++ 抵消，重新检查边界行
            }
            line++
        }
        return out
    }

    /** 从文档行文本计算缩进列数（tab 展开到下一个 tab 停靠点）；纯空白行返回 -1。 */
    fun indentOfLine(lineText: CharSequence, tabSize: Int): Int {
        var col = 0
        var i = 0
        while (i < lineText.length) {
            when (lineText[i]) {
                ' ' -> col++
                '\t' -> col = (col / tabSize + 1) * tabSize
                else -> return col
            }
            i++
        }
        return -1 // 空白行
    }
}
