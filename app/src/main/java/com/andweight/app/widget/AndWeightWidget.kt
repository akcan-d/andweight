package com.andweight.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.andweight.app.AndWeightApplication
import com.andweight.app.MainActivity
import com.andweight.app.domain.TdeeEngine
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A minimal home-screen glance at today's status: the current calorie target, whether today's
 * weight and meals are already logged, and one-tap actions for the most common gaps - all read
 * fresh from the repository on every widget refresh rather than cached, since staleness would be
 * far more confusing than the small cost of recomputing the TDEE state each time.
 */
class AndWeightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce()
        val today = LocalDate.now()
        val logs = app.repository.logsFlow.first()
        val log = logs.firstOrNull { it.date == today }

        val targetCalories = profile?.let { TdeeEngine.compute(it, logs, today).recommendedCalories.roundToInt() }
        val weightLogged = log?.weightKg != null
        val caloriesLogged = log?.calories != null

        provideContent {
            GlanceTheme {
                WidgetContent(
                    hasProfile = profile != null,
                    targetCalories = targetCalories,
                    weightLogged = weightLogged,
                    caloriesLogged = caloriesLogged,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(hasProfile: Boolean, targetCalories: Int?, weightLogged: Boolean, caloriesLogged: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(12.dp).background(GlanceTheme.colors.background)) {
        Text("AndWeight", style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onBackground))

        if (!hasProfile) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                "Open the app to finish setup.",
                style = TextStyle(color = GlanceTheme.colors.onBackground),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            return@Column
        }

        Spacer(GlanceModifier.height(4.dp))
        Text("Target: $targetCalories kcal", style = TextStyle(color = GlanceTheme.colors.onBackground))
        Text(
            if (weightLogged) "Weight: logged" else "Weight: not yet logged",
            style = TextStyle(color = GlanceTheme.colors.onBackground),
        )
        Text(
            if (caloriesLogged) "Meals: logged" else "Meals: not yet logged",
            style = TextStyle(color = GlanceTheme.colors.onBackground),
        )

        Spacer(GlanceModifier.height(8.dp))
        Row {
            if (!caloriesLogged) {
                Text(
                    "Ate usual",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionRunCallback<LogUsualCaloriesAction>()),
                )
                Spacer(GlanceModifier.width(16.dp))
            }
            Text(
                "Open app",
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
        }
    }
}
