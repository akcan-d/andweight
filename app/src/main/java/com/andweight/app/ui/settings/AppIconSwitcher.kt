package com.andweight.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.andweight.app.domain.AccentTheme

/**
 * Switches the launcher icon's color to match the chosen [AccentTheme] - three activity-aliases
 * all pointing at the same MainActivity, each declaring its own icon, with exactly one enabled
 * at a time (see AndroidManifest.xml). Android remembers the enabled/disabled state itself, so
 * nothing needs to be re-applied on launch; this only runs when the person changes the setting.
 */
object AppIconSwitcher {

    private const val PACKAGE = "com.andweight.app"
    private val ALIASES = mapOf(
        AccentTheme.SAGE to "$PACKAGE.MainActivitySage",
        AccentTheme.BERRY to "$PACKAGE.MainActivityBerry",
        AccentTheme.OCEAN to "$PACKAGE.MainActivityOcean",
    )

    fun apply(context: Context, theme: AccentTheme) {
        val packageManager = context.packageManager
        ALIASES.forEach { (candidate, className) ->
            val state = if (candidate == theme) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(PACKAGE, className),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
