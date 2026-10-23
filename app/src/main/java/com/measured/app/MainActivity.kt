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

/** Health Connect requires every client app to declare an activity that responds to this action,
 * so a user can reach an explanation of the app's data use directly from Health Connect's own
 * "App permissions" screen - not just from inside Measured. There's no dedicated in-app screen
 * for it; it just opens the same privacy policy Settings links to and finishes immediately. */
private const val ACTION_HEALTH_CONNECT_PERMISSIONS_RATIONALE =
    "androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_HEALTH_CONNECT_PERMISSIONS_RATIONALE) {
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
