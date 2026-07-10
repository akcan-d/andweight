package com.andweight.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Single place every data-mutating operation calls through to keep the home-screen widget in
 * sync - see [com.andweight.app.data.AndWeightRepository], which calls this after every write. */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        AndWeightWidget().updateAll(context)
    }
}
