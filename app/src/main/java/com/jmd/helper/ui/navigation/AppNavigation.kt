/**
 * Package: com.jmd.helper.ui.navigation
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppNavigation].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AppNavigation.
 *
 * Linked With:
 * - Android Framework & Jetpack: [kotlinx.serialization.Serializable]
 */

package com.jmd.helper.ui.navigation

import kotlinx.serialization.Serializable

/**
 * What it is:
 * Type-safe navigation destinations for Jetpack Navigation Compose (Navigation 2.8+).
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AppRoute].
 *
 * Linked with:
 * - Architecture: [AppNavigation], [AppRoute]
 */
sealed interface AppRoute {

    /**
     * What it is:
     * Component definition for [Tasks].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Tasks.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object Tasks : AppRoute

    /**
     * What it is:
     * Component definition for [ComposeTask].
     *
     * What it does:
     * Provides domain logic and lifecycle management for ComposeTask.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data class ComposeTask(val taskId: Long = -1L) : AppRoute

    /**
     * What it is:
     * Component definition for [DailyCampaign].
     *
     * What it does:
     * Provides domain logic and lifecycle management for DailyCampaign.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object DailyCampaign : AppRoute

    /**
     * What it is:
     * Component definition for [AutoReply].
     *
     * What it does:
     * Provides domain logic and lifecycle management for AutoReply.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object AutoReply : AppRoute

    /**
     * What it is:
     * Component definition for [Templates].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Templates.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object Templates : AppRoute

    /**
     * What it is:
     * Component definition for [Logs].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Logs.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object Logs : AppRoute

    /**
     * What it is:
     * Component definition for [SystemLogs].
     *
     * What it does:
     * Provides domain logic and lifecycle management for SystemLogs.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object SystemLogs : AppRoute

    /**
     * What it is:
     * Component definition for [More].
     *
     * What it does:
     * Provides domain logic and lifecycle management for More.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object More : AppRoute

    /**
     * What it is:
     * Component definition for [Permissions].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Permissions.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object Permissions : AppRoute

    /**
     * What it is:
     * Component definition for [Documentation].
     *
     * What it does:
     * Provides domain logic and lifecycle management for Documentation.
     *
     * Linked with:
     * - Component: [AppNavigation]
     */
    @Serializable
    data object Documentation : AppRoute
}
