package com.deepseek.balancewidget.core

import com.deepseek.balancewidget.data.BalanceInfo

/**
 * 插件 / 通知里要展示的全部内容。由 [com.deepseek.balancewidget.service.BalanceService] 持续刷新。
 */
data class WidgetState(
    /** 当前生效档位（可能被用户手动覆盖）。 */
    val tier: TariffTier = TariffTier.OFF_PEAK,
    /** 是否处于「自动」判定（不是手动覆盖）。 */
    val autoTier: Boolean = true,
    /** 下一次档位切换后的档位。 */
    val nextTier: TariffTier = TariffTier.PEAK,
    /** 距离下一次档位切换的毫秒数。 */
    val countdownMillis: Long = 0L,
    /** 今天是否是工作日 / 节假日名称。 */
    val dayContext: DayContext? = null,

    /** 余额（未同步成功时为 null）。 */
    val balance: BalanceInfo? = null,
    /** 账户是否还有余额可用。 */
    val isAvailable: Boolean = true,
    /** 是否曾经同步成功过。 */
    val hasData: Boolean = false,

    /** 上次成功同步的时刻（epoch 毫秒）。 */
    val lastSuccessAt: Long = 0L,
    /** 最近一次错误信息，成功时清空。 */
    val lastError: String? = null,
    /** 是否正在请求中。 */
    val loading: Boolean = false,
    /** 已连续失败次数。 */
    val failureCount: Int = 0,
    /** 后台服务是否在运行。 */
    val serviceRunning: Boolean = false,
) {
    /** 大号余额文本，例如「¥ 109.29」。 */
    val balanceText: String
        get() = balance?.let { "${it.currencySymbol}${it.totalBalance}" } ?: "--"

    /** 「距谷时 / 距高峰」标签，取决于下一阶段是什么。 */
    val countdownLabel: String
        get() = if (nextTier.isPeak) "距高峰" else "距谷时"

    /** 倒计时文本。 */
    fun countdownText(showSeconds: Boolean): String {
        val ms = countdownMillis
        return if (showSeconds) PeakScheduler.formatCountdown(ms) else formatMinutes(ms)
    }

    /** 状态行：正常显示同步时间，异常显示错误。 */
    fun statusText(): String = when {
        lastError != null -> lastError
        loading && !hasData -> "正在同步…"
        lastSuccessAt > 0 -> "${DateUtil.relativeFrom(lastSuccessAt)}同步"
        else -> "等待首次同步"
    }

    /** 节假日/补班说明，例如「国庆节 休」「春节后补班」。 */
    fun dayBadge(): String? {
        val ctx = dayContext ?: return null
        val name = ctx.holidayName ?: return null
        return if (ctx.isWorkday) "$name 班" else "$name 休"
    }

    private fun formatMinutes(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        return if (h > 0) "${h}小时${m}分" else "${m}分"
    }

    companion object {
        fun empty(): WidgetState = WidgetState(
            tier = PeakScheduler.tierAt(DateUtil.nowBeijing()),
            autoTier = true,
            dayContext = HolidayCalendar.dayContext(),
        )
    }
}
