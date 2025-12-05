package com.reboundrocket.app.domain.model

data class TradingConfig(
    val symbol: String = "TSLA",
    val useLiveTrading: Boolean = false,
    val manualTargetPercent: Double? = null,
    val lockTarget: Boolean = false,
    val isPaused: Boolean = false,
    val vwapPrice: Double? = null,
    val vwapCalculatedAt: Long? = null
) {
    fun getTargetPercent(equity: Double): Double {
        if (lockTarget && manualTargetPercent != null) {
            return manualTargetPercent
        }
        
        return when {
            equity < 10_000 -> 0.20
            equity < 25_000 -> 0.30
            equity < 50_000 -> 0.50
            equity < 100_000 -> 0.70
            equity < 250_000 -> 0.90
            equity < 1_000_000 -> 1.10
            else -> 1.20
        }
    }
    
    fun getLeverageMultiplier(equity: Double): Int {
        return if (equity >= 25_000) 2 else 1
    }
}
