package com.jmd.helper.core

import androidx.tracing.trace

/**
 * Enterprise performance tracing utility integrating AndroidX Tracing and Perfetto.
 * Enables zero-overhead section profiling for critical automation paths:
 * keyguard dismissal, accessibility traversal, database maintenance, and dispatch loops.
 */
object AppTrace {

    inline fun <T> section(name: String, crossinline block: () -> T): T {
        return trace(name) {
            block()
        }
    }
}
