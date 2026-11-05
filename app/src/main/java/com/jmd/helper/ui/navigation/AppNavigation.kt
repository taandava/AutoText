package com.jmd.helper.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for Jetpack Navigation Compose (Navigation 2.8+).
 */
sealed interface AppRoute {

    @Serializable
    data object Tasks : AppRoute

    @Serializable
    data class ComposeTask(val taskId: Long = -1L) : AppRoute

    @Serializable
    data object DailyCampaign : AppRoute

    @Serializable
    data object AutoReply : AppRoute

    @Serializable
    data object ContactSequences : AppRoute

    @Serializable
    data object Contacts : AppRoute

    @Serializable
    data object Templates : AppRoute

    @Serializable
    data object Logs : AppRoute

    @Serializable
    data object SystemLogs : AppRoute

    @Serializable
    data object More : AppRoute

    @Serializable
    data object Permissions : AppRoute

    @Serializable
    data object Documentation : AppRoute
}

