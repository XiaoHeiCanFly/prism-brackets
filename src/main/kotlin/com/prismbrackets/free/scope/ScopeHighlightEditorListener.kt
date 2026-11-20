package com.prismbrackets.free.scope

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.prismbrackets.free.colors.PrismColors
import com.prismbrackets.free.core.BracketScanService
import com.prismbrackets.free.core.ScopeResolver
import com.prismbrackets.free.settings.PrismSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * 作用域高亮（需求 2：独立开关，默认关闭）：
 * - 每个编辑器挂 CaretListener，光标移动时计算光标所在的最内层配对括号；
 * - 开启时创建带 ScopeHighlightRenderer 的 RangeHighlighter（边框=括号色，填充=同色减弱）；
 * - 关闭或光标不在任何配对内时移除高亮；
 * - 开关切换后通过 [refreshAll] 立即作用到所有已打开编辑器。
 */
class ScopeHighlightEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.project == null) return
        val controller = ScopeController(editor)
        editor.putUserData(CONTROLLER_KEY, controller)
        CONTROLLERS.add(controller)
        editor.caretModel.addCaretListener(controller)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val controller = event.editor.getUserData(CONTROLLER_KEY) ?: return
        CONTROLLERS.remove(controller)
        event.editor.caretModel.removeCaretListener(controller)
        controller.clear()
        event.editor.putUserData(CONTROLLER_KEY, null)
    }

    /** 设置/开关变化后调用：重算所有打开编辑器的作用域高亮（EDT）。 */
    fun refreshAll() = refreshAllControllers()

    private class ScopeController(private val editor: Editor) : CaretListener {

        private var highlighter: RangeHighlighter? = null

        override fun caretPositionChanged(event: CaretEvent) {
            if (event.caret != event.editor.caretModel.primaryCaret) return
            refresh()
        }

        fun refresh() {
            val settings = PrismSettings.getInstance()
            if (!settings.scopeHighlightingEnabled) {
                clear()
                return
            }
            val project = editor.project
            if (project == null || editor.isDisposed) {
                clear()
                return
            }
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (psiFile == null) {
                clear()
                return
            }

            val scan = BracketScanService.scan(psiFile)
            val palette = paletteOf(editor)
            val scope = ScopeResolver.resolve(
                caretOffset = editor.caretModel.offset,
                brackets = scan.tokens,
                palette = palette,
                fillOpacity = settings.scopeFillOpacity,
            )
            if (scope == null) {
                clear()
                return
            }

            val start = scope.openOffset
            val end = (scope.closeOffset + 1).coerceAtMost(editor.document.textLength)
            val existing = highlighter
            if (existing != null && existing.isValid &&
                existing.startOffset == start && existing.endOffset == end
            ) {
                // 范围没变但颜色可能变（colorCount/配色调整）：重建 renderer
                existing.setCustomRenderer(ScopeHighlightRenderer(scope.borderColor, scope.fillColor))
                return
            }
            clear()

            val renderer = ScopeHighlightRenderer(scope.borderColor, scope.fillColor)
            highlighter = editor.markupModel.addRangeHighlighter(
                start,
                end,
                HighlighterLayer.SELECTION - 1,
                null,
                HighlighterTargetArea.EXACT_RANGE,
            ).also { it.setCustomRenderer(renderer) }
        }

        fun clear() {
            highlighter?.let {
                if (it.isValid) {
                    it.dispose()
                }
            }
            highlighter = null
        }

        /** 从配色方案取括号调色板（用户可自定义的颜色优先，回退默认）。 */
        private fun paletteOf(editor: Editor): List<java.awt.Color> {
            val scheme = editor.colorsScheme
            return PrismColors.BRACKET_KEYS.mapIndexed { i, key ->
                scheme.getAttributes(key).foregroundColor ?: PrismColors.BRACKET_DEFAULTS[i]
            }
        }
    }

    companion object {
        private val CONTROLLER_KEY: Key<ScopeController> = Key.create("rainbow.free.scope.controller")
        private val CONTROLLERS: MutableSet<ScopeController> = ConcurrentHashMap.newKeySet()

        private fun refreshAllControllers() {
            for (controller in CONTROLLERS) {
                controller.refresh()
            }
        }

        /** 静态入口：供 Action/Configurable 触发全量刷新（自动切到 EDT）。 */
        fun refreshAllEditors() {
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) {
                refreshAllControllers()
            } else {
                app.invokeLater { refreshAllControllers() }
            }
        }
    }
}
