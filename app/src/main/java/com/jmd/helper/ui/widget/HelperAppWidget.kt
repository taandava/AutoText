/**
 * Package: com.jmd.helper.ui.widget
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [HelperAppWidget].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for HelperAppWidget.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.graphics.Color], [androidx.compose.ui.unit.dp], [androidx.compose.ui.unit.sp], [com.jmd.helper.ui.activity.MainActivity]
 * - Android Framework & Jetpack: [androidx.glance.GlanceId], [androidx.glance.GlanceModifier], [androidx.glance.GlanceTheme], [androidx.glance.action.actionStartActivity]
 */

package com.jmd.helper.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jmd.helper.ui.activity.MainActivity

/**
 * What it is:
 * Modern Jetpack Glance Home Screen Widget for Helper.
 *
 * What it does:
 * Renders on-device automation status and provides 1-tap fast access to scheduled tasks.
 *
 * Linked with:
 * - Architecture: [HelperAppWidget], [HelperAppWidget]
 */
class HelperAppWidget : GlanceAppWidget() {
    /**
     * What it is:
     * Execution routine for [provideGlance].
     *
     * What it does:
     * Coordinates provideGlance operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [HelperAppWidget]
     */

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .background(ColorProvider(Color(0xFF0D1527)))
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .width(8.dp)
                                    .height(8.dp)
                                    .cornerRadius(4.dp)
                                    .background(ColorProvider(Color(0xFF10B981)))
                            ) {}

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            Text(
                                text = "Helper Ready",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        Text(
                            text = "100% On-Device Sovereignty",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF94A3B8)),
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = GlanceModifier.height(4.dp))

                        Text(
                            text = "Tap to open tasks dashboard",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF38BDF8)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * What it is:
 * BroadcastReceiver hosting the GlanceAppWidget lifecycle.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [HelperAppWidgetReceiver].
 *
 * Linked with:
 * - Architecture: [HelperAppWidget], [HelperAppWidgetReceiver]
 */
class HelperAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HelperAppWidget()
}
