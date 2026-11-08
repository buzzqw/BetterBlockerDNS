/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.workDataOf
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.BlocklistUpdateStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@HiltWorker
class RuleDatabaseUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val ruleDatabase: RuleDatabase,
) : CoroutineWorker(context, params) {

    companion object {
        var lastErrors by atomic<MutableList<String>?>(null)

        private const val DATABASE_UPDATE_TIMEOUT = 3600000L

        @SuppressLint("MutableCollectionMutableState")
        var doneNames = MutableStateFlow(mutableListOf<String>())

        private val _isRefreshing = MutableStateFlow(false)

        /** Live state of each list handled by the running update, for the progress dialog. */
        val itemProgress = MutableStateFlow<List<ItemProgress>>(emptyList())
    }

    enum class ItemState { PENDING, DOWNLOADING, DONE, UP_TO_DATE, FAILED }

    data class ItemProgress(
        val title: String,
        val data: String,
        val state: ItemState,
        val message: String? = null,
    )

    private fun setItemState(item: Host, state: ItemState, message: String? = null) {
        itemProgress.value = itemProgress.value.map {
            if (it.data == item.data) it.copy(state = state, message = message) else it
        }
    }

    private val errors = ArrayList<String>()
    private val pending = ArrayList<String>()
    private val done = ArrayList<String>()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        _isRefreshing.value = true

        // Check if this is an auto-update
        val isAutoUpdate = inputData.getBoolean("auto_update", false)
        // The "Update now" button updates only the lists that are in use.
        val enabledOnly = inputData.getBoolean("enabled_only", false)

        // Check if we should update specific lists only
        val targetLists = inputData.getStringArray("target_lists")
        val hostsToProcess = if (targetLists != null && targetLists.isNotEmpty()) {
            config.hosts.items.filter { targetLists.contains(it.data) }
        } else {
            // For auto-updates and manual "Update now", only update enabled lists.
            if (isAutoUpdate || enabledOnly) {
                config.hosts.items.filter { it.state == HostState.DENY }
            } else {
                config.hosts.items
            }
        }

        val updateType = if (isAutoUpdate) "auto-update" else "manual update"
        Logger.info("DNS blocklist $updateType started: ${hostsToProcess.size} lists to process")

        // Set initial progress
        setProgress(workDataOf("progress" to 0, "stage" to "downloading"))

        // Process downloads in parallel
        val jobs = mutableListOf<Deferred<Unit>>()
        var totalJobs = 0
        val updates = hostsToProcess.map { RuleDatabaseItemUpdate(context, this@RuleDatabaseUpdateWorker, it) to it }
            .filter { (update, _) -> update.shouldDownload() }
        itemProgress.value = updates.map { (_, host) -> ItemProgress(host.title, host.data, ItemState.PENDING) }
        updates.forEach { (update, _) ->
            totalJobs++
            val job = async { update.run() }
            job.start()
            jobs.add(job)
        }

        releaseGarbagePermissions()

        // Wait for all downloads with timeout and update progress
        try {
            withTimeout(DATABASE_UPDATE_TIMEOUT) {
                jobs.forEachIndexed { index, job ->
                    job.await()
                    // Update progress: 0-70% for downloading
                    val downloadProgress = ((index + 1) * 70) / totalJobs.coerceAtLeast(1)
                    setProgress(workDataOf("progress" to downloadProgress, "stage" to "downloading"))
                }
            }
        } catch (_: TimeoutCancellationException) {
            Logger.warn("DNS update timeout: Some downloads did not complete within ${DATABASE_UPDATE_TIMEOUT}ms")
        }

        // Update progress for parsing stage
        setProgress(workDataOf("progress" to 75, "stage" to "parsing"))

        // Update domains once after all downloads complete with progress callback
        Logger.info("Reloading domain rules from downloaded files")
        try {
            ruleDatabase.initialize { progress ->
                setProgress(workDataOf("progress" to progress, "stage" to "parsing"))
            }
        } catch (e: Exception) {
            // The files are downloaded: a failed reload must not report the
            // download as failed. The next reload picks them up.
            Logger.error("Reloading domain rules failed: ${e.message}", e)
        }

        FileHelper.cleanupOrphanedCache(config.hosts.items)

        val duration = System.currentTimeMillis() - start
        val successCount = done.size
        val errorCount = errors.size

        Logger.info("DNS blocklist $updateType completed: ${successCount} succeeded, ${errorCount} failed (${duration}ms total)")

        postExecute()

        // Consider it a success if:
        // 1. There were no errors at all, OR
        // 2. There were some errors but at least some succeeded (partial success is still success)
        // This handles both fresh downloads and cases where download fails but cached file was loaded
        if (errors.isNotEmpty() && successCount == 0) {
            // Only fail if ALL operations failed with no successes
            Logger.warn("All downloads failed with ${errorCount} errors")
            Result.failure()
        } else {
            // Set final progress to 100% on success
            setProgress(workDataOf("progress" to 100, "stage" to "complete"))
            if (errors.isNotEmpty()) {
                Logger.info("Completed with ${successCount} successes and ${errorCount} errors (partial success)")
            }
            BlocklistUpdateStatus.recordSuccess(context)
            Result.success()
        }
    }

    private fun releaseGarbagePermissions() {
        val contentResolver = context.contentResolver
        var releasedCount = 0
        for (permission in contentResolver.persistedUriPermissions) {
            if (isGarbage(permission.uri)) {
                contentResolver.releasePersistableUriPermission(permission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                releasedCount++
            }
        }
        if (releasedCount > 0) {
            Logger.debug("Released $releasedCount unused URI permissions")
        }
    }

    private fun isGarbage(uri: Uri): Boolean {
        for (item in config.hosts.items) {
            if (Uri.parse(item.data) == uri) {
                return false
            }
        }
        return true
    }

    @Synchronized
    private fun updateProgressNotification() {
        val builder = StringBuilder()
        for (p in pending) {
            if (builder.isNotEmpty()) {
                builder.append("\n")
            }
            builder.append(p)
        }
    }

    @Synchronized
    private fun postExecute() {
        if (!errors.isEmpty()) {
            lastErrors = errors
        }
        _isRefreshing.value = false
    }

    @Synchronized
    fun addError(item: Host, message: String) {
        Logger.warn("${item.title}: $message")
        errors.add("${item.title}\n$message")
        setItemState(item, ItemState.FAILED, message)
    }

    @Synchronized
    fun addDone(item: Host, upToDate: Boolean = false) {
        setItemState(item, if (upToDate) ItemState.UP_TO_DATE else ItemState.DONE)
        // Don't update domains here - wait until all downloads complete
        pending.remove(item.title)
        done.add(item.title)
        doneNames.value = doneNames.value.toMutableList().apply { add(item.title) }
        updateProgressNotification()
    }
    @Synchronized
    fun addBegin(item: Host) {
        setItemState(item, ItemState.DOWNLOADING)
        pending.add(item.title)
        updateProgressNotification()
    }
}