package com.reboundrocket.app.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.reboundrocket.app.data.repository.ConfigRepository
import com.reboundrocket.app.domain.model.TradingConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: TradingConfig,
    configRepository: ConfigRepository,
    onNavigateBack: () -> Unit,
    onConfigUpdated: (TradingConfig) -> Unit
) {
    var paperApiKey by remember { mutableStateOf(configRepository.getPaperApiKey() ?: "") }
    var paperApiSecret by remember { mutableStateOf(configRepository.getPaperApiSecret() ?: "") }
    var liveApiKey by remember { mutableStateOf(configRepository.getLiveApiKey() ?: "") }
    var liveApiSecret by remember { mutableStateOf(configRepository.getLiveApiSecret() ?: "") }
    var finnhubApiKey by remember { mutableStateOf(configRepository.getFinnhubApiKey() ?: "") }
    
    var symbol by remember { mutableStateOf(config.symbol) }
    var useLiveTrading by remember { mutableStateOf(config.useLiveTrading) }
    var manualTarget by remember { mutableStateOf(config.manualTargetPercent?.toString() ?: "") }
    var lockTarget by remember { mutableStateOf(config.lockTarget) }
    
    var showSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "API Credentials",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = paperApiKey,
                onValueChange = { paperApiKey = it },
                label = { Text("Paper API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = paperApiSecret,
                onValueChange = { paperApiSecret = it },
                label = { Text("Paper API Secret") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Divider()

            OutlinedTextField(
                value = liveApiKey,
                onValueChange = { liveApiKey = it },
                label = { Text("Live API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = liveApiSecret,
                onValueChange = { liveApiSecret = it },
                label = { Text("Live API Secret") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Divider()

            OutlinedTextField(
                value = finnhubApiKey,
                onValueChange = { finnhubApiKey = it },
                label = { Text("Finnhub API Key (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Trading Configuration",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it.uppercase() },
                label = { Text("Stock Symbol") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use Live Trading")
                Switch(
                    checked = useLiveTrading,
                    onCheckedChange = { useLiveTrading = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Manual Target Override",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = manualTarget,
                onValueChange = { manualTarget = it },
                label = { Text("Target % (e.g., 0.50)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lock Target")
                Switch(
                    checked = lockTarget,
                    onCheckedChange = { lockTarget = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Save credentials
                    configRepository.savePaperApiKey(paperApiKey)
                    configRepository.savePaperApiSecret(paperApiSecret)
                    configRepository.saveLiveApiKey(liveApiKey)
                    configRepository.saveLiveApiSecret(liveApiSecret)
                    if (finnhubApiKey.isNotBlank()) {
                        configRepository.saveFinnhubApiKey(finnhubApiKey)
                    }
                    
                    // Update config
                    val updatedConfig = config.copy(
                        symbol = symbol,
                        useLiveTrading = useLiveTrading,
                        manualTargetPercent = manualTarget.toDoubleOrNull(),
                        lockTarget = lockTarget
                    )
                    onConfigUpdated(updatedConfig)
                    
                    showSaved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            if (showSaved) {
                Text(
                    text = "✓ Saved",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showSaved = false
                }
            }
        }
    }
}
