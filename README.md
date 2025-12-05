# Rebound Rocket 🚀

A fully automated, rule-based, hold-til-rebound stock trading bot for Android with Alpaca API integration.

## Overview

Rebound Rocket is a 100% native Android application built with Kotlin and Jetpack Compose that automates stock trading using the Alpaca Paper and Live Trading APIs. The bot runs 24/7 as a foreground service, implementing a sophisticated "hold-til-rebound" strategy that never sells for a loss.

## Key Features

### 🔄 24/7 Foreground Service
- Runs continuously even when screen is off or app is swiped away
- Persistent notification showing current symbol and daily P&L
- Android 14+ foreground service compliance with proper service types
- Automatic restart on device reboot

### 📈 Live Price Feed
- Primary: Alpaca WebSocket (IEX for Paper, SIP for Live)
- Exponential backoff reconnection (1s → 2s → 5s → 10s → 30s cap)
- Auto-reconnect on network changes with ping/pong keep-alive
- Optional Finnhub WebSocket fallback (requires API key)

### 🎯 Dynamic Target % Scaling
Target profit percentages automatically scale with account equity:

| Account Equity | Target % | Leverage |
|---------------|----------|----------|
| $0 - $9,999 | 0.20% | 1× |
| $10,000 - $24,999 | 0.30% | 1× |
| $25,000 - $49,999 | 0.50% | 2× |
| $50,000 - $99,999 | 0.70% | 2× |
| $100,000 - $249,999 | 0.90% | 2× |
| $250,000 - $999,999 | 1.10% | 2× |
| $1,000,000+ | 1.20% | 2× |

### 📊 Trading Rules

**VWAP Calculation (9:30-10:00 AM ET)**
- Calculates first 30-minute VWAP daily
- Used as the buy threshold reference price

**Buy Windows (11:15-12:15 and 14:15-15:15 ET)**
- Buys 50% of available buying power
- Only triggers if price ≤ VWAP × 0.9985
- No position opened if one already exists
- Immediately places GTC limit sell at entry × (1 + target %)

**Hold-til-Rebound Strategy**
- Never sells for a loss under normal operation
- Next morning (9:30-10:30 AM): if limit order not filled, converts to market order at 10:30:01 AM ONLY if still in loss
- Maximum position age: 10 calendar days → force market sell

**Leverage**
- 1× buying power until account reaches $25,000
- Permanent 2× leverage activated at $25,000+

### 🎨 Beautiful Dark-Mode UI

**Dashboard**
- Large live price ticker
- Real-time equity and daily P&L percentage
- Color-coded target % banner
- Current position details with unrealized P&L
- "$1M in ~X months" projection based on recent performance

**Manual Override Buttons**
- BUY 50% NOW - Purchase immediately at market price
- SELL ALL NOW - Close entire position at market
- CANCEL ALL ORDERS - Cancel all pending orders
- PAUSE/RESUME BOT - Temporarily halt automated trading
- All protected by biometric/PIN authentication

**Settings Screen**
- Separate Paper and Live API credentials (encrypted storage)
- Optional Finnhub API key for fallback price feed
- Stock symbol selection (default: TSLA)
- Manual target % override with lock toggle
- All changes applied instantly

### 🔒 Security & Safety

- API keys stored in Android EncryptedSharedPreferences
- Biometric or PIN authentication for manual actions
- Auto-pause + loud alarm if drawdown >20% from high-water mark
- Daily summary notification at 4:05 PM ET
- Loud notification if WebSocket disconnects >30 seconds
- Full trade history with CSV export capability

### 📱 Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with Hilt dependency injection
- **Database**: Room for trade history and equity snapshots
- **Async**: Coroutines and Flow
- **Networking**: OkHttp, Retrofit, WebSocket
- **Security**: AndroidX Security Crypto, Biometric

## Setup Instructions

### Prerequisites

1. **Android Device/Emulator**
   - Android 8.0 (API 26) or higher
   - Android 14+ recommended for best foreground service support

