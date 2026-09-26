package com.simivr.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore by preferencesDataStore(name = "sim_ivr_settings")

data class AppSettings(
    val defaultSimSlot: Int = 0,
    val callVolumePercent: Int = 100,
    val dtmfSensitivity: Double = 0.5,
    val crmBaseUrl: String = "",
    val crmApiKey: String = "",
    val recordingEnabledGlobal: Boolean = false,
    val defaultTtsLocale: String = "en-IN",
    val bulkCallingComplianceAccepted: Boolean = false,
    val telemarketerRegistered: Boolean = false,
    val keepScreenOnDuringCalls: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(private val context: Context) {

    private object Keys {
        val DEFAULT_SIM = intPreferencesKey("default_sim_slot")
        val CALL_VOLUME = intPreferencesKey("call_volume_percent")
        val DTMF_SENSITIVITY = doublePreferencesKey("dtmf_sensitivity")
        val CRM_BASE_URL = stringPreferencesKey("crm_base_url")
        val CRM_API_KEY = stringPreferencesKey("crm_api_key")
        val RECORDING_ENABLED = booleanPreferencesKey("recording_enabled_global")
        val TTS_LOCALE = stringPreferencesKey("default_tts_locale")
        val COMPLIANCE_ACCEPTED = booleanPreferencesKey("bulk_calling_compliance_accepted")
        val TELEMARKETER_REGISTERED = booleanPreferencesKey("telemarketer_registered")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_during_calls")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultSimSlot = prefs[Keys.DEFAULT_SIM] ?: 0,
            callVolumePercent = prefs[Keys.CALL_VOLUME] ?: 100,
            dtmfSensitivity = prefs[Keys.DTMF_SENSITIVITY] ?: 0.5,
            crmBaseUrl = prefs[Keys.CRM_BASE_URL] ?: "",
            crmApiKey = prefs[Keys.CRM_API_KEY] ?: "",
            recordingEnabledGlobal = prefs[Keys.RECORDING_ENABLED] ?: false,
            defaultTtsLocale = prefs[Keys.TTS_LOCALE] ?: "en-IN",
            bulkCallingComplianceAccepted = prefs[Keys.COMPLIANCE_ACCEPTED] ?: false,
            telemarketerRegistered = prefs[Keys.TELEMARKETER_REGISTERED] ?: false,
            keepScreenOnDuringCalls = prefs[Keys.KEEP_SCREEN_ON] ?: true
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                defaultSimSlot = prefs[Keys.DEFAULT_SIM] ?: 0,
                callVolumePercent = prefs[Keys.CALL_VOLUME] ?: 100,
                dtmfSensitivity = prefs[Keys.DTMF_SENSITIVITY] ?: 0.5,
                crmBaseUrl = prefs[Keys.CRM_BASE_URL] ?: "",
                crmApiKey = prefs[Keys.CRM_API_KEY] ?: "",
                recordingEnabledGlobal = prefs[Keys.RECORDING_ENABLED] ?: false,
                defaultTtsLocale = prefs[Keys.TTS_LOCALE] ?: "en-IN",
                bulkCallingComplianceAccepted = prefs[Keys.COMPLIANCE_ACCEPTED] ?: false,
                telemarketerRegistered = prefs[Keys.TELEMARKETER_REGISTERED] ?: false,
                keepScreenOnDuringCalls = prefs[Keys.KEEP_SCREEN_ON] ?: true
            )
            val updated = transform(current)
            prefs[Keys.DEFAULT_SIM] = updated.defaultSimSlot
            prefs[Keys.CALL_VOLUME] = updated.callVolumePercent
            prefs[Keys.DTMF_SENSITIVITY] = updated.dtmfSensitivity
            prefs[Keys.CRM_BASE_URL] = updated.crmBaseUrl
            prefs[Keys.CRM_API_KEY] = updated.crmApiKey
            prefs[Keys.RECORDING_ENABLED] = updated.recordingEnabledGlobal
            prefs[Keys.TTS_LOCALE] = updated.defaultTtsLocale
            prefs[Keys.COMPLIANCE_ACCEPTED] = updated.bulkCallingComplianceAccepted
            prefs[Keys.TELEMARKETER_REGISTERED] = updated.telemarketerRegistered
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOnDuringCalls
        }
    }
}
