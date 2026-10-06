package com.deepseek.balancewidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保活引导的品牌匹配单测（纯 JVM）。
 *
 * 覆盖：主流国产 ROM、三星、类原生、以及认不出来时的兜底，
 * 保证任何机型都能拿到「非空、有条目」的引导。
 */
class KeepAliveGuideTest {

    @Test
    fun `小米系机型给 MIUI 路径`() {
        val guide = KeepAliveGuide.guideFor("Xiaomi", "Redmi")
        assertTrue(guide.brand, guide.brand.contains("小米"))
        assertTrue(guide.steps.any { it.contains("省电策略") })
    }

    @Test
    fun `华为与荣耀都走 EMUI 路径`() {
        for (manufacturer in listOf("HUAWEI", "HONOR")) {
            val guide = KeepAliveGuide.guideFor(manufacturer, manufacturer)
            assertTrue(manufacturer, guide.brand.contains("华为") || guide.brand.contains("荣耀"))
            assertTrue(guide.steps.any { it.contains("应用启动管理") })
        }
    }

    @Test
    fun `vivo 与 iQOO 都走 OriginOS 路径`() {
        // iQOO 的 Build.MANUFACTURER 是 vivo，BRAND 才是 iQOO
        val guide = KeepAliveGuide.guideFor("vivo", "iQOO")
        assertTrue(guide.brand, guide.brand.contains("vivo"))
        assertTrue(guide.steps.any { it.contains("后台高耗电") })
    }

    @Test
    fun `OPPO 系与三星各有自己的路径`() {
        val oppo = KeepAliveGuide.guideFor("OPPO", "OnePlus")
        assertTrue(oppo.brand, oppo.brand.contains("OPPO"))
        assertTrue(oppo.steps.any { it.contains("自启动") })

        val samsung = KeepAliveGuide.guideFor("samsung", "samsung")
        assertTrue(samsung.brand, samsung.brand.contains("三星"))
        assertTrue(samsung.steps.any { it.contains("休眠") })
    }

    @Test
    fun `认不出来的机型给通用兜底且不为空`() {
        for (input in listOf(null to null, "" to "", "SomeBrand" to "Unknown")) {
            val guide = KeepAliveGuide.guideFor(input.first, input.second)
            assertTrue("brand 不应为空", guide.brand.isNotBlank())
            assertTrue("至少要有一条操作", guide.steps.size >= 3)
            assertTrue("步骤不应为空字符串", guide.steps.none { it.isBlank() })
        }
    }

    @Test
    fun `同一厂商名大小写不同结果一致`() {
        assertEquals(
            KeepAliveGuide.guideFor("XIAOMI", "XIAOMI").brand,
            KeepAliveGuide.guideFor("xiaomi", "xiaomi").brand,
        )
    }
}
