@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import com.jmd.autotext.ui.theme.BentoGlassTokens
import kotlin.math.abs

/**
 * High-performance LRU memory cache for decoded contact avatar bitmaps (500 items capacity)
 */
object AvatarBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(500) {}

    fun get(uri: String): ImageBitmap? {
        if (uri.isBlank()) return null
        return synchronized(cache) { cache.get(uri) }
    }

    fun put(uri: String, bitmap: ImageBitmap) {
        if (uri.isBlank()) return
        synchronized(cache) { cache.put(uri, bitmap) }
    }
}

/**
 * Material 3 Contact Avatar with Initials & Dynamic Gradient Palette
 */
@Composable
fun M3Avatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    size: Dp = 38.dp,
    badgeIcon: ImageVector? = null,
    badgeColor: Color = BentoGlassTokens.VividEmerald
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

    // Deterministic palette based on name hash
    val avatarGradient = remember(cleanName) {
        val hash = abs(cleanName.hashCode())
        when (hash % 6) {
            0 -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))) // Indigo / Violet
            1 -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))) // Cyan / Blue
            2 -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))) // Emerald
            3 -> Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48))) // Rose
            4 -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))) // Amber
            else -> Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))) // Purple / Pink
        }
    }

    val context = LocalContext.current
    var contactBitmap by remember(photoUri) { mutableStateOf(AvatarBitmapCache.get(photoUri)) }

    if (photoUri.isNotBlank() && contactBitmap == null) {
        LaunchedEffect(photoUri) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = Uri.parse(photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            // Downsample to avoid decoding full megapixel photos for a 38dp avatar
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        val bmp = BitmapFactory.decodeStream(stream, null, options)
                        if (bmp != null) {
                            val imgBmp = bmp.asImageBitmap()
                            AvatarBitmapCache.put(photoUri, imgBmp)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                contactBitmap = imgBmp
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val bmp = contactBitmap
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = cleanName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentScale = ContentScale.Crop
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
                    .border(1.dp, BentoGlassTokens.VoidBackground, CircleShape),
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
 * Backward compatibility alias for BentoAvatar
 */
@Composable
fun BentoAvatar(
    name: String,
    modifier: Modifier = Modifier,
    photoUri: String = "",
    size: Dp = 38.dp,
    badgeIcon: ImageVector? = null,
    badgeColor: Color = BentoGlassTokens.VividEmerald
) = M3Avatar(
    name = name,
    modifier = modifier,
    photoUri = photoUri,
    size = size,
    badgeIcon = badgeIcon,
    badgeColor = badgeColor
)
