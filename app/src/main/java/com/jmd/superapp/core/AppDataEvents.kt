package com.jmd.superapp.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global reactive event bus linking data alterations across screens, background engines, and tabs.
 * Emits lightweight change notifications so Compose views dynamically synchronize their state.
 */
object AppDataEvents {
    private val _tasksChanged = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val tasksChanged: SharedFlow<Unit> = _tasksChanged.asSharedFlow()

    private val _campaignsChanged = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val campaignsChanged: SharedFlow<Unit> = _campaignsChanged.asSharedFlow()

    private val _autoReplyChanged = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val autoReplyChanged: SharedFlow<Unit> = _autoReplyChanged.asSharedFlow()

    private val _logsChanged = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val logsChanged: SharedFlow<Unit> = _logsChanged.asSharedFlow()

    fun notifyTasksChanged() {
        _tasksChanged.tryEmit(Unit)
    }

    fun notifyCampaignsChanged() {
        _campaignsChanged.tryEmit(Unit)
    }

    fun notifyAutoReplyChanged() {
        _autoReplyChanged.tryEmit(Unit)
    }

    fun notifyLogsChanged() {
        _logsChanged.tryEmit(Unit)
    }

    fun notifyAllDataChanged() {
        notifyTasksChanged()
        notifyCampaignsChanged()
        notifyAutoReplyChanged()
        notifyLogsChanged()
    }
}
