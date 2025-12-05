package com.reboundrocket.app.data.model

import com.google.gson.annotations.SerializedName

data class AlpacaAccount(
    @SerializedName("equity") val equity: String,
    @SerializedName("cash") val cash: String,
    @SerializedName("buying_power") val buyingPower: String,
    @SerializedName("portfolio_value") val portfolioValue: String,
    @SerializedName("daytrade_count") val daytradeCount: Int,
    @SerializedName("pattern_day_trader") val patternDayTrader: Boolean
)

data class AlpacaPosition(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("qty") val qty: String,
    @SerializedName("avg_entry_price") val avgEntryPrice: String,
    @SerializedName("current_price") val currentPrice: String,
    @SerializedName("market_value") val marketValue: String,
    @SerializedName("unrealized_pl") val unrealizedPl: String,
    @SerializedName("unrealized_plpc") val unrealizedPlpc: String
)

data class AlpacaOrder(
    @SerializedName("id") val id: String,
    @SerializedName("client_order_id") val clientOrderId: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("qty") val qty: String?,
    @SerializedName("notional") val notional: String?,
    @SerializedName("side") val side: String,
    @SerializedName("type") val type: String,
    @SerializedName("time_in_force") val timeInForce: String,
    @SerializedName("limit_price") val limitPrice: String?,
    @SerializedName("filled_avg_price") val filledAvgPrice: String?,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("filled_at") val filledAt: String?,
    @SerializedName("expired_at") val expiredAt: String?,
    @SerializedName("canceled_at") val canceledAt: String?
)

data class OrderRequest(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("qty") val qty: Double? = null,
    @SerializedName("notional") val notional: Double? = null,
    @SerializedName("side") val side: String,
    @SerializedName("type") val type: String,
    @SerializedName("time_in_force") val timeInForce: String,
    @SerializedName("limit_price") val limitPrice: Double? = null
)

data class AlpacaBar(
    @SerializedName("t") val timestamp: String,
    @SerializedName("o") val open: Double,
    @SerializedName("h") val high: Double,
    @SerializedName("l") val low: Double,
    @SerializedName("c") val close: Double,
    @SerializedName("v") val volume: Long,
    @SerializedName("vw") val vwap: Double?
)

data class AlpacaBarsResponse(
    @SerializedName("bars") val bars: Map<String, List<AlpacaBar>>
)
