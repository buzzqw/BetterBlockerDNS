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

import android.content.Context
import android.net.Uri
import com.buzzqw.bbdns.App.Companion.applicationContext
import com.buzzqw.bbdns.core.logging.Logger
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

object FileHelper{

    @Throws(IOException::class)
    fun openRead(filename: String?): InputStream? =
        try {
            applicationContext.openFileInput(filename)
        } catch (e: FileNotFoundException) {
            null
        }


    @Throws(IOException::class)
    fun openWrite(filename: String): OutputStream {
        val out = applicationContext.getFileStreamPath(filename)

        out.renameTo(applicationContext.getFileStreamPath("$filename.bak"))
        return applicationContext.openFileOutput(filename, Context.MODE_PRIVATE)
    }

    fun getItemFile(item: HostFile): File? =
        if (item.isDownloadable()) {
            try {
                File(
                    applicationContext.filesDir,  // Use internal storage instead of external
                    URLEncoder.encode(item.data, "UTF-8"),
                )
            } catch (e: UnsupportedEncodingException) {
                Logger.error("getItemFile: File failed to decode", e)
                null
            }
        } else {
            null
        }

    fun openItemFile(host: HostFile): InputStreamReader? {
        return if (host.data.startsWith("content://")) {
            try {
                InputStreamReader(applicationContext.contentResolver.openInputStream(Uri.parse(host.data)))
            } catch (e: SecurityException) {
                Logger.error("openItemFile: Cannot open", e)
                throw FileNotFoundException("File noot found")
            }
        } else {
            val itemFile = getItemFile(host) ?: return null
            if (host.isDownloadable()) {
                InputStreamReader(
                    SingleWriterMultipleReaderFile(itemFile).openRead()
                )
            } else {
                FileReader(itemFile)
            }
        }
    }

    /**
     * Deletes cached downloads that are no longer referenced by the active
     * configuration, plus stale temporary files. This keeps the app's private
     * storage from growing after lists are removed or replaced.
     */
    fun cleanupOrphanedCache(items: Collection<HostFile>) {
        val validNames = items.mapNotNull { getItemFile(it)?.name }.toSet()
        applicationContext.filesDir.listFiles()?.forEach { file ->
            try {
                val name = file.name
                val isTemporary = name.endsWith(".dnsnet-new")
                val isDownloadedList = name.contains("%3A")
                if (isTemporary || (isDownloadedList && name !in validNames)) {
                    if (file.delete()) {
                        Logger.debug("Removed orphaned cache file: $name")
                    }
                }
            } catch (e: Exception) {
                Logger.warn("Could not clean cache file ${file.name}: ${e.message}")
            }
        }
    }

    fun <T : Closeable?> closeOrWarn(fd: T, message: String): FileDescriptor? {
        try {
            fd?.close()
        } catch (e: java.lang.Exception) {
            Logger.error("closeOrWarn: $message", e)
        }

        return null
    }
}