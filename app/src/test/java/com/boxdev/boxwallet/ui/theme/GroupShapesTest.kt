package com.boxdev.boxwallet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupShapesTest {
    @Test
    fun loneRowIsRoundOnAllCorners() =
        assertEquals(RoundedCornerShape(28.dp), GroupShapes.item(0, 1))

    @Test
    fun firstRowHasTightBottomCorners() =
        assertEquals(RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp), GroupShapes.item(0, 3))

    @Test
    fun middleRowIsTight() =
        assertEquals(RoundedCornerShape(4.dp), GroupShapes.item(1, 3))

    @Test
    fun lastRowHasTightTopCorners() =
        assertEquals(RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp), GroupShapes.item(2, 3))
}
