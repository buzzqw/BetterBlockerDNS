/*
 * Copyright (C) 2025-2026 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.buzzqw.bbdns.di.repository

import android.app.Application
import com.buzzqw.bbdns.data.repository.SettingsRepositoryImpl
import com.buzzqw.bbdns.domain.repository.SettingsRepository
import com.buzzqw.bbdns.domain.usecase.preferences.LoadPreferences
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.SavePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(application: Application): SettingsRepository {
        return SettingsRepositoryImpl(application)
    }

    @Provides
    @Suppress
    fun providePreferenceUseCases(repository: SettingsRepository): PreferencesUseCases {
        return PreferencesUseCases(
            loadSettings = LoadPreferences(repository),
            saveSettings = SavePreferences(repository)
        )
    }
}