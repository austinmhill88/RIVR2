package com.reboundrocket.app.data.repository

import android.util.Log
import com.reboundrocket.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlpacaRepository @Inject constructor(
    private val configRepository: ConfigRepository
) {
    private var paperApi: AlpacaApiService? = null
    private var liveApi: AlpacaApiService? = null

    private fun getApiService(useLive: Boolean): AlpacaApiService? {
        val (key, secret) = if (useLive) {
            configRepository.getLiveApiKey() to configRepository.getLiveApiSecret()
        } else {
            configRepository.getPaperApiKey() to configRepository.getPaperApiSecret()
        }

        if (key.isNullOrBlank() || secret.isNullOrBlank()) {
            Log.w(TAG, "API credentials not configured")
            return null
        }

        val cached = if (useLive) liveApi else paperApi
        if (cached != null) return cached

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("APCA-API-KEY-ID", key)
                .addHeader("APCA-API-SECRET-KEY", secret)
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val baseUrl = if (useLive) {
            "https://api.alpaca.markets/"
        } else {
            "https://paper-api.alpaca.markets/"
        }

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlpacaApiService::class.java)

        if (useLive) liveApi = api else paperApi = api
        return api
    }

    suspend fun getAccount(useLive: Boolean): Account? = withContext(Dispatchers.IO) {
        try {
            val response = getApiService(useLive)?.getAccount()
            if (response?.isSuccessful == true) {
                response.body()?.let { acc ->
                    Account(
                        equity = acc.equity.toDouble(),
                        cash = acc.cash.toDouble(),
                        buyingPower = acc.buyingPower.toDouble(),
                        portfolioValue = acc.portfolioValue.toDouble(),
                        dayTradeCount = acc.daytradeCount,
                        patternDayTrader = acc.patternDayTrader
                    )
                }
            } else {
                Log.e(TAG, "Failed to get account: ${response?.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting account", e)
            null
        }
    }

    suspend fun getCurrentPosition(symbol: String, useLive: Boolean): Position? = 
        withContext(Dispatchers.IO) {
            try {
                val response = getApiService(useLive)?.getPosition(symbol)
                if (response?.isSuccessful == true) {
                    response.body()?.let { pos ->
                        val entryTime = System.currentTimeMillis() // Approximate
                        Position(
                            symbol = pos.symbol,
                            quantity = pos.qty.toDouble(),
                            entryPrice = pos.avgEntryPrice.toDouble(),
                            currentPrice = pos.currentPrice.toDouble(),
                            entryTime = entryTime,
                            targetPrice = 0.0, // Will be set from orders
                            ageInDays = 0 // Will be calculated
                        )
                    }
                } else if (response?.code() == 404) {
                    null // No position
                } else {
                    Log.e(TAG, "Failed to get position: ${response?.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting position", e)
                null
            }
        }

    suspend fun buyMarket(symbol: String, notional: Double, useLive: Boolean): String? =
        withContext(Dispatchers.IO) {
            try {
                val order = com.reboundrocket.app.data.model.OrderRequest(
                    symbol = symbol,
                    notional = notional,
                    side = "buy",
                    type = "market",
                    timeInForce = "day"
                )
                val response = getApiService(useLive)?.createOrder(order)
                if (response?.isSuccessful == true) {
                    response.body()?.id
                } else {
                    Log.e(TAG, "Failed to create buy order: ${response?.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating buy order", e)
                null
            }
        }

    suspend fun sellLimit(symbol: String, qty: Double, limitPrice: Double, useLive: Boolean): String? =
        withContext(Dispatchers.IO) {
            try {
                val order = com.reboundrocket.app.data.model.OrderRequest(
                    symbol = symbol,
                    qty = qty,
                    side = "sell",
                    type = "limit",
                    timeInForce = "gtc",
                    limitPrice = limitPrice
                )
                val response = getApiService(useLive)?.createOrder(order)
                if (response?.isSuccessful == true) {
                    response.body()?.id
                } else {
                    Log.e(TAG, "Failed to create sell limit order: ${response?.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating sell limit order", e)
                null
            }
        }

    suspend fun sellMarket(symbol: String, qty: Double, useLive: Boolean): String? =
        withContext(Dispatchers.IO) {
            try {
                val order = com.reboundrocket.app.data.model.OrderRequest(
                    symbol = symbol,
                    qty = qty,
                    side = "sell",
                    type = "market",
                    timeInForce = "day"
                )
                val response = getApiService(useLive)?.createOrder(order)
                if (response?.isSuccessful == true) {
                    response.body()?.id
                } else {
                    Log.e(TAG, "Failed to create sell market order: ${response?.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating sell market order", e)
                null
            }
        }

    suspend fun cancelAllOrders(useLive: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getApiService(useLive)?.cancelAllOrders()
            response?.isSuccessful == true
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling orders", e)
            false
        }
    }

    suspend fun getVWAP(symbol: String, start: Instant, end: Instant, useLive: Boolean): Double? =
        withContext(Dispatchers.IO) {
            try {
                val formatter = DateTimeFormatter.ISO_INSTANT
                val response = getApiService(useLive)?.getBars(
                    symbol = symbol,
                    timeframe = "1Min",
                    start = formatter.format(start),
                    end = formatter.format(end)
                )
                if (response?.isSuccessful == true) {
                    response.body()?.bars?.get(symbol)?.let { bars ->
                        if (bars.isNotEmpty()) {
                            var totalVolumePrice = 0.0
                            var totalVolume = 0L
                            bars.forEach { bar ->
                                val price = bar.vwap ?: bar.close
                                totalVolumePrice += price * bar.volume
                                totalVolume += bar.volume
                            }
                            if (totalVolume > 0) totalVolumePrice / totalVolume else null
                        } else null
                    }
                } else {
                    Log.e(TAG, "Failed to get VWAP: ${response?.code()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting VWAP", e)
                null
            }
        }

    companion object {
        private const val TAG = "AlpacaRepository"
    }
}
