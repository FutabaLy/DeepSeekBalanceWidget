package com.deepseek.balancewidget.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局状态总线。
 *
 * 前台服务是唯一写入方；桌面插件、通知、App 界面都是读取方。
 * 这样即使 Activity 或插件被销毁重建，也能立刻拿到当前余额与时段，不会出现「空窗期」。
 */
object WidgetStateBus {

    private val _state = MutableStateFlow(WidgetState.empty())
    val state: StateFlow<WidgetState> = _state.asStateFlow()

    fun publish(value: WidgetState) {
        _state.value = value
    }

    fun update(transform: (WidgetState) -> WidgetState) {
        _state.value = transform(_state.value)
    }

    fun current(): WidgetState = _state.value
}
