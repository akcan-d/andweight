package com.measured.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/** A rough countdown to the goal date at the current pace. */
class TimeRemainingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val theme = WidgetTheme.resolve(context)
        val stat = WidgetStats.compute(context, WidgetStats.Metric.TIME_REMAINING)

        provideContent {
            GlanceTheme(colors = theme.colorProviders) {
                MinimalWidgetContent(stat, theme.scheme)
            }
        }
    }
}

class TimeRemainingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeRemainingWidget()
}
