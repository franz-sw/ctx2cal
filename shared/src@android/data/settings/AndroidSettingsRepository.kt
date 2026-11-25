package com.example.ctx2cal.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.ctx2cal.domain.ports.SettingsRepository

/** SharedPreferences-backed persisted backend/model-path settings. */
class AndroidSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ctx2cal-settings", Context.MODE_PRIVATE)

    override fun preferredBackend(): String? = prefs.getString(KEY_BACKEND, null)

    override fun setPreferredBackend(name: String?) {
        prefs.edit().apply {
            if (name == null) remove(KEY_BACKEND) else putString(KEY_BACKEND, name)
        }.apply()
    }

    override fun modelPath(): String? = prefs.getString(KEY_MODEL_PATH, null)

    override fun setModelPath(path: String?) {
        prefs.edit().apply {
            if (path == null) remove(KEY_MODEL_PATH) else putString(KEY_MODEL_PATH, path)
        }.apply()
    }

    companion object {
        private const val KEY_BACKEND = "preferred_backend"
        private const val KEY_MODEL_PATH = "litertlm_model_path"
    }
}
