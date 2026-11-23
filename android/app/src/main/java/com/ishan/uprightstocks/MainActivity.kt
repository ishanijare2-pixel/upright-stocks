package com.ishan.uprightstocks

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val LightThemeState = mutableStateOf(false)
private val Navy: Color get() = if (LightThemeState.value) Color(0xFFF1F6F4) else Color(0xFF071B1C)
private val Panel: Color get() = if (LightThemeState.value) Color.White else Color(0xFF102A2B)
private val PanelLight: Color get() = if (LightThemeState.value) Color(0xFFE2ECE8) else Color(0xFF173738)
private val Mint: Color get() = if (LightThemeState.value) Color(0xFF087F68) else Color(0xFF70E0C0)
private val Muted: Color get() = if (LightThemeState.value) Color(0xFF60756F) else Color(0xFF9BB7B2)
private val Gain: Color get() = if (LightThemeState.value) Color(0xFF14865D) else Color(0xFF54D6A1)
private val Loss: Color get() = if (LightThemeState.value) Color(0xFFC84D4D) else Color(0xFFFF7777)
private val White: Color get() = if (LightThemeState.value) Color(0xFF152D28) else Color(0xFFF3FAF8)

private const val PREFS_NAME = "upright_stocks"
private const val WATCHLIST_KEY = "watchlist"
private const val WATCHLISTS_KEY = "watchlists"
private const val PORTFOLIO_KEY = "portfolio"
private const val ALERTS_KEY = "alerts"
private const val THEME_KEY = "light_theme"
private val DEFAULT_WATCHLIST = setOf(
    "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
    "SBIN", "ITC", "LT", "BHARTIARTL", "TATAMOTORS"
)

data class Stock(
    val symbol: String,
    val company: String,
    val price: Double,
    val previousClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val date: String
) {
    val change: Double get() = price - previousClose
    val changePercent: Double
        get() = if (previousClose > 0.0) (change / previousClose) * 100.0 else 0.0
}

data class PricePoint(val date: String, val close: Double, val open: Double = close, val high: Double = close, val low: Double = close, val volume: Long = 0L)
data class PortfolioHolding(val symbol: String, val quantity: Double, val averagePrice: Double)
data class PriceAlert(val symbol: String, val target: Double, val above: Boolean, val kind: String = "Price")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var lightTheme by remember { mutableStateOf(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(THEME_KEY, false)) }
            MaterialTheme(
                colorScheme = if (lightTheme) androidx.compose.material3.lightColorScheme(
                    primary = Color(0xFF087F68), background = Color(0xFFF1F6F4), surface = Color.White,
                    onPrimary = Color.White, onBackground = Color(0xFF152D28), onSurface = Color(0xFF152D28)
                ) else darkColorScheme(
                    primary = Color(0xFF70E0C0), background = Color(0xFF071B1C), surface = Color(0xFF102A2B),
                    onPrimary = Color(0xFF071B1C), onBackground = Color(0xFFF3FAF8), onSurface = Color(0xFFF3FAF8)
                )
            ) {
                UprightStocksApp(isLightTheme = lightTheme, onToggleTheme = {
                    lightTheme = !lightTheme
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(THEME_KEY, lightTheme).apply()
                })
            }
        }
    }
}

