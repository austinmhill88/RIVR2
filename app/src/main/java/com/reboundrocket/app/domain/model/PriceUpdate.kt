package com.reboundrocket.app.domain.model

data class PriceUpdate(
    val symbol: String,
    val price: Double,
    val timestamp: Long,
    val volume: Long = 0,
    val source: PriceSource = PriceSource.ALPACA
)

enum class PriceSource {
    ALPACA,
    FINNHUB,
    ALPACA_REST
}
