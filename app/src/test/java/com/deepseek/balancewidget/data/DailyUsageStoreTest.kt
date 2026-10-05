package com.deepseek.balancewidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「今日已用」累计逻辑单测（纯 JVM，不碰 SharedPreferences）。
 *
 * 覆盖：跨天归零、首次无基准、连续扣费累加、充值不冲抵、余额原文字符串解析精度。
 */
class DailyUsageStoreTest {
    @Test
    fun historyPreservesPreviousDaysAndReplacesCurrentTotal() {
        val original = mapOf("2026-10-05" to 6_290_000L)
        val next = DailyUsageStore.updateDays(original, "2026-10-06", 1_000_000L)
        val updated = DailyUsageStore.updateDays(next, "2026-10-06", 3_360_000L)
        assertEquals(6_290_000L, updated["2026-10-05"])
        assertEquals(3_360_000L, updated["2026-10-06"])
        assertEquals(9_650_000L, updated.values.sum())
        assertEquals(1, original.size)
    }

    @Test
    fun overflowIsRejectedInsteadOfWrappingBalance() {
        assertNull(DailyUsageStore.toMicro("999999999999999999999999"))
    }

    @Test
    fun accountFingerprintChangesWithKeyOrEndpoint() {
        val first = DailyUsageStore.sourceId("key-a", "https://api.deepseek.com")
        assertEquals(first, DailyUsageStore.sourceId("key-a", "https://api.deepseek.com/"))
        org.junit.Assert.assertNotEquals(first, DailyUsageStore.sourceId("key-b", "https://api.deepseek.com"))
        org.junit.Assert.assertNotEquals(first, DailyUsageStore.sourceId("key-a", "https://other.example"))
    }


    @Test
    fun `跨天归零并重置基准`() {
        val (used, last) = DailyUsageStore.accumulate(
            sameDay = false,
            usedMicro = 9_990_000L,
            lastMicro = 10_000_000L,
            currentMicro = 20_000_000L,
        )
        assertEquals(0L, used)
        assertEquals(20_000_000L, last)
    }

    @Test
    fun `首次没有基准时只记录基准`() {
        val (used, last) = DailyUsageStore.accumulate(
            sameDay = true,
            usedMicro = 0L,
            lastMicro = null,
            currentMicro = 29_100_000L,
        )
        assertEquals(0L, used)
        assertEquals(29_100_000L, last)
    }

    @Test
    fun `连续扣费按差值累加`() {
        var used = 0L
        var last: Long? = 29_100_000L

        // 花掉 2.46 元
        var step = DailyUsageStore.accumulate(true, used, last, 26_640_000L)
        used = step.first
        last = step.second
        assertEquals(2_460_000L, used)

        // 再花 0.14 元
        step = DailyUsageStore.accumulate(true, used, last, 26_500_000L)
        used = step.first
        last = step.second
        assertEquals(2_600_000L, used)

        // 余额没变（中间几次刷新）
        step = DailyUsageStore.accumulate(true, used, last, 26_500_000L)
        used = step.first
        last = step.second
        assertEquals(2_600_000L, used)
    }

    @Test
    fun `充值只抬高基准不冲抵已用`() {
        // 今天已经花了 2.46 元，然后充了 100 元
        val (used, last) = DailyUsageStore.accumulate(
            sameDay = true,
            usedMicro = 2_460_000L,
            lastMicro = 26_640_000L,
            currentMicro = 126_640_000L,
        )
        assertEquals(2_460_000L, used)
        assertEquals(126_640_000L, last)

        // 充值后继续消耗，仍然在已用上继续加
        val next = DailyUsageStore.accumulate(true, used, last, 126_540_000L)
        assertEquals(2_560_000L, next.first)
    }

    @Test
    fun `余额原文字符串按微元精确解析`() {
        assertEquals(29_100_000L, DailyUsageStore.toMicro("29.100000"))
        assertEquals(1L, DailyUsageStore.toMicro("0.000001"))
        assertEquals(0L, DailyUsageStore.toMicro("0"))
        assertEquals(100_000_000L, DailyUsageStore.toMicro("100"))
        assertNull(DailyUsageStore.toMicro("abc"))
        assertNull(DailyUsageStore.toMicro(""))
        assertNull(DailyUsageStore.toMicro(null))
    }

    @Test
    fun `微元转元保留两位展示`() {
        assertEquals(2.46, DailyUsageStore.microToYuan(2_460_000L), 0.0000001)
        assertEquals(0.0, DailyUsageStore.microToYuan(0L), 0.0000001)
    }
}
