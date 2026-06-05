package com.oppowatch.aichat

import android.app.Application
import androidx.room.Room
import com.oppowatch.aichat.data.database.AppDatabase

/**
 * Application 子类 —— 负责全局初始化，包括 Room 数据库单例。
 *
 * 已在 AndroidManifest.xml 中声明：android:name=".App"
 */
class App : Application() {

    companion object {
        private const val DATABASE_NAME = "aichat.db"

        /** Application 实例，方便非 Context 场景获取。 */
        lateinit var instance: App
            private set

        /** Room 数据库单例，在 [onCreate] 时初始化。 */
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            // 手表 ROM/CPU 资源有限，数据库操作在后台协程中执行，
            // 此处仅构建实例，不开启 allowMainThreadQueries。
            .build()
    }
}
