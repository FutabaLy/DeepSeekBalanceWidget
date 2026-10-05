package com.deepseek.balancewidget.widget

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.WidgetState

/**
 * 把 [WidgetState] 渲染成桌面插件 / 通知的 RemoteViews。
 *
 * 配色约定：
 * - 空闲（谷）：青绿 #12D6A0，卡片偏冷蓝黑
 * - 高峰（峰）：琥珀 #FFB020，卡片偏暖黑
 *
 * 卡片底色、顶部标题带、峰谷徽章三者都是 drawable（见 res/drawable/bg_widget_*、
 * bg_header_*、bg_badge_*），这里只负责按档位切换资源，保证圆角与分层不会被抹掉。
 */
object WidgetRenderer {

    // 配色
    private const val COLOR_PEAK = 0xFFFFB020.toInt()
    private const val COLOR_OFF_PEAK = 0xFF12D6A0.toInt()
    private const val COLOR_TEXT_TITLE = 0xFF8FA6C0.toInt()
    private const val COLOR_TEXT_SYNC = 0xFF7C90A8.toInt()
    private const val COLOR_TEXT_MAIN = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SUB = 0xFFC6D6E8.toInt()
    private const val COLOR_TEXT_ERROR = 0xFFFF8A80.toInt()

    const val ACTION_REFRESH = "com.deepseek.balancewidget.action.REFRESH"
    const val ACTION_TOGGLE_TIER = "com.deepseek.balancewidget.action.TOGGLE_TIER"

    fun build(
        context: Context,
        state: WidgetState,
        showSeconds: Boolean = true,
        intervalSeconds: Int = 5,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        val accent = if (state.tier.isPeak) COLOR_PEAK else COLOR_OFF_PEAK
        val peak = state.tier.isPeak

        // 背景分三层，全部用 setBackgroundResource 切换（View.setBackgroundResource 带
        // @RemotableViewMethod，任何 Android 版本都支持，也不需要 API 31 的 setColorStateList）：
        //   1. 卡片底 —— 18dp 圆角 + 渐变 + 1dp 描边
        //   2. 顶部标题带 —— 比卡片亮一档，做出背景分层
        //   3. 峰/谷徽章 —— 胶囊底色
        // 千万不要改回 setInt(..., "setBackgroundColor", ...)：那会把 shape 换成纯色 ColorDrawable，
        // 圆角、渐变、描边全部丢失，插件就变成一块方方正正的色块。
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (peak) R.drawable.bg_widget_peak else R.drawable.bg_widget_off_peak,
        )
        views.setInt(
            R.id.widget_header,
            "setBackgroundResource",
            if (peak) R.drawable.bg_header_peak else R.drawable.bg_header_off_peak,
        )
        views.setInt(
            R.id.tv_tier,
            "setBackgroundResource",
            if (peak) R.drawable.bg_badge_peak else R.drawable.bg_badge_off_peak,
        )
        views.setTextColor(R.id.tv_tier, accent)
        views.setTextViewText(
            R.id.tv_tier,
            state.tier.shortLabel + if (state.autoTier) "" else "*",
        )

        // 标题
        views.setTextViewText(R.id.tv_title, context.getString(R.string.widget_title_default))
        views.setTextColor(R.id.tv_title, COLOR_TEXT_TITLE)

        // 同步状态：把状态行与「x 秒刷新」拼在一起，间隔数值来自用户设置
        val statusText = if (state.lastError != null) {
            state.lastError
        } else {
            state.statusText() + " · ${intervalSeconds}秒"
        }
        views.setTextViewText(R.id.tv_sync, statusText)
        views.setTextColor(
            R.id.tv_sync,
            if (state.lastError != null) COLOR_TEXT_ERROR else COLOR_TEXT_SYNC,
        )

        // 余额大字
        views.setTextViewText(R.id.tv_balance, state.balanceText)
        views.setTextColor(R.id.tv_balance, COLOR_TEXT_MAIN)

        // 倒计时：文案与等宽数字拆成两个 TextView，秒数跳动时不会左右抖动
        views.setTextViewText(R.id.tv_countdown, state.countdownLabel)
        views.setTextViewText(R.id.tv_countdown_num, state.countdownText(showSeconds))
        views.setTextColor(R.id.tv_countdown, COLOR_TEXT_SUB)
        views.setTextColor(R.id.tv_countdown_num, COLOR_TEXT_SUB)

        // 节假日 / 补班标记
        val badge = state.dayBadge()
        views.setTextViewText(R.id.tv_day, badge.orEmpty())
        views.setViewVisibility(R.id.tv_day, if (badge == null) View.GONE else View.VISIBLE)

        // 余额明细
        val detail = buildDetail(state)
        views.setTextViewText(R.id.tv_detail, detail.orEmpty())
        views.setViewVisibility(R.id.tv_detail, if (detail == null) View.GONE else View.VISIBLE)

        // 刷新按钮：请求中变半透明，给出即时反馈（背景保持圆角形状，仅调透明度）
        // setFloat 同样是高版本才有的 RemoteViews 方法，低版本直接跳过这层视觉反馈。
        views.setImageViewResource(R.id.btn_refresh, R.drawable.ic_refresh)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setFloat(R.id.btn_refresh, "setAlpha", if (state.loading) 0.35f else 1.0f)
        }

        // 点击事件：点刷新按钮 → 立即刷新；点卡片其它区域 → 打开设置页
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            WidgetIntents.broadcast(context, ACTION_REFRESH, 1),
        )
        views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openApp(context, 2))
        views.setOnClickPendingIntent(
            R.id.tv_tier,
            WidgetIntents.broadcast(context, ACTION_TOGGLE_TIER, 3),
        )

        return views
    }

    /** 「赠金 ¥9.29 · 充值 ¥100.00」，官方扣费规则是先用赠金。 */
    private fun buildDetail(state: WidgetState): String? {
        val b = state.balance ?: return null
        if (state.lastError != null) return null
        return "赠金 ${b.currencySymbol}${twoDecimals(b.grantedBalance)}" +
            " · 充值 ${b.currencySymbol}${twoDecimals(b.toppedUpBalance)}"
    }

    /** "9.290000" → "9.29"；无法解析时原样返回。 */
    private fun twoDecimals(raw: String): String {
        val d = raw.toDoubleOrNull() ?: return raw
        return String.format(java.util.Locale.CHINA, "%.2f", d)
    }

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()

    /** 判断某个 provider 当前是否还有存活的插件实例。 */
    fun hasWidgetInstances(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BalanceWidgetProvider::class.java))
        return ids != null && ids.isNotEmpty()
    }
}
