package com.tianhuiu.solvex.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tianhuiu.solvex.data.models.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓库
 */
class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val APP_CONFIG_KEY = stringPreferencesKey("app_config")
    private val LAST_UPDATE_CHECK_KEY = longPreferencesKey("last_update_check")
    private val LAUNCH_COUNT_KEY = intPreferencesKey("launch_count")
    private val UPDATE_ETAG_KEY = stringPreferencesKey("update_etag")
    private val CACHED_VERSION_KEY = stringPreferencesKey("cached_version")
    private val CONSECUTIVE_NO_UPDATE_KEY = intPreferencesKey("consecutive_no_update")
    private val LAST_UPDATE_DIALOG_DISMISSED_KEY = longPreferencesKey("last_update_dialog_dismissed")

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[APP_CONFIG_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<AppConfig>(jsonStr)
            } catch (e: Exception) {
                AppConfig()
            }
        } else {
            AppConfig()
        }
    }

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK_KEY] ?: 0L
    }

    val launchCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[LAUNCH_COUNT_KEY] ?: 0
    }

    val updateEtagFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_ETAG_KEY]
    }

    val cachedVersionFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CACHED_VERSION_KEY]
    }

    val consecutiveNoUpdateFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CONSECUTIVE_NO_UPDATE_KEY] ?: 0
    }

    val lastUpdateDialogDismissedFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_DIALOG_DISMISSED_KEY] ?: 0L
    }

    suspend fun incrementLaunchCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[LAUNCH_COUNT_KEY] ?: 0
            preferences[LAUNCH_COUNT_KEY] = current + 1
        }
    }

    suspend fun saveLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK_KEY] = timestamp
        }
    }

    suspend fun saveAppConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            preferences[APP_CONFIG_KEY] = json.encodeToString(config)
        }
    }

    suspend fun saveUpdateEtag(etag: String?) {
        context.dataStore.edit { preferences ->
            if (etag != null) {
                preferences[UPDATE_ETAG_KEY] = etag
            } else {
                preferences.remove(UPDATE_ETAG_KEY)
            }
        }
    }

    suspend fun saveCachedVersion(versionJson: String?) {
        context.dataStore.edit { preferences ->
            if (versionJson != null) {
                preferences[CACHED_VERSION_KEY] = versionJson
            } else {
                preferences.remove(CACHED_VERSION_KEY)
            }
        }
    }

    suspend fun saveConsecutiveNoUpdate(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[CONSECUTIVE_NO_UPDATE_KEY] = count
        }
    }

    suspend fun saveLastUpdateDialogDismissed(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_DIALOG_DISMISSED_KEY] = timestamp
        }
    }
}
