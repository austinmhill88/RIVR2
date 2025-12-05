package com.reboundrocket.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reboundrocket.app.data.database.EquitySnapshotDao
import com.reboundrocket.app.data.database.TradeDao
import com.reboundrocket.app.data.repository.AlpacaRepository
import com.reboundrocket.app.data.repository.AlpacaWebSocketClient
import com.reboundrocket.app.data.repository.ConfigRepository
import com.reboundrocket.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val alpacaRepository: AlpacaRepository,
    private val webSocketClient: AlpacaWebSocketClient,
    private val tradeDao: TradeDao,
    private val equitySnapshotDao: EquitySnapshotDao
) : ViewModel() {

    val config = configRepository.config.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TradingConfig()
    )

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _currentPrice = MutableStateFlow<Double?>(null)
    val currentPrice: StateFlow<Double?> = _currentPrice.asStateFlow()

    private val _position = MutableStateFlow<Position?>(null)
    val position: StateFlow<Position?> = _position.asStateFlow()

    val trades = tradeDao.getAllTrades().stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    )

    private val _equityHistory = MutableStateFlow<List<EquitySnapshot>>(emptyList())
    val equityHistory: StateFlow<List<EquitySnapshot>> = _equityHistory.asStateFlow()

    val connectionState = webSocketClient.connectionState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AlpacaWebSocketClient.ConnectionState.DISCONNECTED
    )

    init {
        viewModelScope.launch {
            webSocketClient.priceUpdates.collectLatest { update ->
                _currentPrice.value = update.price
            }
        }

        viewModelScope.launch {
            config.collectLatest {
                refreshAccountData()
                loadEquityHistory()
            }
        }

        // Refresh account data periodically
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30000) // Every 30 seconds
                refreshAccountData()
            }
        }
    }

    private suspend fun refreshAccountData() {
        val cfg = config.value
        _account.value = alpacaRepository.getAccount(cfg.useLiveTrading)
        _position.value = alpacaRepository.getCurrentPosition(cfg.symbol, cfg.useLiveTrading)
    }

    private suspend fun loadEquityHistory() {
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
        equitySnapshotDao.getSnapshotsSince(thirtyDaysAgo).collectLatest { snapshots ->
            _equityHistory.value = snapshots
        }
    }

    fun updateConfig(config: TradingConfig) {
        configRepository.updateConfig(config)
    }

    fun buyNow() {
        viewModelScope.launch {
            val cfg = config.value
            val acct = _account.value ?: return@launch
            val buyingPower = acct.buyingPower
            val leverageMultiplier = cfg.getLeverageMultiplier(acct.equity)
            val notional = buyingPower * leverageMultiplier * 0.5
            
            alpacaRepository.buyMarket(cfg.symbol, notional, cfg.useLiveTrading)
            kotlinx.coroutines.delay(2000)
            refreshAccountData()
        }
    }

    fun sellAll() {
        viewModelScope.launch {
            val cfg = config.value
            val pos = _position.value ?: return@launch
            
            alpacaRepository.cancelAllOrders(cfg.useLiveTrading)
            kotlinx.coroutines.delay(1000)
            alpacaRepository.sellMarket(cfg.symbol, pos.quantity, cfg.useLiveTrading)
            kotlinx.coroutines.delay(2000)
            refreshAccountData()
        }
    }

    fun cancelAllOrders() {
        viewModelScope.launch {
            val cfg = config.value
            alpacaRepository.cancelAllOrders(cfg.useLiveTrading)
        }
    }

    fun togglePause() {
        val current = config.value
        updateConfig(current.copy(isPaused = !current.isPaused))
    }

    fun calculateMillionaireCountdown(): String {
        val acct = _account.value ?: return "N/A"
        val equity = acct.equity
        
        if (equity <= 0) return "N/A"
        
        // Estimate based on recent performance
        val history = _equityHistory.value
        if (history.size < 2) return "calculating..."
        
        val recentGrowth = history.takeLast(7)
        if (recentGrowth.size < 2) return "calculating..."
        
        val startEquity = recentGrowth.first().equity
        val endEquity = recentGrowth.last().equity
        val days = ((recentGrowth.last().timestamp - recentGrowth.first().timestamp) / 
                    (1000 * 60 * 60 * 24)).toInt()
        
        if (days == 0 || endEquity <= startEquity) return "N/A"
        
        val dailyGrowthRate = (endEquity / startEquity).let { kotlin.math.pow(it, 1.0 / days) }
        val targetEquity = 1_000_000.0
        
        if (dailyGrowthRate <= 1.0) return "N/A"
        
        val daysToMillion = (kotlin.math.ln(targetEquity / equity) / 
                            kotlin.math.ln(dailyGrowthRate)).toInt()
        
        val months = daysToMillion / 30.0
        return if (months < 1) {
            "$daysToMillion days"
        } else {
            String.format("%.1f months", months)
        }
    }
}
