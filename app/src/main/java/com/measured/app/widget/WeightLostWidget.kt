package com.measured.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/** How much weight has moved toward the goal so far, in the person's own unit. */
class WeightLostWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val theme = WidgetTheme.resolve(context)
        val stat = WidgetStats.compute(context, WidgetStats.Metric.WEIGHT_MOVED)

        provideContent {
            GlanceTheme(colors = theme.colorProviders) {
                MinimalWidgetContent(stat, theme.scheme)
            }
        }
    }
}

class WeightLostWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightLostWidget()
}
