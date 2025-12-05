package com.reboundrocket.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.reboundrocket.app.domain.model.PriceSource
import com.reboundrocket.app.domain.model.PriceUpdate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlpacaWebSocketClient @Inject constructor(
    private val configRepository: ConfigRepository
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val _priceUpdates = MutableSharedFlow<PriceUpdate>(replay = 1)
    val priceUpdates: SharedFlow<PriceUpdate> = _priceUpdates.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    private var reconnectAttempt = 0
    private val maxReconnectDelay = 30000L
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var currentSymbol: String? = null
    private var isAuthenticated = false
    private val gson = Gson()

    fun connect(symbol: String, useLive: Boolean) {
        currentSymbol = symbol
        disconnect()
        
        val (key, secret) = if (useLive) {
            configRepository.getLiveApiKey() to configRepository.getLiveApiSecret()
        } else {
            configRepository.getPaperApiKey() to configRepository.getPaperApiSecret()
        }

        if (key.isNullOrBlank() || secret.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect: API credentials not set")
            scope.launch {
                _connectionState.emit(ConnectionState.ERROR)
            }
            return
        }

        val wsUrl = if (useLive) {
            "wss://stream.data.alpaca.markets/v2/sip"
        } else {
            "wss://stream.data.alpaca.markets/v2/iex"
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                scope.launch {
                    _connectionState.emit(ConnectionState.CONNECTED)
                }
                reconnectAttempt = 0
                
                // Send authentication
                val authMessage = mapOf(
                    "action" to "auth",
                    "key" to key,
                    "secret" to secret
                )
                webSocket.send(gson.toJson(authMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val messages = gson.fromJson(text, Array<JsonObject>::class.java)
                    messages.forEach { message ->
                        when (message.get("T")?.asString) {
                            "success" -> {
                                if (message.get("msg")?.asString?.contains("authenticated") == true) {
                                    isAuthenticated = true
                                    subscribeTo(symbol, webSocket)
                                }
                            }
                            "subscription" -> {
                                Log.d(TAG, "Subscribed: ${message.get("trades")}")
                            }
                            "t" -> { // Trade
                                val sym = message.get("S")?.asString
                                val price = message.get("p")?.asDouble
                                val timestamp = message.get("t")?.asLong
                                val volume = message.get("s")?.asLong ?: 0L
                                
                                if (sym == symbol && price != null && timestamp != null) {
                                    scope.launch {
                                        _priceUpdates.emit(
                                            PriceUpdate(
                                                symbol = sym,
                                                price = price,
                                                timestamp = timestamp / 1_000_000, // Convert to millis
                                                volume = volume,
                                                source = PriceSource.ALPACA
                                            )
                                        )
                                    }
                                }
                            }
                            "q" -> { // Quote
                                val sym = message.get("S")?.asString
                                val askPrice = message.get("ap")?.asDouble
                                val bidPrice = message.get("bp")?.asDouble
                                val timestamp = message.get("t")?.asLong
                                
                                if (sym == symbol && askPrice != null && bidPrice != null && timestamp != null) {
                                    val midPrice = (askPrice + bidPrice) / 2.0
                                    scope.launch {
                                        _priceUpdates.emit(
                                            PriceUpdate(
                                                symbol = sym,
                                                price = midPrice,
                                                timestamp = timestamp / 1_000_000,
                                                source = PriceSource.ALPACA
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                scope.launch {
                    _connectionState.emit(ConnectionState.DISCONNECTED)
                }
                scheduleReconnect(symbol, useLive)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                scope.launch {
                    _connectionState.emit(ConnectionState.DISCONNECTED)
                }
            }
        })
    }

    private fun subscribeTo(symbol: String, webSocket: WebSocket) {
        val subscribeMessage = mapOf(
            "action" to "subscribe",
            "trades" to listOf(symbol),
            "quotes" to listOf(symbol)
        )
        webSocket.send(gson.toJson(subscribeMessage))
        Log.d(TAG, "Subscribed to $symbol")
    }

    private fun scheduleReconnect(symbol: String, useLive: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delays = listOf(1000L, 2000L, 5000L, 10000L)
            val delay = delays.getOrElse(reconnectAttempt) { maxReconnectDelay }
            Log.d(TAG, "Reconnecting in ${delay}ms (attempt ${reconnectAttempt + 1})")
            delay(delay)
            reconnectAttempt++
            connect(symbol, useLive)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isAuthenticated = false
        scope.launch {
            _connectionState.emit(ConnectionState.DISCONNECTED)
        }
    }

    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    companion object {
        private const val TAG = "AlpacaWebSocket"
    }
}
