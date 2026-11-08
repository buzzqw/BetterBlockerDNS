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
import com.buzzqw.bbdns.data.repository.NetworkFilterImpl
import com.buzzqw.bbdns.domain.repository.NetworkFilterRepository
import com.buzzqw.bbdns.domain.usecase.networkFilter.AddIp
import com.buzzqw.bbdns.domain.usecase.networkFilter.DeleteIp
import com.buzzqw.bbdns.domain.usecase.networkFilter.DeleteIps
import com.buzzqw.bbdns.domain.usecase.networkFilter.GetIps
import com.buzzqw.bbdns.domain.usecase.networkFilter.NetworkFilterUseCases
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkFilter {
    @Provides
    @Singleton
    fun provideNetworkFilterRepository(provider: DatabaseProvider): NetworkFilterRepository {
        return NetworkFilterImpl(provider)
    }

    @Provides
    @Singleton
    fun provideNetworkFilterCases(repository: NetworkFilterRepository): NetworkFilterUseCases {
        return NetworkFilterUseCases(
            getIps = GetIps(repository),
            deleteIp = DeleteIp(repository),
            addIp = AddIp(repository),
            deleteIps = DeleteIps(repository)
        )
    }
}