@Composable
private fun UprightStocksApp(isLightTheme: Boolean, onToggleTheme: () -> Unit) {
    SideEffect { LightThemeState.value = isLightTheme }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stocks by remember { mutableStateOf(emptyList<Stock>()) }
    var watchlists by remember { mutableStateOf(loadWatchlists(context)) }
    var activeWatchlist by remember { mutableStateOf("Main") }
    var watchlist by remember { mutableStateOf(watchlists["Main"] ?: loadWatchlist(context)) }
    var portfolio by remember { mutableStateOf(loadPortfolio(context)) }
    var alerts by remember { mutableStateOf(loadAlerts(context)) }
    var changeFilter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Symbol") }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedStock by remember { mutableStateOf<Stock?>(null) }
    var history by remember { mutableStateOf(emptyList<PricePoint>()) }
    var loading by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var marketDate by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Preparing your NSE market dashboard…") }

    fun refresh() {
        scope.launch {
            loading = true
            message = "Loading the latest available NSE closing data…"
            try {
                val result = StockRepository.fetchLatestSnapshot()
                stocks = result.first
                marketDate = result.second
                message = "End-of-day data loaded. Prices are not live."
            } catch (error: Exception) {
                message = error.message ?: "Could not load market data. Check your internet connection."
            } finally {
                loading = false
            }
        }
    }

    fun openStock(stock: Stock) {
        selectedStock = stock
        history = emptyList()
        scope.launch {
            historyLoading = true
            try {
                history = StockRepository.fetchHistory(stock.symbol)
            } catch (_: Exception) {
                history = emptyList()
            } finally {
                historyLoading = false
            }
        }
    }

    fun toggleWatchlist(symbol: String) {
        watchlist = if (watchlist.contains(symbol)) watchlist - symbol else watchlist + symbol
        watchlists = watchlists + (activeWatchlist to watchlist)
        saveWatchlists(context, watchlists)
        saveWatchlist(context, watchlist)
    }

    fun selectWatchlist(name: String) {
        activeWatchlist = name
        watchlist = watchlists[name] ?: emptySet()
    }

    fun createWatchlist(name: String) {
        val clean = name.trim().take(24)
        if (clean.isNotBlank() && !watchlists.containsKey(clean)) {
            watchlists = watchlists + (clean to emptySet())
            saveWatchlists(context, watchlists)
            selectWatchlist(clean)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val gainers = stocks.filter { it.changePercent > 0 }.sortedByDescending { it.changePercent }.take(10)
    val losers = stocks.filter { it.changePercent < 0 }.sortedBy { it.changePercent }.take(10)
    val advancers = stocks.count { it.change > 0 }
    val decliners = stocks.count { it.change < 0 }
    val filtered = stocks.filter {
        (it.symbol.contains(search, true) || it.company.contains(search, true)) &&
            when (changeFilter) {
                "Gainers" -> it.changePercent > 0.0
                "Losers" -> it.changePercent < 0.0
                else -> true
            }
    }
    val sortedFiltered = when (sortMode) {
        "Price" -> filtered.sortedByDescending { it.price }
        "Change" -> filtered.sortedByDescending { it.changePercent }
        "Volume" -> filtered.sortedByDescending { it.volume }
        else -> filtered.sortedBy { it.symbol }
    }
    val watchlistStocks = stocks.filter { watchlist.contains(it.symbol) }.sortedBy { it.symbol }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        if (selectedStock != null) {
            StockDetailsScreen(
                stock = selectedStock!!,
                history = history,
                historyLoading = historyLoading,
                isWatched = watchlist.contains(selectedStock!!.symbol),
                onBack = { selectedStock = null },
                onToggleWatchlist = { toggleWatchlist(selectedStock!!.symbol) }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UprightLogo(Modifier.size(48.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("UPRIGHT STOCKS", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                        Text("INDIAN EQUITY MARKET", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp)
                    }
                    Surface(
                        modifier = Modifier.clickable(enabled = !loading, onClick = { refresh() }),
                        color = PanelLight, shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (loading) "…" else "↻", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(5.dp))
                            Text(if (loading) "Loading" else "Refresh", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("MARKET OVERVIEW", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                                Text(if (marketDate.isBlank()) "Your market at a glance" else "NSE closing session", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(color = Mint.copy(alpha = 0.13f), shape = RoundedCornerShape(20.dp)) {
                                Text("● EOD", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("NSE STOCKS", if (stocks.isEmpty()) "—" else stocks.size.toString(), Modifier.weight(1f))
                            MetricCard("ADVANCERS", if (stocks.isEmpty()) "—" else advancers.toString(), Modifier.weight(1f), Gain)
                            MetricCard("DECLINERS", if (stocks.isEmpty()) "—" else decliners.toString(), Modifier.weight(1f), Loss)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (marketDate.isBlank()) "Waiting for latest available trading day" else "NSE CLOSE  ·  $marketDate",
                            color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
                        )
                        Text(message, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Panel, RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf("Market", "Watchlist", "Movers", "Indices", "Sectors", "Portfolio", "Alerts", "Settings", "Screener", "Research").forEachIndexed { index, title ->
                        Surface(
                            modifier = Modifier.clickable { selectedTab = index },
                            color = if (selectedTab == index) Mint else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(title, color = if (selectedTab == index) Navy else Muted,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search NSE symbols") }
                        )
                        Spacer(Modifier.height(8.dp))
                        if (loading && stocks.isEmpty()) LoadingState()
                        else if (stocks.isEmpty()) EmptyState(message, onRetry = { refresh() })
                        else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SORT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                listOf("Symbol", "Price", "Change", "Volume").forEach { mode ->
                                    SortChip(label = mode, selected = sortMode == mode) {
                                        sortMode = mode
                                    }
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text("${sortedFiltered.size} matching stocks · sorted by $sortMode", color = Muted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                                listOf("All", "Gainers", "Losers").forEach { filter -> SortChip(filter, changeFilter == filter) { changeFilter = filter } }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sortedFiltered.forEach { stock ->
                                    StockCard(stock = stock, isWatched = watchlist.contains(stock.symbol), onClick = { openStock(stock) }, onToggleWatchlist = { toggleWatchlist(stock.symbol) })
                                }
                            }
                        }
                    }
                    1 -> WatchlistsScreen(
                        stocks = stocks,
                        watchlist = watchlist,
                        names = watchlists.keys.sorted(),
                        activeName = activeWatchlist,
                        onSelect = { selectWatchlist(it) },
                        onCreate = { createWatchlist(it) },
                        onOpen = { openStock(it) },
                        onToggle = { toggleWatchlist(it) }
                    )
                    2 -> {
                        if (stocks.isEmpty() && loading) LoadingState()
                        else if (stocks.isEmpty()) EmptyState(message, onRetry = { refresh() })
                        else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionHeading("TOP GAINERS", "Biggest percentage rises in the latest session")
                                gainers.forEach { stock ->
                                    StockCard(stock = stock, isWatched = watchlist.contains(stock.symbol), onClick = { openStock(stock) }, onToggleWatchlist = { toggleWatchlist(stock.symbol) })
                                }
                                Spacer(Modifier.height(4.dp))
                                SectionHeading("TOP LOSERS", "Biggest percentage falls in the latest session")
                                losers.forEach { stock ->
                                    StockCard(stock = stock, isWatched = watchlist.contains(stock.symbol), onClick = { openStock(stock) }, onToggleWatchlist = { toggleWatchlist(stock.symbol) })
                                }
                            }
                        }
                    }
                    3 -> IndicesScreen()
                    4 -> SectorsScreen(stocks, { openStock(it) })
                    5 -> PortfolioScreen(stocks, portfolio, { symbol, qty, avg ->
                        portfolio = (portfolio.filterNot { it.symbol == symbol } + PortfolioHolding(symbol, qty, avg)).sortedBy { it.symbol }
                        savePortfolio(context, portfolio)
                    }, { symbol -> portfolio = portfolio.filterNot { it.symbol == symbol }; savePortfolio(context, portfolio) })
                    6 -> AlertsScreen(stocks, alerts, marketDate, { symbol, target, above, kind ->
                        alerts = (alerts.filterNot { it.symbol == symbol && it.kind == kind } + PriceAlert(symbol, target, above, kind)).sortedBy { it.symbol }
                        saveAlerts(context, alerts)
                    }, { symbol, kind -> alerts = alerts.filterNot { it.symbol == symbol && it.kind == kind }; saveAlerts(context, alerts) })
                    7 -> SettingsScreen(isLightTheme, onToggleTheme, marketDate)
                    8 -> ScreenerScreen(stocks, { openStock(it) })
                    else -> ResearchScreen()
                }

                Spacer(Modifier.height(8.dp))
                Divider(color = PanelLight)
                Text("PERSONAL USE • END-OF-DAY DATA • NOT INVESTMENT ADVICE", color = Muted,
                    fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Data: TejHQ, sourced from NSE bhavcopy", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun UprightLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = Color(0xFF102A2B), size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.24f))
        drawRoundRect(color = Color(0xFF70E0C0),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.62f),
            size = androidx.compose.ui.geometry.Size(w * 0.13f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f))
        drawRoundRect(color = Color(0xFF70E0C0),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.40f, h * 0.47f),
            size = androidx.compose.ui.geometry.Size(w * 0.13f, h * 0.39f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f))
        drawRoundRect(color = Color(0xFF70E0C0),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.13f, h * 0.54f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f))
        val trend = Path().apply {
            moveTo(w * 0.17f, h * 0.45f)
            lineTo(w * 0.39f, h * 0.31f)
            lineTo(w * 0.56f, h * 0.37f)
            lineTo(w * 0.82f, h * 0.14f)
        }
        drawPath(trend, Color(0xFFF3FAF8), style = Stroke(width = w * 0.075f, cap = StrokeCap.Round))
        val arrow = Path().apply {
            moveTo(w * 0.66f, h * 0.14f)
            lineTo(w * 0.82f, h * 0.14f)
            lineTo(w * 0.82f, h * 0.30f)
        }
        drawPath(arrow, Color(0xFFF3FAF8), style = Stroke(width = w * 0.075f, cap = StrokeCap.Round))
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Mint.copy(alpha = 0.18f) else Panel,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            label,
            color = if (selected) Mint else Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier, valueColor: Color = Mint) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = valueColor, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StockCard(stock: Stock, isWatched: Boolean, onClick: () -> Unit, onToggleWatchlist: () -> Unit) {
    val movementColor = if (stock.change >= 0) Gain else Loss
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stock.symbol, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(stock.company, color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRupees(stock.price), color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(String.format(Locale.US, "%+.2f%%", stock.changePercent), color = movementColor,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(7.dp))
            Surface(
                modifier = Modifier.size(34.dp).clickable(onClick = onToggleWatchlist),
                color = if (isWatched) Mint.copy(alpha = 0.16f) else PanelLight,
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isWatched) "★" else "☆", color = if (isWatched) Mint else Muted, fontSize = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(subtitle, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Mint, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text("Loading NSE closing prices…", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(message: String, onRetry: (() -> Unit)? = null) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Market data unavailable", color = Mint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(message, color = Muted, fontSize = 12.sp)
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun StockDetailsScreen(
    stock: Stock,
    history: List<PricePoint>,
    historyLoading: Boolean,
    isWatched: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val movementColor = if (stock.change >= 0) Gain else Loss
    var selectedRange by remember(stock.symbol) { mutableStateOf("3M") }
    var chartStyle by remember(stock.symbol) { mutableStateOf("Line") }
    val rangeCount = when (selectedRange) {
        "1M" -> 22
        "6M" -> 132
        "1Y" -> 252
        else -> 66
    }
    val visibleHistory = history.takeLast(rangeCount)
    val firstValue = visibleHistory.firstOrNull()?.close ?: 0.0
    val lastValue = visibleHistory.lastOrNull()?.close ?: 0.0
    val periodChange = lastValue - firstValue
    val periodPercent = if (firstValue > 0.0) periodChange / firstValue * 100.0 else 0.0
    Column(modifier = Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("‹  BACK TO MARKET", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Spacer(Modifier.height(14.dp))
        Text(stock.symbol, color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(stock.company, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text(formatRupees(stock.price), color = White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Text(String.format(Locale.US, "%+.2f  (%+.2f%%)", stock.change, stock.changePercent),
            color = movementColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("NSE closing price · ${stock.date}", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(18.dp))
        Button(onClick = onToggleWatchlist, modifier = Modifier.fillMaxWidth()) {
            Text(if (isWatched) "Remove from watchlist" else "＋ Add to watchlist")
        }
        Spacer(Modifier.height(20.dp))
        Text("PRICE HISTORY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Choose a period and chart style · daily OHLC data", color = Muted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("1M", "3M", "6M", "1Y").forEach { range ->
                SortChip(range, selectedRange == range) { selectedRange = range }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SortChip("Line", chartStyle == "Line") { chartStyle = "Line" }
            SortChip("Candles", chartStyle == "Candles") { chartStyle = "Candles" }
        }
        if (visibleHistory.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("PERIOD CHANGE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "%+.2f%%", periodPercent), color = if (periodChange >= 0) Gain else Loss, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("HIGH / LOW", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${formatRupees(visibleHistory.maxOf { it.close })} / ${formatRupees(visibleHistory.minOf { it.close })}", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(230.dp).padding(8.dp)) {
                when {
                    historyLoading -> CircularProgressIndicator(color = Mint, modifier = Modifier.align(Alignment.Center))
                    visibleHistory.size >= 2 -> TradingViewChart(visibleHistory, chartStyle)
                    else -> Text("Price history is not available for this period.", color = Muted,
                        modifier = Modifier.align(Alignment.Center), fontSize = 12.sp)
                }
            }
        }
        Text("${visibleHistory.size} trading sessions shown", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(18.dp))
        Text("TECHNICAL INDICATORS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Calculated from available daily closing prices · educational indicators, not signals", color = Muted, fontSize = 10.sp)
        val indicatorValues = calculateIndicators(visibleHistory)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("RSI (14)", indicatorValues.rsi?.let { String.format(Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
            MetricCard("SMA 20", indicatorValues.sma20?.let { formatRupees(it) } ?: "—", Modifier.weight(1f))
            MetricCard("SMA 50", indicatorValues.sma50?.let { formatRupees(it) } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("EMA 9", indicatorValues.ema9?.let { formatRupees(it) } ?: "—", Modifier.weight(1f))
            MetricCard("EMA 21", indicatorValues.ema21?.let { formatRupees(it) } ?: "—", Modifier.weight(1f))
            MetricCard("MACD", indicatorValues.macd?.let { String.format(Locale.US, "%.2f", it) } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("SESSION DETAILS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        DetailLine("Open", formatRupees(stock.open))
        DetailLine("Day high", formatRupees(stock.high))
        DetailLine("Day low", formatRupees(stock.low))
        DetailLine("Previous close", formatRupees(stock.previousClose))
        DetailLine("Volume", String.format(Locale.US, "%,d", stock.volume))
        Spacer(Modifier.height(14.dp))
        Text("Historical prices are unadjusted end-of-day data. Periods use the latest available trading sessions and may not reflect later stock splits or bonuses.",
            color = Muted, fontSize = 10.sp)
    }
}

private data class TechnicalIndicators(
    val rsi: Double?, val sma20: Double?, val sma50: Double?,
    val ema9: Double?, val ema21: Double?, val macd: Double?
)

private fun calculateIndicators(points: List<PricePoint>): TechnicalIndicators {
    val closes = points.map { it.close }.filter { it > 0.0 }
    fun sma(period: Int): Double? = if (closes.size >= period) closes.takeLast(period).average() else null
    fun ema(period: Int): Double? {
        if (closes.size < period) return null
        val alpha = 2.0 / (period + 1.0)
        var value = closes.take(period).average()
        closes.drop(period).forEach { value = it * alpha + value * (1.0 - alpha) }
        return value
    }
    val rsi = if (closes.size >= 15) {
        val changes = closes.zipWithNext { a, b -> b - a }.takeLast(14)
        val gains = changes.filter { it > 0 }.sum() / 14.0
        val losses = -changes.filter { it < 0 }.sum() / 14.0
        when {
            losses == 0.0 && gains > 0.0 -> 100.0
            losses == 0.0 -> 50.0
            else -> 100.0 - (100.0 / (1.0 + gains / losses))
        }
    } else null
    return TechnicalIndicators(rsi, sma(20), sma(50), ema(9), ema(21),
        if (closes.size >= 26) (ema(12) ?: 0.0) - (ema(26) ?: 0.0) else null)
}

@Composable
private fun TradingViewChart(points: List<PricePoint>, chartStyle: String) {
    val candleData = remember(points) {
        org.json.JSONArray().apply {
            points.forEach { point ->
                if (point.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    put(JSONObject().put("time", point.date).put("open", point.open)
                        .put("high", point.high).put("low", point.low).put("close", point.close))
                }
            }
        }.toString()
    }
    val volumeData = remember(points) {
        org.json.JSONArray().apply {
            points.forEach { point ->
                if (point.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    put(JSONObject().put("time", point.date).put("value", point.volume)
                        .put("color", if (point.close >= point.open) "#275F50" else "#713B42"))
                }
            }
        }.toString()
    }
    val lineData = remember(points) {
        org.json.JSONArray().apply {
            points.forEach { point ->
                if (point.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    put(JSONObject().put("time", point.date).put("value", point.close))
                }
            }
        }.toString()
    }
    val html = remember(candleData, lineData, volumeData, chartStyle) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <style>
          html,body,#chart{margin:0;width:100%;height:100%;overflow:hidden;background:#102A2B}
          body{font-family:Arial,sans-serif}
          #chart{height:calc(100% - 18px)}
          #credit{height:18px;text-align:right;font-size:10px;color:#9BB7B2;padding-right:5px;box-sizing:border-box}
          #credit a{color:#70E0C0;text-decoration:none}
          #error{color:#FF7777;font-size:12px;padding:12px}
        </style></head><body>
        <div id="chart"></div><div id="credit">Charts by <a href="https://www.tradingview.com/" target="_blank" rel="noopener">TradingView</a> · Lightweight Charts™</div>
        <script src="https://unpkg.com/lightweight-charts@4.2.3/dist/lightweight-charts.standalone.production.js"
          onerror="document.getElementById('chart').innerHTML='<div id=error>Chart library could not load. Check your internet connection.</div>'"></script>
        <script>
          (function(){
            if (!window.LightweightCharts) return;
            const chart = LightweightCharts.createChart(document.getElementById('chart'), {
              width: document.getElementById('chart').clientWidth,
              height: document.getElementById('chart').clientHeight,
              layout: { background: { type: 'solid', color: '#102A2B' }, textColor: '#D5E7E2', attributionLogo: true },
              grid: { vertLines: { color: '#1B3B3C' }, horzLines: { color: '#1B3B3C' } },
              rightPriceScale: { borderColor: '#315354' },
              timeScale: { borderColor: '#315354', timeVisible: true, secondsVisible: false },
              crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
              handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
              handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true }
            });
            const candles = ${candleData};
            const lines = ${lineData};
            if ('${chartStyle}' === 'Candles') {
              const series = chart.addCandlestickSeries({
                upColor:'#54D6A1', downColor:'#FF7777',
                borderUpColor:'#54D6A1', borderDownColor:'#FF7777',
                wickUpColor:'#54D6A1', wickDownColor:'#FF7777'
              });
              series.setData(candles);
            } else {
              const series = chart.addLineSeries({ color:'#70E0C0', lineWidth:2, priceLineVisible:true });
              series.setData(lines);
            }
            const volumes = ${volumeData};
            const volumeSeries = chart.addHistogramSeries({
              priceFormat: { type: 'volume' }, priceScaleId: '',
              scaleMargins: { top: 0.82, bottom: 0 }
            });
            volumeSeries.setData(volumes);
            chart.timeScale().fitContent();
            new ResizeObserver(() => chart.applyOptions({
              width: document.getElementById('chart').clientWidth,
              height: document.getElementById('chart').clientHeight
            })).observe(document.getElementById('chart'));
          })();
        </script></body></html>
        """.trimIndent()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.rgb(16, 42, 43))
                loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
                tag = html
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
            }
        }
    )
}

@Composable
private fun PriceChart(points: List<PricePoint>, chartStyle: String) {
    val values = points.flatMap { listOf(it.low, it.high, it.close) }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxSize()) {
        val topPadding = 5.dp.toPx()
        val bottomPadding = 5.dp.toPx()
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
        fun yFor(value: Double): Float =
            topPadding + chartHeight - (((value - minValue) / range).toFloat() * chartHeight)
        if (chartStyle == "Candles") {
            val slotWidth = size.width / points.size.coerceAtLeast(1)
            val bodyWidth = (slotWidth * 0.62f).coerceAtLeast(2.dp.toPx())
            points.forEachIndexed { index, point ->
                val x = slotWidth * (index + 0.5f)
                val rising = point.close >= point.open
                val candleColor = if (rising) Gain else Loss
                drawLine(candleColor, androidx.compose.ui.geometry.Offset(x, yFor(point.high)),
                    androidx.compose.ui.geometry.Offset(x, yFor(point.low)), strokeWidth = 1.dp.toPx())
                val bodyTop = minOf(yFor(point.open), yFor(point.close))
                val bodyBottom = maxOf(yFor(point.open), yFor(point.close))
                val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1.5.dp.toPx())
                drawRect(candleColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x - bodyWidth / 2f, bodyTop),
                    size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight))
            }
        } else {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size == 1) 0f else size.width * index / (points.size - 1)
                val y = yFor(point.close)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        drawLine(color = Muted.copy(alpha = 0.25f),
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private object StockRepository {
    private const val BASE_URL = "https://api.tejhq.dev"

    suspend fun fetchLatestSnapshot(): Pair<List<Stock>, String> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var lastError = "No recent NSE trading-day data was found."

        repeat(15) {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
                val date = formatter.format(calendar.time)
                try {
                    // Snapshot responses may be paginated. Collect every page so the
                    // market screen does not silently show only the first batch.
                    val allStocks = linkedMapOf<String, Stock>()
                    var offset = 0
                    var pageCount = 0
                    var hasSnapshot = false

                    while (pageCount < 30) {
                        val root = getJson(
                            "$BASE_URL/v1/snapshot/nse?date=$date&limit=200&offset=$offset"
                        )
                        val rows = root.optJSONArray("data") ?: break
                        if (rows.length() == 0) break
                        hasSnapshot = true
                        val beforePage = allStocks.size

                        for (index in 0 until rows.length()) {
                            val item = rows.optJSONObject(index) ?: continue
                            val series = item.optString("series", "")
                            val symbol = item.optString("symbol", "").trim()
                            val close = item.optDouble("close", 0.0)
                            val previous = item.optDouble("prev_close", 0.0)
                            if (series == "EQ" && symbol.isNotBlank() && close > 0.0 && previous > 0.0) {
                                allStocks[symbol] = Stock(
                                    symbol = symbol,
                                    company = item.optString("name", symbol).ifBlank { symbol },
                                    price = close,
                                    previousClose = previous,
                                    open = item.optDouble("open", 0.0),
                                    high = item.optDouble("high", 0.0),
                                    low = item.optDouble("low", 0.0),
                                    volume = item.optLong("volume", 0L),
                                    date = date
                                )
                            }
                        }

                        pageCount++
                        // Stop safely if the server ignores pagination or repeats a page.
                        if (allStocks.size == beforePage) break
                        val nextOffset = root.optJSONObject("meta")?.optInt("next_offset", -1) ?: -1
                        if (nextOffset > offset) {
                            offset = nextOffset
                        } else if (rows.length() >= 200) {
                            offset += rows.length()
                        } else {
                            break
                        }
                    }

                    if (hasSnapshot && allStocks.isNotEmpty()) {
                        return@withContext allStocks.values.sortedBy { it.symbol } to date
                    }
                } catch (error: Exception) {
                    lastError = error.message ?: lastError
                }
            }
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        throw IllegalStateException("Could not find a recent NSE snapshot. $lastError")
    }

    suspend fun fetchHistory(symbol: String): List<PricePoint> = withContext(Dispatchers.IO) {
        val toCalendar = Calendar.getInstance()
        val to = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(toCalendar.time)
        toCalendar.add(Calendar.DAY_OF_YEAR, -400)
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(toCalendar.time)
        val root = getJson("$BASE_URL/v1/ohlcv/nse/$symbol?from=$from&to=$to")
        val rows = root.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val close = item.optDouble("close", 0.0)
                val open = item.optDouble("open", close)
                val high = item.optDouble("high", maxOf(open, close))
                val low = item.optDouble("low", minOf(open, close))
                if (close > 0.0 && open > 0.0 && high > 0.0 && low > 0.0) {
                    add(PricePoint(item.optString("date", ""), close, open, high, low, item.optLong("volume", 0L)))
                }
            }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "UprightStocks-Android/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Market data API returned HTTP $code." }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}


private val sectorMap = mapOf(
"RELIANCE" to "Energy", "ONGC" to "Energy", "IOC" to "Energy", "BPCL" to "Energy", "NTPC" to "Utilities", "POWERGRID" to "Utilities",
"TCS" to "IT", "INFY" to "IT", "HCLTECH" to "IT", "WIPRO" to "IT", "TECHM" to "IT", "LTIM" to "IT",
"HDFCBANK" to "Banking", "ICICIBANK" to "Banking", "SBIN" to "Banking", "KOTAKBANK" to "Banking", "AXISBANK" to "Banking",
"BAJFINANCE" to "Financials", "BAJAJFINSV" to "Financials", "ITC" to "FMCG", "HINDUNILVR" to "FMCG", "NESTLEIND" to "FMCG",
"TATAMOTORS" to "Auto", "MARUTI" to "Auto", "HEROMOTOCO" to "Auto", "SUNPHARMA" to "Pharma", "DRREDDY" to "Pharma",
"LT" to "Industrials", "ADANIPORTS" to "Industrials", "ULTRACEMCO" to "Materials", "TATASTEEL" to "Materials",
"BHARTIARTL" to "Telecom", "ASIANPAINT" to "Consumer", "TITAN" to "Consumer"
)

@Composable
private fun IndicesScreen() {
    var nifty by remember { mutableStateOf<IndexQuote?>(null) }
    var sensex by remember { mutableStateOf<IndexQuote?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refreshIndices() {
        scope.launch {
            loading = true
            error = ""
            try {
                val results = withContext(Dispatchers.IO) {
                    Pair(
                        fetchIndexQuote("^NSEI", "NIFTY 50"),
                        fetchIndexQuote("^BSESN", "SENSEX")
                    )
                }
                nifty = results.first
                sensex = results.second
            } catch (e: Exception) {
                error = e.message ?: "Could not load index data."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshIndices() }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionHeading("MARKET INDICES", "NSE and BSE benchmark closing levels")
            }
            Text(
                if (loading) "Loading…" else "↻ Refresh",
                color = Mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !loading) { refreshIndices() }.padding(8.dp)
            )
        }
        IndexQuoteCard("NIFTY 50", nifty, loading)
        IndexQuoteCard("SENSEX", sensex, loading)
        if (error.isNotBlank()) {
            Text(error, color = Loss, fontSize = 11.sp)
            Text("Tap Refresh to try again.", color = Muted, fontSize = 11.sp)
        }
        Text(
            "Source: Yahoo Finance chart feed (unofficial). Daily closing bars only; values may be delayed or unavailable. Not an exchange-certified feed.",
            color = Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun IndexQuoteCard(title: String, quote: IndexQuote?, loading: Boolean) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (quote == null) {
                Text(if (loading) "Fetching latest daily close…" else "No index data loaded", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            } else {
                val change = quote.close - quote.previousClose
                val percent = if (quote.previousClose > 0.0) change / quote.previousClose * 100.0 else 0.0
                Text(String.format(Locale.US, "%,.2f", quote.close), color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    String.format(Locale.US, "%+.2f  (%+.2f%%)", change, percent),
                    color = if (change >= 0) Gain else Loss,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Previous close  " + String.format(Locale.US, "%,.2f", quote.previousClose), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                Text("Daily bar: ${quote.date}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

private data class IndexQuote(
    val close: Double,
    val previousClose: Double,
    val date: String
)

private fun fetchIndexQuote(symbol: String, displayName: String): IndexQuote {
    val encoded = java.net.URLEncoder.encode(symbol, "UTF-8")
    val address = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=5d&interval=1d&includePrePost=false"
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "UprightStocks-Android/1.0")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(code in 200..299) { "$displayName data provider returned HTTP $code." }
        val root = JSONObject(body)
        val chart = root.optJSONObject("chart") ?: error("Invalid $displayName response.")
        val result = chart.optJSONArray("result")?.optJSONObject(0) ?: error("$displayName data is unavailable.")
        val timestamps = result.optJSONArray("timestamp") ?: error("$displayName dates are unavailable.")
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            ?: error("$displayName closing prices are unavailable.")
        val closes = quote.optJSONArray("close") ?: error("$displayName closing prices are unavailable.")
        val valid = (0 until minOf(timestamps.length(), closes.length())).mapNotNull { index ->
            if (closes.isNull(index)) null else {
                val value = closes.optDouble(index, Double.NaN)
                if (!value.isFinite() || value <= 0.0) null else Pair(timestamps.optLong(index), value)
            }
        }
        check(valid.size >= 2) { "Not enough daily data for $displayName yet." }
        val latest = valid[valid.lastIndex]
        val previous = valid[valid.lastIndex - 1]
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
        formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val date = formatter.format(java.util.Date(latest.first * 1000L))
        return IndexQuote(latest.second, previous.second, date)
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun SectorsScreen(stocks: List<Stock>, onOpen: (Stock) -> Unit) {
    val grouped = stocks.filter { sectorMap.containsKey(it.symbol) }.groupBy { sectorMap[it.symbol]!! }
        .map { (name, list) -> Triple(name, list, list.map { it.changePercent }.average()) }.sortedByDescending { it.third }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading("SECTOR PERFORMANCE", "Equal-weight average of selected mapped stocks")
        Text("Indicative calculation, not an official sector index.", color = Muted, fontSize = 10.sp)
        grouped.forEach { (name, members, average) ->
            Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%+.2f%%", average), color = if (average >= 0) Gain else Loss, fontWeight = FontWeight.Bold)
                    }
                    Text("${members.size} mapped stocks", color = Muted, fontSize = 10.sp)
                    members.sortedByDescending { it.changePercent }.take(4).forEach { stock ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(stock) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stock.symbol, color = White, fontSize = 11.sp)
                            Text(String.format(Locale.US, "%+.2f%%", stock.changePercent), color = if (stock.changePercent >= 0) Gain else Loss, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (stocks.isEmpty()) Text("Load market data to calculate sector changes.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PortfolioScreen(stocks: List<Stock>, holdings: List<PortfolioHolding>, onAdd: (String, Double, Double) -> Unit, onRemove: (String) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var average by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf("") }
    val value = holdings.sumOf { h -> (stocks.find { it.symbol == h.symbol }?.price ?: h.averagePrice) * h.quantity }
    val cost = holdings.sumOf { it.averagePrice * it.quantity }
    val pnl = value - cost
    val pnlPercent = if (cost > 0.0) pnl / cost * 100.0 else 0.0
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading("SIMULATED PORTFOLIO", "Practice tracker · no broker connection or real trades")
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Estimated portfolio value", color = Muted, fontSize = 10.sp)
                Text(formatRupees(value), color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Invested amount  ${formatRupees(cost)}", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text("Total P/L  ${String.format(Locale.US, "%+.2f", pnl)}  (${String.format(Locale.US, "%+.2f%%", pnlPercent)})",
                    color = if (pnl >= 0) Gain else Loss, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Text("PORTFOLIO ALLOCATION", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (holdings.isEmpty()) Text("Allocation will appear when you add holdings.", color = Muted, fontSize = 10.sp)
        holdings.sortedByDescending { h -> (stocks.find { it.symbol == h.symbol }?.price ?: h.averagePrice) * h.quantity }.forEach { h ->
            val currentValue = (stocks.find { it.symbol == h.symbol }?.price ?: h.averagePrice) * h.quantity
            val allocationPercent = if (value > 0.0) currentValue / value else 0.0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(h.symbol, color = White, fontSize = 10.sp, modifier = Modifier.width(90.dp))
                Box(Modifier.weight(1f).height(8.dp).background(PanelLight, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(allocationPercent.toFloat().coerceIn(0f, 1f)).height(8.dp).background(Mint, RoundedCornerShape(8.dp)))
                }
                Text(String.format(Locale.US, "%.1f%%", allocationPercent * 100.0), color = Muted, fontSize = 10.sp)
            }
        }
        Text("ADD / UPDATE HOLDING", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase(Locale.US).filter { c -> c.isLetterOrDigit() } ; validation = "" }, label = { Text("NSE symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = average, onValueChange = { average = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Average buy ₹") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        if (validation.isNotBlank()) Text(validation, color = Loss, fontSize = 11.sp)
        Button(onClick = {
            val q = qty.toDoubleOrNull() ?: 0.0
            val p = average.toDoubleOrNull() ?: 0.0
            val knownStock = stocks.any { it.symbol.equals(symbol.trim(), true) }
            when {
                symbol.isBlank() -> validation = "Enter a stock symbol."
                !knownStock -> validation = "Symbol not found in the loaded NSE stock list. Refresh market data first."
                q <= 0.0 || !q.isFinite() -> validation = "Enter a valid quantity greater than zero."
                p <= 0.0 || !p.isFinite() -> validation = "Enter a valid average buy price."
                else -> { onAdd(symbol.trim(), q, p); symbol = ""; qty = "1"; average = ""; validation = "" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save / update holding") }
        Text("YOUR HOLDINGS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (holdings.isEmpty()) Text("No holdings yet. Add a symbol from the loaded NSE list above.", color = Muted, fontSize = 11.sp)
        holdings.forEach { h ->
            val stock = stocks.find { it.symbol == h.symbol }
            val current = stock?.price ?: h.averagePrice
            val holdingValue = current * h.quantity
            val invested = h.averagePrice * h.quantity
            val holdingPnl = holdingValue - invested
            val holdingPercent = if (invested > 0.0) holdingPnl / invested * 100.0 else 0.0
            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.symbol, color = White, fontWeight = FontWeight.Bold)
                            Text("${h.quantity} shares · avg cost ${formatRupees(h.averagePrice)}", color = Muted, fontSize = 10.sp)
                        }
                        Text("Remove", color = Loss, modifier = Modifier.clickable { onRemove(h.symbol) }.padding(6.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("CURRENT VALUE", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(formatRupees(holdingValue), color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("UNREALISED P/L", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "%+.2f (%+.2f%%)", holdingPnl, holdingPercent),
                                color = if (holdingPnl >= 0) Gain else Loss, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (stock == null) Text("No current quote; value is estimated using average cost.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        Text("Educational record-keeping only. Values use latest available closing prices, not live quotes. No orders or investment recommendations.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun AlertsScreen(stocks: List<Stock>, alerts: List<PriceAlert>, marketDate: String, onAdd: (String, Double, Boolean, String) -> Unit, onRemove: (String, String) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("2") }
    var above by remember { mutableStateOf(true) }
    var alertKind by remember { mutableStateOf("Price") }
    var validation by remember { mutableStateOf("") }
    var histories by remember { mutableStateOf<Map<String, List<PricePoint>>>(emptyMap()) }
    val symbolsNeedingHistory = alerts.filter { it.kind in listOf("52W High", "52W Low", "Volume Spike") }.map { it.symbol }.distinct().sorted()
    LaunchedEffect(symbolsNeedingHistory, marketDate) {
        val loaded = histories.toMutableMap()
        symbolsNeedingHistory.filterNot { loaded.containsKey(it) }.take(20).forEach { item ->
            try { loaded[item] = StockRepository.fetchHistory(item) } catch (_: Exception) { loaded[item] = emptyList() }
        }
        histories = loaded
    }
    fun triggered(alert: PriceAlert, stock: Stock?): Boolean {
        if (stock == null) return false
        val history = histories[alert.symbol].orEmpty()
        return when (alert.kind) {
            "Daily %" -> if (alert.above) stock.changePercent >= alert.target else stock.changePercent <= -alert.target
            "52W High" -> history.isNotEmpty() && stock.price >= history.takeLast(252).maxOf { it.high }
            "52W Low" -> history.isNotEmpty() && stock.price <= history.takeLast(252).minOf { it.low }
            "Volume Spike" -> {
                val baseline = history.dropLast(1).takeLast(20).map { it.volume }.filter { it > 0 }.average()
                baseline > 0.0 && stock.volume >= baseline * alert.target
            }
            else -> if (alert.above) stock.price >= alert.target else stock.price <= alert.target
        }
    }
    val hitCount = alerts.count { triggered(it, stocks.find { stock -> stock.symbol == it.symbol }) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading("SMART ALERTS", "Conditions checked against available NSE end-of-day data")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("TOTAL ALERTS", alerts.size.toString(), Modifier.weight(1f))
            MetricCard("TRIGGERED", hitCount.toString(), Modifier.weight(1f), if (hitCount > 0) Gain else Mint)
        }
        Text(if (marketDate.isBlank()) "No market session loaded yet. Refresh the Market tab first." else "Last checked against the ${marketDate} closing session.", color = Muted, fontSize = 10.sp)
        Text("In-app checks only—not live alerts, push notifications, or trading signals.", color = Muted, fontSize = 10.sp)
        OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase(Locale.US).filter { c -> c.isLetterOrDigit() }; validation = "" }, label = { Text("NSE symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("ALERT CONDITION", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Price", "Daily %").forEach { SortChip(it, alertKind == it) { alertKind = it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("52W High", "52W Low", "Volume Spike").forEach { SortChip(it, alertKind == it) { alertKind = it } }
            }
        }
        if (alertKind in listOf("Price", "Daily %", "Volume Spike")) {
            OutlinedTextField(value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' }; validation = "" },
                label = { Text(when(alertKind) { "Price" -> "Target price ₹"; "Daily %" -> "Daily movement threshold %"; else -> "Volume multiple (e.g. 2)" }) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Uses the last 12 months of available daily OHLC history for this symbol.", color = Muted, fontSize = 10.sp)
        }
        if (alertKind in listOf("Price", "Daily %")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip("At or above", above) { above = true }
                SortChip("At or below", !above) { above = false }
            }
        }
        if (validation.isNotBlank()) Text(validation, color = Loss, fontSize = 11.sp)
        Button(onClick = {
            val cleanSymbol = symbol.trim()
            val value = if (alertKind in listOf("52W High", "52W Low")) 0.0 else target.toDoubleOrNull() ?: 0.0
            val known = stocks.any { it.symbol.equals(cleanSymbol, true) }
            when {
                cleanSymbol.isBlank() -> validation = "Enter an NSE symbol."
                !known -> validation = "Symbol not found. Refresh market data and use a listed symbol."
                alertKind !in listOf("52W High", "52W Low") && (!value.isFinite() || value <= 0.0) -> validation = "Enter a valid threshold greater than zero."
                alertKind == "Volume Spike" && value < 1.0 -> validation = "Volume multiple must be at least 1.0."
                else -> { onAdd(cleanSymbol, value, above, alertKind); symbol = ""; target = "2"; validation = "" }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = stocks.isNotEmpty()) { Text("Save alert") }
        Text("SAVED ALERTS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (alerts.isEmpty()) Text("No alerts yet. Add a condition above.", color = Muted, fontSize = 11.sp)
        alerts.forEach { alert ->
            val stock = stocks.find { it.symbol == alert.symbol }
            val history = histories[alert.symbol].orEmpty()
            val hit = triggered(alert, stock)
            val historyRequired = alert.kind in listOf("52W High", "52W Low", "Volume Spike")
            val noHistory = historyRequired && history.isEmpty()
            val description = when(alert.kind) {
                "Price" -> (if (alert.above) "At or above " else "At or below ") + formatRupees(alert.target)
                "Daily %" -> "Daily change " + (if (alert.above) "≥ " else "≤ -") + String.format(Locale.US, "%.2f%%", alert.target)
                "52W High" -> "52-week high breakout"
                "52W Low" -> "52-week low breakout"
                else -> "Volume ≥ " + String.format(Locale.US, "%.1fx", alert.target) + " 20-session average"
            }
            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(alert.symbol, color = White, fontWeight = FontWeight.Bold)
                            Text(description, color = Muted, fontSize = 10.sp)
                        }
                        Surface(color = (if (hit) Gain else Muted).copy(alpha = 0.14f), shape = RoundedCornerShape(18.dp)) {
                            Text(if (hit) "TRIGGERED" else if (stock == null) "NO QUOTE" else if (noHistory) "NO HISTORY" else "WAITING",
                                color = if (hit) Gain else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    if (stock == null) Text("No quote in the currently loaded market snapshot.", color = Muted, fontSize = 10.sp)
                    else {
                        Text("Latest close ${formatRupees(stock.price)} · daily move ${String.format(Locale.US, "%+.2f%%", stock.changePercent)} · volume ${String.format(Locale.US, "%,d", stock.volume)}",
                            color = White, fontSize = 10.sp)
                        if (noHistory) Text("Historical OHLCV data is not available to evaluate this condition.", color = Muted, fontSize = 10.sp)
                        else Text(if (hit) "Condition met on the latest EOD snapshot." else "Condition not met on the latest EOD snapshot.",
                            color = if (hit) Gain else Muted, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("Remove alert", color = Loss, modifier = Modifier.clickable { onRemove(alert.symbol, alert.kind) }.padding(top = 8.dp, start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistsScreen(
    stocks: List<Stock>,
    watchlist: Set<String>,
    names: List<String>,
    activeName: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onToggle: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val listStocks = stocks.filter { watchlist.contains(it.symbol) }.sortedBy { it.symbol }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading("MY WATCHLISTS", "Create separate lists for companies you follow")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            names.forEach { name -> SortChip(name, activeName == name) { onSelect(name) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newName, { newName = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("New watchlist name") })
            Button(onClick = { onCreate(newName); newName = "" }, enabled = newName.isNotBlank() && !names.contains(newName.trim())) { Text("Create") }
        }
        Text("${activeName} · ${listStocks.size} saved stocks · stored on this device", color = Muted, fontSize = 10.sp)
        if (stocks.isEmpty()) Text("Load market data to show current watchlist prices.", color = Muted, fontSize = 11.sp)
        else if (listStocks.isEmpty()) Text("This list is empty. Open Market and tap the star beside a stock to add it.", color = Muted, fontSize = 11.sp)
        listStocks.forEach { stock -> StockCard(stock, true, { onOpen(stock) }, { onToggle(stock.symbol) }) }
    }
}

@Composable
private fun ScreenerScreen(stocks: List<Stock>, onOpen: (Stock) -> Unit) {
    var query by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("All") }
    var minVolume by remember { mutableStateOf("0") }
    var sort by remember { mutableStateOf("Change") }
    val volumeFloor = minVolume.toLongOrNull() ?: 0L
    val results = stocks.filter { stock ->
        (stock.symbol.contains(query, true) || stock.company.contains(query, true)) &&
        stock.volume >= volumeFloor &&
        when (direction) { "Gainers" -> stock.changePercent > 0; "Losers" -> stock.changePercent < 0; else -> true }
    }.let { list -> when(sort) {
        "Price" -> list.sortedByDescending { it.price }
        "Volume" -> list.sortedByDescending { it.volume }
        "Symbol" -> list.sortedBy { it.symbol }
        else -> list.sortedByDescending { it.changePercent }
    } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading("STOCK SCREENER", "Filter the currently loaded NSE equity snapshot")
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Company or symbol") })
        OutlinedTextField(minVolume, { minVolume = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Minimum daily volume") })
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "Gainers", "Losers").forEach { SortChip(it, direction == it) { direction = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Change", "Price", "Volume", "Symbol").forEach { SortChip(it, sort == it) { sort = it } }
        }
        Text("${results.size} matching stocks · latest EOD snapshot", color = Muted, fontSize = 10.sp)
        results.take(150).forEach { stock -> StockCard(stock, false, { onOpen(stock) }, {}) }
        if (results.isEmpty()) Text("No stocks match these filters. Try a lower volume threshold.", color = Muted, fontSize = 11.sp)
        if (results.size > 150) Text("Showing first 150 results. Narrow your filters to see more.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ResearchScreen() {
    var symbol by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("RESEARCH CENTRE", "Company fundamentals and market news")
        OutlinedTextField(symbol, { symbol = it.uppercase(Locale.US).filter { c -> c.isLetterOrDigit() } },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search NSE company") })
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("COMPANY FUNDAMENTALS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("P/E, P/B, EPS, dividends, quarterly revenue and profit will appear here when a verified fundamentals provider is connected.", color = White, fontSize = 12.sp)
                Text("Status: Provider not configured · no financial ratios are fabricated.", color = Muted, fontSize = 10.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("MARKET & COMPANY NEWS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Verified headlines, publisher names, timestamps and source links will appear here after a news provider is connected.", color = White, fontSize = 12.sp)
                Text("Status: News feed not connected · no placeholder headlines.", color = Muted, fontSize = 10.sp)
            }
        }
        Text("For public distribution, fundamentals and news sources must grant the appropriate display and redistribution rights.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsScreen(light: Boolean, toggle: () -> Unit, date: String) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("SETTINGS", "Personalise Upright Stocks")
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Appearance", color = White, fontWeight = FontWeight.Bold)
                    Text(if (light) "Light theme" else "Dark theme", color = Muted, fontSize = 10.sp)
                }
                Button(onClick = toggle) { Text(if (light) "Use dark" else "Use light") }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Data source: TejHQ · NSE EOD bhavcopy", color = White, fontWeight = FontWeight.Bold)
                Text("Latest date: ${date.ifBlank { "Not loaded" }}", color = Muted, fontSize = 11.sp)
                Text("No live or intraday prices.", color = Muted, fontSize = 11.sp)
            }
        }
        Text("Educational market-data viewer. No broker connection or trade execution.", color = Muted, fontSize = 10.sp)
    }
}

private fun loadWatchlists(context: Context): Map<String, Set<String>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(WATCHLISTS_KEY, null)
    if (raw.isNullOrBlank()) return mapOf("Main" to loadWatchlist(context))
    return try {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = root.optJSONArray(key) ?: continue
                val symbols = buildSet {
                    for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
                put(key, symbols)
            }
        }.ifEmpty { mapOf("Main" to loadWatchlist(context)) }
    } catch (_: Exception) { mapOf("Main" to loadWatchlist(context)) }
}

private fun saveWatchlists(context: Context, lists: Map<String, Set<String>>) {
    val root = JSONObject()
    lists.forEach { (name, symbols) ->
        val array = org.json.JSONArray()
        symbols.sorted().forEach { array.put(it) }
        root.put(name, array)
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(WATCHLISTS_KEY, root.toString()).apply()
}

private fun loadPortfolio(context: Context): List<PortfolioHolding> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PORTFOLIO_KEY, null) ?: return emptyList()
    return try {
        val arr = org.json.JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sym = o.optString("symbol"); val q = o.optDouble("qty"); val p = o.optDouble("avg")
                if (sym.isNotBlank() && q > 0 && p > 0) add(PortfolioHolding(sym, q, p))
            }
        }
    } catch (_: Exception) { emptyList() }
}
private fun savePortfolio(context: Context, list: List<PortfolioHolding>) {
    val arr = org.json.JSONArray()
    list.forEach { arr.put(JSONObject().put("symbol", it.symbol).put("qty", it.quantity).put("avg", it.averagePrice)) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PORTFOLIO_KEY, arr.toString()).apply()
}
private fun loadAlerts(context: Context): List<PriceAlert> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(ALERTS_KEY, null) ?: return emptyList()
    return try {
        val arr = org.json.JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sym = o.optString("symbol"); val p = o.optDouble("target")
                if (sym.isNotBlank() && (p > 0 || o.optString("kind") in listOf("52W High", "52W Low"))) add(PriceAlert(sym, p, o.optBoolean("above", true), o.optString("kind", "Price")))
            }
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveAlerts(context: Context, list: List<PriceAlert>) {
    val arr = org.json.JSONArray()
    list.forEach { arr.put(JSONObject().put("symbol", it.symbol).put("target", it.target).put("above", it.above).put("kind", it.kind)) }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(ALERTS_KEY, arr.toString()).apply()
}

private fun loadWatchlist(context: Context): Set<String> {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(WATCHLIST_KEY, null)
    return saved?.toSet() ?: DEFAULT_WATCHLIST
}

private fun saveWatchlist(context: Context, symbols: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putStringSet(WATCHLIST_KEY, symbols.toSet()).apply()
}

private fun formatRupees(value: Double): String =
    "₹" + String.format(Locale.US, "%,.2f", value)