package com.reboundrocket.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reboundrocket.app.data.repository.ConfigRepository
import com.reboundrocket.app.presentation.ui.screens.DashboardScreen
import com.reboundrocket.app.presentation.ui.screens.SettingsScreen
import com.reboundrocket.app.presentation.ui.theme.ReboundRocketTheme
import com.reboundrocket.app.presentation.viewmodel.MainViewModel
import com.reboundrocket.app.service.TradingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    
    @Inject
    lateinit var configRepository: ConfigRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startTradingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        startTradingService()

        setContent {
            ReboundRocketTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val config by viewModel.config.collectAsState()
                    val account by viewModel.account.collectAsState()
                    val currentPrice by viewModel.currentPrice.collectAsState()
                    val position by viewModel.position.collectAsState()

                    NavHost(
                        navController = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                config = config,
                                account = account,
                                currentPrice = currentPrice,
                                position = position,
                                millionaireCountdown = viewModel.calculateMillionaireCountdown(),
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onBuyNow = viewModel::buyNow,
                                onSellAll = viewModel::sellAll,
                                onCancelOrders = viewModel::cancelAllOrders,
                                onTogglePause = viewModel::togglePause
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                config = config,
                                configRepository = configRepository,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onConfigUpdated = { updatedConfig ->
                                    viewModel.updateConfig(updatedConfig)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun startTradingService() {
        TradingService.start(this)
    }
}
