package de.franzsw.ctx2cal.core.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository

/** SharedPreferences-backed persisted backend, active model, download, and GPU fallback settings. */
class AndroidSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ctx2cal-settings", Context.MODE_PRIVATE)

    private val currentAppVersionCode: Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

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

    override fun activeModelId(): String? = prefs.getString(KEY_ACTIVE_MODEL_ID, null)

    override fun setActiveModelId(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE_MODEL_ID) else putString(KEY_ACTIVE_MODEL_ID, id)
        }.apply()
    }

    override fun activeDownloadId(): Long? =
        if (prefs.contains(KEY_ACTIVE_DOWNLOAD_ID)) prefs.getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L).takeIf { it >= 0L }
        else null

    override fun setActiveDownloadId(id: Long?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE_DOWNLOAD_ID) else putLong(KEY_ACTIVE_DOWNLOAD_ID, id)
        }.apply()
    }

    override fun activeDownloadModelId(): String? = prefs.getString(KEY_ACTIVE_DOWNLOAD_MODEL_ID, null)

    override fun setActiveDownloadModelId(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_ACTIVE_DOWNLOAD_MODEL_ID) else putString(KEY_ACTIVE_DOWNLOAD_MODEL_ID, id)
        }.apply()
    }

    override fun isGpuUnsupported(): Boolean {
        if (!prefs.getBoolean(KEY_GPU_UNSUPPORTED, false)) return false
        val savedVersion = prefs.getLong(KEY_GPU_UNSUPPORTED_VERSION, -1L)
        if (savedVersion != currentAppVersionCode) {
            setGpuUnsupported(false)
            return false
        }
        return true
    }

    override fun setGpuUnsupported(unsupported: Boolean) {
        prefs.edit().apply {
            if (!unsupported) {
                remove(KEY_GPU_UNSUPPORTED)
                remove(KEY_GPU_UNSUPPORTED_VERSION)
            } else {
                putBoolean(KEY_GPU_UNSUPPORTED, true)
                putLong(KEY_GPU_UNSUPPORTED_VERSION, currentAppVersionCode)
            }
        }.apply()
    }

    override fun customSystemPrompt(): String? = prefs.getString(KEY_CUSTOM_SYSTEM_PROMPT, null)

    override fun setCustomSystemPrompt(prompt: String?) {
        prefs.edit().apply {
            if (prompt == null) remove(KEY_CUSTOM_SYSTEM_PROMPT) else putString(KEY_CUSTOM_SYSTEM_PROMPT, prompt)
        }.apply()
    }

    companion object {
        private const val KEY_BACKEND = "preferred_backend"
        private const val KEY_MODEL_PATH = "litertlm_model_path"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
        private const val KEY_ACTIVE_DOWNLOAD_MODEL_ID = "active_download_model_id"
        private const val KEY_GPU_UNSUPPORTED = "gpu_unsupported"
        private const val KEY_GPU_UNSUPPORTED_VERSION = "gpu_unsupported_version"
        private const val KEY_CUSTOM_SYSTEM_PROMPT = "custom_system_prompt"
    }
}
