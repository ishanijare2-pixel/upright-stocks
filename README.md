# Upright Stocks

Standalone installable PWA for exploring NSE stock CSV data.

## Features
- Search and sort imported stock records
- Gainers, losers and local watchlist
- Stock details with OHLC and volume
- CSV import; data stored in this browser's localStorage
- Installable PWA and basic offline app-shell caching
- No Firebase account or backend required

## Publish with GitHub Pages
1. Open repository **Settings → Pages**.
2. Choose **Deploy from a branch**.
3. Select branch **main** and folder **/(root)**, then Save.
4. Open the HTTPS Pages URL and use your browser's Install option.

## CSV format
Required headers: SYMBOL and CLOSE. Optional headers: COMPANY, OPEN, HIGH, LOW, PREVCLOSE, VOLUME, DATE. Example (fictional):

```csv
SYMBOL,COMPANY,OPEN,HIGH,LOW,PREVCLOSE,CLOSE,VOLUME,DATE
DEMOCO,Demo Company,100,105,98,99,103,120000,2026-11-20
```

## Data and privacy
This app does not fetch live or 15-minute delayed prices, scrape NSE, or connect to a broker. Import only data you are authorised to use. Check provider/exchange licensing before public redistribution. The repository is public, so source code is public. Imported data and watchlist stay in this browser and do not sync to other devices. Not investment advice.
