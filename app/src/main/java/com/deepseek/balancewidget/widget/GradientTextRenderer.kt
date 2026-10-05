package com.deepseek.balancewidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.deepseek.balancewidget.R
import kotlin.math.ceil

/**
 * 把文字画成带渐变的位图。
 *
 * 为什么非要画成图：RemoteViews 给 TextView 只能用 `setTextColor` 上**纯色**，没有 shader
 * 这条通路，所以「左深蓝 → 右天蓝」这种渐变字只能先在 App 里画好，再用
 * `setImageViewBitmap` 传过去 —— 位图在 Binder 里走 ashmem（>16KB 即共享内存），
 * 不占 1MB 事务限额，动画通过局部更新推送。
 *
 * 位图按屏幕密度绘制，ImageView 用 wrap_content + centerInside 就能 1:1 显示、不缩放。
 */
object GradientTextRenderer {

    /** 位图缓存限制为 4 MiB，不随动画帧数无限增长。 */
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 余额大字的字号（sp）。 */
    const val BALANCE_TEXT_SP = 30f

    /** 渐变起止色：左深蓝 → 右天蓝。 */
    const val BALANCE_GRADIENT_START = 0xFF1B3A8C.toInt()
    const val BALANCE_GRADIENT_END = 0xFF5AA8FF.toInt()

    /** 20 fps，12 秒一轮。 */
    const val FRAME_MS = 50L
    const val ANIMATION_STEPS = 240

    @Volatile
    private var cachedTypeface: Typeface? = null

    /**
     * 余额用的字重：打包进 APK 的 Nunito Black（拉丁与数字），
     * 中文不在这个字体里，会自动回落到系统字体。加载失败就退回系统最粗字重。
     */
    fun balanceTypeface(context: Context): Typeface {
        cachedTypeface?.let { return it }
        val typeface = ResourcesCompat.getFont(context, R.font.nunito_black)
            ?: Typeface.create("sans-serif-black", Typeface.BOLD)
        cachedTypeface = typeface
        return typeface
    }

    /**
     * 当前动画相位（0 到 [ANIMATION_STEPS] - 1）。
     *
     * 插件没法做逐帧动画（RemoteViews 不支持），通过独立协程局部推送相位不同的位图；
     * 不额外请求余额接口。息屏或服务停掉时会停在当前相位。
     */
    fun currentPhase(): Int = ((android.os.SystemClock.elapsedRealtime() / FRAME_MS) % ANIMATION_STEPS).toInt()

    /**
     * 画一段渐变文字。
     *
     * @param textSizeSp 字号（sp）；位图会按 [density] 换算成像素
     * @param startColor 左侧颜色（也是回退用的纯色）
     * @param endColor 右侧颜色
     * @param phase 动画相位；不同相位画出来的是同一套渐变平移后的结果，循环无接缝
     */
    fun render(
        text: String,
        textSizeSp: Float,
        density: Float,
        startColor: Int,
        endColor: Int,
        typeface: Typeface,
        phase: Int = 0,
    ): Bitmap? {
        if (text.isEmpty()) return null
        val step = ((phase % ANIMATION_STEPS) + ANIMATION_STEPS) % ANIMATION_STEPS
        val key = "$text|$textSizeSp|$startColor|$endColor|${typeface.hashCode()}|$density|$step"
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

        // 一个周期正好覆盖整段文字：左深蓝 → 中天蓝 → 右深蓝，首尾同色所以平移循环无接缝；
        // 相位每加一格就把渐变整体左移 1/N 个周期，看起来就是颜色在字上流动。
        val period = width.toFloat()
        val shift = -period * step / ANIMATION_STEPS
        paint.shader = LinearGradient(
            shift,
            0f,
            shift + period,
            0f,
            intArrayOf(startColor, endColor, startColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.REPEAT,
        )
        Canvas(bitmap).drawText(text, 1f, -metrics.ascent, paint)

        cache.put(key, bitmap)
        return bitmap
    }
}
