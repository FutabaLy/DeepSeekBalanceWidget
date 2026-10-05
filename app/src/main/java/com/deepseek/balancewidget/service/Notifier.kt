package com.deepseek.balancewidget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deepseek.balancewidget.MainActivity
import com.deepseek.balancewidget.R
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.widget.WidgetRenderer

/**
 * 前台服务常驻通知：既是 Android 8.0+ 的强制要求，也是插件的「第二块屏」。
 *
 * 收起时：`谷 · ¥109.29 · 5s前` —— 不展开就能看到余额和档位；
 * 展开后：完整的时段解释、倒计时、赠金明细与操作按钮。
 */
object Notifier {

    const val CHANNEL_ID = "deepseek_balance_service"
    const val NOTIFICATION_ID = 1001

    const val ACTION_REFRESH = "com.deepseek.balancewidget.action.NOTIF_REFRESH"
    const val ACTION_TOGGLE_TIER = "com.deepseek.balancewidget.action.NOTIF_TOGGLE_TIER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW, // 静默、不响铃、不弹横幅
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: WidgetState): Notification {
        ensureChannel(context)

        val accent = if (state.tier.isPeak) 0xFFFFB020.toInt() else 0xFF12D6A0.toInt()
        val collapsed = "${state.tier.shortLabel} · ${state.balanceText} · " + when {
            state.lastError != null -> "刷新异常"
            state.lastSuccessAt > 0 -> DateUtil.relativeFrom(state.lastSuccessAt)
            else -> "待同步"
        }

        val detail = buildString {
            append(state.tier.label)
            append("｜").append(state.tier.detail)
            append("\n下次切换：").append(state.countdownLabel).append(' ')
            append(state.countdownText(true))
            append("（").append(DateUtil.formatDateTime(
                com.deepseek.balancewidget.core.PeakScheduler.nextBoundary(DateUtil.nowBeijing()),
            )).append(" 北京时间）")
            append("\n")
            if (state.lastSuccessAt > 0) {
                append("更新于 ").append(DateUtil.formatEpoch(state.lastSuccessAt))
            } else {
                append("尚未同步成功")
            }
            state.balance?.let {
                append("\n赠金 ").append(it.currencySymbol).append(it.grantedBalance)
                append(" · 充值 ").append(it.currencySymbol).append(it.toppedUpBalance)
            }
            state.dayBadge()?.let { append("\n今天：").append(it) }
            state.lastError?.let { append("\n⚠ ").append(it) }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_balance)
            .setColor(accent)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(collapsed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(context))
            .addAction(0, context.getString(R.string.action_refresh), action(context, ACTION_REFRESH, 11))
            .addAction(0, "切换峰/谷显示", action(context, ACTION_TOGGLE_TIER, 12))
            .build()
    }

    /** 更新常驻通知；通知权限被拒时静默失败（前台服务仍可运行）。 */
    fun update(context: Context, state: WidgetState) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, build(context, state)) }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            21,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, com.deepseek.balancewidget.widget.WidgetActionReceiver::class.java)
            .setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
