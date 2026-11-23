package com.ishan.uprightstocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Navy = Color(0xFF071B1C)
private val Panel = Color(0xFF102A2B)
private val Mint = Color(0xFF70E0C0)
private val Muted = Color(0xFF9BB7B2)
private val Gain = Color(0xFF54D6A1)
private val Loss = Color(0xFFFF7777)

data class Stock(
    val symbol: String,
    val company: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Mint,
                background = Navy,
                surface = Panel,
                onPrimary = Navy,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                UprightStocksScreen()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun UprightStocksScreen() {
    var stocks by remember { mutableStateOf(emptyList<Stock>()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("API not connected yet. Add your market-data backend URL to enable live updates.") }
    val scope = rememberCoroutineScope()

    suspend fun refreshStocks() {
        loading = true
        message = "Connecting to your market-data service…"
        try {
            stocks = StockRepository.fetchStocks()
            message = if (stocks.isEmpty()) "The API connected, but returned no stocks." else "Updated from your configured data service."
        } catch (e: Exception) {
            message = e.message ?: "Could not load stock data."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        // This will show a clear setup message until a real backend URL is configured.
        if (BuildConfig.API_BASE_URL.contains("YOUR-BACKEND")) {
            message = "Setup needed: connect a licensed NSE market-data provider through your backend."
        } else {
            refreshStocks()
        }
    }

    val filtered = stocks.filter {
        it.symbol.contains(query, ignoreCase = true) ||
            it.company.contains(query, ignoreCase = true)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(18.dp)
        ) {
            Text("UPRIGHT", color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Stocks", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("NSE MARKET DASHBOARD", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryCard("TRACKED", stocks.size.toString(), Modifier.weight(1f))
                SummaryCard("MARKET", if (stocks.isEmpty()) "—" else "NSE", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search symbol or company") }
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your watchlist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { scope.launch { refreshStocks() } },
                    enabled = !loading && !BuildConfig.API_BASE_URL.contains("YOUR-BACKEND")
                ) {
                    Text("Refresh")
                }
            }

            if (loading) {
                CircularProgressIndicator(color = Mint, modifier = Modifier.padding(16.dp))
            }

            Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))

            if (stocks.isEmpty()) {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("No market feed connected", color = Mint, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The app is ready for an API connection. We will add the provider after choosing a data plan that permits NSE prices to be displayed in an app.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(filtered, key = { it.symbol }) { stock ->
                        StockRow(stock)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Mint, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@androidx.compose.runtime.Composable
private fun StockRow(stock: Stock) {
    val changeColor = if (stock.change >= 0) Gain else Loss
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stock.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                Text(stock.company, color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹" + String.format(Locale.US, "%.2f", stock.price), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    String.format(Locale.US, "%+.2f  (%+.2f%%)", stock.change, stock.changePercent),
                    color = changeColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private object StockRepository {
    suspend fun fetchStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val base = BuildConfig.API_BASE_URL
        require(!base.contains("YOUR-BACKEND")) { "Set API_BASE_URL to your backend URL before refreshing." }

        val connection = (URL(base.trimEnd('/') + "/api/stocks").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            check(code in 200..299) { "Market-data service returned HTTP $code: $body" }

            val root = JSONObject(body)
            val array = root.getJSONArray("stocks")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Stock(
                            symbol = item.getString("symbol"),
                            company = item.optString("company", item.getString("symbol")),
                            price = item.getDouble("price"),
                            change = item.optDouble("change", 0.0),
                            changePercent = item.optDouble("changePercent", 0.0)
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
