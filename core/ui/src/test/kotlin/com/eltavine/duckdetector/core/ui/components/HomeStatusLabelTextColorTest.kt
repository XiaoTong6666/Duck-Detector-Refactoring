package com.eltavine.duckdetector.core.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusLabelTextColorTest {
    @Test
    fun `MIUIX status text is opaque white regardless of theme`() {
        assertEquals(Color.White, homeStatusLabelTextColor())
    }
}
