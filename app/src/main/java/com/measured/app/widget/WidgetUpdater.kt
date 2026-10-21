package com.measured.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Single place every data-mutating operation calls through to keep all the home-screen widgets
 * in sync - see [com.measured.app.data.AndWeightRepository], which calls this after every write. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        WeightLostWidget().updateAll(context)
        PercentProgressWidget().updateAll(context)
        TimeRemainingWidget().updateAll(context)
    }
}
