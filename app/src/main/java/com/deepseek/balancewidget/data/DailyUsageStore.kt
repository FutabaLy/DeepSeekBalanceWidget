package com.deepseek.balancewidget.data

import android.content.Context
import androidx.core.content.edit
import com.deepseek.balancewidget.core.DateUtil
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 「今日已用」统计。
 *
 * 余额接口只返回「当前余额」，没有流水，所以这里用**相邻两次成功刷新的差值**累计：
 * - 余额变小 → 差额计入今日已用；
 * - 余额变大 → 视为充值，只把基准抬高，不冲抵已经花掉的额度；
 * - 北京时间跨天后归零重算。
 *
 * 金额一律用「微元」（1e-6 元，接口正好返回 6 位小数）以 Long 存取，避免 Double 累加出现误差。
 * 只在服务/兜底任务成功刷新时调用，其余地方只读。
 */
object DailyUsageStore {
    val refreshMutex = kotlinx.coroutines.sync.Mutex()

    private const val PREFS = "deepseek_daily_usage"
    private const val KEY_DAY = "day"
    private const val KEY_USED_MICRO = "used_micro"
    private const val KEY_LAST_MICRO = "last_micro"

    /** 当日累计消耗（微元）。 */
    data class Usage(val day: String, val usedMicro: Long)

    /**
     * 用一次成功刷新的余额累计今日消耗。
     *
     * @param totalBalance 接口返回的 total_balance 原文（形如 "29.100000"），解析失败时只读不改。
     * @return 累计后的今日已用（微元）。
     */
    @Synchronized
    fun onBalance(context: Context, totalBalance: String?, source: String = "", currency: String = "CNY"): Long {
        val current = toMicro(totalBalance) ?: return currentUsed(context)?.usedMicro ?: 0L
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selectSource(prefs, source, currency)
        val today = todayKey()

        val previousDay = prefs.getString(KEY_DAY, null)
        val sameSource = prefs.getString("source", source) == source && prefs.getString("currency", currency) == currency
        val sameDay = previousDay == today && sameSource
        val history = readDays(prefs)
        if (previousDay != null && sameSource && previousDay !in history) {
            history[previousDay] = prefs.getLong(KEY_USED_MICRO, 0L)
        }
        if (!sameSource) history.clear()
        val lastMicro = if (sameDay && prefs.contains(KEY_LAST_MICRO)) {
            prefs.getLong(KEY_LAST_MICRO, current)
        } else {
            null
        }
        val (used, last) = accumulate(
            sameDay = sameDay,
            usedMicro = prefs.getLong(KEY_USED_MICRO, 0L),
            lastMicro = lastMicro,
            currentMicro = current,
        )
        val events = if (sameSource) org.json.JSONArray(prefs.getString("events", "[]")) else org.json.JSONArray()
        val delta = if (lastMicro != null) (lastMicro - current).coerceAtLeast(0) else 0L
        if (delta > 0) events.put(org.json.JSONObject().put("at", System.currentTimeMillis()).put("micro", delta))
        val retained = org.json.JSONArray()
        for (i in maxOf(0, events.length() - 1000) until events.length()) retained.put(events.get(i))
        val updatedDays = updateDays(history, today, used)
        prefs.edit {
            putString("source", source)
            putString("currency", currency)
            putString("days", org.json.JSONObject(updatedDays as Map<*, *>).toString())
            putString("events", retained.toString())
            putString(KEY_DAY, today)
            putLong(KEY_USED_MICRO, used)
            putLong(KEY_LAST_MICRO, last)
        }
        return used
    }

    /**
     * 累计逻辑（纯函数，便于单测）：
     * - 跨天或没有基准余额 → 归零重算；
     * - 余额变小 → 差额计入已用；
     * - 余额变大 → 视为充值，只把基准抬高，不冲抵已经花掉的额度。
     *
     * @return first = 新的今日已用（微元），second = 新的基准余额（微元）
     */
    internal fun accumulate(
        sameDay: Boolean,
        usedMicro: Long,
        lastMicro: Long?,
        currentMicro: Long,
    ): Pair<Long, Long> {
        if (!sameDay || lastMicro == null) return 0L to currentMicro
        val spent = if (currentMicro < lastMicro) lastMicro - currentMicro else 0L
        return (usedMicro + spent) to currentMicro
    }

    /** 只读当前值；从没统计过返回 null，跨天还没刷新过按 0 返回。 */
    fun currentUsed(context: Context): Usage? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = prefs.getString(KEY_DAY, null) ?: return null
        val today = todayKey()
        if (day != today) return Usage(today, 0L)
        return Usage(day, prefs.getLong(KEY_USED_MICRO, 0L))
    }

    /** 今日已用（元），界面直接用。 */
    fun usedTodayYuan(context: Context): Double? =
        currentUsed(context)?.let { microToYuan(it.usedMicro) }

    fun microToYuan(micro: Long): Double = micro / 1_000_000.0

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    private fun selectSource(prefs: android.content.SharedPreferences, source: String, currency: String) {
        val oldSource = prefs.getString("source", null) ?: return // Upgrade the legacy ledger in place.
        val oldCurrency = prefs.getString("currency", "CNY") ?: "CNY"
        if (oldSource == source && oldCurrency == currency) return
        val keys = listOf(KEY_DAY, KEY_USED_MICRO, KEY_LAST_MICRO, "days", "events", "source", "currency")
        val archive = org.json.JSONObject()
        keys.forEach { key -> prefs.all[key]?.let { archive.put(key, it) } }
        val restored = org.json.JSONObject(prefs.getString("archive_" + source + "_" + currency + "", "{}"))
        prefs.edit {
            putString("archive_" + oldSource + "_" + oldCurrency + "", archive.toString())
            keys.forEach { remove(it) }
            restored.keys().forEach { key ->
                if (key == KEY_USED_MICRO || key == KEY_LAST_MICRO) putLong(key, restored.getLong(key))
                else putString(key, restored.getString(key))
            }
            putString("source", source)
            putString("currency", currency)
        }
    }

    internal fun updateDays(days: Map<String, Long>, day: String, used: Long): Map<String, Long> =
        days + (day to used)

    data class Event(val at: Long, val micro: Long)
    data class History(val days: Map<String, Long> = emptyMap(), val events: List<Event> = emptyList(), val currency: String = "CNY")

    private fun readDays(prefs: android.content.SharedPreferences): MutableMap<String, Long> {
        val json = org.json.JSONObject(prefs.getString("days", "{}"))
        return json.keys().asSequence().associateWith { json.getLong(it) }.toMutableMap()
    }

    @Synchronized
    fun history(context: Context): History {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val days = readDays(prefs)
        prefs.getString(KEY_DAY, null)?.let { days[it] = prefs.getLong(KEY_USED_MICRO, 0L) }
        val events = org.json.JSONArray(prefs.getString("events", "[]"))
        return History(days.toSortedMap(), (0 until events.length()).map {
            val row = events.getJSONObject(it)
            Event(row.getLong("at"), row.getLong("micro"))
        }.reversed(), prefs.getString("currency", "CNY") ?: "CNY")
    }

    /** Hash only; never persist an API key in usage records. */
    fun sourceId(key: String, baseUrl: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest((baseUrl.trimEnd('/') + "|" + key).toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun todayKey(): String = DateUtil.nowBeijing().toLocalDate().toString()

    /** "29.100000" → 29100000（微元）。 */
    internal fun toMicro(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            BigDecimal(text).movePointRight(6).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }
}
