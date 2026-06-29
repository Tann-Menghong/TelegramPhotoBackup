package com.example.tgphotobackup.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val botToken: String = "",
    val chatId: String = "",
    val wifiOnly: Boolean = true,
    val autoBackup: Boolean = false,
    val includeVideos: Boolean = false,
    val maxFileSizeMb: Int = 50,
    val autoBackupIntervalHours: Int = 12,
    val requiresCharging: Boolean = false,
    val autoDeleteAfterBackup: Boolean = false,
    val themeMode: Int = 0,              // 0=system, 1=light, 2=dark
    val includedAlbums: Set<String> = emptySet(),  // empty = all albums
    val updateUrl: String = "https://github.com/Tann-Menghong/TelegramPhotoBackup",
    val biometricLock: Boolean = false,
    val safFolderUris: Set<String> = emptySet(),
    val licenseKey: String = "",
    val isPro: Boolean = false
) {
    val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()
    val maxFileSizeBytes: Long get() = maxFileSizeMb.toLong() * 1024 * 1024
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val licKey = p[PRO_KEY] ?: ""
        AppSettings(
            botToken                = p[BOT_TOKEN] ?: "",
            chatId                  = p[CHAT_ID] ?: "",
            wifiOnly                = p[WIFI_ONLY] ?: true,
            autoBackup              = p[AUTO_BACKUP] ?: false,
            includeVideos           = p[INCLUDE_VIDEOS] ?: false,
            maxFileSizeMb           = p[MAX_FILE_SIZE_MB] ?: 50,
            autoBackupIntervalHours = p[AUTO_BACKUP_INTERVAL_HOURS] ?: 12,
            requiresCharging        = p[REQUIRES_CHARGING] ?: false,
            autoDeleteAfterBackup   = p[AUTO_DELETE_AFTER_BACKUP] ?: false,
            themeMode               = p[THEME_MODE] ?: 0,
            includedAlbums          = p[INCLUDED_ALBUMS]?.let { s ->
                if (s.isBlank()) emptySet() else s.split(",").toSet()
            } ?: emptySet(),
            updateUrl               = p[UPDATE_URL]
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "https://github.com/Tann-Menghong/TelegramPhotoBackup",
            biometricLock           = p[BIOMETRIC_LOCK] ?: false,
            safFolderUris           = p[INCLUDED_SAF_FOLDERS]?.let { s ->
                if (s.isBlank()) emptySet() else s.split("\n").filter { it.isNotBlank() }.toSet()
            } ?: emptySet(),
            licenseKey              = licKey,
            isPro                   = licKey.isNotBlank() && ProManager.validate(licKey)
        )
    }

    suspend fun save(
        botToken: String,
        chatId: String,
        wifiOnly: Boolean,
        autoBackup: Boolean,
        includeVideos: Boolean,
        maxFileSizeMb: Int = 50,
        autoBackupIntervalHours: Int = 12,
        requiresCharging: Boolean = false,
        autoDeleteAfterBackup: Boolean = false,
        themeMode: Int = 0,
        includedAlbums: Set<String> = emptySet(),
        updateUrl: String = "",
        biometricLock: Boolean = false,
        safFolderUris: Set<String> = emptySet()
    ) {
        context.dataStore.edit { p ->
            p[BOT_TOKEN]                 = botToken.trim()
            p[CHAT_ID]                   = chatId.trim()
            p[WIFI_ONLY]                 = wifiOnly
            p[AUTO_BACKUP]               = autoBackup
            p[INCLUDE_VIDEOS]            = includeVideos
            p[MAX_FILE_SIZE_MB]          = maxFileSizeMb.coerceIn(1, 50)
            p[AUTO_BACKUP_INTERVAL_HOURS] = autoBackupIntervalHours
            p[REQUIRES_CHARGING]         = requiresCharging
            p[AUTO_DELETE_AFTER_BACKUP]  = autoDeleteAfterBackup
            p[THEME_MODE]                = themeMode
            p[INCLUDED_ALBUMS]           = includedAlbums.joinToString(",")
            p[UPDATE_URL]                = updateUrl.trim()
            p[BIOMETRIC_LOCK]            = biometricLock
            p[INCLUDED_SAF_FOLDERS]      = safFolderUris.joinToString("\n")
        }
    }

    suspend fun savePro(key: String) {
        context.dataStore.edit { p -> p[PRO_KEY] = key.trim() }
    }

    companion object {
        private val BOT_TOKEN                  = stringPreferencesKey("bot_token")
        private val CHAT_ID                    = stringPreferencesKey("chat_id")
        private val WIFI_ONLY                  = booleanPreferencesKey("wifi_only")
        private val AUTO_BACKUP                = booleanPreferencesKey("auto_backup")
        private val INCLUDE_VIDEOS             = booleanPreferencesKey("include_videos")
        private val MAX_FILE_SIZE_MB           = intPreferencesKey("max_file_size_mb")
        private val AUTO_BACKUP_INTERVAL_HOURS = intPreferencesKey("auto_backup_interval_hours")
        private val REQUIRES_CHARGING          = booleanPreferencesKey("requires_charging")
        private val AUTO_DELETE_AFTER_BACKUP   = booleanPreferencesKey("auto_delete_after_backup")
        private val THEME_MODE                 = intPreferencesKey("theme_mode")
        private val INCLUDED_ALBUMS            = stringPreferencesKey("included_albums")
        private val UPDATE_URL                 = stringPreferencesKey("update_url")
        private val BIOMETRIC_LOCK             = booleanPreferencesKey("biometric_lock")
        private val INCLUDED_SAF_FOLDERS       = stringPreferencesKey("included_saf_folders")
        private val PRO_KEY                    = stringPreferencesKey("pro_key")
    }
}
