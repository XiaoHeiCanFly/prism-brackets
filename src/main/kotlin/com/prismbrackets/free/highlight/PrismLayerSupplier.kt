package com.prismbrackets.free.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.InternalLayerSupplier
import com.intellij.openapi.editor.markup.HighlighterLayer

/**
 * 彩虹着色绘制层提升。
 *
 * 平台按 severity 分层绘制：INFORMATION(ADDITIONAL_SYNTAX=3000) < WARNING(4000) < ERROR(5000)，
 * 数值大的后绘制、覆盖同位置的字色。当标签上存在警告（如 Vue「组件未导入」）时，
 * 警告的字色会盖掉彩虹——就是"染色失败"的假象。
 *
 * 本扩展把插件自己的彩虹 info 提到 5100（ERROR 之上、HYPERLINK/SELECTION 之下）：
 * - 警告/错误的波浪线、背景、error stripe 先绘制，全部保留（醒目度不受影响）
 * - 彩虹字色最后绘制，覆盖警告的字色
 * - 超链接(5900)、文本选中(6000)仍在彩虹之上，行为不变
 */
class PrismLayerSupplier : InternalLayerSupplier {

    override fun getLayer(info: HighlightInfo): Int {
        val key = info.forcedTextAttributesKey ?: info.type?.attributesKey ?: return -1
        return if (key.externalName.startsWith("RAINBOW_FREE_")) RAINBOW_LAYER else -1
    }

    private companion object {
        /** ERROR(5000) 之上、HYPERLINK(5900) 之下 */
        const val RAINBOW_LAYER = 5100
    }
}
