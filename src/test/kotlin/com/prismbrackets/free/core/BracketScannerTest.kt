package com.prismbrackets.free.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketScannerTest {

    private fun scan(text: String): List<BracketToken> {
        val candidates = ArrayList<CandidateBracket>()
        for (i in text.indices) {
            val c = text[i]
            if (BracketScanner.isBracket(c)) {
                candidates.add(CandidateBracket(i, 1, c))
            }
        }
        return BracketScanner.scan(candidates)
    }

    @Test
    fun `nested parens get matching depths`() {
        val tokens = scan("a(b(c)d)e")
        val byOffset = tokens.associateBy { it.offset }
        assertEquals(0, byOffset[1]!!.depth)
        assertEquals(1, byOffset[3]!!.depth)
        assertEquals(1, byOffset[5]!!.depth)
        assertEquals(0, byOffset[7]!!.depth)
        // 配对关系
        assertEquals(7, byOffset[1]!!.pairOffset)
        assertEquals(5, byOffset[3]!!.pairOffset)
        assertTrue(byOffset[1]!!.isOpen)
        assertTrue(!byOffset[7]!!.isOpen)
    }

    @Test
    fun `mixed bracket types pair correctly`() {
        val tokens = scan("{[()]}")
        val byOffset = tokens.associateBy { it.offset }
        assertEquals(6, tokens.size)
        assertEquals(5, byOffset[0]!!.pairOffset) // { → }
        assertEquals(4, byOffset[1]!!.pairOffset) // [ → ]
        assertEquals(3, byOffset[2]!!.pairOffset) // ( → )
        assertEquals(2, byOffset[3]!!.pairOffset) // ) → (
        tokens.forEach { assertTrue("offset ${it.offset} depth>=0", it.depth >= 0) }
    }

    @Test
    fun `unmatched brackets keep depth but pairOffset is -1`() {
        val tokens = scan("(a")
        val open = tokens.first { it.isOpen }
        assertEquals(-1, open.pairOffset)
        assertEquals(0, open.depth)
    }

    @Test
    fun `mismatched close searches down the stack`() {
        // ( [ )  —— ')' 应与 '(' 配对，跳过未闭合的 '['
        val tokens = scan("( [ )")
        val close = tokens.first { !it.isOpen && it.char == ')' }
        val open = tokens.first { it.char == '(' }
        assertEquals(open.offset, close.pairOffset)
        val bracket = tokens.first { it.char == '[' }
        assertEquals(-1, bracket.pairOffset)
    }

    @Test
    fun `angle brackets pair like generics`() {
        val tokens = scan("List<String>")
        val byOffset = tokens.associateBy { it.offset }
        assertEquals(0, byOffset[4]!!.depth)
        assertEquals(4, byOffset[11]!!.pairOffset) // '<'@4 ↔ '>'@11
    }

    @Test
    fun `scan is sorted by offset`() {
        val tokens = scan("x(f(y))z")
        for (i in 1 until tokens.size) {
            assertTrue(tokens[i - 1].offset < tokens[i].offset)
        }
    }

    @Test
    fun `tag names extracted after paired angle brackets`() {
        val text = "<div class=\"a\">x</div>"
        val (tags, brackets) = BracketScanService.scanMarkupTags(text)
        assertEquals(2, tags.size)
        // <div：名字从 offset 1 开始，层级 0
        assertEquals(1, tags[0].offset)
        assertEquals("div", text.substring(tags[0].offset, tags[0].offset + tags[0].length))
        assertEquals(0, tags[0].depth)
        // </div：层级也回到 0（闭合标签与开标签同层）
        val close = tags[1]
        assertEquals("div", text.substring(close.offset, close.offset + close.length))
        assertEquals(text.lastIndexOf("div"), close.offset)
        assertEquals(0, close.depth)
        // 每个标签：<、（闭合的 /）、> —— 开标签 2 个 + 闭标签 3 个 = 5
        assertEquals(5, brackets.size)
    }

    @Test
    fun `nested tags get increasing levels`() {
        val text = "<div><span>x</span></div>"
        val (tags, _) = BracketScanService.scanMarkupTags(text)
        assertEquals(4, tags.size)
        // div=0 span=1 /span=1 /div=0（开闭同层）
        assertEquals(0, tags.first { it.openOffset == text.indexOf("<div") }.depth)
        assertEquals(1, tags.first { it.openOffset == text.indexOf("<span") }.depth)
        assertEquals(1, tags.first { it.openOffset == text.indexOf("</span") }.depth)
        assertEquals(0, tags.first { it.openOffset == text.indexOf("</div") }.depth)
    }

    @Test
    fun `self closing and void tags do not increase nesting`() {
        val text = "<div><br><img src=\"x\"/><span>y</span></div>"
        val (tags, _) = BracketScanService.scanMarkupTags(text)
        assertEquals(0, tags.first { text.startsWith("<div", it.openOffset) }.depth)
        assertEquals(1, tags.first { text.startsWith("<br", it.openOffset) }.depth)
        assertEquals(1, tags.first { text.startsWith("<img", it.openOffset) }.depth)
        // span 仍在第 1 层（br/img 不增加嵌套）
        assertEquals(1, tags.first { text.startsWith("<span", it.openOffset) }.depth)
        assertEquals(0, tags.first { text.startsWith("</div", it.openOffset) }.depth)
    }

    @Test
    fun `same tag open and close share identical bracket levels`() {
        val text = "<el-form-item label=\"x\"><my-info/></el-form-item>"
        val (tags, brackets) = BracketScanService.scanMarkupTags(text)
        val openLv = tags.first { it.openOffset == 0 }.depth
        val closeTag = tags.last()
        assertEquals(openLv, closeTag.depth)
        // 开标签的 < 与 > 同层
        val lt = brackets.first { it.offset == 0 }
        val gt = brackets.first { it.offset == text.indexOf(">") }
        assertEquals(lt.level, gt.level)
        // 闭标签 </ 的 <、/、> 同层
        val closeStart = text.indexOf("</")
        val cLt = brackets.first { it.offset == closeStart }
        val cSlash = brackets.first { it.offset == closeStart + 1 }
        val cGt = brackets.first { it.offset == text.indexOf(">", closeStart) }
        assertEquals(cLt.level, cSlash.level)
        assertEquals(cLt.level, cGt.level)
        assertEquals(closeTag.depth, cLt.level)
    }

    @Test
    fun `script block contents are not scanned as tags`() {
        val text = "<script>const a: Array<string> = [];</script><div>x</div>"
        val (tags, _) = BracketScanService.scanMarkupTags(text)
        // script 开 + div 开 + div 闭；script 内的 Array<string> 不算标签
        assertEquals(3, tags.size)
        assertTrue(tags.none { it.openOffset in (8 until 37) })
        // script 闭合被跳过但层级已回退：div 仍在第 0 层
        val divOpen = tags.first { text.startsWith("<div", it.openOffset) }
        val divClose = tags.last()
        assertEquals(0, divOpen.depth)
        assertEquals(0, divClose.depth)
    }

    @Test
    fun `angle open accepts tags with attributes and rejects comparisons`() {
        // 带属性标签必须通过（属性值里的 () 在引号内）
        assertTrue(BracketScanService.isValidAngleOpen("<div class=\"a\">x</div>", 0))
        assertTrue(BracketScanService.isValidAngleOpen("<a @click=\"fn(x)\">t</a>", 0))
        assertTrue(BracketScanService.isValidAngleOpen("<Comp :prop=\"a > b\">", 0))
        // 比较/语句必须拒绝
        val cmp1 = "if (a < b) {"
        assertTrue(!BracketScanService.isValidAngleOpen(cmp1, cmp1.indexOf('<')))
        val cmp2 = "a < b && c > d"
        assertTrue(!BracketScanService.isValidAngleOpen(cmp2, cmp2.indexOf('<')))
        // 泛型仍通过
        val gen = "List<String> x"
        assertTrue(BracketScanService.isValidAngleOpen(gen, gen.indexOf('<')))
    }

    @Test
    fun `generic type argument is not a tag name in non markup context`() {
        // scanMarkupTags 只在 markup 文件被调用；这里验证其对纯代码文本的行为边界
        val text = "List<String>"
        val (tags, _) = BracketScanService.scanMarkupTags(text)
        // 泛型文本恰好也会被提取（调用方 looksMarkup 负责不在 Java 等文件调用）
        assertEquals(1, tags.size)
        assertEquals("String", text.substring(tags[0].offset, tags[0].offset + tags[0].length))
    }
}
