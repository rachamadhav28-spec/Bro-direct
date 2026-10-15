package com.bro.assistant.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.bro.assistant.memory.PreferencesStore

/** The colours and motion the owner picked. Compose reads these, so changing them updates every screen at once. */
object ThemeState {
    var primary by mutableStateOf(Color(0xFF00E5FF))
    var secondary by mutableStateOf(Color(0xFF9D6BFF))
    var hud by mutableStateOf(true)
    var layout by mutableStateOf("auto")
    /** Whole-app zoom: 0.5 to 1.5. Below 1 more fits on screen, above 1 everything is bigger. */
    var zoom by mutableStateOf(1f)
    private var store: PreferencesStore? = null
    fun zoomBy(step: Float) {
        zoom = (Math.round((zoom + step).coerceIn(0.5f, 1.5f) * 10f) / 10f)
        store?.uiZoom = zoom
    }
    fun zoomReset() { zoom = 1f; store?.uiZoom = 1f }

    class Preset(val name: String, val a: Long, val b: Long)
    val presets = listOf(
        Preset("Deep space", 0xFF00E5FF, 0xFF9D6BFF),
        Preset("Electric blue", 0xFF2979FF, 0xFF00B0FF),
        Preset("Magenta", 0xFFFF4DA6, 0xFFFFA726),
        Preset("Gold", 0xFFFFC107, 0xFFFF7043),
        Preset("Ice", 0xFF80DEEA, 0xFF7986CB),
        Preset("Rose", 0xFFFF80AB, 0xFFB388FF)
    )

    fun load(p: PreferencesStore) {
        primary = Color(p.accentPrimary.toInt()); secondary = Color(p.accentSecondary.toInt()); hud = p.hudAnimation; layout = p.layoutMode; store = p; zoom = p.uiZoom.coerceIn(0.5f, 1.5f)
    }

    fun set(p: PreferencesStore, a: Color, b: Color) {
        primary = a; secondary = b
        p.accentPrimary = a.toArgb().toLong() and 0xFFFFFFFFL
        p.accentSecondary = b.toArgb().toLong() and 0xFFFFFFFFL
    }

    /** "#RRGGBB" -> colour, or null when it is not a colour or too dark to read on the dark background. */
    fun parse(hex: String): Color? {
        val t = hex.trim().removePrefix("#")
        if (!Regex("^[0-9A-Fa-f]{6}$").matches(t)) return null
        val c = Color(0xFF000000.toInt() or t.toInt(16))
        val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        return if (lum < 0.12f) null else c
    }

    fun on(c: Color): Color = if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.45f) Color(0xFF04101A) else Color.White
}
