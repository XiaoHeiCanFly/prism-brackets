package com.prismbrackets.free.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Color

class ScopeResolverTest {

    private val palette = listOf(Color.RED, Color.GREEN, Color.BLUE)

    private fun bracketsOf(text: String): List<BracketToken> {
        val candidates = ArrayList<CandidateBracket>()
        for (i in text.indices) {
            if (BracketScanner.isBracket(text[i])) {
                candidates.add(CandidateBracket(i, 1, text[i]))
            }
        }
        return BracketScanner.scan(candidates)
    }

    @Test
    fun `caret inside innermost pair resolves to it`() {
        val text = "a(b(c)d)e"
        val brackets = bracketsOf(text)
        // 光标在 'c' 上（offset 4）
        val scope = ScopeResolver.resolve(4, brackets, palette, fillOpacity = 20)
        assertEquals(3, scope!!.openOffset)  // 内层 (
        assertEquals(5, scope.closeOffset)   // 内层 )
        assertEquals(1, scope.depth)
    }

    @Test
    fun `caret on outer pair picks outer when not in inner`() {
        val text = "a(b(c)d)e"
        val brackets = bracketsOf(text)
        // 光标在 'd' 上（offset 6）——在内层 ) 之后、外层 ) 之前
        val scope = ScopeResolver.resolve(6, brackets, palette, fillOpacity = 20)
        assertEquals(1, scope!!.openOffset)
        assertEquals(7, scope.closeOffset)
        assertEquals(0, scope.depth)
    }

    @Test
    fun `caret outside any pair returns null`() {
        val text = "a(b(c)d)e"
        val brackets = bracketsOf(text)
        assertNull(ScopeResolver.resolve(0, brackets, palette, 20))
        assertNull(ScopeResolver.resolve(8, brackets, palette, 20))
    }

    @Test
    fun `border color follows depth palette and fill is weakened`() {
        val text = "(a)"
        val brackets = bracketsOf(text)
        val scope = ScopeResolver.resolve(1, brackets, palette, fillOpacity = 25)!!
        assertEquals(palette[0], scope.borderColor)
        // 填充色 = 同色 25% alpha
        assertEquals(25 * 255 / 100, scope.fillColor.alpha)
        assertEquals(palette[0].red, scope.fillColor.red)
    }
}
