package com.reboundrocket.app.data.repository

import com.reboundrocket.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AlpacaApiService {
    @GET("v2/account")
    suspend fun getAccount(): Response<AlpacaAccount>
    
    @GET("v2/positions")
    suspend fun getPositions(): Response<List<AlpacaPosition>>
    
    @GET("v2/positions/{symbol}")
    suspend fun getPosition(@Path("symbol") symbol: String): Response<AlpacaPosition>
    
    @POST("v2/orders")
    suspend fun createOrder(@Body order: OrderRequest): Response<AlpacaOrder>
    
    @GET("v2/orders")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<AlpacaOrder>>
    
    @DELETE("v2/orders/{order_id}")
    suspend fun cancelOrder(@Path("order_id") orderId: String): Response<AlpacaOrder>
    
    @DELETE("v2/orders")
    suspend fun cancelAllOrders(): Response<List<AlpacaOrder>>
    
    @DELETE("v2/positions/{symbol}")
    suspend fun closePosition(@Path("symbol") symbol: String): Response<AlpacaOrder>
    
    @GET("v2/stocks/{symbol}/bars")
    suspend fun getBars(
        @Path("symbol") symbol: String,
        @Query("timeframe") timeframe: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int? = null
    ): Response<AlpacaBarsResponse>
}
