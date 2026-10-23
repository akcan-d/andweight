package com.measured.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/** What percent of the way to the goal the person has come. */
class PercentProgressWidget : GlanceAppWidget() {
    // Exact rather than a handful of fixed breakpoints - see MinimalWidgetContent for why.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val theme = WidgetTheme.resolve(context)
        val stat = WidgetStats.compute(context, WidgetStats.Metric.PERCENT)

        provideContent {
            GlanceTheme(colors = theme.colorProviders) {
                MinimalWidgetContent(stat, theme.scheme)
            }
        }
    }
}

class PercentProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PercentProgressWidget()
}
