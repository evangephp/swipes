package com.swipes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorsTest {
    private val system = setOf("com.android.systemui", "launcher")

    @Test
    fun `assigns free slots in rank order skipping system apps`() {
        val m = AppColors.assign(emptyMap(), listOf("com.android.systemui", "reddit", "launcher", "wechat"), system)
        assertEquals(mapOf("reddit" to 0, "wechat" to 1), m)
    }

    @Test
    fun `existing slots never move`() {
        val first = AppColors.assign(emptyMap(), listOf("a", "b"), system)
        // Usage changes: b is now first and c appears.
        val second = AppColors.assign(first, listOf("b", "c", "a"), system)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), second)
    }

    @Test
    fun `apps beyond the slots stay unassigned`() {
        val ranked = (1..12).map { "app$it" }
        val m = AppColors.assign(emptyMap(), ranked, system)
        assertEquals(AppColors.SLOTS, m.size)
        assertEquals(null, m["app9"])
    }
}
