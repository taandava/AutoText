package com.jmd.supersms.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.jmd.supersms.R

/**
 * Utility for rendering high-DPI notification icons.
 * Provides explicit large icon bitmaps to NotificationCompat.Builder to guarantee
 * consistent visual branding across OEM notification shades (Vivo, Samsung, Xiaomi, Oppo)
 * and prevent fallbacks to stale system icon caches.
 */
object NotificationIconHelper {
    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    /**
     * Returns a crisp ARGB_8888 Bitmap of the application's gold emblem icon.
     */
    fun getLargeIcon(context: Context): Bitmap? {
        cachedLargeIcon?.let {
            if (!it.isRecycled) return it
        }
        return synchronized(this) {
            cachedLargeIcon?.let {
                if (!it.isRecycled) return it
            }
            try {
                val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    ?: ContextCompat.getDrawable(context, R.drawable.ic_launcher_fg)
                if (drawable != null) {
                    val density = context.resources.displayMetrics.density
                    val size = (64 * density).toInt().coerceAtLeast(128)
                    val bitmap = createBitmap(size, size)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    cachedLargeIcon = bitmap
                    bitmap
                } else null
            } catch (t: Throwable) {
                null
            }
        }
    }
}
