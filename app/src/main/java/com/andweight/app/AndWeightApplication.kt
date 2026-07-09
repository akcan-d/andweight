package com.andweight.app

import android.app.Application
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository

class AndWeightApplication : Application() {
    lateinit var repository: AndWeightRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AndWeightRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
