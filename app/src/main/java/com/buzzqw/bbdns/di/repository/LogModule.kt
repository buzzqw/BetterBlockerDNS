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

import com.buzzqw.bbdns.data.local.provider.DatabaseProvider
import com.buzzqw.bbdns.data.repository.LogRepositoryImpl
import com.buzzqw.bbdns.domain.repository.LogRepository
import com.buzzqw.bbdns.domain.usecase.log.AddLog
import com.buzzqw.bbdns.domain.usecase.log.DeleteLog
import com.buzzqw.bbdns.domain.usecase.log.DeleteLogs
import com.buzzqw.bbdns.domain.usecase.log.GetLog
import com.buzzqw.bbdns.domain.usecase.log.GetLogs
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LogModule {
    @Provides
    @Singleton
    fun provideLogRepository(provider: DatabaseProvider): LogRepository {
        return LogRepositoryImpl(provider)
    }

    @Provides
    @Singleton
    fun provideLogUseCases(repository: LogRepository): LogUseCases {
        return LogUseCases(
            getLogs = GetLogs(repository),
            deleteLog = DeleteLog(repository),
            getLog = GetLog(repository),
            addLog = AddLog(repository),
            deleteLogs = DeleteLogs(repository)
        )
    }
}