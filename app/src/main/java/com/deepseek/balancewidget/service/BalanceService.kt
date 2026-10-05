package com.deepseek.balancewidget.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.HolidayCalendar
import com.deepseek.balancewidget.core.PeakScheduler
import com.deepseek.balancewidget.core.WidgetState
import com.deepseek.balancewidget.core.WidgetStateBus
import com.deepseek.balancewidget.data.AppSettings
import com.deepseek.balancewidget.data.BalanceRepository
import com.deepseek.balancewidget.data.BalanceStore
import com.deepseek.balancewidget.widget.BalanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 常驻刷新服务（前台服务）。
 *
 * 节奏：
 * - 每 1 秒重算一次倒计时并重画插件（可用设置关闭，改为只按刷新间隔重画）；
 * - 每 [AppSettings.intervalSeconds] 秒（默认 5 秒）调用一次 `/user/balance`；
 * - 失败按指数退避重试（5s→10s→20s→40s，最长 60s），避免把接口打爆；
 * - 息屏后暂停网络请求（可在设置里改成继续刷新），亮屏立即补一次。
 *
 * 保活：前台服务 + START_STICKY；被 MIUI 清理后由 WorkManager 兜底任务续上。
 */
class BalanceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var manualJob: Job? = null

    private lateinit var settings: AppSettings
    private lateinit var repository: BalanceRepository

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    Log.i(TAG, "息屏，暂停余额请求")
                }

                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (!screenOn) {
                        screenOn = true
                        Log.i(TAG, "亮屏，立即补一次刷新")
                        forceRefresh = true
                    }
                }
            }
        }
    }

    @Volatile
    private var screenOn: Boolean = true

    @Volatile
    private var forceRefresh: Boolean = false

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.get(this)
        repository = BalanceRepository { settings.baseUrl.value }
        HolidayCalendar.init(this)
        Notifier.ensureChannel(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        isRunning = true
        scope.launch { bootstrap() }
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须立刻进入前台，否则 Android 8.0+ 会直接抛异常
        promoteToForeground(WidgetStateBus.current())

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> refreshNow("通知/插件手动刷新")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 15+ 对长时间运行的前台服务会回调超时；这里主动让位，
     * 由 WorkManager 兜底任务继续维持刷新，避免系统直接 ANR 掉进程。
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "前台服务被系统判定超时，降级为兜底刷新")
        com.deepseek.balancewidget.worker.WidgetWorkScheduler.refreshNow(this)
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "服务销毁")
        isRunning = false
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        WidgetStateBus.update { it.copy(serviceRunning = false) }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务划掉 App 时不要停服务，否则插件会停更
        Log.i(TAG, "任务被移除，保持服务运行")
        super.onTaskRemoved(rootIntent)
    }

    // ---------------------------------------------------------------- 内部实现

    /** 服务启动后先补一次：先画缓存，再异步取新数据。 */
    private suspend fun bootstrap() {
        // 开机后按天级别的节假日数据更新（每天最多一次，避免频繁请求）
        launchHolidayAutoUpdate()

        val cached = withContext(Dispatchers.IO) { BalanceStore.load(this@BalanceService) }
        if (cached != null) {
            WidgetStateBus.update {
                it.copy(
                    balance = cached.toBalanceInfo(),
                    isAvailable = cached.isAvailable,
                    hasData = true,
                    lastSuccessAt = cached.updatedAt,
                    serviceRunning = true,
                )
            }
        }
        forceRefresh = true
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var lastSuccessTick = 0L
            var failureCount = 0
            var lastRendered: String? = null

            while (isActive) {
                val now = System.currentTimeMillis()
                val intervalMs = settings.intervalSeconds.value * 1000L
                // 失败退避：5s → 10s → 20s → 40s，最多 60s
                val backoffMs = if (failureCount > 0) {
                    minOf(60_000L, intervalMs * (1L shl minOf(failureCount, 5)))
                } else {
                    intervalMs
                }
                val effectiveInterval = if (failureCount > 0) backoffMs else intervalMs

                // 1) 每 tick 重算倒计时（纯内存计算，零成本）
                val beijingNow = DateUtil.nowBeijing()
                val nextBoundary = PeakScheduler.nextBoundary(beijingNow)
                val tier = settings.effectiveTier(beijingNow)
                val (_, nextTier) = PeakScheduler.nextTransition(beijingNow)

                WidgetStateBus.update {
                    it.copy(
                        tier = tier,
                        autoTier = settings.isAutoTier,
                        nextTier = nextTier,
                        countdownMillis = java.time.Duration.between(beijingNow, nextBoundary).toMillis(),
                        dayContext = HolidayCalendar.dayContext(beijingNow),
                        serviceRunning = true,
                    )
                }

                // 2) 到点查余额
                val due = forceRefresh || now - lastSuccessTick >= effectiveInterval
                val networkAllowed = screenOn || settings.keepRefreshingInBackground
                if (due && networkAllowed && settings.apiKey.value.isNotBlank()) {
                    forceRefresh = false
                    lastSuccessTick = now
                    val result = fetchBalance()
                    if (result) {
                        failureCount = 0
                    } else {
                        failureCount++
                        // 失败后 1 秒就重试，不等满一个周期
                        lastSuccessTick = System.currentTimeMillis() - effectiveInterval + 1000L
                    }
                } else if (due && !networkAllowed) {
                    forceRefresh = false
                }

                // 3) 重画（内容无变化就不打扰系统）
                val state = WidgetStateBus.current()
                val rendered = render(state)
                if (rendered != lastRendered) {
                    pushToUi(state)
                    lastRendered = rendered
                }

                delay(TICK_MS)
            }
        }
    }

    /** @return 是否成功 */
    private suspend fun fetchBalance(): Boolean {
        WidgetStateBus.update { it.copy(loading = true) }
        val key = settings.apiKey.value
        return repository.fetchBalance(key).fold(
            onSuccess = { snapshot ->
                withContext(Dispatchers.IO) { BalanceStore.save(this@BalanceService, snapshot) }
                WidgetStateBus.update {
                    it.copy(
                        balance = snapshot.primary,
                        isAvailable = snapshot.isAvailable,
                        hasData = true,
                        lastSuccessAt = System.currentTimeMillis(),
                        lastError = null,
                        loading = false,
                        failureCount = 0,
                    )
                }
                Log.d(TAG, "余额更新：${snapshot.totalText}")
                true
            },
            onFailure = { error ->
                WidgetStateBus.update {
                    it.copy(
                        loading = false,
                        lastError = error.message ?: "刷新失败",
                        failureCount = it.failureCount + 1,
                    )
                }
                Log.w(TAG, "余额刷新失败：${error.message}")
                false
            },
        )
    }

    /** 手动触发一次刷新（插件按钮 / 通知按钮 / 打开 App）。 */
    fun refreshNow(reason: String) {
        Log.i(TAG, "手动刷新：$reason")
        forceRefresh = true
        manualJob?.cancel()
        manualJob = scope.launch {
            if (settings.apiKey.value.isBlank()) {
                WidgetStateBus.update { it.copy(lastError = "请先在 App 里填写 API Key") }
                pushToUi(WidgetStateBus.current())
                return@launch
            }
            fetchBalance()
            pushToUi(WidgetStateBus.current())
        }
    }

    private fun promoteToForeground(state: WidgetState) {
        val notification = Notifier.build(this, state)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, Notifier.NOTIFICATION_ID, notification, type)
        }.onFailure {
            Log.e(TAG, "进入前台失败，服务即将停止", it)
            stopSelf()
        }
    }

    private fun pushToUi(state: WidgetState) {
        BalanceWidgetProvider.updateAll(this, state)
        Notifier.update(this, state)
    }

    /** 渲染指纹：只有内容变化时才推送给系统，减少 IPC 与耗电。 */
    private fun render(state: WidgetState): String {
        val showSeconds = settings.tickEverySecond.value
        return buildString {
            append(state.balanceText).append('|')
            append(state.tier).append('|')
            append(state.countdownText(showSeconds)).append('|')
            append(state.countdownLabel).append('|')
            append(state.dayBadge()).append('|')
            append(state.lastError).append('|')
            append(DateUtil.relativeFrom(state.lastSuccessAt))
        }
    }

    /** 每天最多一次联网更新节假日数据（法定节假日是按年公布的，不需要频繁拉）。 */
    private fun launchHolidayAutoUpdate() {
        val last = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_HOLIDAY_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - last < DAY_MS) return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_HOLIDAY_CHECK, now).apply()
        scope.launch {
            runCatching { com.deepseek.balancewidget.data.HolidayUpdater.update(this@BalanceService) }
                .onSuccess { Log.i(TAG, "节假日日历已更新：${it.getOrNull()} 条") }
                .onFailure { Log.i(TAG, "节假日自动更新跳过：${it.message}") }
        }
    }

    companion object {
        private const val TAG = "BalanceService"
        private const val TICK_MS = 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val PREFS = "deepseek_service_state"
        private const val KEY_HOLIDAY_CHECK = "holiday_last_check"

        const val ACTION_START = "com.deepseek.balancewidget.action.START"
        const val ACTION_STOP = "com.deepseek.balancewidget.action.STOP"
        const val ACTION_REFRESH = "com.deepseek.balancewidget.action.SERVICE_REFRESH"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BalanceService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BalanceService::class.java))
        }

        /** 请求立即刷新；服务没跑时先拉起服务。 */
        fun requestRefresh(context: Context, reason: String) {
            if (isRunning) {
                // 通过 Intent 触发，避免直接持有 Service 引用
                val intent = Intent(context, BalanceService::class.java).setAction(ACTION_REFRESH)
                runCatching { context.startService(intent) }
                    .onFailure { start(context) }
            } else {
                start(context)
            }
        }

        /** 当前是否已被系统加入电池优化白名单（MIUI 省电策略影响保活）。 */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(PowerManager::class.java) ?: return false
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
    }
}
