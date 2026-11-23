# Upright Stocks — Native Android Kotlin

The Android app is built with Kotlin and Jetpack Compose. It is built by GitHub Actions; Android Studio and Codespaces are not required.

## Features

- NSE equity market overview using the latest available end-of-day snapshot
- Search by NSE trading symbol or company name
- Device-stored watchlist
- Top gainers and losers for the latest available session
- Stock detail page with OHLC, previous close, volume and a 90-day closing-price chart
- Manual refresh
- Deep teal and mint interface

## Build the APK

1. Open the repository's Actions tab.
2. Select **Build Upright Stocks APK**.
3. Select the kotlin-native branch and choose **Run workflow**.
4. Wait for the run to finish successfully.
5. Download the upright-stocks-debug-apk artifact and extract app-debug.apk.

The APK is generated at android/app/build/outputs/apk/debug/app-debug.apk.

## Market data

The app reads the keyless end-of-day REST endpoints from TejHQ:

- Latest available NSE market snapshot: GET /v1/snapshot/nse?date=YYYY-MM-DD
- Individual stock history: GET /v1/ohlcv/nse/{SYMBOL}?from=YYYY-MM-DD&to=YYYY-MM-DD

The app checks recent weekdays to find the latest published trading-day snapshot. The source is NSE daily bhavcopy data republished by TejHQ. This is end-of-day data, not live or intraday quotes, and is generally ready by the next morning IST.

Data source documentation: https://tejhq.dev/docs

## Important data-use limits

This configuration is for one person's private, personal-use dashboard. TejHQ's published data terms say use inside an app or service for other people, a business, or public users is commercial use and requires its Pro plan; exchange display and redistribution rules may also apply. Do not publish this app or distribute market data to other people without first obtaining the necessary permissions and licences.

Data is provided as-is and is not investment advice. Historical prices are unadjusted and may not reflect later splits or bonuses.

## Technical details

- Package: com.ishan.uprightstocks
- Minimum Android version: Android 7.0 (API 24)
- Compile/target SDK: 36
- Java: 17
- Build: GitHub Actions
- Data is fetched directly over HTTPS; no API key is embedded in the APK.
