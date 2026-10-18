package org.ivanivorontsov.stroka.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.core.ExtraKeys
import org.ivanivorontsov.stroka.voice.VoskModels

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val bar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        bar.setNavigationOnClickListener { finish() }
        Insets.applyTopBar(bar)
        val host = findViewById<android.view.View>(R.id.settingsHost)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.updatePadding(left = i.left, right = i.right, bottom = i.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settingsHost, Fragment()).commit()
        }
    }

    class Fragment : PreferenceFragmentCompat() {
        private var downloading: Thread? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            val prefs = Prefs(requireContext())
            findPreference<Preference>("theme")?.setOnPreferenceChangeListener { _, _ ->
                view?.post { prefs.applyTheme() }
                true
            }
            findPreference<EditTextPreference>("extra_keys")?.apply {
                if (text.isNullOrBlank()) text = ExtraKeys.DEFAULT
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { it.text?.take(60) ?: "" }
                setOnBindEditTextListener { it.setSingleLine(false); it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
            }
            listOf("git_name", "git_email", "git_user").forEach { k ->
                findPreference<EditTextPreference>(k)?.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; it.setSingleLine(true) }
            }
            updateModelSummaries()
            for (lang in listOf("ru", "en")) {
                findPreference<Preference>("vosk_$lang")?.setOnPreferenceClickListener { onModelClick(lang); true }
            }
            val tokenPref = findPreference<Preference>("git_token")
            updateTokenSummary(tokenPref)
            tokenPref?.setOnPreferenceClickListener { askToken(it); true }
        }

        private fun updateTokenSummary(p: Preference?) {
            val has = SecureStore(requireContext()).has("github_token")
            p?.summary = if (has) "Сохранён в зашифрованном виде (Android Keystore). Нажмите, чтобы заменить или удалить" else "Не задан. Нужен для push и приватных репозиториев"
        }

        private fun askToken(p: Preference) {
            val ctx = requireContext()
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "ghp_… или github_pat_…"
                setSingleLine(true)
            }
            val box = LinearLayout(ctx).apply {
                setPadding(48, 16, 48, 0)
                addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Токен GitHub")
                .setMessage("Создайте fine-grained токен на github.com → Settings → Developer settings с правом Contents: Read and write. Токен хранится только на этом телефоне в зашифрованном виде.")
                .setView(box)
                .setPositiveButton("Сохранить") { _, _ ->
                    val v = input.text.toString().trim()
                    if (v.isNotEmpty()) {
                        SecureStore(ctx).put("github_token", v)
                        Toast.makeText(ctx, "Токен сохранён", Toast.LENGTH_SHORT).show()
                    }
                    updateTokenSummary(p)
                }
                .setNeutralButton("Удалить") { _, _ ->
                    SecureStore(ctx).put("github_token", null)
                    updateTokenSummary(p)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun updateModelSummaries() {
            val ctx = context ?: return
            for ((lang, info) in VoskModels.models) {
                findPreference<Preference>("vosk_$lang")?.summary =
                    if (VoskModels.isInstalled(ctx, lang)) "Скачана. Нажмите, чтобы удалить" else "Не скачана (~${info.sizeMb} МБ). Нажмите, чтобы скачать"
            }
        }

        private fun onModelClick(lang: String) {
            val ctx = requireContext()
            val info = VoskModels.models[lang] ?: return
            if (downloading != null) {
                Toast.makeText(ctx, "Уже идёт загрузка", Toast.LENGTH_SHORT).show()
                return
            }
            if (VoskModels.isInstalled(ctx, lang)) {
                MaterialAlertDialogBuilder(ctx).setTitle("Удалить модель (${info.title})?")
                    .setPositiveButton("Удалить") { _, _ -> VoskModels.delete(ctx, lang); updateModelSummaries() }
                    .setNegativeButton("Отмена", null).show()
                return
            }
            MaterialAlertDialogBuilder(ctx).setTitle("Скачать офлайн-модель")
                .setMessage("Модель Vosk (${info.title}), около ${info.sizeMb} МБ, с alphacephei.com. Лучше по Wi‑Fi.")
                .setPositiveButton("Скачать") { _, _ ->
                    val pref = findPreference<Preference>("vosk_$lang")
                    downloading = VoskModels.download(ctx.applicationContext, lang, { p ->
                        pref?.summary = if (p < 0) "Загрузка…" else if (p < 90) "Загрузка: $p%" else if (p < 100) "Распаковка…" else "Готово"
                    }) { err ->
                        downloading = null
                        if (err != null) Toast.makeText(ctx, "Ошибка: $err", Toast.LENGTH_LONG).show()
                        updateModelSummaries()
                    }
                }
                .setNegativeButton("Отмена", null).show()
        }

        override fun onDestroy() {
            super.onDestroy()
            downloading?.interrupt()
        }
    }
}
