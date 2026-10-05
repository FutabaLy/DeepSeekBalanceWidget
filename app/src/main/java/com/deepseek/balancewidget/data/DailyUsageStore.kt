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
    fun onBalance(context: Context, totalBalance: String?): Long {
        val current = toMicro(totalBalance) ?: return currentUsed(context)?.usedMicro ?: 0L
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayKey()

        val sameDay = prefs.getString(KEY_DAY, null) == today
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
        prefs.edit {
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

    private fun todayKey(): String = DateUtil.nowBeijing().toLocalDate().toString()

    /** "29.100000" → 29100000（微元）。 */
    internal fun toMicro(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            BigDecimal(text).movePointRight(6).setScale(0, RoundingMode.HALF_UP).toLong()
        }.getOrNull()
    }
}
