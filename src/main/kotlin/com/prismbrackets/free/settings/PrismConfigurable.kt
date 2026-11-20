package com.prismbrackets.free.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.prismbrackets.free.PrismBundle
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings | Tools | Prism Brackets
 *
 * 各功能独立开关；作用域高亮默认关闭。
 * apply() 后重启所有打开项目的高亮。
 */
class PrismConfigurable : Configurable {

    private var panel: JPanel? = null

    private val rainbowCheck = JBCheckBox(PrismBundle.message("settings.rainbow"))
    private val scopeCheck = JBCheckBox(PrismBundle.message("settings.scope"))
    private val variablesCheck = JBCheckBox(PrismBundle.message("settings.variables"))
    private val keywordCheck = JBCheckBox(PrismBundle.message("settings.keyword"))
    private val stringCheck = JBCheckBox(PrismBundle.message("settings.string"))
    private val indentCheck = JBCheckBox(PrismBundle.message("settings.indent"))
    private val colorCountLabel = JBLabel(PrismBundle.message("settings.color.count"))
    private val colorCountField = JBTextField(2)
    private val scopeOpacityField = JBTextField(3)

    override fun getDisplayName(): String = PrismBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        val form = FormBuilder.createFormBuilder()
            .addComponent(rainbowCheck)
            .addComponent(scopeCheck)
            .addLabeledComponent(PrismBundle.message("settings.scope.opacity"), scopeOpacityField)
            .addComponent(variablesCheck)
            .addComponent(keywordCheck)
            .addComponent(stringCheck)
            .addComponent(indentCheck)
            .addLabeledComponent(colorCountLabel, colorCountField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        panel = form
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val s = PrismSettings.getInstance()
        return rainbowCheck.isSelected != s.rainbowEnabled ||
                scopeCheck.isSelected != s.scopeHighlightingEnabled ||
                variablesCheck.isSelected != s.rainbowVariablesEnabled ||
                keywordCheck.isSelected != s.keywordColoringEnabled ||
                stringCheck.isSelected != s.stringColoringEnabled ||
                indentCheck.isSelected != s.indentGuideEnabled ||
                scopeOpacityField.text.trim() != s.scopeFillOpacity.toString() ||
                colorCountField.text.trim() != s.colorCount.toString()
    }

    override fun apply() {
        val s = PrismSettings.getInstance()
        s.rainbowEnabled = rainbowCheck.isSelected
        s.scopeHighlightingEnabled = scopeCheck.isSelected
        s.rainbowVariablesEnabled = variablesCheck.isSelected
        s.keywordColoringEnabled = keywordCheck.isSelected
        s.stringColoringEnabled = stringCheck.isSelected
        s.indentGuideEnabled = indentCheck.isSelected
        s.scopeFillOpacity = scopeOpacityField.text.trim().toIntOrNull()?.coerceIn(0, 100) ?: s.scopeFillOpacity
        s.colorCount = colorCountField.text.trim().toIntOrNull()?.coerceIn(2, 10) ?: s.colorCount
        restartHighlighting()
        com.prismbrackets.free.scope.ScopeHighlightEditorListener.refreshAllEditors()
    }

    override fun reset() {
        val s = PrismSettings.getInstance()
        rainbowCheck.isSelected = s.rainbowEnabled
        scopeCheck.isSelected = s.scopeHighlightingEnabled
        variablesCheck.isSelected = s.rainbowVariablesEnabled
        keywordCheck.isSelected = s.keywordColoringEnabled
        stringCheck.isSelected = s.stringColoringEnabled
        indentCheck.isSelected = s.indentGuideEnabled
        scopeOpacityField.text = s.scopeFillOpacity.toString()
        colorCountField.text = s.colorCount.toString()
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun restartHighlighting() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) {
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }
}
