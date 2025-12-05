package com.reboundrocket.app.domain.model

data class Position(
    val symbol: String,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val entryTime: Long,
    val targetPrice: Double,
    val ageInDays: Int
) {
    val unrealizedPnL: Double
        get() = (currentPrice - entryPrice) * quantity
    
    val unrealizedPnLPercent: Double
        get() = ((currentPrice - entryPrice) / entryPrice) * 100.0
    
    val marketValue: Double
        get() = currentPrice * quantity
}
