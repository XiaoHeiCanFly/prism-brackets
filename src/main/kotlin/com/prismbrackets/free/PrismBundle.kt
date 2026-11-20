package com.prismbrackets.free

import com.intellij.DynamicBundle

/**
 * 插件文案资源：跟随 IDE 语言加载 PrismBundle.properties（en）
 * 或 PrismBundle_zh_CN.properties（简体中文）。
 */
object PrismBundle : DynamicBundle("messages.PrismBundle") {

    @JvmStatic
    fun message(key: String, vararg params: Any): String = getMessage(key, *params)
}
