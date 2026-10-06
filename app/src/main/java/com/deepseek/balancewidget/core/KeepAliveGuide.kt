package com.deepseek.balancewidget.core

import android.os.Build

/**
 * 「怎么让系统别把本应用冻结」的分品牌引导。
 *
 * 这个插件靠前台服务维持秒级刷新，而各家 ROM 的后台策略差别很大
 * （华为、vivo 的清理力度其实比 MIUI 还猛），菜单路径也各不相同，
 * 所以这里按 [Build.MANUFACTURER] / [Build.BRAND] 判断机型，给对应的操作步骤。
 *
 * 匹配逻辑抽成了纯函数 [guideFor]，不依赖真机也能单测。
 */
object KeepAliveGuide {

    /** 某个品牌的保活说明。 */
    data class Guide(
        /** 显示名，例如「小米 / 红米（MIUI / HyperOS）」 */
        val brand: String,
        /** 该品牌要做的几条设置，顺序即操作顺序 */
        val steps: List<String>,
    )

    /** 当前设备的引导。 */
    fun current(): Guide = guideFor(Build.MANUFACTURER, Build.BRAND)

    /** 按厂商 / 品牌名匹配；认不出来就给一套通用说明，不会崩也不会空。 */
    fun guideFor(manufacturer: String?, brand: String?): Guide {
        val text = listOfNotNull(manufacturer, brand)
            .joinToString(" ")
            .lowercase()

        fun matches(vararg names: String) = names.any { text.contains(it) }

        return when {
            matches("xiaomi", "redmi", "poco") -> Guide(
                brand = "小米 / 红米（MIUI / HyperOS）",
                steps = listOf(
                    "省电策略设为「无限制」",
                    "允许「自启动」",
                    "最近任务里给本应用加锁，避免被一键清理",
                ),
            )

            matches("huawei", "honor", "harmony") -> Guide(
                brand = "华为 / 荣耀（EMUI / MagicOS）",
                steps = listOf(
                    "应用启动管理 → 本应用改为「手动管理」，自启动 / 关联启动 / 后台活动全部允许",
                    "电池 → 更多电池设置 → 关闭「休眠时始终保持网络连接」的限制，或把本应用设为「不受限制」",
                    "最近任务里给本应用加锁（下拉卡片加锁图标）",
                ),
            )

            matches("vivo", "iqoo") -> Guide(
                brand = "vivo / iQOO（OriginOS）",
                steps = listOf(
                    "后台高耗电 → 允许本应用",
                    "自启动 → 允许",
                    "电池 → 后台耗电管理 → 允许后台运行",
                ),
            )

            matches("oppo", "oneplus", "realme", "one plus") -> Guide(
                brand = "OPPO / 一加 / realme（ColorOS）",
                steps = listOf(
                    "应用管理 → 本应用 → 允许「自启动」与「关联启动」",
                    "耗电管理 → 允许完全后台行为 / 允许后台运行",
                    "最近任务里给本应用加锁",
                ),
            )

            matches("samsung") -> Guide(
                brand = "三星（One UI）",
                steps = listOf(
                    "电池 → 后台使用限制 → 把本应用加入「永不自动休眠的应用」",
                    "关闭「使未使用的应用进入休眠」对本应用的影响",
                    "关闭本应用的「自适应电池」限制",
                ),
            )

            matches("meizu") -> Guide(
                brand = "魅族（Flyme）",
                steps = listOf(
                    "手机管家 → 权限管理 → 后台管理 → 允许后台运行",
                    "设置 → 应用管理 → 本应用 → 允许自启动",
                    "最近任务里给本应用加锁",
                ),
            )

            matches("google", "nothing", "sony", "motorola", "asus", "lenovo", "nokia", "zte") -> Guide(
                brand = "原生 / 类原生系统",
                steps = listOf(
                    "电池 → 不受限制（Unrestricted）",
                    "允许后台活动（部分系统在应用信息页里）",
                    "最近任务里给本应用加锁（若有该功能）",
                ),
            )

            else -> Guide(
                brand = "本机系统",
                steps = listOf(
                    "允许「自启动」/「后台运行」",
                    "电池或省电设置里把本应用改为「不限制」",
                    "最近任务里给本应用加锁（若有该功能）",
                ),
            )
        }
    }
}
