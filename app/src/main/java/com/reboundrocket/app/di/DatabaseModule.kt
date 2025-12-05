package com.reboundrocket.app.di

import android.content.Context
import androidx.room.Room
import com.reboundrocket.app.data.database.TradingDatabase
import com.reboundrocket.app.data.database.TradeDao
import com.reboundrocket.app.data.database.EquitySnapshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTradingDatabase(
        @ApplicationContext context: Context
    ): TradingDatabase {
        return Room.databaseBuilder(
            context,
            TradingDatabase::class.java,
            "trading_database"
        ).build()
    }

    @Provides
    fun provideTradeDao(database: TradingDatabase): TradeDao {
        return database.tradeDao()
    }

    @Provides
    fun provideEquitySnapshotDao(database: TradingDatabase): EquitySnapshotDao {
        return database.equitySnapshotDao()
    }
}
