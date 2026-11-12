/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [TraceUtils].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for TraceUtils.
 *
 * Linked With:
 * - Android Framework & Jetpack: [androidx.tracing.trace]
 */

package com.jmd.helper.core

import androidx.tracing.trace

/**
 * What it is:
 * Enterprise performance tracing utility integrating AndroidX Tracing and Perfetto.
 *
 * What it does:
 * Enables zero-overhead section profiling for critical automation paths: keyguard dismissal, accessibility traversal, database maintenance, and dispatch loops.
 *
 * Linked with:
 * - Architecture: [TraceUtils], [AppTrace]
 */
object AppTrace {

    inline fun <T> section(name: String, crossinline block: () -> T): T {
        return trace(name) {
            block()
        }
    }
}
