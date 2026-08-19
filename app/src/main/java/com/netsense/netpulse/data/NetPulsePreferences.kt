package com.netsense.netpulse.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "netpulse_settings")

data class AppSettings(
    val zombieAlertsEnabled: Boolean = true,
    val alertThresholdScore: Int = 40,
    val sentinelIntervalSeconds: Long = 45L,
    val preferredDnsProvider: String = "Google (8.8.8.8)",
    val dataSaverMode: Boolean = true,
    val onboardingCompleted: Boolean = false,
    /** How long raw DiagnosticLogEntity rows are kept before being purged (already rolled
     *  into a DailyUsabilityAggregateEntity by then, so long-range trends survive). */
    val diagnosticLogRetentionDays: Int = 30,
    /** How long raw ml_telemetry_observations rows are kept - shorter than diagnostic log
     *  retention since this table is one row per telemetry tick (the ~14MB/day source),
     *  not one row per user-visible probe. */
    val rawTelemetryRetentionDays: Int = 14,
    val lastDataPurgeTimestamp: Long = 0L
)

class NetPulsePreferences(private val context: Context) {

    companion object {
        val KEY_ZOMBIE_ALERTS = booleanPreferencesKey("zombie_alerts_enabled")
        val KEY_ALERT_THRESHOLD = intPreferencesKey("alert_threshold_score")
        val KEY_SENTINEL_INTERVAL = longPreferencesKey("sentinel_interval_seconds")
        val KEY_DNS_PROVIDER = stringPreferencesKey("preferred_dns_provider")
        val KEY_DATA_SAVER = booleanPreferencesKey("data_saver_mode")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_DIAGNOSTIC_LOG_RETENTION_DAYS = intPreferencesKey("diagnostic_log_retention_days")
        val KEY_RAW_TELEMETRY_RETENTION_DAYS = intPreferencesKey("raw_telemetry_retention_days")
        val KEY_LAST_DATA_PURGE_TIMESTAMP = longPreferencesKey("last_data_purge_timestamp")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            zombieAlertsEnabled = preferences[KEY_ZOMBIE_ALERTS] ?: true,
            alertThresholdScore = preferences[KEY_ALERT_THRESHOLD] ?: 40,
            sentinelIntervalSeconds = preferences[KEY_SENTINEL_INTERVAL] ?: 45L,
            preferredDnsProvider = preferences[KEY_DNS_PROVIDER] ?: "Google (8.8.8.8)",
            dataSaverMode = preferences[KEY_DATA_SAVER] ?: true,
            onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false,
            diagnosticLogRetentionDays = preferences[KEY_DIAGNOSTIC_LOG_RETENTION_DAYS] ?: 30,
            rawTelemetryRetentionDays = preferences[KEY_RAW_TELEMETRY_RETENTION_DAYS] ?: 14,
            lastDataPurgeTimestamp = preferences[KEY_LAST_DATA_PURGE_TIMESTAMP] ?: 0L
        )
    }

    suspend fun updateZombieAlerts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ZOMBIE_ALERTS] = enabled
        }
    }

    suspend fun updateAlertThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERT_THRESHOLD] = threshold
        }
    }

    suspend fun updateSentinelInterval(seconds: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SENTINEL_INTERVAL] = seconds
        }
    }

    suspend fun updateDnsProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DNS_PROVIDER] = provider
        }
    }

    suspend fun updateDataSaver(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DATA_SAVER] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun updateDiagnosticLogRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DIAGNOSTIC_LOG_RETENTION_DAYS] = days
        }
    }

    suspend fun updateRawTelemetryRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RAW_TELEMETRY_RETENTION_DAYS] = days
        }
    }

    suspend fun setLastDataPurgeTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_DATA_PURGE_TIMESTAMP] = timestamp
        }
    }
}
