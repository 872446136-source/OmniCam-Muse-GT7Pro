package local.jiege.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import local.jiege.settings.ModuleSettings
import local.jiege.settings.MasterExposure
import local.jiege.settings.MasterLooks

/**
 * Module settings as the hooks read them. LSPosed (xposedsharedprefs) makes a world-readable
 * file available to the hooked camera; without an active LSPosed it falls back to private mode,
 * where the UI still works but the hooks see defaults.
 *
 * Master colour integer settings are polled by the running camera; other changes restart it.
 */
class SettingsStore(context: Context) {
    private val preferences: SharedPreferences = open(context)

    init {
        if (!preferences.getBoolean("master_exposure_migrated_514", false)) {
            val edit = preferences.edit()
            for (family in MasterLooks.VISIBLE_FAMILIES) {
                if (!preferences.contains(MasterExposure.offsetKey(family))) edit.putInt(MasterExposure.offsetKey(family), getInt(ModuleSettings.MASTER_LOOK_EXPOSURE))
                if (!preferences.contains(MasterExposure.alignKey(family))) edit.putInt(MasterExposure.alignKey(family), getInt(ModuleSettings.MASTER_ALIGN))
                if (!preferences.contains(MasterExposure.modeKey(family))) edit.putInt(MasterExposure.modeKey(family), MasterExposure.LEGACY)
            }
            edit.putBoolean("master_exposure_migrated_514", true).apply()
        }
    }

    fun get(key: String): Boolean = preferences.getBoolean(key, ModuleSettings.defaultValue(key))

    fun set(key: String, value: Boolean) {
        if (get(key) == value) return
        preferences.edit().putBoolean(key, value).apply()
        AppRestart.stopCamera()
    }

    fun getInt(key: String): Int = preferences.getInt(key, ModuleSettings.defaultInt(key))

    fun setInt(key: String, value: Int) {
        if (getInt(key) == value) return
        preferences.edit().putInt(key, value).apply()
        if (!MasterExposure.isLiveInt(key)) AppRestart.stopCamera()
    }

    fun getString(key: String): String = preferences.getString(key, ModuleSettings.defaultString(key)) ?: ModuleSettings.defaultString(key)

    fun setString(key: String, value: String) {
        if (getString(key) == value) return
        preferences.edit().putString(key, value).apply()
        AppRestart.stopCamera()
    }

    private companion object {
        @SuppressLint("WorldReadableFiles")
        @Suppress("DEPRECATION")
        fun open(context: Context): SharedPreferences = try {
            context.getSharedPreferences(ModuleSettings.FILE, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            context.getSharedPreferences(ModuleSettings.FILE, Context.MODE_PRIVATE)
        }
    }
}
