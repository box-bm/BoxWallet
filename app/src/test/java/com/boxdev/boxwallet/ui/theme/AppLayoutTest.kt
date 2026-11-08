package com.boxdev.boxwallet.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLayoutTest {
    @Test
    fun `portrait phone uses bar with compact margins`() {
        assertEquals(
            AppLayout(WidthClass.Compact, NavKind.Bar, railExpanded = false, twoPane = false, margin = 16.dp, compactHeight = false),
            appLayout(411, 891),
        )
    }

    @Test
    fun `small phone landscape is medium with rail and compact height`() {
        assertEquals(
            AppLayout(WidthClass.Medium, NavKind.Rail, railExpanded = false, twoPane = false, margin = 24.dp, compactHeight = true),
            appLayout(780, 360),
        )
    }

    @Test
    fun `phone landscape is expanded two pane with collapsed rail`() {
        assertEquals(
            AppLayout(WidthClass.Expanded, NavKind.Rail, railExpanded = false, twoPane = true, margin = 24.dp, compactHeight = true),
            appLayout(891, 411),
        )
    }

    @Test
    fun `foldable portrait is medium single pane with rail`() {
        val layout = appLayout(673, 841)
        assertEquals(WidthClass.Medium, layout.widthClass)
        assertEquals(NavKind.Rail, layout.nav)
        assertFalse(layout.twoPane)
    }

    @Test
    fun `desktop-size window expands the rail`() {
        val layout = appLayout(1280, 800)
        assertEquals(WidthClass.Expanded, layout.widthClass)
        assertEquals(NavKind.Rail, layout.nav)
        assertTrue(layout.railExpanded)
        assertTrue(layout.twoPane)
    }

    @Test
    fun `width breakpoints`() {
        assertEquals(WidthClass.Compact, appLayout(599, 800).widthClass)
        assertEquals(WidthClass.Medium, appLayout(600, 800).widthClass)
        assertEquals(WidthClass.Medium, appLayout(839, 800).widthClass)
        assertEquals(WidthClass.Expanded, appLayout(840, 800).widthClass)
        assertFalse(appLayout(1199, 800).railExpanded)
        assertTrue(appLayout(1200, 800).railExpanded)
    }

    @Test
    fun `height breakpoint`() {
        assertTrue(appLayout(411, 479).compactHeight)
        assertFalse(appLayout(411, 480).compactHeight)
    }
}
