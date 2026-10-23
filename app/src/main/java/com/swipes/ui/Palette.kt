package com.swipes.ui

import androidx.compose.ui.graphics.Color

/** Categorical colors (dataviz reference palette): fixed slot order, separate steps for dark surfaces. */
object Palette {
    private val LIGHT = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948).map(::Color)
    private val DARK = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767).map(::Color)
    const val SLOTS = 8

    fun categorical(slot: Int, dark: Boolean) = (if (dark) DARK else LIGHT)[slot]
}
