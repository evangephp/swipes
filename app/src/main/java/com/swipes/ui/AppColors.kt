package com.swipes.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.swipes.data.AppDatabase

/**
 * Stable app → color mapping for replay and widgets. The eight [Palette] slots go to the most-used apps, in rank order, once; they don't move as
 * usage changes until the user resets them. System UI and the launcher share a neutral "System"
 * gray; everything else is "Other".
 */
object AppColors {
    private val SYSTEM = Color(0xFF8E8E93) to Color(0xFF8E8E93)
    private val OTHER = Color(0xFFC7C7CC) to Color(0xFF55555A)
    const val SLOTS = Palette.SLOTS
    private const val PREFS = "app_colors"

    @Volatile private var mapping: Map<String, Int>? = null
    @Volatile private var systemPackages: Set<String> = setOf("com.android.systemui")

    sealed interface Category {
        data class App(val slot: Int) : Category
        data object System : Category
        data object Other : Category
    }

    /** Pure assignment: keeps existing slots and gives free ones to unassigned apps in rank order. */
    fun assign(existing: Map<String, Int>, ranked: List<String>, system: Set<String>): Map<String, Int> {
        val result = existing.toMutableMap()
        val free = ArrayDeque((0 until SLOTS).filter { it !in existing.values })
        for (pkg in ranked) {
            if (free.isEmpty()) break
            if (pkg in system || pkg in result) continue
            result[pkg] = free.removeFirst()
        }
        return result
    }

    /** Loads the mapping and fills free slots from all-time usage. Blocking; call off the main thread. */
    fun ensureAssigned(context: Context) {
        val app = context.applicationContext
        systemPackages = setOf("com.android.systemui") + launchers(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
        val ranked = AppDatabase.get(app).captureDao().topAppsAllTime(SLOTS * 3).map { it.pkg }
        val updated = assign(existing, ranked, systemPackages)
        if (updated != existing) prefs.edit().apply { updated.forEach { (k, v) -> putInt(k, v) } }.apply()
        mapping = updated
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        mapping = null
        ensureAssigned(context)
    }

    fun isSystem(pkg: String?) = pkg != null && pkg in systemPackages

    fun category(pkg: String?): Category = when {
        pkg == null -> Category.Other
        pkg in systemPackages -> Category.System
        else -> mapping?.get(pkg)?.let { Category.App(it) } ?: Category.Other
    }

    fun color(category: Category, dark: Boolean): Color = when (category) {
        is Category.App -> Palette.categorical(category.slot, dark)
        Category.System -> if (dark) SYSTEM.second else SYSTEM.first
        Category.Other -> if (dark) OTHER.second else OTHER.first
    }

    fun color(pkg: String?, dark: Boolean) = color(category(pkg), dark)

    private fun launchers(context: Context): Set<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }
            .toSet()
}
