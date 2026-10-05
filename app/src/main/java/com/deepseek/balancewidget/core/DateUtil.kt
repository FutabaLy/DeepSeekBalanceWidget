package com.deepseek.balancewidget.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间工具：一律使用北京时间（DeepSeek 计费时区）。
 */
object DateUtil {

    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun nowBeijing(): LocalDateTime = LocalDateTime.now(ZONE)

    fun formatTime(time: LocalDateTime): String = time.format(TIME_FORMATTER)

    fun formatDateTime(time: LocalDateTime): String = time.format(DATE_TIME_FORMATTER)

    /** 把 epoch 毫秒（本地时钟）格式化成北京时间文本。 */
    fun formatEpoch(epochMillis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZONE)
            .format(DATE_TIME_FORMATTER)

    /** 「x 秒前 / x 分钟前」相对时间描述。 */
    fun relativeFrom(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "尚未同步"
        val diff = (nowMillis - epochMillis).coerceAtLeast(0L) / 1000
        return when {
            diff < 5 -> "刚刚"
            diff < 60 -> "${diff}秒前"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            else -> "${diff / 86400}天前"
        }
    }
}
