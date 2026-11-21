package com.focusguard.app

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Small synchronous settings store (language, theme mode, palette). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val language = MutableStateFlow(sp.getString("lang", "fa") ?: "fa")      // fa | en | system
    val mode = MutableStateFlow(sp.getString("mode", "system") ?: "system")  // system | light | dark
    val palette = MutableStateFlow(sp.getString("palette", "LAVENDER") ?: "LAVENDER")

    fun localeList(): LocaleListCompat =
        if (language.value == "system") LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(language.value)

    fun setLanguage(v: String) {
        sp.edit().putString("lang", v).apply(); language.value = v
        AppCompatDelegate.setApplicationLocales(localeList())
    }
    fun setMode(v: String) { sp.edit().putString("mode", v).apply(); mode.value = v }
    fun setPalette(v: String) { sp.edit().putString("palette", v).apply(); palette.value = v }
}

/** Context whose resources follow the in-app language (used by services / notifications). */
fun Context.localized(): Context {
    val lang = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "fa") ?: "fa"
    if (lang == "system") return this
    val cfg = Configuration(resources.configuration)
    cfg.setLocale(Locale.forLanguageTag(lang))
    return createConfigurationContext(cfg)
}
