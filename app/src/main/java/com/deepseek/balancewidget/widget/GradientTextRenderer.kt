package com.deepseek.balancewidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import kotlin.math.ceil

/**
 * 把文字画成带渐变的位图。
 *
 * 为什么非要画成图：RemoteViews 给 TextView 只能用 `setTextColor` 上**纯色**，没有 shader
 * 这条通路，所以「左深蓝 → 右天蓝」这种渐变字只能先在 App 里画好，再用
 * `setImageViewBitmap` 传过去 —— 位图在 Binder 里走 ashmem（>16KB 即共享内存），
 * 不占 1MB 事务限额，每秒重推也扛得住。
 *
 * 位图按屏幕密度绘制，ImageView 用 wrap_content + centerInside 就能 1:1 显示、不缩放。
 */
object GradientTextRenderer {

    /** 缓存最近几张（余额变化不频繁，避免每秒重新分配与绘制）。 */
    private val cache = LruCache<String, Bitmap>(6)

    /** 余额大字的字号（sp），和原来 TextView 的 26sp 对齐。 */
    const val BALANCE_TEXT_SP = 26f

    /** 渐变起止色：左深蓝 → 右天蓝。 */
    const val BALANCE_GRADIENT_START = 0xFF1B3A8C.toInt()
    const val BALANCE_GRADIENT_END = 0xFF5AA8FF.toInt()

    /** 余额用的字重：系统里最粗的那一档（MIUI 上就是 MiSans Black）。 */
    val balanceTypeface: Typeface by lazy {
        Typeface.create("sans-serif-black", Typeface.BOLD)
    }

    /**
     * 画一段渐变文字。
     *
     * @param textSizeSp 字号（sp）；位图会按 [density] 换算成像素
     * @param startColor 左侧颜色（也是回退用的纯色）
     * @param endColor 右侧颜色
     */
    fun render(
        text: String,
        textSizeSp: Float,
        density: Float,
        startColor: Int,
        endColor: Int,
        typeface: Typeface,
    ): Bitmap? {
        if (text.isEmpty()) return null
        val key = "$text|$textSizeSp|$startColor|$endColor|${typeface.hashCode()}|$density"
        cache.get(key)?.let { if (!it.isRecycled) return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizeSp * density
            color = startColor
        }
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text)
        if (textWidth <= 0f) return null

        val width = ceil(textWidth).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        // 左右各留 1px，避免抗锯齿边缘被裁掉
        val bitmap = Bitmap.createBitmap(width + 2, height, Bitmap.Config.ARGB_8888)
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            startColor,
            endColor,
            Shader.TileMode.MIRROR,
        )
        Canvas(bitmap).drawText(text, 1f, -metrics.ascent, paint)

        cache.put(key, bitmap)
        return bitmap
    }
}
