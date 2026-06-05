package com.oppowatch.aichat

import android.app.Application
import com.oppowatch.aichat.data.AppDatabase

class App : Application() {

    companion object {
        private lateinit var instance: App
        val database: AppDatabase by lazy { AppDatabase.getInstance(instance) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 触发 Room 表预创建
        database.openHelper.writableDatabase
    }
}
