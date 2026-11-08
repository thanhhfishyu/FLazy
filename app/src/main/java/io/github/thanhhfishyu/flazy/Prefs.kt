package io.github.thanhhfishyu.flazy

import android.content.Context

/** How FLazy moves to the next page in the reading app. */
enum class TurnMode(val label: String) {
    TAP_RIGHT("Chạm cạnh phải"),
    SWIPE_LEFT("Vuốt từ phải sang trái"),
    TAP_LEFT("Chạm cạnh trái"),
    SWIPE_RIGHT("Vuốt từ trái sang phải"),
    SCROLL_UP("Cuộn dọc (app cuộn trang lên xuống)");
}

/** User settings, stored only on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("flazy", Context.MODE_PRIVATE)

    var bookTitle: String
        get() = sp.getString("title", "") ?: ""
        set(v) = sp.edit().putString("title", v).apply()

    var turnMode: TurnMode
        get() = runCatching { TurnMode.valueOf(sp.getString("mode", null) ?: "") }
            .getOrDefault(TurnMode.TAP_RIGHT)
        set(v) = sp.edit().putString("mode", v.name).apply()

    /** Wait after turning a page before the next screenshot, in milliseconds. */
    var delayMs: Long
        get() = sp.getLong("delay", 1500L)
        set(v) = sp.edit().putLong("delay", v.coerceIn(MIN_DELAY_MS, 30_000L)).apply()

    var maxPages: Int
        get() = sp.getInt("max", 500)
        set(v) = sp.edit().putInt("max", v.coerceIn(1, 10_000)).apply()

    /** Percent of the screen height ignored at the top (status bar, app header). */
    var cropTopPct: Int
        get() = sp.getInt("cropTop", 8)
        set(v) = sp.edit().putInt("cropTop", v.coerceIn(0, 45)).apply()

    /** Percent of the screen height ignored at the bottom (page number, menus). */
    var cropBottomPct: Int
        get() = sp.getInt("cropBottom", 8)
        set(v) = sp.edit().putInt("cropBottom", v.coerceIn(0, 45)).apply()

    companion object {
        // Android refuses screenshots taken closer together than ~1/3 s.
        const val MIN_DELAY_MS = 500L
    }
}
