package com.measured.app.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.measured.app.MainActivity
import com.measured.app.domain.AccentTheme

/**
 * Switches the launcher icon's color to match the chosen [AccentTheme] - three activity-aliases
 * all pointing at the same MainActivity, each declaring its own icon, with exactly one enabled
 * at a time (see AndroidManifest.xml).
 *
 * Disabling the alias the currently-running task was actually launched through makes Android
 * force-finish that activity a few seconds later - a well-known side effect of
 * setComponentEnabledSetting. The fix is to track which single alias this process's MainActivity
 * was actually opened through ([recordLaunchAlias]) and never disable that one until the app
 * cold-starts again; every other alias is always safe to disable immediately, because nothing is
 * running under it regardless of whether a previous switch this session happened to leave it
 * enabled.
 *
 * An earlier version of this logic used "is this alias currently enabled" as a proxy for "is it
 * in use," which is wrong - switching themes two or three times in one session left several
 * aliases enabled at once (several differently-colored launcher icons appearing simultaneously),
 * and since the stale-cleanup ran before MainActivity had a chance to say which one it actually
 * needed, it could end up disabling the very component in the middle of launching - closing the
 * app immediately and removing its icon from the launcher entirely.
 *
 * That fix is safe, but on its own it still leaves the old icon sitting in the launcher
 * (deferred) until the app happens to fully restart, which reads as a bug even though nothing's
 * actually wrong. [applyAndRestart] closes that gap: it restarts the task through a direct
 * (non-alias) intent - Android's own supported way to replace a task, not us yanking a component
 * out from under a live one - which makes it safe to retire the old alias immediately afterward
 * instead of waiting.
 */
object AppIconSwitcher {

    private const val PACKAGE = "com.measured.app"
    private val ALIASES = mapOf(
        AccentTheme.SAGE to "$PACKAGE.MainActivitySage",
        AccentTheme.BERRY to "$PACKAGE.MainActivityBerry",
        AccentTheme.OCEAN to "$PACKAGE.MainActivityOcean",
    )

    private const val PREFS_NAME = "icon_switcher"
    private const val PENDING_DISABLE_KEY = "pending_disable_classes"

    // In-memory only, not persisted - which alias className this process's MainActivity was
    // actually launched through, if any. Fixed for the lifetime of the process.
    @Volatile
    private var launchAliasClassName: String? = null

    /** Call once from MainActivity.onCreate, before anything else touches component state. Always
     * re-evaluates (rather than only updating when a match is found) - a direct, non-alias launch
     * (from [applyAndRestart], a notification tap, or the widget) means this task doesn't depend
     * on any alias at all, which must actively clear out whatever alias an *earlier* activity
     * instance in this same process happened to depend on, or that stale value would keep
     * protecting an alias nothing is using anymore. */
    fun recordLaunchAlias(activity: Activity) {
        val launchedClassName = activity.intent?.component?.className
        launchAliasClassName = launchedClassName?.takeIf { ALIASES.containsValue(it) }
        // Only safe to do this now that launchAliasClassName reflects this activity - it protects
        // whichever alias this process actually depends on from being disabled out from under it.
        applyPendingDisables(activity)
    }

    fun apply(context: Context, theme: AccentTheme) {
        val packageManager = context.packageManager
        packageManager.setComponentEnabledSetting(
            ComponentName(PACKAGE, ALIASES.getValue(theme)),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        ALIASES.forEach { (candidate, className) ->
            if (candidate == theme) return@forEach
            if (className == launchAliasClassName) {
                addPendingDisable(context, className)
            } else {
                packageManager.setComponentEnabledSetting(
                    ComponentName(PACKAGE, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    /** Same as [apply], but also closes the window where the old icon lingers in the launcher -
     * restarts through MainActivity directly (not through any alias), which replaces the current
     * task the same way Android itself would (not us disabling a component a live task still
     * considers its root). That direct-launch restart naturally retriggers [recordLaunchAlias],
     * which sees a non-alias launch, correctly clears the old protection, and lets the existing
     * pending-cleanup disable the old alias right away instead of waiting for a real cold start. */
    fun applyAndRestart(context: Context, theme: AccentTheme) {
        apply(context, theme)
        Toast.makeText(context, "Updating the app icon…", Toast.LENGTH_SHORT).show()
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(restartIntent)
    }

    /** Finishes disabling whichever alias was protected during a previous session, now that a
     * fresh process means nothing is running under it - unless this very process turns out to be
     * launched through that same alias again, in which case it stays deferred once more. */
    private fun applyPendingDisables(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(PENDING_DISABLE_KEY, null) ?: return
        if (pending.isEmpty()) return

        val packageManager = context.packageManager
        val stillPending = mutableSetOf<String>()
        pending.forEach { className ->
            if (className == launchAliasClassName) {
                stillPending += className
            } else {
                packageManager.setComponentEnabledSetting(
                    ComponentName(PACKAGE, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        val prefsEditor = prefs.edit()
        if (stillPending.isEmpty()) prefsEditor.remove(PENDING_DISABLE_KEY) else prefsEditor.putStringSet(PENDING_DISABLE_KEY, stillPending)
        prefsEditor.apply()
    }

    private fun addPendingDisable(context: Context, className: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(PENDING_DISABLE_KEY, null) ?: emptySet()
        prefs.edit().putStringSet(PENDING_DISABLE_KEY, existing + className).apply()
    }
}
