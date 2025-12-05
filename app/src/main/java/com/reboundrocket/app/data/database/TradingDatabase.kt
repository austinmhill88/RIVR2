package com.reboundrocket.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reboundrocket.app.domain.model.Trade
import com.reboundrocket.app.domain.model.EquitySnapshot

@Database(
    entities = [Trade::class, EquitySnapshot::class],
    version = 1,
    exportSchema = false
)
abstract class TradingDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun equitySnapshotDao(): EquitySnapshotDao
}
