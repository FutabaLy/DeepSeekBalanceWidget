package com.deepseek.balancewidget.core

import java.time.LocalDate

/**
 * 计费档位。
 */
enum class TariffTier(
    /** 桌面插件 / 通知里显示的短标签，例如「谷」「峰」。 */
    val shortLabel: String,
    /** 完整名称。 */
    val label: String,
    /** 一句话说明。 */
    val detail: String,
) {
    PEAK(
        shortLabel = "峰",
        label = "高峰时段",
        detail = "当前按高峰价计费（工作日 9:00-12:00 / 14:00-18:00，北京时间）",
    ),
    OFF_PEAK(
        shortLabel = "谷",
        label = "空闲时段",
        detail = "当前按空闲价计费，单价为高峰价的 5 折",
    ),
    ;

    val isPeak: Boolean get() = this == PEAK
}

/**
 * 判定用的「一天」上下文：今天是什么日子、今天的时间表是什么。
 */
data class DayContext(
    val date: LocalDate,
    val isWorkday: Boolean,
    val holidayName: String?,
) {
    val isHoliday: Boolean get() = holidayName != null && !isWorkday
    val isMakeupWorkday: Boolean get() = holidayName != null && isWorkday
}