2. **Alpaca API Account**
   - Sign up at [alpaca.markets](https://alpaca.markets)
   - Generate Paper Trading API keys (free)
   - Optionally generate Live Trading API keys

3. **Finnhub API (Optional)**
   - Sign up at [finnhub.io](https://finnhub.io) for fallback price feed
   - Free tier available

### Building the Project

**Note**: Due to network restrictions in some environments, you may need to build this project locally on your own machine with full internet access.

1. **Clone the repository**
   ```bash
   git clone https://github.com/austinmhill88/RIVR2.git
   cd RIVR2
   ```

2. **Open in Android Studio**
   - Android Studio Hedgehog (2023.1.1) or newer
   - File → Open → Select RIVR2 folder
   - Wait for Gradle sync to complete

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install APK**
   ```bash
   ./gradlew installDebug
   ```
   
   Or build release APK:
   ```bash
   ./gradlew assembleRelease
   ```
   APK will be in `app/build/outputs/apk/release/`

### Alternative: Manual Build in Android Studio

1. Open Android Studio
2. File → Open → Select the RIVR2 folder
3. Wait for Gradle sync
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. The APK will be in `app/build/outputs/apk/debug/`

### First-Time Configuration

1. **Launch the app** - Grant notification permission when prompted

2. **Open Settings** (gear icon in top-right)

3. **Enter API Credentials**
   - Paper API Key and Secret (required for testing)
   - Live API Key and Secret (optional, for real trading)
   - Finnhub API Key (optional, for fallback)

4. **Configure Trading**
   - Stock Symbol (default: TSLA)
   - Toggle "Use Live Trading" when ready (default: OFF)
   - Optionally set manual target % and lock it

5. **Save Settings**

6. **Important: Disable Battery Optimization**
   - Settings → Apps → Rebound Rocket → Battery → Unrestricted
   - This ensures the service runs 24/7

### Battery Optimization Whitelist (CRITICAL)

For uninterrupted 24/7 operation:

**Samsung/One UI:**
1. Settings → Apps → Rebound Rocket
2. Battery → Allow background activity: ON
3. Settings → Device care → Battery → Background usage limits
4. Remove Rebound Rocket from any restricted lists

**Stock Android/Pixel:**
1. Settings → Apps → Rebound Rocket → Battery
2. Select "Unrestricted"

**Other Manufacturers:**
- Similar process - ensure app is not restricted by battery saver

## Usage

### Starting Trading

1. Ensure API credentials are configured
2. Check that the bot is not paused (button shows "PAUSE BOT")
3. Service starts automatically on app launch and device boot
4. Persistent notification shows: "Rebound Rocket • Trading TSLA • +0.62% today"

### Manual Trading

All manual actions require biometric/PIN authentication:

- **BUY 50% NOW**: Immediately purchases 50% of buying power at market price
- **SELL ALL NOW**: Closes entire position at market price
- **CANCEL ALL ORDERS**: Cancels all pending limit orders
- **PAUSE BOT**: Stops automated trading (manual actions still work)
- **RESUME BOT**: Re-enables automated trading

### Monitoring

- **Dashboard**: Real-time price, equity, P&L, and position details
- **Notifications**: 
  - Daily summary at 4:05 PM ET
  - High drawdown alerts (>20%)
  - WebSocket disconnect warnings
- **Trade History**: Exportable CSV log of all trades

### Trading Schedule

The bot operates on Eastern Time (ET):
- **9:30 AM**: Market open, VWAP calculation begins
- **10:00 AM**: VWAP calculation complete
- **11:15 AM - 12:15 PM**: First buy window
- **2:15 PM - 3:15 PM**: Second buy window
- **4:00 PM**: Market close
- **4:05 PM**: Daily summary notification
- **Next day 9:30-10:30 AM**: Position conversion window (if still in loss)

## Architecture

```
app/src/main/java/com/reboundrocket/app/
├── data/
│   ├── database/        # Room database (trades, equity snapshots)
│   ├── model/           # Data models for Alpaca API
│   └── repository/      # Data layer (Alpaca API, WebSocket, Config)
├── domain/
│   └── model/           # Business models (Position, Account, Trade, etc.)
├── di/                  # Hilt dependency injection modules
├── service/             # TradingService (foreground service)
├── receiver/            # BootReceiver (auto-start on reboot)
├── presentation/
│   ├── ui/
│   │   ├── screens/     # Dashboard, Settings screens
│   │   └── theme/       # Material 3 dark theme
│   └── viewmodel/       # MainViewModel
└── util/                # Utility classes
```

## Disclaimer

**This software is for educational purposes only.**

- Not financial advice
- Use at your own risk
- Trading involves risk of loss
- Test thoroughly with Paper Trading before using Live Trading
- Author assumes no liability for losses

## License

MIT License - See LICENSE file for details

## Contributing

Contributions welcome! Please open an issue or PR.

## Support

For issues, questions, or feature requests, please open a GitHub issue.
