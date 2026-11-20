package com.prismbrackets.free.colors

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.lang.Language
import com.prismbrackets.free.PrismBundle
import javax.swing.Icon

/**
 * Settings | Editor | Color Scheme | Prism Brackets
 *
 * 展示五组颜色 key（括号/变量/类型/方法/关键字/缩进线），默认值由 PrismColors 中
 * createTextAttributesKey 的默认属性提供。
 */
class PrismColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = PrismBundle.message("color.page.display.name")

    override fun getIcon(): Icon? = null

    override fun getHighlighter(): SyntaxHighlighter =
        SyntaxHighlighterFactory.getSyntaxHighlighter(Language.findLanguageByID("TEXT")!!, null, null)

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        val map = HashMap<String, TextAttributesKey>()
        PrismColors.BRACKET_KEYS.forEachIndexed { i, k -> map["b${i + 1}"] = k }
        PrismColors.VARIABLE_KEYS.forEachIndexed { i, k -> map["v${i + 1}"] = k }
        PrismColors.TYPE_KEYS.forEachIndexed { i, k -> map["t${i + 1}"] = k }
        PrismColors.METHOD_KEYS.forEachIndexed { i, k -> map["m${i + 1}"] = k }
        PrismColors.KEYWORD_KEYS.forEachIndexed { i, k -> map["k${i + 1}"] = k }
        map["s"] = PrismColors.STRING_KEY
        PrismColors.INDENT_KEYS.forEachIndexed { i, k -> map["i${i + 1}"] = k }
        return map
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        val list = ArrayList<AttributesDescriptor>()
        PrismColors.BRACKET_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.bracket", i + 1), k))
        }
        PrismColors.VARIABLE_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.variable", i + 1), k))
        }
        PrismColors.TYPE_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.type", i + 1), k))
        }
        PrismColors.METHOD_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.method", i + 1), k))
        }
        PrismColors.KEYWORD_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.keyword", i + 1), k))
        }
        list.add(AttributesDescriptor(PrismBundle.message("color.page.string"), PrismColors.STRING_KEY))
        PrismColors.INDENT_KEYS.forEachIndexed { i, k ->
            list.add(AttributesDescriptor(PrismBundle.message("color.page.indent", i + 1), k))
        }
        return list.toTypedArray()
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    companion object {
        private val DEMO_TEXT = """
            <k1>import</k1> <t2>java</t2>.<t3>util</t3>.<t4>List</t4>;
            <k2>class</k2> <t5>Demo</t5> {
                <k3>fun</k3> <b2>run</b2>(<v1>arg</v1>: <t6>String</t6>) {
                    <v2>list</v2>.<v3>forEach</v3> { <v4>item</v4> <b4>=></b4>
                        <b5>println</b5>(<b6>"${'$'}{'$'}{<v1>arg</v1>}</b6>)
                    }
                }
            }
            <k4>val</k4> <v5>nested</v5> = <b7>(</b7><b8>[</b8><b9>{</b9><b10>(</b10><b1>)</b1><b10>)</b10><b9>}</b9><b8>]</b8><b7>)</b7>
            <k5>var</k5> <v6>a</v6> = <v7>b</v7> + <v8>c</v8> * (<v9>d</v9> - <v10>e</v10>)
            <t7>User</t7> <v6>u</v6> = <k6>new</k6> <t9>User</t9>(<m1>getId</m1>());
            <m2>conn</m2>.<m3>prepareStatement</m3>(<v8>sql</v8>);
            <m4>users</m4>.<m5>add</m5>(<m6>rs</m6>.<m7>getInt</m7>(<m8>id</m8>));
            <k7>val</k7> <v9>s</v9> = <s>"bright string"</s>;
        """.trimIndent()
    }
}
