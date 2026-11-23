package org.ivanivorontsov.stroka.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.ivanivorontsov.stroka.core.ExtraKeys

class Prefs(ctx: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)

    val theme: String get() = sp.getString("theme", "system") ?: "system"
    var fontSize: Float
        get() = sp.getInt("font_size", 15).toFloat()
        set(v) = sp.edit().putInt("font_size", v.toInt().coerceIn(8, 36)).apply()
    val lineNumbers: Boolean get() = sp.getBoolean("line_numbers", true)
    var wrap: Boolean
        get() = sp.getBoolean("wrap", true)
        set(v) = sp.edit().putBoolean("wrap", v).apply()
    val highRefresh: Boolean get() = sp.getBoolean("high_refresh", true)
    val indentUnit: String get() = when (sp.getString("indent", "4")) { "2" -> "  "; "tab" -> "\t"; else -> "    " }
    val autoPair: Boolean get() = sp.getBoolean("auto_pair", true)
    val autoIndent: Boolean get() = sp.getBoolean("auto_indent", true)
    val autocomplete: Boolean get() = sp.getBoolean("autocomplete", true)
    val autosave: Boolean get() = sp.getBoolean("autosave", true)
    val extraKeys: String get() = sp.getString("extra_keys", null)?.takeIf { it.isNotBlank() } ?: ExtraKeys.DEFAULT
    val haptics: Boolean get() = sp.getBoolean("haptics", true)
    val voiceEngine: String get() = sp.getString("voice_engine", "auto") ?: "auto"
    var voiceMode: String
        get() = sp.getString("voice_mode", "blocks") ?: "blocks"
        set(v) = sp.edit().putString("voice_mode", v).apply()
    var voiceLang: String
        get() = sp.getString("voice_lang", "ru") ?: "ru"
        set(v) = sp.edit().putString("voice_lang", v).apply()
    var aiBase: String
        get() = sp.getString("ai_base", "") ?: ""
        set(v) = sp.edit().putString("ai_base", v).apply()
    var aiModel: String
        get() = sp.getString("ai_model", "deepseek-chat") ?: "deepseek-chat"
        set(v) = sp.edit().putString("ai_model", v).apply()
    val aiAutofix: Boolean get() = sp.getBoolean("ai_autofix", true)
    val aiInterfaceAssist: Boolean get() = sp.getBoolean("ai_interface_assist", true)
    val gitUser: String get() = sp.getString("git_user", "") ?: ""
    val gitName: String get() = sp.getString("git_name", "") ?: ""
    val gitEmail: String get() = sp.getString("git_email", "") ?: ""
    var panelHeight: Int
        get() = sp.getInt("panel_height", 0)
        set(v) = sp.edit().putInt("panel_height", v).apply()

    var safRoots: List<String>
        get() = (sp.getString("saf_roots", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("saf_roots", v.joinToString("\n")).apply()

    var openDocs: List<String>
        get() = (sp.getString("open_docs", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("open_docs", v.joinToString("\n")).apply()

    var activeDoc: Int
        get() = sp.getInt("active_doc", 0)
        set(v) = sp.edit().putInt("active_doc", v).apply()

    var buildOutput: String
        get() = sp.getString("build_output_uri", "") ?: ""
        set(v) = sp.edit().putString("build_output_uri", v).apply()

    var buildVersionName: String
        get() = sp.getString("build_version_name", "1.0.0") ?: "1.0.0"
        set(v) = sp.edit().putString("build_version_name", v).apply()

    var buildVersionCode: Int
        get() = sp.getInt("build_version_code", 1)
        set(v) = sp.edit().putInt("build_version_code", v.coerceAtLeast(1)).apply()

    var buildIconUri: String
        get() = sp.getString("build_icon_uri", "") ?: ""
        set(v) = sp.edit().putString("build_icon_uri", v).apply()

    var buildIconEmoji: String
        get() = sp.getString("build_icon_emoji", "") ?: ""
        set(v) = sp.edit().putString("build_icon_emoji", v).apply()

    var expanded: Set<String>
        get() = sp.getStringSet("tree_expanded", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("tree_expanded", HashSet(v)).apply()

    fun applyTheme() {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
