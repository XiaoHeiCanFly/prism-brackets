package com.prismbrackets.free.core

import com.prismbrackets.free.colors.PrismColors
import java.awt.Color

/**
 * 作用域高亮的计算：给定光标位置与已扫描的括号列表，
 * 找到光标所在的最内层已配对括号，并给出边框色/填充色。
 *
 * 纯逻辑，便于测试。
 */
object ScopeResolver {

    data class Scope(
        val openOffset: Int,
        val closeOffset: Int,
        val depth: Int,
        val borderColor: Color,
        val fillColor: Color,
    )

    /**
     * @param caretOffset 光标偏移
     * @param brackets 已扫描括号（BracketScanner.scan 输出）
     * @param palette 括号调色板（按级别索引）
     * @param fillOpacity 内部填充透明度百分比 0-100
     * @return 光标不在任何配对括号内时返回 null
     */
    fun resolve(
        caretOffset: Int,
        brackets: List<BracketToken>,
        palette: List<Color>,
        fillOpacity: Int,
    ): Scope? {
        // 光标位于配对区间 [open, close+1) 内（或正好在括号上）的最内层
        val candidates = brackets
            .filter { !it.isOpen && it.pairOffset >= 0 }
            // 区间 [open, close]：光标在开括号、中间、或闭括号上时命中；
            // 闭括号之后一位不算（否则紧邻位置会误取更内层）。
            .filter { caretOffset in it.pairOffset..it.offset }
            .minByOrNull { it.offset - it.pairOffset } // 最内层 = 区间最短
            ?: return null

        val level = PrismColors.levelOf(candidates.depth, palette.size)
        val border = palette[level]
        return Scope(
            openOffset = candidates.pairOffset,
            closeOffset = candidates.offset,
            depth = candidates.depth,
            borderColor = border,
            fillColor = PrismColors.withAlpha(border, fillOpacity),
        )
    }
}
