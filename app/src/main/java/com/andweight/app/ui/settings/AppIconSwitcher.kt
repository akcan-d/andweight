package com.andweight.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.andweight.app.domain.AccentTheme

/**
 * Switches the launcher icon's color to match the chosen [AccentTheme] - three activity-aliases
 * all pointing at the same MainActivity, each declaring its own icon, with exactly one enabled
 * at a time (see AndroidManifest.xml).
 *
 * Disabling the alias the currently-running task was actually launched through makes Android
 * force-finish that activity a few seconds later - a well-known side effect of
 * setComponentEnabledSetting, and the cause of a crash shortly after switching themes. So the
 * alias that's actually active right now is never disabled immediately; it's recorded instead
 * and only cleaned up the next time the app cold-starts, once nothing is running under it.
 */
object AppIconSwitcher {

    private const val PACKAGE = "com.andweight.app"
    private val ALIASES = mapOf(
        AccentTheme.SAGE to "$PACKAGE.MainActivitySage",
        AccentTheme.BERRY to "$PACKAGE.MainActivityBerry",
        AccentTheme.OCEAN to "$PACKAGE.MainActivityOcean",
    )

    // What AndroidManifest.xml declares for each alias before anything has ever been toggled -
    // PackageManager reports COMPONENT_ENABLED_STATE_DEFAULT until setComponentEnabledSetting
    // has explicitly overridden it, so this is needed to interpret that "default" state.
    private val MANIFEST_DEFAULT_ENABLED = mapOf(
        AccentTheme.SAGE to true,
        AccentTheme.BERRY to false,
        AccentTheme.OCEAN to false,
    )

    private const val PREFS_NAME = "icon_switcher"
    private const val PENDING_DISABLE_KEY = "pending_disable_classes"

    fun apply(context: Context, theme: AccentTheme) {
        val packageManager = context.packageManager
        packageManager.setComponentEnabledSetting(
            ComponentName(PACKAGE, ALIASES.getValue(theme)),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        val stillPending = mutableSetOf<String>()
        ALIASES.forEach { (candidate, className) ->
            if (candidate == theme) return@forEach
            val isActiveNow = isAliasActive(packageManager, className, MANIFEST_DEFAULT_ENABLED.getValue(candidate))
            if (isActiveNow) {
                stillPending += className
            } else {
                packageManager.setComponentEnabledSetting(
                    ComponentName(PACKAGE, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        if (stillPending.isNotEmpty()) {
            addPendingDisables(context, stillPending)
        }
    }

    /** Call once on app start - finishes disabling whichever alias(es) were in use at the time
     * of the last switch(es), now that nothing is running under them. */
    fun applyPendingDisables(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(PENDING_DISABLE_KEY, null) ?: return
        if (pending.isEmpty()) return
        pending.forEach { className ->
            context.packageManager.setComponentEnabledSetting(
                ComponentName(PACKAGE, className),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        prefs.edit().remove(PENDING_DISABLE_KEY).apply()
    }

    private fun isAliasActive(packageManager: PackageManager, className: String, manifestDefault: Boolean): Boolean =
        when (packageManager.getComponentEnabledSetting(ComponentName(PACKAGE, className))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> manifestDefault
        }

    private fun addPendingDisables(context: Context, classNames: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(PENDING_DISABLE_KEY, null) ?: emptySet()
        prefs.edit().putStringSet(PENDING_DISABLE_KEY, existing + classNames).apply()
    }
}
