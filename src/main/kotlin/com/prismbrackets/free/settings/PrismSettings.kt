package com.prismbrackets.free.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 应用级设置。全部功能独立开关；scopeHighlighting 默认关闭（需求 2）。
 */
@State(name = "PrismBracketsFreeSettings", storages = [Storage("prismBracketsFree.xml")])
class PrismSettings : PersistentStateComponent<PrismSettings> {

    /** 彩虹括号总开关 */
    var rainbowEnabled: Boolean = true

    /** 作用域高亮：独立开关，默认关闭 */
    var scopeHighlightingEnabled: Boolean = false

    /** 作用域填充透明度（0-100，内部填充的 alpha 百分比） */
    var scopeFillOpacity: Int = 20

    /** 彩虹变量（语义高亮）开关 */
    var rainbowVariablesEnabled: Boolean = true

    /** 缩进线高亮开关 */
    var indentGuideEnabled: Boolean = true

    /** 关键字着色：覆盖方案里的关键字颜色（默认开启） */
    var keywordColoringEnabled: Boolean = true

    /** 字符串着色：覆盖方案里的字符串颜色（默认开启） */
    var stringColoringEnabled: Boolean = true

    /** 彩虹色循环数量 */
    var colorCount: Int = 8

    override fun getState(): PrismSettings = this

    override fun loadState(state: PrismSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): PrismSettings =
            ApplicationManager.getApplication().getService(PrismSettings::class.java)

        /** 设置变更后调用：重启全项目高亮 */
        fun notifySettingsChanged() {
            // 高亮重算由 Configurable.apply / Action 触发 DaemonCodeAnalyzer.restart
        }
    }
}
