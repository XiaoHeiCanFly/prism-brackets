package com.prismbrackets.free.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import java.awt.Color
import java.awt.Font

/**
 * 自定义 TextAttributesKey 集合：
 * - 括号 N 级颜色（按嵌套深度循环）
 * - 变量 N 级颜色（彩虹变量，斜体）
 * - 缩进线 N 级颜色
 * - 作用域：不需要单独 key——边框取当前括号颜色，内部为其透明度减弱版本（运行时合成）
 */
object PrismColors {

    private const val PREFIX_BRACKET = "RAINBOW_FREE_BRACKET_"
    private const val PREFIX_VARIABLE = "RAINBOW_FREE_VARIABLE_"
    private const val PREFIX_TYPE = "RAINBOW_FREE_TYPE_"
    private const val PREFIX_METHOD = "RAINBOW_FREE_METHOD_"
    private const val PREFIX_KEYWORD = "RAINBOW_FREE_KEYWORD_"
    private const val PREFIX_STRING = "RAINBOW_FREE_STRING_"
    private const val PREFIX_INDENT = "RAINBOW_FREE_INDENT_"

    /** 支持的最大颜色级数（与配色页展示一致） */
    const val MAX_LEVELS = 10

    // ---- 默认颜色：深色主题高饱和色轮，色相均匀分布（参考主流彩虹括号的丰富配色）----

    /**
     * 括号默认前景色（10 色，冷色调为主：青/蓝/紫/薄荷/绿循环）。
     * 按色轮步长交错排列，相邻嵌套深度色相差约 1/3 色轮；
     * 循环回首尾同样保持大色差。暖色只保留少量用于跳色对比。
     */
    val BRACKET_DEFAULTS: List<Color> = listOf(
        Color(0x6E, 0xC8, 0xE8), // 0 淡青
        Color(0xB7, 0xD5, 0x4C), // 1 黄绿
        Color(0x8A, 0x9C, 0xF0), // 2 蓝紫
        Color(0x4F, 0xC3, 0xA0), // 3 青绿
        Color(0x7A, 0xA8, 0xFF), // 4 冰蓝
        Color(0xA8, 0x7B, 0xE8), // 5 紫
        Color(0x56, 0xD4, 0xC0), // 6 薄荷
        Color(0x6E, 0x90, 0xF0), // 7 蓝
        Color(0x98, 0xC3, 0x79), // 8 冷绿
        Color(0x56, 0xB6, 0xC2), // 9 灰青
    )

    /** 变量默认前景色：括号色轮旋转半个周期错开 + 整体提亮，避免与括号混淆 */
    val VARIABLE_DEFAULTS: List<Color> = listOf(
        Color(0xFF, 0x9E, 0xC8), // 亮粉
        Color(0xFF, 0xC1, 0x78), // 亮橙
        Color(0xFF, 0xE0, 0x8A), // 亮黄
        Color(0xD4, 0xEF, 0x7A), // 亮黄绿
        Color(0x9A, 0xE0, 0x8A), // 亮绿
        Color(0x7E, 0xE0, 0xC2), // 亮青绿
        Color(0x7E, 0xD4, 0xF5), // 亮青蓝
        Color(0x9A, 0xB5, 0xF5), // 亮蓝
        Color(0xC9, 0xA0, 0xFF), // 亮紫
        Color(0xFF, 0x9E, 0xD8), // 亮品红
    )

    /** 类型/包名默认前景色（类名、import 路径等；偏冷色系，与变量暖色区分） */
    val TYPE_DEFAULTS: List<Color> = listOf(
        Color(0x80, 0xCB, 0xC4), // 青瓷
        Color(0x82, 0xAA, 0xFF), // 亮蓝
        Color(0xC7, 0x92, 0xEA), // 紫
        Color(0xF7, 0x8C, 0x6C), // 橙红
        Color(0xFF, 0xCB, 0x6B), // 金黄
        Color(0x89, 0xDD, 0xFF), // 浅青
        Color(0xC3, 0xE8, 0x8D), // 亮绿
        Color(0xFF, 0x9C, 0xAC), // 珊瑚
        Color(0xA5, 0xB6, 0xFF), // 淡蓝紫
        Color(0xB2, 0xCC, 0xD6), // 灰蓝
    )

