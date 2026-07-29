package luzzr.muse.ui.screens.audiobook

import luzzr.muse.domain.model.ReadAlongFontFamily
import luzzr.muse.domain.model.ReadAlongTextIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAlongChapterScriptContractTest {
    @Test
    fun `follow controller suspends and resumes without losing request ordering`() {
        val suspended = ReadAlongFollowState()
            .onEvent(ReadAlongFollowEvent.UserInteraction)
        val resumed = suspended.onEvent(ReadAlongFollowEvent.Resume)
        val jumped = resumed.onEvent(ReadAlongFollowEvent.ExplicitJump)

        assertTrue(suspended.suspended)
        assertFalse(resumed.suspended)
        assertEquals(1, resumed.resumeRequest)
        assertEquals(2, jumped.resumeRequest)
    }

    @Test
    fun `paged restore uses body viewport and waits for layout`() {
        val script = buildRestoreScrollScript(progress = 0.5f, paged = true)

        assertTrue(script.contains("const scroller = isPaged ? (document.getElementById('muse-viewport')"))
        assertTrue(script.contains("scroller.clientWidth || window.innerWidth"))
        assertTrue(script.contains("scroller.clientHeight || window.innerHeight"))
        assertTrue(script.contains("scroller.scrollWidth - viewport"))
        assertTrue(script.contains("const raw = max * 0.5"))
        assertTrue(script.contains("scroller.scrollLeft = Math.min(max, Math.max(0, page * viewport))"))
        assertTrue(script.contains("requestAnimationFrame(() => requestAnimationFrame"))
        assertFalse(script.contains("const viewport = isPaged ? window.innerWidth"))
        assertFalse(script.contains("scroller.scrollLeft = max * 0.5"))
    }

    @Test
    fun `setup script keeps viewport and mature reader tap zones`() {
        val script = buildSetupScript(
            settings = ReadAlongSettingsState(),
            annotations = emptyList(),
            jumpMode = false
        )

        assertTrue(script.contains("muse-viewport"))
        assertTrue(script.contains("window.__musePaged = paged"))
        assertTrue(script.contains("scrollBy({ left: -width"))
        assertTrue(script.contains("scrollBy({ left: width"))
        assertTrue(script.contains("window.MuseReader.onReaderTap()"))
        assertTrue(script.contains("pager.style.height = `${'$'}{pageHeight}px`"))
        assertTrue(script.contains("viewport.clientWidth || window.innerWidth"))
        assertTrue(script.contains("const pagedWidth = ()"))
        assertTrue(script.contains("::highlight(muse-sentence-active)"))
        assertTrue(script.contains("muse-highlight-overlay"))
        assertTrue(script.contains("window.__museInvalidateHighlightRanges"))
        assertTrue(script.contains("event.preventDefault()"))
        assertTrue(script.contains("scroller.scrollTo({ left: targetPage * width"))
        assertTrue(script.contains("window.__museSuppressNextTap"))
        assertFalse(script.contains("MuseReaderDiagnostics"))
    }

    @Test
    fun `book font mode preserves embedded EPUB font rules`() {
        val bookScript = buildSetupScript(
            settings = ReadAlongSettingsState(fontFamily = ReadAlongFontFamily.BOOK),
            annotations = emptyList(),
            jumpMode = false
        )
        val serifScript = buildSetupScript(
            settings = ReadAlongSettingsState(fontFamily = ReadAlongFontFamily.SERIF),
            annotations = emptyList(),
            jumpMode = false
        )

        assertFalse(bookScript.contains("font-family:"))
        assertTrue(serifScript.contains("font-family: Georgia"))
    }

    @Test
    fun `highlight index renders active ranges without changing EPUB DOM`() {
        val index = ReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = "甲乙",
            elementIdByChar = null,
            sentenceStartByChar = null,
            unitStartByChar = null,
            sentenceRanges = listOf(0..1),
            unitRanges = listOf(0..0, 1..1)
        )

        val script = buildHighlightIndexScript(index)

        assertTrue(script.contains("window.__museHighlightIndex"))
        assertTrue(script.contains("CSS.highlights.set('muse-sentence-active'"))
        assertTrue(script.contains("new Highlight(activeUnitRange)"))
        assertTrue(script.contains("range.getClientRects()"))
        assertTrue(script.contains("const followRange"))
        assertTrue(script.contains("document.getElementById('muse-viewport')"))
        assertTrue(script.contains("scroller.clientWidth || window.innerWidth"))
        assertTrue(script.contains("scroller.scrollLeft = target"))
        assertTrue(script.contains("const focusTop = window.innerHeight * 0.28"))
        assertTrue(script.contains("const focusBottom = window.innerHeight * 0.66"))
        assertTrue(script.contains("window.innerHeight * 0.42"))
        assertTrue(script.contains("!window.__museFollowSuspended"))
        assertTrue(script.contains("window.__museResumeFollow"))
        assertTrue(script.contains("window.__museReduceMotion ? 'auto' : 'smooth'"))
        assertFalse(script.contains("scroller.scrollTo({ left: target, behavior: 'smooth' })"))
        assertFalse(script.contains("surroundContents"))
        assertFalse(script.contains("extractContents"))
        assertFalse(script.contains("data-muse-transient"))
        assertFalse(script.contains("wrapRange"))
        assertFalse(script.contains("scrollIntoView"))
        assertFalse(script.contains("order.forEach"))
        assertFalse(script.contains("__museUnitSpans"))
        assertFalse(script.contains("span[data-muse-unit]"))
    }

    @Test
    fun `setup script suspends follow only for reader navigation gestures`() {
        val script = buildSetupScript(
            settings = ReadAlongSettingsState(autoFollow = true),
            annotations = emptyList(),
            jumpMode = false,
            reduceMotion = true
        )

        assertTrue(script.contains("window.__museAutoFollowEnabled = true"))
        assertTrue(script.contains("window.__museReduceMotion = true"))
        assertTrue(script.contains("window.MuseReader.onFollowSuspended()"))
        assertTrue(script.contains("if (travel > 12) window.__museSuspendAutoFollow()"))
        assertTrue(script.contains("document.addEventListener('wheel'"))
        assertTrue(script.contains("behavior: 'auto'"))
    }

    @Test
    fun `resume script clears suspension without rebuilding document`() {
        val script = buildResumeFollowScript()

        assertTrue(script.contains("window.__museFollowSuspended = false"))
        assertTrue(script.contains("window.__museResumeFollow"))
        assertFalse(script.contains("createElement"))
        assertFalse(script.contains("innerHTML"))
    }

    @Test
    fun `precomputed highlight requests latest indexed unit without DOM traversal`() {
        val index = ReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = "甲乙",
            elementIdByChar = null,
            sentenceStartByChar = null,
            unitStartByChar = null,
            sentenceRanges = listOf(0..1),
            unitRanges = listOf(0..0, 1..1)
        )

        val script = buildPrecomputedHighlightScript(index, activeUnitIndex = 1, activeSentenceIndex = 0, autoFollow = true)

        assertTrue(script.contains("__museApplyHighlight"))
        assertTrue(script.contains("unit: 1"))
        assertTrue(script.contains("sentence: 0"))
        assertFalse(script.contains("createTreeWalker"))
    }

    @Test
    fun `text range script scrolls nearest indexed content`() {
        val script = buildScrollToTextRangeScript(start = -4, end = 12)

        assertTrue(script.contains("Math.min(0, joined.length)"))
        assertTrue(script.contains("scrollIntoView"))
        assertTrue(script.contains("inline: 'nearest'"))
    }
}
