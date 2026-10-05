package com.deepseek.balancewidget.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.WidgetState

/**
 * 2×2 紧凑插件的渲染（浅色主题：白底 + 蓝色描边）。
 *
 * 内容：标题 + 刷新按钮 / 余额大字 / 今日已用 / 峰谷胶囊 + 倒计时。
 * 配色与 4×2 深色版完全独立，两边互不影响。
 */
object CompactWidgetRenderer {

    /** 主色：标题、余额、描边。 */
    private const val COLOR_BRAND = 0xFF3A55A8.toInt()
    private const val COLOR_USED = 0xFF8A93A6.toInt()
    private const val COLOR_ERROR = 0xFFC0392B.toInt()
    private const val COLOR_PILL_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_ACCENT_OFF_PEAK = 0xFF2FA05A.toInt()
    private const val COLOR_ACCENT_PEAK = 0xFFE0902A.toInt()

    fun build(
        context: Context,
        state: WidgetState,
        showSeconds: Boolean = true,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance_compact)
        val peak = state.tier.isPeak
        val accent = if (peak) COLOR_ACCENT_PEAK else COLOR_ACCENT_OFF_PEAK

        // 卡片与胶囊同样用 setBackgroundResource（全版本可用），不要用 setBackgroundColor 抹掉圆角
        views.setInt(R.id.compact_root, "setBackgroundResource", R.drawable.bg_compact_card)
        views.setInt(
            R.id.compact_tier,
            "setBackgroundResource",
            if (peak) R.drawable.bg_compact_pill_peak else R.drawable.bg_compact_pill_off_peak,
        )

        views.setTextViewText(R.id.compact_title, context.getString(R.string.widget_title_default))
        views.setTextColor(R.id.compact_title, COLOR_BRAND)

        val error = state.lastError
        views.setTextViewText(R.id.compact_balance, state.balanceText)
        views.setTextColor(
            R.id.compact_balance,
            if (error != null) COLOR_ERROR else COLOR_BRAND,
        )

        // 第三行：正常显示「今日已用 ¥x.xx」，出错时整行显示原因（紧凑版没有别的位置放提示）
        if (error != null) {
            views.setTextViewText(R.id.compact_used, error)
            views.setTextColor(R.id.compact_used, COLOR_ERROR)
        } else {
            views.setTextViewText(
                R.id.compact_used,
                state.usedTodayLine(context.getString(R.string.widget_used_today)),
            )
            views.setTextColor(R.id.compact_used, COLOR_USED)
        }

        views.setTextViewText(
            R.id.compact_tier,
            state.tier.shortLabel + if (state.autoTier) "" else "*",
        )
        views.setTextColor(R.id.compact_tier, COLOR_PILL_TEXT)

        // 倒计时（等宽数字，秒数跳动不抖）
        views.setTextViewText(R.id.compact_countdown, state.countdownText(showSeconds))
        views.setTextColor(R.id.compact_countdown, accent)

        // 刷新按钮：请求中变半透明（setFloat 需 API 31+，低版本跳过这层反馈）
        views.setImageViewResource(R.id.compact_refresh, R.drawable.ic_refresh_blue)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setFloat(R.id.compact_refresh, "setAlpha", if (state.loading) 0.35f else 1.0f)
        }

        views.setOnClickPendingIntent(
            R.id.compact_refresh,
            WidgetIntents.broadcast(context, WidgetRenderer.ACTION_REFRESH, 11),
        )
        views.setOnClickPendingIntent(R.id.compact_root, WidgetIntents.openApp(context, 12))

        return views
    }
}
