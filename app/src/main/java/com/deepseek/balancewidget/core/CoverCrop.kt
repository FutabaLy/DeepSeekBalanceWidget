package com.deepseek.balancewidget.core

/**
 * 封面裁剪的几何计算。
 *
 * 裁剪窗口是一个**固定边长**的正方形（不随图片大小变化），图片在里面可以拖动、缩放，
 * 但永远不会露出窗口边缘（最小缩放就是「铺满」）。这里全是纯函数，方便单测。
 *
 * 坐标系：
 * - 原图坐标系：像素，左上角 (0,0)
 * - 视口坐标系：像素，边长 [viewport]，左上角 (0,0)
 * - [offsetX]/[offsetY] 是图片相对「居中位置」的额外位移（视口像素）
 */
object CoverCrop {

    /** 视口里能看到的一块原图区域。 */
    data class SourceRect(val left: Float, val top: Float, val size: Float)

    /** 让图片刚好铺满视口所需的最小缩放（cover 语义）。 */
    fun minScale(srcWidth: Int, srcHeight: Int, viewport: Float): Float {
        if (srcWidth <= 0 || srcHeight <= 0 || viewport <= 0f) return 1f
        return maxOf(viewport / srcWidth, viewport / srcHeight)
    }

    /** 把位移限制在「图片边缘不会进入视口」的范围内。 */
    fun clampOffset(
        srcWidth: Int,
        srcHeight: Int,
        viewport: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Pair<Float, Float> {
        val maxX = ((srcWidth * scale - viewport) / 2f).coerceAtLeast(0f)
        val maxY = ((srcHeight * scale - viewport) / 2f).coerceAtLeast(0f)
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }

    /** 视口当前对应的原图区域（像素）。 */
    fun sourceRect(
        srcWidth: Int,
        srcHeight: Int,
        viewport: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): SourceRect {
        val visible = viewport / scale
        val left = (srcWidth - visible) / 2f - offsetX / scale
        val top = (srcHeight - visible) / 2f - offsetY / scale
        return SourceRect(left = left, top = top, size = visible)
    }

    /** 图片在视口里的绘制位置（左上角），供预览绘制用。 */
    fun drawOffset(
        srcWidth: Int,
        srcHeight: Int,
        viewport: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Pair<Float, Float> {
        val dx = (viewport - srcWidth * scale) / 2f + offsetX
        val dy = (viewport - srcHeight * scale) / 2f + offsetY
        return dx to dy
    }
}
