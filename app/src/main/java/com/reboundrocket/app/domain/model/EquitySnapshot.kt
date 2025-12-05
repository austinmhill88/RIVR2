package com.reboundrocket.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equity_snapshots")
data class EquitySnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val equity: Double,
    val dayPnL: Double,
    val dayPnLPercent: Double
)
