package com.wanderwildwood.iori

import com.wanderwildwood.iori.ui.IoriSettingsPages
import com.wanderwildwood.iori.ui.IoriSettingsPages.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IoriSettingsPagesTest {
    // Ten 100 px rows; a heading at 300..340 shifts the rows under it.
    private val rows = (0 until 10).map { Span(it * 100, it * 100 + 100) }
    private val page = 450

    @Test
    fun theRowTheFootCutsIsNotDrawnAndOpensTheNextPage() {
        assertEquals(400, IoriSettingsPages.foot(rows, 0, page))
        assertEquals(400, IoriSettingsPages.forward(rows, 0, page, 1000))
        assertEquals(400, IoriSettingsPages.foot(rows, 400, page))
        assertEquals(800, IoriSettingsPages.forward(rows, 400, page, 1000))
    }

    @Test
    fun aPageWhereEverythingFitsDrawsToTheFoot() {
        assertNull(IoriSettingsPages.foot(rows, 600, page))
        assertNull(IoriSettingsPages.forward(rows, 600, page, 1000))
    }

    @Test
    fun aHeadingIsNeverLastOnAPage() {
        val spans = listOf(Span(0, 100), Span(100, 200), Span(200, 240, heading = true), Span(240, 340), Span(340, 440), Span(440, 540))
        // The page holds up to 450: the row at 440 is cut; the one before is an ordinary row, so it stays.
        assertEquals(440, IoriSettingsPages.foot(spans, 0, 450))
        // At 300 the row at 240 is cut, and the heading at 200 would be left alone: it goes over too.
        assertEquals(200, IoriSettingsPages.foot(spans, 0, 300))
        assertEquals(200, IoriSettingsPages.forward(spans, 0, 300, 540))
    }

    @Test
    fun aTurnBackLandsOnARow() {
        assertEquals(400, IoriSettingsPages.back(rows, 800, page))
        assertEquals(0, IoriSettingsPages.back(rows, 400, page))
        assertNull(IoriSettingsPages.back(rows, 0, page))
        // 750 - 450 = 300: a row starts there.
        assertEquals(300, IoriSettingsPages.back(rows, 750, page))
        // 780 - 450 = 330: the next row edge is 400.
        assertEquals(400, IoriSettingsPages.back(rows, 780, page))
    }

    @Test
    fun theLastPageOpensOnARowThatLetsTheEndFit() {
        assertEquals(600, IoriSettingsPages.lastStart(rows, page, 1000))
        assertEquals(0, IoriSettingsPages.jump(rows, 0f, page, 1000))
        assertEquals(500, IoriSettingsPages.jump(rows, 0.55f, page, 1000))
        assertEquals(600, IoriSettingsPages.jump(rows, 1f, page, 1000))
    }

    @Test
    fun aPageEndsAtItsLastRowNotATrailingSpacer() {
        // The rows end at 1000; the content closes on a 16 px spacer.
        assertEquals(1000, IoriSettingsPages.end(rows, 1016))
        assertEquals(2000, IoriSettingsPages.end(emptyList(), 2000))
        // A page showing every row down to 1000 but not the spacer turns no further.
        assertNull(IoriSettingsPages.forward(rows, 600, 410, 1016))
        assertEquals(600, IoriSettingsPages.lastStart(rows, 410, 1016))
        assertEquals(600, IoriSettingsPages.jump(rows, 1f, 410, 1016))
        // Before it, a turn still lands on the row the foot cut.
        assertEquals(400, IoriSettingsPages.forward(rows, 0, page, 1016))
    }

    @Test
    fun contentWithNoRowsTurnsAPageAtATime() {
        assertNull(IoriSettingsPages.foot(emptyList(), 0, page))
        assertEquals(450, IoriSettingsPages.forward(emptyList(), 0, page, 2000))
    }

    @Test
    fun aRowTallerThanThePageIsReadAPageAtATime() {
        // A, then R taller than the 450 px page, then B; the content ends at 1200.
        val spans = listOf(Span(0, 100), Span(100, 1100), Span(1100, 1200))
        // R does not fit under A: the next page opens on R.
        assertEquals(100, IoriSettingsPages.forward(spans, 0, page, 1200))
        // On R's own page nothing whole follows it there: the next page is the next part of R,
        // not B (which skipped R's rest), and the one after that shows R's end with B.
        assertEquals(550, IoriSettingsPages.forward(spans, 100, page, 1200))
        assertEquals(1000, IoriSettingsPages.forward(spans, 550, page, 1200))
        assertNull(IoriSettingsPages.forward(spans, 1000, page, 1200))
        // Back from part-way down R lands on R's top.
        assertEquals(100, IoriSettingsPages.back(spans, 550, page))
    }

    @Test
    fun aTallRowUnderItsHeadingIsNotCutAtItsTop() {
        val spans = listOf(Span(0, 40, heading = true, key = "h"), Span(40, 1000), Span(1000, 1100))
        // Cut at its top, the page would hold the heading alone; it is drawn as far as it goes.
        assertNull(IoriSettingsPages.foot(spans, 0, page))
        assertEquals(450, IoriSettingsPages.forward(spans, 0, page, 1100))
        // A tall row with an ordinary row above it on the page is still cut at its top.
        val under = listOf(Span(0, 100), Span(100, 1000))
        assertEquals(100, IoriSettingsPages.foot(under, 0, page))
    }

    @Test
    fun aHeadingWithNothingUnderItFolds() {
        val a = "a"; val b = "b"; val c = "c"
        // a heads two rows; b heads nothing (c follows at once); c heads nothing (the end).
        val spans = listOf(
            Span(0, 40, heading = true, key = a), Span(40, 140), Span(140, 240),
            Span(240, 280, heading = true, key = b), Span(280, 320, heading = true, key = c),
        )
        assertEquals(setOf(b, c), IoriSettingsPages.emptyHeadings(spans))
        // Folded (no height), they stay folded: what follows them has not changed.
        val folded = listOf(
            Span(0, 40, heading = true, key = a), Span(40, 140), Span(140, 240),
            Span(240, 240, heading = true, key = b), Span(240, 240, heading = true, key = c),
        )
        assertEquals(setOf(b, c), IoriSettingsPages.emptyHeadings(folded))
        // A row comes back under the folded b: b opens again; c stays folded.
        val back = folded + Span(240, 340)
        assertEquals(setOf(c), IoriSettingsPages.emptyHeadings(back.map { if (it.key == c) Span(340, 340, true, c) else it }))
    }
}
