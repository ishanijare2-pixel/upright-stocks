package com.ishan.uprightstocks

import android.content.Context
import android.os.Bundle
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

private val Navy = Color(0xFF071B1C)
private val Panel = Color(0xFF102A2B)
private val PanelLight = Color(0xFF173738)
private val Mint = Color(0xFF70E0C0)
private val Muted = Color(0xFF9BB7B2)
private val Gain = Color(0xFF54D6A1)
private val Loss = Color(0xFFFF7777)
private val White = Color(0xFFF3FAF8)

private const val PREFS_NAME = "upright_stocks"
private const val WATCHLIST_KEY = "watchlist"
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

data class PricePoint(val date: String, val close: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Mint,
                    background = Navy,
                    surface = Panel,
                    onPrimary = Navy,
                    onBackground = White,
                    onSurface = White
                )
            ) {
                UprightStocksApp()
            }
        }
    }
}

@Composable
private fun UprightStocksApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stocks by remember { mutableStateOf(emptyList<Stock>()) }
    var watchlist by remember { mutableStateOf(loadWatchlist(context)) }
    var search by remember { mutableStateOf("") }
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
        saveWatchlist(context, watchlist)
    }

    LaunchedEffect(Unit) { refresh() }

    val gainers = stocks.filter { it.changePercent > 0 }.sortedByDescending { it.changePercent }.take(10)
    val losers = stocks.filter { it.changePercent < 0 }.sortedBy { it.changePercent }.take(10)
    val advancers = stocks.count { it.change > 0 }
    val decliners = stocks.count { it.change < 0 }
    val filtered = stocks.filter {
        it.symbol.contains(search, true) || it.company.contains(search, true)
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
                modifier = Modifier.fillMaxSize().background(Navy).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("UPRIGHT", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Stocks", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("INDIAN EQUITY MARKET", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    }
                    Button(onClick = { refresh() }, enabled = !loading) {
                        Text(if (loading) "Loading…" else "Refresh")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MetricCard("NSE STOCKS", if (stocks.isEmpty()) "—" else stocks.size.toString(), Modifier.weight(1f))
                    MetricCard("ADVANCERS", if (stocks.isEmpty()) "—" else advancers.toString(), Modifier.weight(1f), Gain)
                    MetricCard("DECLINERS", if (stocks.isEmpty()) "—" else decliners.toString(), Modifier.weight(1f), Loss)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (marketDate.isBlank()) "MARKET DATE · WAITING FOR DATA" else "NSE CLOSE · $marketDate",
                    color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                Text(message, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))

                TabRow(selectedTabIndex = selectedTab, containerColor = Navy, contentColor = Mint) {
                    listOf("Market", "Watchlist", "Movers").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
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
                            Text("\${filtered.size} matching stocks", color = Muted, fontSize = 11.sp)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.symbol }) { stock ->
                                    StockCard(stock = stock, onClick = { openStock(stock) })
                                }
                            }
                        }
                    }
                    1 -> {
                        if (stocks.isEmpty() && loading) LoadingState()
                        else if (stocks.isEmpty()) EmptyState(message, onRetry = { refresh() })
                        else if (watchlistStocks.isEmpty()) {
                            EmptyState("Your watchlist is empty. Open Market and tap a stock to view it, then add it to your watchlist.")
                        } else {
                            Text("\${watchlistStocks.size} saved symbols · stored on this device", color = Muted, fontSize = 11.sp)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(watchlistStocks, key = { it.symbol }) { stock ->
                                    StockCard(stock = stock, onClick = { openStock(stock) })
                                }
                            }
                        }
                    }
                    else -> {
                        if (stocks.isEmpty() && loading) LoadingState()
                        else if (stocks.isEmpty()) EmptyState(message, onRetry = { refresh() })
                        else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item { SectionHeading("TOP GAINERS", "Biggest percentage rises in the latest session") }
                                items(gainers, key = { "g-\${it.symbol}" }) { stock ->
                                    StockCard(stock = stock, onClick = { openStock(stock) })
                                }
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    SectionHeading("TOP LOSERS", "Biggest percentage falls in the latest session")
                                }
                                items(losers, key = { "l-\${it.symbol}" }) { stock ->
                                    StockCard(stock = stock, onClick = { openStock(stock) })
                                }
                            }
                        }
                    }
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
private fun StockCard(stock: Stock, onClick: () -> Unit) {
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
        Text("NSE closing price · \${stock.date}", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(18.dp))
        Button(onClick = onToggleWatchlist, modifier = Modifier.fillMaxWidth()) {
            Text(if (isWatched) "Remove from watchlist" else "＋ Add to watchlist")
        }
        Spacer(Modifier.height(20.dp))
        Text("PRICE HISTORY", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Daily closing prices · last 90 days", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(170.dp).padding(12.dp)) {
                when {
                    historyLoading -> CircularProgressIndicator(color = Mint, modifier = Modifier.align(Alignment.Center))
                    history.size >= 2 -> PriceChart(history)
                    else -> Text("Price history is not available right now.", color = Muted,
                        modifier = Modifier.align(Alignment.Center), fontSize = 12.sp)
                }
            }
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
        Text("Historical prices are unadjusted end-of-day data. They may not reflect later stock splits or bonuses.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PriceChart(points: List<PricePoint>) {
    val values = points.map { it.close }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.fillMaxSize()) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) 0f else size.width * index / (values.size - 1)
            val y = size.height - (((value - minValue) / range).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Mint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = Muted.copy(alpha = 0.25f),
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
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
        toCalendar.add(Calendar.DAY_OF_YEAR, -100)
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(toCalendar.time)
        val root = getJson("$BASE_URL/v1/ohlcv/nse/$symbol?from=$from&to=$to")
        val rows = root.optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val close = item.optDouble("close", 0.0)
                if (close > 0.0) add(PricePoint(item.optString("date", ""), close))
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
