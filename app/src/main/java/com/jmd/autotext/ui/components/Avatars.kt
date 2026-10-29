

/**
 * Package: com.jmd.autotext.ui.components
 * Module: UI Avatar Layer
 *
 * Contact avatars with initials generation, color hashing, LruCache photo caching,
 * and badge overlays for WhatsApp, SMS, and Call channel indicators.
 *
 * Linked With:
 * - Theme: `ThemeKt`, [com.jmd.autotext.ui.theme.AppDesignTokens]
 * - Screens: `ContactsScreen`, `TasksScreen`
 */
package com.jmd.autotext.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jmd.autotext.ui.theme.AppDesignTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ============================================================================
// REUSABLE BENTO GLASS UI COMPONENTS
// ============================================================================

// ==================== AVATARS & USER GRAPHICS COMPONENTS ====================

/**
 * Memory-efficient LRU cache storing decoded contact photo bitmaps.
 *
 * Implements an in-memory ceiling of 500 bitmaps with synchronized read/write access
 * to eliminate repeated disk I/O and ContentResolver querying during fast scrolling.
 */
object AvatarBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(500) {}

    /**
     * Retrieves a cached [ImageBitmap] by its content URI string.
     *
     * @param uri Device photo content URI string.
     * @return Cached bitmap or null if not yet loaded.
     */
    fun get(uri: String): ImageBitmap? {
        if (uri.isBlank()) return null
        return synchronized(cache) { cache[uri] }
    }

    /**
     * Stores a decoded [ImageBitmap] into the cache.
     *
     * @param uri Device photo content URI string.
     * @param bitmap Decoded Compose [ImageBitmap] instance.
     */
    fun put(uri: String, bitmap: ImageBitmap) {
        if (uri.isBlank()) return
        synchronized(cache) { cache.put(uri, bitmap) }
    }
}

/**
 * Material 3 glassmorphic contact avatar rendering either a cached contact photo or a
 * deterministic hash-based linear gradient with two-letter initials.
 *
 * Features:
 * - Deterministic Linear Gradients: Generates harmonious two-color gradient based on name hash.
 * - Non-blocking Image Loading: Reads ContentResolver stream on IO dispatcher with sub-sampling.
 * - Optional Channel Badge: Overlays channel-specific icon (WhatsApp, SMS, Call) in bottom-right corner.
 *
 * @param name Contact display name used for initials derivation and color hashing.
 * @param modifier Compose [Modifier] for positioning and layout.
 * @param photoUri Device content URI string referencing the contact's thumbnail or photo.
 * @param size Outer avatar diameter (defaults to 38.dp).
 * @param badgeIcon Optional channel badge [ImageVector] displayed in bottom corner.
 * @param badgeColor Background color tint for the badge circle.
 */
@Composable
fun m3Avatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    size: Dp = 38.dp,
    badgeIcon: ImageVector? = null,
    badgeColor: Color = AppDesignTokens.VividEmerald,
) {
    val cleanName = name.trim()
    val initials = remember(cleanName) {
        if (cleanName.isBlank()) "?"
        else {
            val parts = cleanName.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                cleanName.take(2).uppercase()
            }
        }
    }

    val avatarGradient = remember(cleanName) {
        val hash = abs(cleanName.hashCode())
        when (hash % 6) {
            0 -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
            1 -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
            2 -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
            3 -> Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))
            4 -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
            else -> Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
        }
    }

    val context = LocalContext.current
    var contactBitmap by remember(photoUri) { mutableStateOf(AvatarBitmapCache.get(photoUri)) }

    if ((photoUri.isNotBlank()) && (contactBitmap == null)) {
        LaunchedEffect(photoUri) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = photoUri.toUri()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        val bmp = BitmapFactory.decodeStream(stream, null, options)
                        if (bmp != null) {
                            val imgBmp = bmp.asImageBitmap()
                            AvatarBitmapCache.put(photoUri, imgBmp)
                            withContext(Dispatchers.Main) {
                                contactBitmap = imgBmp
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Avatar unavailable - fall back to initials
                }
            }
        }
    }

    val bmp = contactBitmap
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = cleanName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(avatarGradient)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        if (badgeIcon != null) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.42f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(1.dp, AppDesignTokens.VoidBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((size.value * 0.26f).dp)
                )
            }
        }
    }
}

/**
 * Convenience wrapper Composable for [m3Avatar] standardizing contact representation across list views and headers.
 *
 * @param name Contact display name.
 * @param modifier Layout [Modifier].
 * @param photoUri Photo URI string if available.
 * @param size Avatar diameter (defaults to 38.dp).
 * @param badgeIcon Optional channel overlay icon.
 * @param badgeColor Background color for channel badge.
 */
@Composable
fun contactAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    size: Dp = 38.dp,
    badgeIcon: ImageVector? = null,
    badgeColor: Color = AppDesignTokens.VividEmerald,
) = m3Avatar(
    name = name,
    modifier = modifier,
    photoUri = photoUri,
    size = size,
    badgeIcon = badgeIcon,
    badgeColor = badgeColor
)
