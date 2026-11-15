package com.wellbeing.tracker.di

import android.content.Context
import androidx.room.Room
import com.wellbeing.tracker.data.db.WellbeingDao
import com.wellbeing.tracker.data.db.WellbeingDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): WellbeingDatabase =
        Room.databaseBuilder(c, WellbeingDatabase::class.java, "wellbeing.db").build()

    @Provides
    fun dao(db: WellbeingDatabase): WellbeingDao = db.dao()
}
