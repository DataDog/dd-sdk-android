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
import com.datadog.android.core.internal.remote.model.RemoteConfigSyncMetadata
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.allowThreadDiskReads
import com.datadog.android.internal.utils.allowThreadDiskWrites
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import okhttp3.HttpUrl
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Manages the remote configuration lifecycle: fetches from the CDN, persists to disk,
 * and exposes the latest configuration for feature consumption.
 */
internal interface RemoteConfigService {
    fun syncWithRemote()
    fun getCurrentConfig(): RemoteConfiguration?

    /**
     * Sync/apply bookkeeping for the currently cached configuration version, used to populate
     * the SDK's configuration telemetry event. See "RFC - Remote Configuration Telemetry".
     */
    fun getSyncMetadata(): RemoteConfigSyncMetadata?
    fun stop()

    fun interface Factory {
        fun create(
            remoteConfigurationId: String,
            remoteConfigurationEndpoint: HttpUrl,
            fetcher: RemoteConfigFetcher,
            storageDir: File,
            executor: Executor,
            internalLogger: InternalLogger,
            timeProvider: TimeProvider
        ): RemoteConfigService
    }
}

internal class RemoteConfigServiceImpl(
    private val remoteConfigurationId: String,
    remoteConfigurationEndpoint: HttpUrl,
    private val fetcher: RemoteConfigFetcher,
    storageDir: File,
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val timeProvider: TimeProvider,
    private val fileReaderWriter: FileReaderWriter = FileReaderWriter.create(internalLogger, null)
) : RemoteConfigService {

    private val configFile = File(storageDir, "$remoteConfigurationId.json")
    private val metadataFile = File(storageDir, "$remoteConfigurationId.meta.json")

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

    @Volatile
    private var syncMetadata: RemoteConfigSyncMetadata? = null

    init {
        // Synchronous read on the caller's thread (main thread during SDK init).
        // Acceptable because the file is small and only present after a previous
        // successful fetch — absent on first launch.
        @Suppress("ThreadSafety") // allowThreadDiskReads is the SDK mechanism for safe main-thread disk reads
        cachedConfig = allowThreadDiskReads { readConfigFromDisk() }
        @Suppress("ThreadSafety")
        syncMetadata = allowThreadDiskReads { readMetadataFromDisk() }

        // Reading the cached config back out here IS "apply time" per the RFC: this is the one
        // place a session observes the config it's about to hand to features. Only stamp
        // first_applied when there is actually a config to apply.
        if (cachedConfig != null) {
            @Suppress("ThreadSafety") // allowThreadDiskWrites is the SDK mechanism for safe main-thread disk writes
            allowThreadDiskWrites { stampFirstAppliedIfNeeded() }
        }
    }

    override fun getCurrentConfig(): RemoteConfiguration? = cachedConfig

    override fun getSyncMetadata(): RemoteConfigSyncMetadata? = syncMetadata

    override fun stop() {
        fetcher.release()
    }

    override fun syncWithRemote() {
        executor.executeSafe(SYNC_OPERATION_NAME, internalLogger) {
            fetchAndCache()
        }
    }

    @WorkerThread
    private fun fetchAndCache() {
        val fetchResult = fetcher.fetch(configUrl) ?: return
        val config = parseConfig(fetchResult.body)
        if (config == null) {
            // Evict the bad response from the HTTP cache so the next syncWithRemote()
            // re-fetches from the network rather than serving the unparseable body again.
            fetcher.evictCache()
            return
        }

        configFile.parentFile?.mkdirsSafe(internalLogger)
        val written = fileReaderWriter.writeData(
            file = configFile,
            data = fetchResult.body.toByteArray(Charsets.UTF_8),
            append = false,
            telemetryContext = TelemetryContext(featureName = REMOTE_CONFIG_FEATURE_NAME)
        )
        if (written) {
            cachedConfig = config
            persistSyncMetadata(fetchResult)
        }
    }

    @WorkerThread
    private fun persistSyncMetadata(fetchResult: RemoteConfigFetcher.FetchResult) {
        val metadata = RemoteConfigSyncMetadata(
            configId = remoteConfigurationId,
            versionId = fetchResult.versionId,
            lastModified = fetchResult.lastModified,
            lastSynced = timeProvider.getDeviceTimestampMillis(),
            firstApplied = null,
            syncId = UUID.randomUUID().toString()
        )
        if (writeMetadata(metadata)) {
            syncMetadata = metadata
        }
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun stampFirstAppliedIfNeeded() {
        val metadata = syncMetadata ?: return
        if (metadata.firstApplied != null) return
        val updated = metadata.copy(firstApplied = timeProvider.getDeviceTimestampMillis())
        if (writeMetadata(updated)) {
            syncMetadata = updated
        }
    }

    @WorkerThread
    private fun writeMetadata(metadata: RemoteConfigSyncMetadata): Boolean {
        metadataFile.parentFile?.mkdirsSafe(internalLogger)
        return fileReaderWriter.writeData(
            file = metadataFile,
            data = metadata.toJsonString().toByteArray(Charsets.UTF_8),
            append = false,
            telemetryContext = TelemetryContext(featureName = REMOTE_CONFIG_FEATURE_NAME)
        )
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun readMetadataFromDisk(): RemoteConfigSyncMetadata? {
        if (!metadataFile.existsSafe(internalLogger)) return null
        val bytes = fileReaderWriter.readData(metadataFile, TelemetryContext(featureName = REMOTE_CONFIG_FEATURE_NAME))
        if (bytes.isEmpty()) return null
        return try {
            RemoteConfigSyncMetadata.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: JsonSyntaxException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE_METADATA },
                e
            )
            // Delete the corrupt file; the next successful fetch will write a clean one.
            metadataFile.deleteSafe(internalLogger)
            null
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
        val bytes = fileReaderWriter.readData(configFile, TelemetryContext(featureName = REMOTE_CONFIG_FEATURE_NAME))
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
        internal const val ERROR_PARSE_METADATA = "Failed to parse remote configuration sync metadata"
        internal const val REMOTE_CONFIG_FEATURE_NAME = "remote_config"
    }
}
