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

package com.buzzqw.bbdns.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.buzzqw.bbdns.core.utils.constants.AppConstants
import com.buzzqw.bbdns.data.local.dao.ApplicationDao
import com.buzzqw.bbdns.data.local.dao.LogDao
import com.buzzqw.bbdns.data.local.dao.NetworkFilterDao
import com.buzzqw.bbdns.data.database.dao.CustomDomainDao
import com.buzzqw.bbdns.data.database.entity.CustomDomainEntity
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.domain.model.Ip
import com.buzzqw.bbdns.domain.model.Log

@Database(
    entities = [Application::class, Log::class, Ip::class, CustomDomainEntity::class],
    version = AppConstants.DatabaseConstants.DATABASE_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun packageDao(): ApplicationDao
    abstract fun logDao(): LogDao
    abstract fun blockedDao() : NetworkFilterDao
    abstract fun customDomainDao(): CustomDomainDao
}