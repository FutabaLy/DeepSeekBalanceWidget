package com.deepseek.balancewidget.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.data.CompactCoverStore

/**
 * 2×2 紧凑插件的渲染（浅色主题：柔和白底 + 蓝色描边）。
 *
 * 插件是「双层」的：
 * - 封面层 [buildCover]：整张图片，点一下翻到数据层；
 * - 数据层 [build]：余额大字 / 今日已用 / 峰谷胶囊 + 倒计时 / 一个刷新按钮，
 *   点卡片其它地方翻回封面层，点 ↻ 立即刷新。
 *
 * 翻面状态按 widgetId 存在 [com.deepseek.balancewidget.data.CompactFaceStore]。
 */
object CompactWidgetRenderer {

    /** 翻面动作：点封面 → 数据面，点数据面 → 封面。 */
    const val ACTION_FLIP = "com.deepseek.balancewidget.action.COMPACT_FLIP"

    /** 翻面的 requestCode 基数，实际用 base + widgetId 区分不同插件实例。 */
    private const val FLIP_REQUEST_CODE_BASE = 1000

    /** 主色：标题、余额、描边。 */
    private const val COLOR_BRAND = 0xFF3A55A8.toInt()
    private const val COLOR_USED = 0xFF8A93A6.toInt()
    private const val COLOR_ERROR = 0xFFC0392B.toInt()
    private const val COLOR_PILL_TEXT = 0xFFFFFFFF.toInt()
    private const val COLOR_ACCENT_OFF_PEAK = 0xFF2FA05A.toInt()
    private const val COLOR_ACCENT_PEAK = 0xFFE0902A.toInt()

    /** 封面层：整张图片，点一下翻到数据面。 */
    fun buildCover(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance_compact_cover)
        // 自定义封面直接把位图随 RemoteViews 传过去：
        // 桌面进程读不到 App 私有目录，给桌面授 content:// 权限又依赖具体 launcher 包名、
        // 重启后授权还会失效；位图在 Binder 里走 ashmem，任何桌面、重启后都能显示。
        val custom = CompactCoverStore.loadBitmap(context)
        if (custom != null) {
            views.setImageViewBitmap(R.id.compact_cover_image, custom)
        } else {
            views.setImageViewResource(R.id.compact_cover_image, R.drawable.compact_cover)
        }
        views.setOnClickPendingIntent(
            R.id.compact_cover_root,
            flipIntent(context, widgetId),
        )
        return views
    }

    /** 数据层。 */
    fun build(
        context: Context,
        state: WidgetState,
        showSeconds: Boolean = true,
        widgetId: Int = -1,
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
        // 余额大字：左深蓝 → 右天蓝的循环渐变动画。RemoteViews 给 TextView 只能上纯色，
        // 所以这里画成位图再 setImageViewBitmap；相位每秒推一格，跟着刷新节奏流动
        val balanceBitmap = GradientTextRenderer.render(
            text = state.balanceText,
            textSizeSp = GradientTextRenderer.BALANCE_TEXT_SP,
            density = context.resources.displayMetrics.density,
            startColor = if (error != null) COLOR_ERROR else GradientTextRenderer.BALANCE_GRADIENT_START,
            endColor = if (error != null) COLOR_ERROR else GradientTextRenderer.BALANCE_GRADIENT_END,
            typeface = GradientTextRenderer.balanceTypeface(context),
            phase = if (error != null) 0 else GradientTextRenderer.currentPhase(),
        )
        if (balanceBitmap != null) {
            views.setImageViewBitmap(R.id.compact_balance, balanceBitmap)
            views.setContentDescription(R.id.compact_balance, state.balanceText)
        }

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

        // 倒计时：累计小时制（57:16:52），窄卡片上不会被截断
        views.setTextViewText(R.id.compact_countdown, state.countdownTextCompact(showSeconds))
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
        // 点卡片其它地方 → 翻回封面层
        views.setOnClickPendingIntent(R.id.compact_root, flipIntent(context, widgetId))

        return views
    }

    private fun flipIntent(context: Context, widgetId: Int) =
        WidgetIntents.broadcast(
            context,
            ACTION_FLIP,
            FLIP_REQUEST_CODE_BASE + widgetId.coerceAtLeast(0),
            appWidgetId = widgetId.takeIf { it >= 0 },
        )
}
