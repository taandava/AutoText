/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [LifecycleObserver].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for LifecycleObserver.
 *
 * Linked With:
 * - Android Framework & Jetpack: [androidx.lifecycle.DefaultLifecycleObserver], [androidx.lifecycle.LifecycleOwner], [androidx.lifecycle.ProcessLifecycleOwner], [kotlinx.coroutines.flow.MutableStateFlow]
 */

package com.jmd.helper.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What it is:
 * Monitors application-wide foreground/background visibility states using AndroidX ProcessLifecycleOwner.
 *
 * What it does:
 * Allows background dispatch engines and accessibility services to observe app presence without manual activity counting.
 *
 * Linked with:
 * - Architecture: [LifecycleObserver], [AppLifecycleObserver]
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()
    /**
     * What it is:
     * Execution routine for [init].
     *
     * What it does:
     * Coordinates init operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [LifecycleObserver]
     */

    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        AppFileLogger.i("AppLifecycleObserver", "ProcessLifecycleOwner observer registered")
    }
    /**
     * What it is:
     * Execution routine for [onStart].
     *
     * What it does:
     * Coordinates onStart operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [LifecycleObserver]
     */

    override fun onStart(owner: LifecycleOwner) {
        _isAppInForeground.value = true
        AppFileLogger.d("AppLifecycleObserver", "Helper transitioned to FOREGROUND")
    }
    /**
     * What it is:
     * Execution routine for [onStop].
     *
     * What it does:
     * Coordinates onStop operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [LifecycleObserver]
     */

    override fun onStop(owner: LifecycleOwner) {
        _isAppInForeground.value = false
        AppFileLogger.d("AppLifecycleObserver", "Helper transitioned to BACKGROUND")
    }
}
