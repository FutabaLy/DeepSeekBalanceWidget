package com.deepseek.balancewidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 把用户选的图片解码成可裁剪的位图。
 *
 * 格式尽量交给系统：JPEG / PNG / WebP / GIF（首帧）/ BMP / HEIF / AVIF 等都能进；
 * Android 9+ 走 [ImageDecoder]（顺带自动应用 EXIF 方向），8.x 回退 BitmapFactory + ExifInterface。
 *
 * 两道限制：
 * - 文件 ≤ [MAX_BYTES]（30MB）：再大基本是没压过的原图，解码又慢又占内存；
 * - 解码后最长边 ≤ [MAX_EDGE]（2048px）：裁剪输出只有 512，4 倍余量足够缩放。
 */
object CoverImageLoader {

    private const val TAG = "CoverImageLoader"

    const val MAX_BYTES = 30L * 1024 * 1024
    const val MAX_EDGE = 2048

    /** 解码失败返回 null（格式不支持 / 文件过大 / 读取异常）。 */
    fun decode(context: Context, uri: Uri): Bitmap? {
        val size = querySize(context, uri)
        if (size != null && size > MAX_BYTES) {
            Log.w(TAG, "图片超过上限：$size 字节")
            return null
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(context, uri)
            } else {
                decodeWithFactory(context, uri)
            }
        }.onFailure { Log.w(TAG, "解码失败", it) }.getOrNull()
    }

    fun describeLimit(): String = "最大 ${MAX_BYTES / 1024 / 1024}MB，支持 JPG/PNG/WebP/GIF/HEIC 等常见格式"

    // ---------------------------------------------------------------- 实现

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                var sample = 1
                while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }
        // 少数机器仍可能给硬件位图，转成软件位图才能参与后面的 Canvas 裁剪
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    private fun decodeWithFactory(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // 8.x 上 BitmapFactory 不处理 EXIF 方向，竖着拍的照片会躺倒
        val rotation = readExifRotation(context, uri)
        return if (rotation != 0f) bitmap.rotated(rotation) else bitmap
    }

    private fun readExifRotation(context: Context, uri: Uri): Float = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = android.media.ExifInterface(input)
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    private fun Bitmap.rotated(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val result = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (result != this) recycle()
        return result
    }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }
    }.getOrNull()
}
