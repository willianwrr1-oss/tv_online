package com.willian.tvaberta

import android.app.Application
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Repo.init(this)
    }
}
