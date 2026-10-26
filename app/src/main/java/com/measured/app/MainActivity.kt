package com.measured.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.measured.app.ui.nav.AndWeightApp
import com.measured.app.ui.settings.AppIconSwitcher

/** Key for the extra the "Log weight"/"Log meal" app shortcuts (see res/xml/shortcuts.xml)
 * attach to their launch intent - "weight" or "meal", read in [MainActivity.onCreate] and
 * forwarded down to [com.measured.app.ui.today.TodayScreen] so it opens straight into the
 * relevant entry field instead of just landing on the Today tab as usual. */
const val EXTRA_SHORTCUT_TARGET = "shortcut_target"

/** Health Connect requires every client app to declare an activity that responds to one of these
 * actions, so a user can reach an explanation of the app's data use directly from Health
 * Connect's own "App permissions" screen - not just from inside Measured. [ACTION_RATIONALE_PRE_14]
 * is reached directly (see the intent-filter on this activity in the manifest);
 * [ACTION_RATIONALE_V14] is reached through the `.HealthConnectRationaleAlias` alias, which Health
 * Connect targets on Android 14+ instead. There's no dedicated in-app screen for either - both
 * just open the same privacy policy Settings links to and finish immediately. The onboarding
 * aliases (also declared in the manifest, for when Health Connect launches this app directly from
 * its own "compatible apps" list) need no handling here - they should just open the app normally,
 * which is what happens by default when neither of these matches. */
private const val ACTION_RATIONALE_PRE_14 = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
private const val ACTION_RATIONALE_V14 = "android.intent.action.VIEW_PERMISSION_USAGE"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_RATIONALE_PRE_14 || intent?.action == ACTION_RATIONALE_V14) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            finish()
            return
        }
        // Before anything else - this both protects whichever launcher-icon alias this process
        // was actually opened through from being disabled out from under it, and finishes
        // disabling any other alias left over from a theme switch in a previous session.
        AppIconSwitcher.recordLaunchAlias(this)
        enableEdgeToEdge()
        val shortcutTarget = intent.getStringExtra(EXTRA_SHORTCUT_TARGET)
        setContent {
            AndWeightApp(initialShortcutTarget = shortcutTarget)
        }
    }
}
