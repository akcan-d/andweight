package com.measured.app

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
