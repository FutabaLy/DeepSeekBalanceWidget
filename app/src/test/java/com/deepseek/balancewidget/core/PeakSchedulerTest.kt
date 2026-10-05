package com.deepseek.balancewidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 峰谷判定单元测试（纯 JVM，无需模拟器）。
 *
 * 覆盖：工作日、午间谷、周末、法定节假日、调休补班日、跨天/跨周末切换边界、倒计时格式化。
 */
class PeakSchedulerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpCalendar() {
            // 只注入测试用的少量日期，避免依赖 assets
            HolidayCalendar.apply(
                """
                {
                  "2026-10-01": { "h": true,  "n": "国庆节" },
                  "2026-10-02": { "h": true,  "n": "国庆节" },
                  "2026-10-03": { "h": true,  "n": "国庆节" },
                  "2026-10-04": { "h": true,  "n": "国庆节" },
                  "2026-10-05": { "h": true,  "n": "国庆节" },
                  "2026-10-06": { "h": true,  "n": "国庆节" },
                  "2026-10-07": { "h": true,  "n": "国庆节" },
                  "2026-10-10": { "h": false, "n": "国庆节后补班" }
                }
                """.trimIndent(),
            )
        }

        private fun at(date: String, time: String): LocalDateTime =
            LocalDateTime.parse("${date}T$time")
    }

    // ------------------------------------------------------------ 工作日判定

    @Test
    fun `法定节假日不算工作日`() {
        assertFalse(PeakScheduler.isWorkday(LocalDate.parse("2026-10-05")))
        assertFalse(PeakScheduler.isWorkday(LocalDate.parse("2026-10-06")))
    }

    @Test
    fun `调休补班的周六算工作日`() {
        assertTrue(PeakScheduler.isWorkday(LocalDate.parse("2026-10-10")))
    }

    @Test
    fun `普通周六不算工作日`() {
        assertFalse(PeakScheduler.isWorkday(LocalDate.parse("2026-10-17")))
    }

    @Test
    fun `普通周一算工作日`() {
        assertTrue(PeakScheduler.isWorkday(LocalDate.parse("2026-10-12")))
    }

    // ------------------------------------------------------------ 档位判定

    @Test
    fun `法定节假日全天为谷时`() {
        assertFalse(PeakScheduler.isPeak(at("2026-10-05", "10:00")))
        assertFalse(PeakScheduler.isPeak(at("2026-10-05", "15:00")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-05", "10:00")))
    }

    @Test
    fun `工作日上午高峰边界`() {
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "08:59")))
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-12", "09:00")))
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-12", "11:59")))
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-12", "12:00")))
        // 12:00 之后是午间谷
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "12:01")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "13:59")))
    }

    @Test
    fun `工作日下午高峰边界`() {
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-12", "14:00")))
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-12", "18:00")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "18:01")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "23:30")))
    }

    @Test
    fun `凌晨为谷时`() {
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-12", "03:00")))
    }

    @Test
    fun `周末全天为谷时`() {
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-17", "10:00")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-18", "15:00")))
    }

    @Test
    fun `调休补班的周六有峰谷之分`() {
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-10", "10:00")))
        assertEquals(TariffTier.OFF_PEAK, PeakScheduler.tierAt(at("2026-10-10", "13:00")))
    }

    @Test
    fun `谷时单价是峰时的一半`() {
        assertEquals(1.0, PeakScheduler.priceFactorAt(at("2026-10-12", "10:00")), 0.0001)
        assertEquals(0.5, PeakScheduler.priceFactorAt(at("2026-10-12", "13:00")), 0.0001)
    }

    // ------------------------------------------------------------ 切换边界

    @Test
    fun `工作日早八点切换到当天九点`() {
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-12", "08:00"))
        assertEquals(at("2026-10-12", "09:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `上午高峰切换到午间谷`() {
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-12", "10:00"))
        assertEquals(at("2026-10-12", "12:00"), boundary)
        assertEquals(TariffTier.OFF_PEAK, next)
    }

    @Test
    fun `午间谷切换到下午高峰`() {
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-12", "12:30"))
        assertEquals(at("2026-10-12", "14:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `下午高峰切换到下一个工作日九点`() {
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-12", "16:00"))
        assertEquals(at("2026-10-13", "09:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `周五晚高峰后跳到下周一`() {
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-16", "18:30"))
        assertEquals(at("2026-10-19", "09:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `节假日期间的下一次切换跳过整个假期`() {
        // 2026-10-01 ~ 10-07 放假，假期后第一个工作日是 10-08（周四）
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-05", "10:00"))
        assertEquals(at("2026-10-08", "09:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `假期结束后恢复峰谷且周六补班照常算工作日`() {
        // 10-08 周四、10-09 周五恢复正常峰谷；10-10 周六补班同样是工作日
        assertEquals(TariffTier.PEAK, PeakScheduler.tierAt(at("2026-10-08", "10:00")))
        val (boundary, next) = PeakScheduler.nextTransition(at("2026-10-09", "18:30"))
        assertEquals(at("2026-10-10", "09:00"), boundary)
        assertEquals(TariffTier.PEAK, next)
    }

    @Test
    fun `倒计时永不为负且指向未来`() {
        val times = listOf("00:00", "08:59", "09:00", "11:59", "12:00", "13:59", "14:00", "17:59", "18:00", "23:59")
        for (t in times) {
            val now = at("2026-10-12", t)
            val duration = PeakScheduler.timeUntilBoundary(now)
            assertTrue("$t 倒计时应为正数", duration.toMillis() > 0)
            assertTrue("$t 不应超过 10 天", duration.toMillis() <= 10L * 24 * 3600 * 1000)
        }
    }

    @Test
    fun `切换点前后的档位与切换后档位一致`() {
        val samples = listOf(
            at("2026-10-12", "08:00"),
            at("2026-10-12", "10:00"),
            at("2026-10-12", "12:30"),
            at("2026-10-12", "16:00"),
            at("2026-10-17", "22:00"),
            at("2026-10-05", "10:00"),
        )
        for (now in samples) {
            val (boundary, nextTier) = PeakScheduler.nextTransition(now)
            val after = boundary.plusSeconds(1)
            assertEquals("$now 切换后档位应一致", nextTier, PeakScheduler.tierAt(after))
            val before = boundary.minusSeconds(1)
            assertTrue("$now 切换前档位应与切换后相反", PeakScheduler.tierAt(before) != nextTier)
        }
    }

    // ------------------------------------------------------------ 文案

    @Test
    fun `倒计时格式化`() {
        assertEquals("00:05", PeakScheduler.formatCountdown(5_000))
        assertEquals("01:30", PeakScheduler.formatCountdown(90_000))
        assertEquals("01:00:00", PeakScheduler.formatCountdown(3_600_000))
        assertEquals("03:25:45", PeakScheduler.formatCountdown((3 * 3600 + 25 * 60 + 45) * 1000L))
        assertEquals("1天01小时01分", PeakScheduler.formatCountdown(90_061_000))
        assertEquals("00:00", PeakScheduler.formatCountdown(0))
        assertEquals("00:00", PeakScheduler.formatCountdown(-500))
    }

    @Test
    fun `紧凑版倒计时把天折算成小时`() {
        // 2 天 08 小时 53 分 12 秒 → 56:53:12（不出现「天」，窄卡片放得下）
        val twoDays = (2L * 86_400 + 8 * 3600 + 53 * 60 + 12) * 1000
        assertEquals("56:53:12", PeakScheduler.formatCountdownHours(twoDays))
        // 跨整个国庆假期（最多 10 天）也不会超过 3 位小时
        assertEquals("240:00:00", PeakScheduler.formatCountdownHours(10L * 86_400 * 1000))
        assertEquals("00:00:05", PeakScheduler.formatCountdownHours(5_000))
        assertEquals("00:00:00", PeakScheduler.formatCountdownHours(0))
        assertEquals("00:00:00", PeakScheduler.formatCountdownHours(-500))
        // 与「带天」的写法指向同一时刻（4×2 仍然用带天的版本）
        assertEquals("2天08小时53分", PeakScheduler.formatCountdown(twoDays))
    }

    @Test
    fun `档位标签可用于插件徽章`() {
        assertEquals("峰", TariffTier.PEAK.shortLabel)
        assertEquals("谷", TariffTier.OFF_PEAK.shortLabel)
        assertTrue(TariffTier.PEAK.isPeak)
        assertFalse(TariffTier.OFF_PEAK.isPeak)
    }
}
