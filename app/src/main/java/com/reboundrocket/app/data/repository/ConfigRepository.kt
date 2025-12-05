package com.reboundrocket.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.reboundrocket.app.domain.model.TradingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<TradingConfig> = _config.asStateFlow()

    fun getPaperApiKey(): String? = encryptedPrefs.getString(KEY_PAPER_API_KEY, null)
    fun getPaperApiSecret(): String? = encryptedPrefs.getString(KEY_PAPER_API_SECRET, null)
    fun getLiveApiKey(): String? = encryptedPrefs.getString(KEY_LIVE_API_KEY, null)
    fun getLiveApiSecret(): String? = encryptedPrefs.getString(KEY_LIVE_API_SECRET, null)
    fun getFinnhubApiKey(): String? = encryptedPrefs.getString(KEY_FINNHUB_API_KEY, null)

    fun savePaperApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_PAPER_API_KEY, key).apply()
    }

    fun savePaperApiSecret(secret: String) {
        encryptedPrefs.edit().putString(KEY_PAPER_API_SECRET, secret).apply()
    }

    fun saveLiveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_LIVE_API_KEY, key).apply()
    }

    fun saveLiveApiSecret(secret: String) {
        encryptedPrefs.edit().putString(KEY_LIVE_API_SECRET, secret).apply()
    }

    fun saveFinnhubApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_FINNHUB_API_KEY, key).apply()
    }

    private fun loadConfig(): TradingConfig {
        val prefs = context.getSharedPreferences("trading_config", Context.MODE_PRIVATE)
        return TradingConfig(
            symbol = prefs.getString(KEY_SYMBOL, "TSLA") ?: "TSLA",
            useLiveTrading = prefs.getBoolean(KEY_USE_LIVE, false),
            manualTargetPercent = if (prefs.contains(KEY_MANUAL_TARGET)) {
                prefs.getFloat(KEY_MANUAL_TARGET, 0f).toDouble()
            } else null,
            lockTarget = prefs.getBoolean(KEY_LOCK_TARGET, false),
            isPaused = prefs.getBoolean(KEY_IS_PAUSED, false),
            vwapPrice = if (prefs.contains(KEY_VWAP_PRICE)) {
                prefs.getFloat(KEY_VWAP_PRICE, 0f).toDouble()
            } else null,
            vwapCalculatedAt = if (prefs.contains(KEY_VWAP_CALC_TIME)) {
                prefs.getLong(KEY_VWAP_CALC_TIME, 0L)
            } else null
        )
    }

    fun updateConfig(config: TradingConfig) {
        val prefs = context.getSharedPreferences("trading_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_SYMBOL, config.symbol)
            putBoolean(KEY_USE_LIVE, config.useLiveTrading)
            config.manualTargetPercent?.let { putFloat(KEY_MANUAL_TARGET, it.toFloat()) }
                ?: remove(KEY_MANUAL_TARGET)
            putBoolean(KEY_LOCK_TARGET, config.lockTarget)
            putBoolean(KEY_IS_PAUSED, config.isPaused)
            config.vwapPrice?.let { putFloat(KEY_VWAP_PRICE, it.toFloat()) }
                ?: remove(KEY_VWAP_PRICE)
            config.vwapCalculatedAt?.let { putLong(KEY_VWAP_CALC_TIME, it) }
                ?: remove(KEY_VWAP_CALC_TIME)
        }.apply()
        _config.value = config
    }

    companion object {
        private const val KEY_PAPER_API_KEY = "paper_api_key"
        private const val KEY_PAPER_API_SECRET = "paper_api_secret"
        private const val KEY_LIVE_API_KEY = "live_api_key"
        private const val KEY_LIVE_API_SECRET = "live_api_secret"
        private const val KEY_FINNHUB_API_KEY = "finnhub_api_key"
        private const val KEY_SYMBOL = "symbol"
        private const val KEY_USE_LIVE = "use_live"
        private const val KEY_MANUAL_TARGET = "manual_target"
        private const val KEY_LOCK_TARGET = "lock_target"
        private const val KEY_IS_PAUSED = "is_paused"
        private const val KEY_VWAP_PRICE = "vwap_price"
        private const val KEY_VWAP_CALC_TIME = "vwap_calc_time"
    }
}
