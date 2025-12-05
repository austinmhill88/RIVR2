package com.reboundrocket.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reboundrocket.app.service.TradingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot completed - starting trading service")
            TradingService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
