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

package com.buzzqw.bbdns.data.repository

import com.buzzqw.bbdns.data.local.provider.DatabaseProvider
import com.buzzqw.bbdns.domain.model.Log
import com.buzzqw.bbdns.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class LogRepositoryImpl @Inject constructor(
    private val provider: DatabaseProvider
) : LogRepository {

    private val insertCounter = AtomicInteger(0)

    override suspend fun insertLog(log: Log) {
        provider.logDao().insertLog(log)

        // Keep the database bounded: prune old entries and cap the total size.
        // Running this every 200 inserts keeps the cost negligible during normal
        // browsing while preventing the multi-GB log database reported upstream.
        if (insertCounter.incrementAndGet() % 200 == 0) {
            val cutoff = System.currentTimeMillis() - LOG_RETENTION_MS
            provider.logDao().deleteLogsOlderThan(cutoff)
            provider.logDao().trimToLatest(MAX_LOG_ROWS)
        }
    }

    private companion object {
        const val LOG_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_LOG_ROWS = 5000
    }

    override suspend fun deleteLogById(id: Int) {
        provider.logDao().deleteLogById(id)
    }

    override suspend fun deleteLogs() {
        provider.logDao().deleteLogs()
    }

    override suspend fun getLogById(id: Int): Log? {
        return provider.logDao().getLogById(id)
    }

    override suspend fun getAllLogs(): Flow<List<Log>> {
        return provider.logDao().getAllLogs()
    }
}