    /** 方法默认前景色（调用名/构造器；柔和 Nord 系，方法多也不吵） */
    val METHOD_DEFAULTS: List<Color> = listOf(
        Color(0xE8, 0xA2, 0xBE), // 粉紫
        Color(0xA3, 0xD9, 0xA5), // 浅绿
        Color(0x7E, 0xC8, 0xE3), // 天蓝
        Color(0xF0, 0xC6, 0x74), // 沙金
        Color(0xC5, 0xA3, 0xCF), // 淡紫
        Color(0x88, 0xC0, 0xD0), // 蓝灰
        Color(0xEB, 0xCB, 0x8B), // 麦黄
        Color(0xA3, 0xBE, 0x8C), // 苔绿
        Color(0xB4, 0x8E, 0xAD), // 紫红
        Color(0x8F, 0xBC, 0xBB), // 青灰
    )

    /** 关键字默认前景色（覆盖方案里的橙色关键字；偏冷的粉彩系，避免刺眼） */
    val KEYWORD_DEFAULTS: List<Color> = listOf(
        Color(0x8A, 0xA8, 0xFF), // 淡蓝紫
        Color(0x6E, 0xC6, 0xE8), // 淡青
        Color(0xB8, 0x9A, 0xE8), // 淡紫
        Color(0xF0, 0x8A, 0xAB), // 淡粉
        Color(0x8E, 0xD4, 0x9A), // 淡绿
        Color(0xE8, 0xC8, 0x78), // 淡金
        Color(0x7A, 0xDC, 0xC8), // 薄荷
        Color(0xA0, 0xB8, 0xF8), // 雾蓝
        Color(0xD9, 0x8A, 0xC8), // 兰紫
        Color(0x92, 0xC8, 0xD8), // 灰青
    )

    /** 缩进线默认前景色（比括号暗淡，避免抢视线） */
    val INDENT_DEFAULTS: List<Color> = BRACKET_DEFAULTS.map { c ->
        Color(
            (c.red * 0.65).toInt().coerceIn(0, 255),
            (c.green * 0.65).toInt().coerceIn(0, 255),
            (c.blue * 0.65).toInt().coerceIn(0, 255),
        )
    }

    val BRACKET_KEYS: List<TextAttributesKey> = BRACKET_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_BRACKET}${i + 1}", defaultAttrs(c))
    }

    /** 变量 key 带斜体——与语义高亮惯例一致，也更接近参考效果 */
    val VARIABLE_KEYS: List<TextAttributesKey> = VARIABLE_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_VARIABLE}${i + 1}", defaultAttrs(c, Font.ITALIC))
    }

    val TYPE_KEYS: List<TextAttributesKey> = TYPE_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_TYPE}${i + 1}", defaultAttrs(c))
    }

    val METHOD_KEYS: List<TextAttributesKey> = METHOD_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_METHOD}${i + 1}", defaultAttrs(c))
    }

    val KEYWORD_KEYS: List<TextAttributesKey> = KEYWORD_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_KEYWORD}${i + 1}", defaultAttrs(c))
    }

    /** 字符串统一色：鲜艳亮绿，替换方案里发灰的默认字符串色 */
    val STRING_KEY: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("${PREFIX_STRING}1", defaultAttrs(Color(0xC3, 0xE8, 0x8D)))

    val INDENT_KEYS: List<TextAttributesKey> = INDENT_DEFAULTS.mapIndexed { i, c ->
        TextAttributesKey.createTextAttributesKey("${PREFIX_INDENT}${i + 1}", defaultAttrs(c))
    }

    private fun defaultAttrs(fg: Color, fontStyle: Int = Font.PLAIN) =
        com.intellij.openapi.editor.markup.TextAttributes(fg, null, null, null, fontStyle)

    /** 深度 → 级别（0-based 深度，循环 colorCount 种颜色） */
    fun levelOf(depth: Int, colorCount: Int): Int {
        val n = colorCount.coerceIn(2, MAX_LEVELS)
        return (depth % n + n) % n
    }

    /** 将颜色与 alpha 合成（scope 内部填充用）：返回带 alpha 的 ARGB。 */
    fun withAlpha(color: Color, alphaPercent: Int): Color {
        val a = alphaPercent.coerceIn(0, 100)
        return Color(color.red, color.green, color.blue, a * 255 / 100)
    }
}
