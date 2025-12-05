package com.reboundrocket.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class Trade(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val type: TradeType,
    val quantity: Double,
    val price: Double,
    val timestamp: Long,
    val orderId: String? = null,
    val pnl: Double? = null,
    val pnlPercent: Double? = null
)

enum class TradeType {
    BUY,
    SELL
}
