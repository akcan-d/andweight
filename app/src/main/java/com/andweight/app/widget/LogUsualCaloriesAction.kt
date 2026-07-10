package com.andweight.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.andweight.app.AndWeightApplication
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
import com.andweight.app.domain.TdeeEngine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Same one-tap "log my usual intake" shortcut as the reminder notification's action button,
 * just triggered from the widget instead. */
class LogUsualCaloriesAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce() ?: return
        val today = LocalDate.now()
        val logs = app.repository.logsFlow.first()
        val state = TdeeEngine.compute(profile, logs, today)
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.AVERAGE, state.currentTdee, profile.sex)
        val existing = app.repository.getLogForDate(today)
        app.repository.saveLog(today, existing?.weightKg, resolution.calories, IntakeLevel.AVERAGE, wasExact = false)
    }
}
