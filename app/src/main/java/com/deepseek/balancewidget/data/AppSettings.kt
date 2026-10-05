package com.deepseek.balancewidget.data

import android.content.Context
import androidx.core.content.edit
import com.deepseek.balancewidget.core.DateUtil
import com.deepseek.balancewidget.core.PeakScheduler
import com.deepseek.balancewidget.core.TariffTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地设置。API Key 只保存在本 App 私有目录的 SharedPreferences 中，
 * 不写入日志、不随备份上传；需要更高安全性请自行接入 Android Keystore 加密。
 */
class AppSettings private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    private val _intervalSeconds = MutableStateFlow(prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL))
    private val _tickEverySecond = MutableStateFlow(prefs.getBoolean(KEY_TICK, true))
    private val _override = MutableStateFlow(prefs.getString(KEY_OVERRIDE, OVERRIDE_AUTO) ?: OVERRIDE_AUTO)
    private val _serviceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_ENABLED, false))
    private val _keepRefreshingInBackground = MutableStateFlow(prefs.getBoolean(KEY_KEEP_REFRESH, false))
    private val _baseUrl = MutableStateFlow(prefs.getString(KEY_BASE_URL, DeepSeekApi.DEFAULT_BASE_URL).orEmpty())

    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()
    val tickEverySecond: StateFlow<Boolean> = _tickEverySecond.asStateFlow()
    val overrideMode: StateFlow<String> = _override.asStateFlow()
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /** 息屏后是否继续按间隔请求余额（默认 false：省电，亮屏立刻补刷）。 */
    val keepRefreshingInBackground: Boolean
        get() = _keepRefreshingInBackground.value

    fun setKeepRefreshingInBackground(value: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_REFRESH, value) }
        _keepRefreshingInBackground.value = value
    }

    fun setApiKey(value: String) {
        val v = value.trim()
        prefs.edit { putString(KEY_API_KEY, v) }
        _apiKey.value = v
    }

    fun setIntervalSeconds(value: Int) {
        val v = value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        prefs.edit { putInt(KEY_INTERVAL, v) }
        _intervalSeconds.value = v
    }

    fun setTickEverySecond(value: Boolean) {
        prefs.edit { putBoolean(KEY_TICK, value) }
        _tickEverySecond.value = value
    }

    fun setOverrideMode(value: String) {
        val v = if (value in OVERRIDE_MODES) value else OVERRIDE_AUTO
        prefs.edit { putString(KEY_OVERRIDE, v) }
        _override.value = v
    }

    fun setServiceEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }
        _serviceEnabled.value = value
    }

    fun setBaseUrl(value: String) {
        val v = value.trim().ifBlank { DeepSeekApi.DEFAULT_BASE_URL }
        prefs.edit { putString(KEY_BASE_URL, v) }
        _baseUrl.value = v
    }

    /** 生效档位：考虑用户手动覆盖（系统时间不准 / 需要提前摸底时用）。 */
    fun effectiveTier(now: java.time.LocalDateTime = DateUtil.nowBeijing()): TariffTier =
        when (_override.value) {
            OVERRIDE_PEAK -> TariffTier.PEAK
            OVERRIDE_OFF_PEAK -> TariffTier.OFF_PEAK
            else -> PeakScheduler.tierAt(now)
        }

    val isAutoTier: Boolean get() = _override.value == OVERRIDE_AUTO

    companion object {
        const val PREFS = "deepseek_widget_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_INTERVAL = "interval_seconds"
        private const val KEY_TICK = "tick_every_second"
        private const val KEY_OVERRIDE = "tier_override"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_KEEP_REFRESH = "keep_refreshing_in_background"
        private const val KEY_BASE_URL = "base_url"

        const val DEFAULT_INTERVAL = 5
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 600

        const val OVERRIDE_AUTO = "auto"
        const val OVERRIDE_PEAK = "peak"
        const val OVERRIDE_OFF_PEAK = "off_peak"
        val OVERRIDE_MODES = listOf(OVERRIDE_AUTO, OVERRIDE_PEAK, OVERRIDE_OFF_PEAK)

        /** 界面上的刷新间隔选项（秒）。 */
        val INTERVAL_PRESETS = listOf(5, 10, 15, 30, 60, 120, 300)

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}
