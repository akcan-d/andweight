package com.andweight.app

import android.app.Application
import com.andweight.app.data.AndWeightRepository

class AndWeightApplication : Application() {
    lateinit var repository: AndWeightRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AndWeightRepository(this)
    }
}
