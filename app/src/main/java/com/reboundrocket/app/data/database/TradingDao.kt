package com.reboundrocket.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reboundrocket.app.domain.model.Trade
import com.reboundrocket.app.domain.model.EquitySnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Insert
    suspend fun insertTrade(trade: Trade)
    
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<Trade>>
    
    @Query("SELECT * FROM trades ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrades(limit: Int): List<Trade>
    
    @Query("SELECT * FROM trades WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getTradesSince(startTime: Long): List<Trade>
}

@Dao
interface EquitySnapshotDao {
    @Insert
    suspend fun insertSnapshot(snapshot: EquitySnapshot)
    
    @Query("SELECT * FROM equity_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int): List<EquitySnapshot>
    
    @Query("SELECT * FROM equity_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getSnapshotsSince(startTime: Long): Flow<List<EquitySnapshot>>
    
    @Query("DELETE FROM equity_snapshots WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSnapshots(cutoffTime: Long)
}
