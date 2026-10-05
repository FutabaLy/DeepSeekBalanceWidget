package com.deepseek.balancewidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import java.io.File

/**
 * 2×2 封面图（用户自定义那张）的存取。
 *
 * 为什么不用 content:// URI 交给桌面去读：
 * 桌面进程（launcher）读不到本应用私有目录，而给它授权要先知道具体 launcher 包名，
 * 而且这种授权重启后会失效 —— 一旦失效封面就白屏，排查起来还很隐蔽。
 * 这里改成渲染时用 `RemoteViews.setImageViewBitmap` 把位图随 RemoteViews 直接传过去：
 * 位图在 Binder 里走 ashmem（不占 1MB 事务限额），任何桌面、重启后都照样显示。
 *
 * 代价是位图会随 RemoteViews 被系统缓存一份，所以尺寸统一限到 [OUTPUT_SIZE] 见方。
 */
object CompactCoverStore {

    /** 自定义封面统一存成这个边长（2×2 卡片 180dp @3x ≈ 540px，512 足够且不占内存）。 */
    const val OUTPUT_SIZE = 512

    private const val DIR = "covers"
    private const val NAME = "custom_cover.png"
    private const val PREFS = "deepseek_compact_cover"
    private const val KEY_VERSION = "version"

    @Volatile
    private var cache: Bitmap? = null

    @Volatile
    private var cacheVersion = -1

    /** 用户是否设置过自定义封面。 */
    fun hasCustom(context: Context): Boolean = file(context).isFile

    /** 每次换图 +1，用来让插件重推封面。 */
    fun version(context: Context): Int =
        prefs(context).getInt(KEY_VERSION, 0)

    /** 保存用户裁剪好的封面（调用方保证是 [OUTPUT_SIZE] 见方，保留透明通道）。 */
    fun save(context: Context, bitmap: Bitmap): Boolean {
        val target = file(context)
        return runCatching {
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bump(context)
            true
        }.getOrElse { false }
    }

    /** 恢复内置封面。 */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
        bump(context)
    }

    /** 取自定义封面位图（带内存缓存）；没有设置或解码失败返回 null。 */
    fun loadBitmap(context: Context): Bitmap? {
        val f = file(context)
        if (!f.isFile) return null
        val v = version(context)
        cache?.let { if (cacheVersion == v && !it.isRecycled) return it }
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        cache = bmp
        cacheVersion = v
        return bmp
    }

    private fun bump(context: Context) {
        val next = version(context) + 1
        prefs(context).edit { putInt(KEY_VERSION, next) }
        cache = null
        cacheVersion = -1
    }

    private fun file(context: Context): File =
        File(File(context.applicationContext.filesDir, DIR), NAME)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
