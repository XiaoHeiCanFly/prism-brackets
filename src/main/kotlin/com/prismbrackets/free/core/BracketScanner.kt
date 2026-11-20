package com.prismbrackets.free.core

/**
 * 括号对扫描结果中的单个括号。
 *
 * @param offset 文档中的起始偏移
 * @param length 括号文本长度（通常 1，HTML 标签等情况可为多字符 token 的切片）
 * @param char 括号字符
 * @param isOpen 是否为开括号
 * @param depth 嵌套深度（0-based）：同一对开/括号共享同一 depth
 * @param pairOffset 配对括号的偏移；未配对时为 -1
 */
data class BracketToken(
    val offset: Int,
    val length: Int,
    val char: Char,
    val isOpen: Boolean,
    val depth: Int,
    val pairOffset: Int,
)

/**
 * 括号扫描器：给定一个"候选括号字符序列"（已剔除字符串/注释内的字符），
 * 用栈计算每个括号的嵌套深度与配对关系。
 *
 * 纯函数、不依赖 IntelliJ API，便于单元测试。
 */
object BracketScanner {

    private val OPEN = charArrayOf('(', '[', '{', '<')
    private val CLOSE = charArrayOf(')', ']', '}', '>')

    /** 配对表：openChar -> closeChar */
    private val PAIRS: Map<Char, Char> = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '<' to '>',
    )

    fun isOpen(c: Char): Boolean = c in OPEN
    fun isClose(c: Char): Boolean = c in CLOSE
    fun isBracket(c: Char): Boolean = isOpen(c) || isClose(c)

    /**
     * 扫描候选括号序列。
     *
     * @param candidates 按 offset 升序的候选括号（已排除字符串/注释中的）
     * @return 按 offset 升序的 BracketToken 列表（含未配对括号，pairOffset = -1）
     */
    fun scan(candidates: List<CandidateBracket>): List<BracketToken> {
        data class Frame(val idx: Int, val cand: CandidateBracket, val depth: Int)

        val result = arrayOfNulls<BracketToken>(candidates.size)
        val stack = ArrayDeque<Frame>()

        candidates.forEachIndexed { i, cand ->
            if (isOpen(cand.char)) {
                val depth = stack.size
                stack.addLast(Frame(i, cand, depth))
                // 先占位，配对时回填 pairOffset
                result[i] = BracketToken(cand.offset, cand.length, cand.char, true, depth, -1)
            } else if (isClose(cand.char)) {
                val expectedOpen = PAIRS.entries.firstOrNull { it.value == cand.char }?.key
                // 尝试与栈顶配对；栈顶类型不匹配时向上找同类型（容错，不弹出中间不匹配项）
                var matchIdx = -1
                var matchFrame: Frame? = null
                val it = stack.listIterator(stack.size)
                while (it.hasPrevious()) {
                    val f = it.previous()
                    if (f.cand.char == expectedOpen) {
                        matchIdx = stack.indexOf(f)
                        matchFrame = f
                        break
                    }
                }
                if (matchFrame != null) {
                    val depth = matchFrame.depth
                    result[i] = BracketToken(cand.offset, cand.length, cand.char, false, depth, matchFrame.cand.offset)
                    // 回填开括号的 pairOffset，并移除栈中该帧及其上所有帧（上帧视为未配对）
                    val openResult = result[matchFrame.idx]!!
                    result[matchFrame.idx] = openResult.copy(pairOffset = cand.offset)
                    // 移除 matchIdx 及之后的帧
                    while (stack.size > matchIdx) {
                        stack.removeLast()
                    }
                } else {
                    // 未配对闭括号：按当前栈深着色
                    val depth = stack.size
                    result[i] = BracketToken(cand.offset, cand.length, cand.char, false, depth, -1)
                }
            }
        }

        // 栈中剩余开括号保持 pairOffset = -1
        return result.filterNotNull().sortedBy { it.offset }
    }
}

/**
 * 候选括号：扫描输入。
 *
 * @param offset 偏移
 * @param length 长度
 * @param char 括号字符
 */
data class CandidateBracket(val offset: Int, val length: Int, val char: Char)
