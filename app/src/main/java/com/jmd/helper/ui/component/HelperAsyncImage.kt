/**
 * Package: com.jmd.helper.ui.component
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [HelperAsyncImage].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for HelperAsyncImage.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Android Framework & Jetpack: [androidx.compose.foundation.background], [androidx.compose.foundation.layout.Box], [androidx.compose.foundation.layout.size], [androidx.compose.foundation.shape.RoundedCornerShape]
 */

package com.jmd.helper.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * What it is:
 * Memory-safe, cached asynchronous image composable powered by Coil for Jetpack Compose.
 *
 * What it does:
 * Provides smooth crossfade animations and graceful fallback states for local file URIs and thumbnails.
 *
 * Linked with:
 * - Architecture: [HelperAsyncImage], [HelperAsyncImage]
 */
@Composable
fun HelperAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    if (model == null || (model is String && model.isBlank())) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = contentDescription,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(size * 0.5f)
            )
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .size(size)
            .clip(shape)
    )
}
