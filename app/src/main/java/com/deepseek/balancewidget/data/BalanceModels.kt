package com.deepseek.balancewidget.data

/**
 * DeepSeek `/user/balance` 返回的单个币种余额。
 * 官方文档：https://api-docs.deepseek.com/zh-cn/api/get-user-balance
 */
data class BalanceInfo(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
) {
    /** 总额转 Double，仅用于展示排序/判断，失败返回 null。 */
    val totalAsDouble: Double? get() = totalBalance.toDoubleOrNull()

    val currencySymbol: String
        get() = when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> "$currency "
        }
}

/**
 * 一次查询的完整结果。
 */
data class BalanceSnapshot(
    val isAvailable: Boolean,
    val infos: List<BalanceInfo>,
) {
    /** 优先返回人民币余额，其次第一条。 */
    val primary: BalanceInfo?
        get() = infos.firstOrNull { it.currency.equals("CNY", ignoreCase = true) } ?: infos.firstOrNull()

    val totalText: String
        get() = primary?.let { "${it.currencySymbol}${it.totalBalance}" } ?: "--"
}
