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

package com.buzzqw.bbdns.domain.usecase.networkFilter

import com.buzzqw.bbdns.core.utils.Error
import com.buzzqw.bbdns.core.utils.Result
import com.buzzqw.bbdns.domain.repository.NetworkFilterRepository
import javax.inject.Inject

class DeleteIps @Inject constructor(
    private val networkFilterRepository: NetworkFilterRepository
) {
    suspend fun execute(): Result<Unit, Error> {
        return try {
            networkFilterRepository.deleteIps()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(Error.ServerError(e.message ?: "Error while deleting ips"))
        }
    }
}
