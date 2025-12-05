package com.reboundrocket.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.reboundrocket.app.R
import com.reboundrocket.app.data.database.EquitySnapshotDao
import com.reboundrocket.app.data.database.TradeDao
import com.reboundrocket.app.data.repository.AlpacaRepository
import com.reboundrocket.app.data.repository.AlpacaWebSocketClient
import com.reboundrocket.app.data.repository.ConfigRepository
import com.reboundrocket.app.domain.model.*
import com.reboundrocket.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class TradingService : Service() {

    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var alpacaRepository: AlpacaRepository
    @Inject lateinit var webSocketClient: AlpacaWebSocketClient
    @Inject lateinit var tradeDao: TradeDao
    @Inject lateinit var equitySnapshotDao: EquitySnapshotDao

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var currentAccount: Account? = null
    private var currentPosition: Position? = null
    private var currentPrice: Double? = null
    private var todayOpenEquity: Double? = null
    private var highWaterMark: Double = 0.0
    
    private var vwapCalculationJob: Job? = null
    private var tradingJob: Job? = null
    private var monitoringJob: Job? = null
    private var wsMonitorJob: Job? = null
    private var lastWsMessageTime = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ReboundRocket::TradingWakeLock"
        )
        wakeLock?.acquire(TimeUnit.HOURS.toMillis(24))
        
        createNotificationChannels()
        startForeground()
        startTradingLogic()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
            )
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }

    private fun startForeground() {
        val notification = buildServiceNotification("Starting...", "+0.00%")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildServiceNotification(symbol: String, pnl: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_running, symbol, pnl))
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(symbol: String, pnl: String) {
        val notification = buildServiceNotification(symbol, pnl)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startTradingLogic() {
        serviceScope.launch {
            // Collect configuration changes
            launch {
                configRepository.config.collectLatest { config ->
                    Log.d(TAG, "Config updated: symbol=${config.symbol}, paused=${config.isPaused}")
                    restartTradingForSymbol(config.symbol, config.useLiveTrading)
                }
            }

            // Collect price updates
            launch {
                webSocketClient.priceUpdates.collectLatest { priceUpdate ->
                    currentPrice = priceUpdate.price
                    lastWsMessageTime = System.currentTimeMillis()
                    updateCurrentState()
                }
            }

            // Monitor WebSocket connection
            wsMonitorJob = launch {
                while (isActive) {
                    delay(35000) // Check every 35 seconds
                    val timeSinceLastMessage = System.currentTimeMillis() - lastWsMessageTime
                    if (timeSinceLastMessage > 30000) {
                        showAlert(
                            getString(R.string.websocket_disconnect_title),
                            getString(R.string.websocket_disconnect_message)
                        )
                    }
                }
            }

            // Start main trading loop
            tradingJob = launch {
                while (isActive) {
                    val config = configRepository.config.value
                    if (!config.isPaused) {
                        executeTradingCycle(config)
                    }
                    delay(10000) // Check every 10 seconds
                }
            }

            // Daily summary at 4:05 PM ET
            launch {
                while (isActive) {
                    val now = ZonedDateTime.now(ZoneId.of("America/New_York"))
                    val target = now.withHour(16).withMinute(5).withSecond(0).withNano(0)
                    val delay = if (now.isAfter(target)) {
                        Duration.between(now, target.plusDays(1)).toMillis()
                    } else {
                        Duration.between(now, target).toMillis()
                    }
                    delay(delay)
                    sendDailySummary()
                }
            }
        }
    }

    private suspend fun restartTradingForSymbol(symbol: String, useLive: Boolean) {
        webSocketClient.disconnect()
        delay(500)
        webSocketClient.connect(symbol, useLive)
    }

    private suspend fun updateCurrentState() {
        val config = configRepository.config.value
        currentAccount = alpacaRepository.getAccount(config.useLiveTrading)
        currentPosition = alpacaRepository.getCurrentPosition(config.symbol, config.useLiveTrading)
        
        currentAccount?.let { account ->
            if (todayOpenEquity == null) {
                todayOpenEquity = account.equity
            }
            
            if (account.equity > highWaterMark) {
                highWaterMark = account.equity
            }
            
            // Check for high drawdown
            val drawdown = ((highWaterMark - account.equity) / highWaterMark) * 100.0
            if (drawdown > 20.0) {
                handleHighDrawdown(drawdown)
            }
            
            // Update notification
            val dayPnL = todayOpenEquity?.let { open ->
                ((account.equity - open) / open) * 100.0
            } ?: 0.0
            val pnlStr = String.format("%+.2f%%", dayPnL)
            updateNotification(config.symbol, pnlStr)
            
            // Save equity snapshot periodically
            if (System.currentTimeMillis() % 300000 < 15000) { // Every 5 minutes
                equitySnapshotDao.insertSnapshot(
                    EquitySnapshot(
                        timestamp = System.currentTimeMillis(),
                        equity = account.equity,
                        dayPnL = account.equity - (todayOpenEquity ?: account.equity),
                        dayPnLPercent = dayPnL
                    )
                )
            }
        }
    }

    private suspend fun executeTradingCycle(config: TradingConfig) {
        val now = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val hour = now.hour
        val minute = now.minute
        
        // Calculate VWAP at 9:30-10:00 AM ET
        if (hour == 9 && minute == 30 && config.vwapCalculatedAt?.let { 
            System.currentTimeMillis() - it > TimeUnit.HOURS.toMillis(20)
        } != false) {
            calculateAndStoreVWAP(config)
        }
        
        // Buy windows: 11:15-12:15 and 14:15-15:15
        val inBuyWindow = (hour == 11 && minute >= 15) || 
                          (hour == 12 && minute <= 15) ||
                          (hour == 14 && minute >= 15) ||
                          (hour == 15 && minute <= 15)
        
        if (inBuyWindow && currentPosition == null) {
            attemptBuy(config)
        }
        
        // Check next-day conversion (9:30-10:30 AM)
        if (hour == 10 && minute == 30 && currentPosition != null) {
            checkPositionConversion(config)
        }
        
        // Check max position age (10 days)
        currentPosition?.let { position ->
            val ageInDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - position.entryTime)
            if (ageInDays >= 10) {
                forceClosePosition(config)
            }
        }
    }

    private suspend fun calculateAndStoreVWAP(config: TradingConfig) {
        val now = Instant.now()
        val start = now.atZone(ZoneId.of("America/New_York"))
            .withHour(9).withMinute(30).withSecond(0).toInstant()
        val end = start.plusSeconds(1800) // 30 minutes
        
        val vwap = alpacaRepository.getVWAP(config.symbol, start, end, config.useLiveTrading)
        vwap?.let {
            val updated = config.copy(
                vwapPrice = it,
                vwapCalculatedAt = System.currentTimeMillis()
            )
            configRepository.updateConfig(updated)
            Log.d(TAG, "VWAP calculated: $it")
        }
    }

    private suspend fun attemptBuy(config: TradingConfig) {
        val vwap = config.vwapPrice ?: return
        val price = currentPrice ?: return
        val account = currentAccount ?: return
        
        if (price <= vwap * 0.9985) {
            val buyingPower = account.buyingPower
            val leverageMultiplier = config.getLeverageMultiplier(account.equity)
            val notional = (buyingPower * leverageMultiplier * 0.5)
            
            Log.d(TAG, "Attempting buy: price=$price, vwap=$vwap, notional=$notional")
            
            val orderId = alpacaRepository.buyMarket(config.symbol, notional, config.useLiveTrading)
            if (orderId != null) {
                Log.d(TAG, "Buy order placed: $orderId")
                delay(2000) // Wait for fill
                
                // Get updated position
                val position = alpacaRepository.getCurrentPosition(config.symbol, config.useLiveTrading)
                position?.let {
                    val targetPercent = config.getTargetPercent(account.equity)
                    val targetPrice = it.entryPrice * (1 + targetPercent / 100.0)
                    
                    // Place sell limit order
                    alpacaRepository.sellLimit(
                        config.symbol,
                        it.quantity,
                        targetPrice,
                        config.useLiveTrading
                    )
                    
                    tradeDao.insertTrade(
                        Trade(
                            symbol = config.symbol,
                            type = TradeType.BUY,
                            quantity = it.quantity,
                            price = it.entryPrice,
                            timestamp = System.currentTimeMillis(),
                            orderId = orderId
                        )
                    )
                }
            }
        }
    }

    private suspend fun checkPositionConversion(config: TradingConfig) {
        val position = currentPosition ?: return
        
        // Check if still in loss
        if (position.unrealizedPnL < 0) {
            Log.d(TAG, "Converting to market order - still in loss")
            alpacaRepository.cancelAllOrders(config.useLiveTrading)
            delay(1000)
            alpacaRepository.sellMarket(config.symbol, position.quantity, config.useLiveTrading)
        }
    }

    private suspend fun forceClosePosition(config: TradingConfig) {
        val position = currentPosition ?: return
        Log.d(TAG, "Force closing position - max age reached")
        alpacaRepository.cancelAllOrders(config.useLiveTrading)
        delay(1000)
        alpacaRepository.sellMarket(config.symbol, position.quantity, config.useLiveTrading)
        
        tradeDao.insertTrade(
            Trade(
                symbol = config.symbol,
                type = TradeType.SELL,
                quantity = position.quantity,
                price = currentPrice ?: position.currentPrice,
                timestamp = System.currentTimeMillis(),
                pnl = position.unrealizedPnL,
                pnlPercent = position.unrealizedPnLPercent
            )
        )
    }

    private suspend fun handleHighDrawdown(drawdown: Double) {
        Log.w(TAG, "High drawdown detected: $drawdown%")
        
        // Pause bot
        val config = configRepository.config.value
        configRepository.updateConfig(config.copy(isPaused = true))
        
        // Show alarm notification
        showAlert(
            getString(R.string.drawdown_alert_title),
            getString(R.string.drawdown_alert_message, drawdown)
        )
    }

    private fun showAlert(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private suspend fun sendDailySummary() {
        val account = currentAccount ?: return
        val dayPnL = todayOpenEquity?.let { open ->
            ((account.equity - open) / open) * 100.0
        } ?: 0.0
        
        showAlert(
            getString(R.string.daily_summary_title),
            "Equity: $${String.format("%.2f", account.equity)} • ${String.format("%+.2f%%", dayPnL)}"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        webSocketClient.disconnect()
        wakeLock?.release()
    }

    companion object {
        private const val TAG = "TradingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_SERVICE = "trading_service"
        private const val CHANNEL_ALARM = "trading_alarm"

        fun start(context: Context) {
            val intent = Intent(context, TradingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TradingService::class.java)
            context.stopService(intent)
        }
    }
}
