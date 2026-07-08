/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.utils.allowThreadDiskReads
import com.google.gson.JsonParseException
import okhttp3.Call
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.Executor

/**
 * Manages the remote configuration lifecycle: fetches from the CDN, persists to disk,
 * and exposes the latest configuration for feature consumption.
 */
internal interface RemoteConfigService {
    fun syncWithRemote()
    fun getCurrentConfig(): RemoteConfiguration?

    fun interface Factory {
        fun create(
            remoteConfigurationId: String,
            remoteConfigurationEndpoint: HttpUrl,
            callFactory: Call.Factory,
            storageDir: File,
            executor: Executor,
            internalLogger: InternalLogger
        ): RemoteConfigService
    }
}

internal class RemoteConfigServiceImpl(
    remoteConfigurationId: String,
    remoteConfigurationEndpoint: HttpUrl,
    private val fetcher: RemoteConfigFetcher,
    storageDir: File,
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val fileReaderWriter: FileReaderWriter = FileReaderWriter.create(internalLogger, null)
) : RemoteConfigService {

    private val configFile = File(storageDir, "$remoteConfigurationId.json")

    // addPathSegment throws IllegalArgumentException for invalid URL chars (e.g. '?', '#').
    // remoteConfigurationId is a UUID-format opaque ID from the Datadog UI, which only
    // contains hex chars and hyphens — all valid URL path segment characters.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private val configUrl: HttpUrl = remoteConfigurationEndpoint.newBuilder()
        .addPathSegment(API_VERSION)
        .addPathSegment("$remoteConfigurationId.json")
        .build()

    @Volatile
    private var cachedConfig: RemoteConfiguration? = null

    init {
        // Synchronous read on the caller's thread (main thread during SDK init).
        // Acceptable because the file is small and only present after a previous
        // successful fetch — absent on first launch.
        @Suppress("ThreadSafety") // allowThreadDiskReads is the SDK mechanism for safe main-thread disk reads
        cachedConfig = allowThreadDiskReads { readConfigFromDisk() }
    }

    override fun getCurrentConfig(): RemoteConfiguration? = cachedConfig

    override fun syncWithRemote() {
        executor.executeSafe(SYNC_OPERATION_NAME, internalLogger) {
            fetchAndCache()
        }
    }

    @WorkerThread
    private fun fetchAndCache() {
        val rawConfig = fetcher.fetch(configUrl) ?: return
        val config = parseConfig(rawConfig) ?: return

        configFile.parentFile?.mkdirsSafe(internalLogger)
        val written = fileReaderWriter.writeData(
            file = configFile,
            data = rawConfig.toByteArray(Charsets.UTF_8),
            append = false
        )
        if (written) {
            cachedConfig = config
        }
    }

    @WorkerThread
    private fun parseConfig(rawConfig: String): RemoteConfiguration? {
        return try {
            RemoteConfiguration.fromJson(rawConfig)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE },
                e
            )
            null
        } catch (e: NoSuchElementException) {
            // Generated enum readers use values().first { } which throws NoSuchElementException
            // for unknown enum values — treat the same as a parse failure.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE },
                e
            )
            null
        }
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun readConfigFromDisk(): RemoteConfiguration? {
        if (!configFile.existsSafe(internalLogger)) return null
        val bytes = fileReaderWriter.readData(configFile)
        if (bytes.isEmpty()) return null
        return try {
            RemoteConfiguration.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE },
                e
            )
            // Delete the corrupt file so the next successful fetch can write a clean one.
            configFile.deleteSafe(internalLogger)
            null
        } catch (e: NoSuchElementException) {
            // Generated enum readers use values().first { } which throws NoSuchElementException
            // for unknown enum values — treat the same as a parse failure.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE },
                e
            )
            // Delete the corrupt file so the next successful fetch can write a clean one.
            configFile.deleteSafe(internalLogger)
            null
        }
    }

    internal companion object {
        internal const val API_VERSION = "v1"
        internal const val SYNC_OPERATION_NAME = "remote config sync"
        internal const val ERROR_PARSE = "Failed to parse remote configuration"
    }
}
