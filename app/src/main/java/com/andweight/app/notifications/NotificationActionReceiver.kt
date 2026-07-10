package com.andweight.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.andweight.app.AndWeightApplication
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
import com.andweight.app.domain.TdeeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

const val ACTION_LOG_USUAL_CALORIES = "com.andweight.app.ACTION_LOG_USUAL_CALORIES"
const val ACTION_LOG_WEIGHT = "com.andweight.app.ACTION_LOG_WEIGHT"
const val EXTRA_WEIGHT_REPLY = "weight_reply"

/** Handles the reminder notification's quick actions, so the most common check-ins can happen
 * without opening the app at all. Whichever field wasn't targeted by the action (if already
 * logged, or simply not part of this action) is left untouched. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as AndWeightApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_LOG_USUAL_CALORIES -> logUsualCalories(app)
                    ACTION_LOG_WEIGHT -> logWeightFromReply(app, intent)
                }
            } finally {
                NotificationHelper.cancel(context)
                pendingResult.finish()
            }
        }
    }

    private suspend fun logUsualCalories(app: AndWeightApplication) {
        val profile = app.repository.getProfileOnce() ?: return
        val today = LocalDate.now()
        val logs = app.repository.logsFlow.first()
        val state = TdeeEngine.compute(profile, logs, today)
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.AVERAGE, state.currentTdee, profile.sex)
        val existing = app.repository.getLogForDate(today)
        app.repository.saveLog(today, existing?.weightKg, resolution.calories, IntakeLevel.AVERAGE, wasExact = false)
    }

    private suspend fun logWeightFromReply(app: AndWeightApplication, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_WEIGHT_REPLY)?.toString() ?: return
        val enteredValue = replyText.trim().toDoubleOrNull() ?: return
        val profile = app.repository.getProfileOnce() ?: return
        val weightKg = profile.weightUnit.toKg(enteredValue)
        val today = LocalDate.now()
        val existing = app.repository.getLogForDate(today)
        app.repository.saveLog(today, weightKg, existing?.calories, existing?.intakeLevel, existing?.wasExactEntry ?: false)
    }
}
