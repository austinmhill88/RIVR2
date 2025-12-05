package com.reboundrocket.app.domain.model

data class Account(
    val equity: Double,
    val cash: Double,
    val buyingPower: Double,
    val portfolioValue: Double,
    val dayTradeCount: Int,
    val patternDayTrader: Boolean
)
