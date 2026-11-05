package com.jmd.helper.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors application-wide foreground/background visibility states using AndroidX ProcessLifecycleOwner.
 * Allows background dispatch engines and accessibility services to observe app presence without manual activity counting.
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        AppFileLogger.i("AppLifecycleObserver", "ProcessLifecycleOwner observer registered")
    }

    override fun onStart(owner: LifecycleOwner) {
        _isAppInForeground.value = true
        AppFileLogger.d("AppLifecycleObserver", "Helper transitioned to FOREGROUND")
    }

    override fun onStop(owner: LifecycleOwner) {
        _isAppInForeground.value = false
        AppFileLogger.d("AppLifecycleObserver", "Helper transitioned to BACKGROUND")
    }
}

