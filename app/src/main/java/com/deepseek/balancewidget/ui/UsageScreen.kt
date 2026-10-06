package com.deepseek.balancewidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.data.DailyUsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UsageScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var history by remember { mutableStateOf(DailyUsageStore.History()) }
    var today by remember { mutableStateOf(DateUtil.nowBeijing().toLocalDate()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var rangeDays by remember { mutableIntStateOf(7) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            history = withContext(Dispatchers.IO) { DailyUsageStore.history(context) }
            today = DateUtil.nowBeijing().toLocalDate()
            loaded = true
            delay(1000)
        }
    }
    val blue = Color(0xFF394F99)
    val green = Color(0xFF2FA05A)
    val numberFont = remember { FontFamily(Font(R.font.nunito_black, FontWeight.Black)) }
    val symbol = if (history.currency == "USD") "$" else "¥"
    fun money(value: Long) = symbol + String.format(Locale.ROOT, "%.2f", DailyUsageStore.microToYuan(value))
    val days = ((rangeDays - 1).toLong() downTo 0L).map { today.minusDays(it).toString() }
    val rangeUsage = history.days.filterKeys { it >= days.first() && it <= days.last() }
    val peak = rangeUsage.maxByOrNull { it.value }
    val max = days.maxOf { history.days[it] ?: 0 }.coerceAtLeast(1)
    val zone = remember { ZoneId.of("Asia/Shanghai") }
    val eventsByDay = remember(history.events) { history.events.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate().toString() } }
    val rows = rangeUsage.entries.sortedByDescending { it.key }.filter { it.key.contains(query.trim()) && (selected == null || it.key == selected) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F6FF), contentColor = blue) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("API 用量记录", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text("返回") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30).forEach { count ->
                        FilterChip(
                            selected = rangeDays == count,
                            onClick = {
                                rangeDays = count
                                selected = null
                                query = ""
                            },
                            label = { Text("近${count}天") },
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("近${rangeDays}天已观测消费 · " + history.currency)
                        Text(money(rangeUsage.values.sum()), fontFamily = numberFont, fontSize = 36.sp, color = blue)
                        Text("今日 " + money(history.days[today.toString()] ?: 0) + "   累计 " + money(history.days.values.sum()))
                        Text(peak?.let { "本时段峰值 ${it.key} · ${money(it.value)}" } ?: "本时段暂无观测记录", fontSize = 12.sp)
                    }
                }
            }
            item {
                Text("近${rangeDays}天消费", fontWeight = FontWeight.Bold)
                Text(if (rangeDays == 7) "绿柱为今天；点击柱子查看金额。灰柱表示未观测。"
                    else "绿柱为今天；左右滑动，点击柱子查看金额。灰柱表示未观测。", fontSize = 12.sp)
                val selectedDay = selected
                val selectedAmount = selectedDay?.let { history.days[it] }
                Text(
                    text = if (selectedDay == null) "点击柱子查看当天消费"
                        else "$selectedDay · " + (selectedAmount?.let { money(it) } ?: "暂无观测记录"),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                key(rangeDays) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columnWidth = if (rangeDays == 7) maxWidth / 7 else 54.dp
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
                            days.forEach { day ->
                                val value = history.days[day]
                                Column(Modifier.width(columnWidth)
                                    .background(if (selected == day) blue.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selected = day; query = "" }.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.height(130.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                        Box(Modifier.width(26.dp).height(if (value == null || value == 0L) 2.dp else (120f * value / max).coerceAtLeast(3f).dp)
                                            .background(if (value == null) Color.LightGray else if (day == today.toString()) green else blue, RoundedCornerShape(3.dp)))
                                    }
                                    Text(day.substring(5), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("模型 / Token 用量", fontWeight = FontWeight.Bold)
                Text("余额接口未提供模型和 Token 明细，暂无法统计模型占比。", fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("近${rangeDays}天每日与观测明细", fontWeight = FontWeight.Bold)
                OutlinedTextField(query, { query = it; selected = null }, Modifier.fillMaxWidth(), label = { Text("搜索日期，如 10-06") }, singleLine = true)
                selected?.let { day ->
                    TextButton(onClick = { selected = null }) { Text("$day · 显示近${rangeDays}天") }
                }
            }
            if (rows.isEmpty()) item { Text(if (!loaded) "正在读取…" else if (history.days.isEmpty()) "暂无记录，请先保存 API Key 并刷新余额。" else "所选日期没有观测记录。") }
            items(rows, key = { it.key }) { row ->
                var expanded by remember(row.key) { mutableStateOf(false) }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp)) {
                            Text(row.key, Modifier.weight(1f))
                            Text(money(row.value) + if (expanded) "  ▾" else "  ▸", fontFamily = numberFont)
                        }
                        if (expanded) {
                            val events = eventsByDay[row.key].orEmpty()
                            Text("余额差额记录（非逐次 API 调用账单）", fontSize = 11.sp)
                            if (events.isEmpty()) Text("仅保留当日合计，暂无观测明细。", fontSize = 12.sp)
                            events.forEach { event ->
                                Text(Instant.ofEpochMilli(event.at).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "    " + money(event.micro), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Text("按北京时间统计余额下降，仅保留最近1000条差额明细，每日合计持续保留。充值不抵扣；离线、跨天及赠金到期会影响估算，无法还原安装前历史或同时发生的充值与消费。不同 Key、接口和币种分开保存，切回后恢复原记录。", fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
