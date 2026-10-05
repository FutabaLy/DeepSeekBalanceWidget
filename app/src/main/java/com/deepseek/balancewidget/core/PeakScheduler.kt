package com.deepseek.balancewidget.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * DeepSeek 峰谷时段判定。
 *
 * 官方规则（https://api-docs.deepseek.com/zh-cn/quick_start/pricing）：
 * 「空闲时段价格为高峰时段价格的一半。北京时间周一至周五（不含中国法定节假日）
 *   9:00 - 12:00、14:00 - 18:00 为高峰时段；其余时段，包括周末及中国法定节假日全天均为空闲时段。」
 *
 * 所以判定顺序为：法定节假日 → 全天谷；周末（未被调休补班覆盖）→ 全天谷；
 * 工作日 → 9:00-12:00、14:00-18:00 为峰，其余为谷。
 */
object PeakScheduler {

    /** DeepSeek 计费一律以北京时间为准，与手机系统时区无关。 */
    val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

    val PEAK_WINDOWS: List<ClosedRange<LocalTime>> = listOf(
        LocalTime.of(9, 0)..LocalTime.of(12, 0),
        LocalTime.of(14, 0)..LocalTime.of(18, 0),
    )

    /** 判定某一时刻是否处于高峰时段。 */
    fun isPeak(time: LocalDateTime): Boolean {
        val date = time.toLocalDate()
        if (!isWorkday(date)) return false
        val t = time.toLocalTime()
        return PEAK_WINDOWS.any { t >= it.start && t <= it.endInclusive }
    }

    /** 当前档位。 */
    fun tierAt(time: LocalDateTime): TariffTier = if (isPeak(time)) TariffTier.PEAK else TariffTier.OFF_PEAK

    /** 下一个档位切换时刻（严格大于 [time]）。 */
    fun nextBoundary(time: LocalDateTime): LocalDateTime = transitionFrom(time).first

    /**
     * 下一次档位切换。
     * @return first = 切换时刻，second = 切换后所处的档位
     */
    fun nextTransition(time: LocalDateTime): Pair<LocalDateTime, TariffTier> = transitionFrom(time)

    /** 距离下一次档位切换的剩余时间，最小为 0。 */
    fun timeUntilBoundary(time: LocalDateTime): Duration {
        val d = Duration.between(time, nextBoundary(time))
        return if (d.isNegative) Duration.ZERO else d
    }

    /** 峰时单价倍率：谷时是峰时的一半。 */
    fun priceFactorAt(time: LocalDateTime): Double =
        if (isPeak(time)) 1.0 else 0.5

    private fun transitionFrom(from: LocalDateTime): Pair<LocalDateTime, TariffTier> {
        val date = from.toLocalDate()
        val t = from.toLocalTime()

        if (!isWorkday(date)) {
            // 全天谷：下一次切换 = 下一个工作日 9:00 进入高峰
            val next = nextWorkdayAfter(date)
            return LocalDateTime.of(next, PEAK_WINDOWS.first().start) to TariffTier.PEAK
        }

        // 工作日
        if (t < PEAK_WINDOWS[0].start) {
            // 谷 → 9:00 进入高峰
            return LocalDateTime.of(date, PEAK_WINDOWS[0].start) to TariffTier.PEAK
        }
        if (t < PEAK_WINDOWS[0].endInclusive) {
            // 峰 → 12:00 进入午间谷
            return LocalDateTime.of(date, PEAK_WINDOWS[0].endInclusive) to TariffTier.OFF_PEAK
        }
        if (t < PEAK_WINDOWS[1].start) {
            // 谷 → 14:00 进入高峰
            return LocalDateTime.of(date, PEAK_WINDOWS[1].start) to TariffTier.PEAK
        }
        if (t < PEAK_WINDOWS[1].endInclusive) {
            // 峰 → 18:00 进入谷，直到下一个工作日 9:00
            val next = nextWorkdayAfter(date)
            return LocalDateTime.of(next, PEAK_WINDOWS[0].start) to TariffTier.PEAK
        }
        // 18:00 之后
        val next = nextWorkdayAfter(date)
        return LocalDateTime.of(next, PEAK_WINDOWS[0].start) to TariffTier.PEAK
    }

    /** 该日是否按「工作日」计费（法定节假日不算，调休补班的周末算）。 */
    fun isWorkday(date: LocalDate): Boolean {
        val holiday = HolidayCalendar.holidayOf(date)
        if (holiday != null) return !holiday
        return date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }

    /** 严格晚于 [date] 的第一个工作日。 */
    fun nextWorkdayAfter(date: LocalDate): LocalDate {
        var d = date.plusDays(1)
        var guard = 0
        while (!isWorkday(d)) {
            d = d.plusDays(1)
            if (++guard > 400) break // 防御：日历异常时避免死循环
        }
        return d
    }

    /** 把毫秒格式化为「x天xx小时xx分xx秒」/「xx小时xx分xx秒」/「xx分xx秒」/「xx秒」。 */
    fun formatCountdown(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val d = total / 86_400
        val h = total % 86_400 / 3_600
        val m = total % 3_600 / 60
        val s = total % 60
        return when {
            d > 0 -> "${d}天${two(h)}小时${two(m)}分"
            h > 0 -> "${two(h)}:${two(m)}:${two(s)}"
            else -> "${two(m)}:${two(s)}"
        }
    }

    private fun two(v: Long): String = if (v < 10) "0$v" else v.toString()
}
