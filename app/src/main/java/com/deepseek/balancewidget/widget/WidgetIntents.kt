package com.deepseek.balancewidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.deepseek.balancewidget.MainActivity

/**
 * 插件点击事件的 PendingIntent 工厂（4×2 与 2×2 共用）。
 *
 * requestCode 由各插件各自分配，避免两种插件的 PendingIntent 互相覆盖。
 */
internal object WidgetIntents {

    /** 点按钮 → 广播给 [WidgetActionReceiver]（不依赖服务存活）。 */
    fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 点卡片空白处 → 打开设置页。 */
    fun openApp(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
