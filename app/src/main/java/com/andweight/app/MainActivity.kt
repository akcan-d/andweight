package com.andweight.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.andweight.app.ui.nav.AndWeightApp
import com.andweight.app.ui.settings.AppIconSwitcher

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything else - this both protects whichever launcher-icon alias this process
        // was actually opened through from being disabled out from under it, and finishes
        // disabling any other alias left over from a theme switch in a previous session.
        AppIconSwitcher.recordLaunchAlias(this)
        enableEdgeToEdge()
        setContent {
            AndWeightApp()
        }
    }
}
