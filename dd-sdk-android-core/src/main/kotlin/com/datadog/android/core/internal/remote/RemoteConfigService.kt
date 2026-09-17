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
import com.datadog.android.internal.utils.allowThreadDiskWrites
import com.google.gson.JsonParseException
import okhttp3.HttpUrl
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Manages the remote configuration lifecycle: fetches from the CDN, persists to disk,
 * and exposes the configuration that was applied at the start of this process.
 */
internal interface RemoteConfigService {
    fun syncWithRemote()

    /**
     * Returns the configuration applied at process start. Frozen for the lifetime of this
     * instance: a fetch completing mid-session updates disk only, never this value, per the
     * RFC's design — "the updated config only takes effect on the next application launch."
     */
    fun getCurrentConfig(): RemoteConfiguration?

    /**
     * Sync/apply bookkeeping for the currently cached configuration version, used to populate
     * the SDK's configuration telemetry event. See "RFC - Remote Configuration Telemetry".
     * Frozen for the lifetime of this instance, same guarantee as [getCurrentConfig].
     * Always null whenever [getCurrentConfig] is null: metadata is only ever paired with a
     * configuration that was actually applied, so telemetry never reports a version that RUM
     * never applied.
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
    private val metadataFile = File(storageDir, "$remoteConfigurationId.metadata.json")

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

    // Tracks whether the on-disk config file currently needs repair, independently of
    // `cachedConfig` — which is deliberately frozen for this process's lifetime and must not be
    // used as a proxy for "is there a usable file on disk right now." Seeded from `cachedConfig`
    // at construction, then updated as fetches succeed or fail during this process's life.
    @Volatile
    private var configFileNeedsRecovery: Boolean = false

    init {
        // Synchronous reads on the caller's thread (main thread during SDK init).
        // Acceptable because the files are small and only present after a previous
        // successful fetch — absent on first launch.
        @Suppress("ThreadSafety")
        // allowThreadDiskWrites is the SDK mechanism for safe main-thread disk reads and writes
        cachedConfig = allowThreadDiskWrites { readConfigFromDisk() }
        @Suppress("ThreadSafety")
        syncMetadata = if (cachedConfig != null) {
            allowThreadDiskWrites { readMetadataFromDisk() }
        } else {
            // No usable config: clear both files so stale metadata can't misreport an unapplied version as applied.
            allowThreadDiskWrites { deleteRemoteConfigFiles() }
            null
        }
        configFileNeedsRecovery = cachedConfig == null

        // Stamp firstApplied asynchronously — ordering is guaranteed. Only when there is a
        // config to apply.
        if (cachedConfig != null) {
            executor.executeSafe(STAMP_FIRST_APPLIED_OPERATION_NAME, internalLogger) {
                stampFirstAppliedIfNeeded()
            }
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
        if (configFileNeedsRecovery) {
            // Nothing usable on disk — force a genuine fetch, since a fresh cache hit would
            // otherwise be discarded as "nothing new," leaving the file unrepaired until the
            // cache goes stale.
            fetcher.evictCache()
        }
        val fetchResult = fetcher.fetch(configUrl) ?: return
        if (parseConfig(fetchResult.body) == null) {
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
            // Deliberately does not update `cachedConfig`: a fetch completing mid-session must
            // not retroactively change what this process considers "applied." The write above
            // is only for the *next* process's constructor read.
            configFileNeedsRecovery = false
            persistSyncMetadata(fetchResult)
        } else {
            // The network round-trip succeeded and OkHttp cached it, but our own write failed —
            // evict immediately so the next fetch is forced to be genuine instead of silently
            // discarding this same response as a cache hit. Also recorded on configFileNeedsRecovery
            // in case evictCache() itself fails, so the next attempt retries the eviction too.
            configFileNeedsRecovery = true
            fetcher.evictCache()
        }
    }

    @WorkerThread
    private fun persistSyncMetadata(fetchResult: RemoteConfigFetcher.FetchResult) {
        // firstApplied must stay stable for a given version: a genuine re-fetch that returns the
        // same version (e.g. after the HTTP cache was evicted) is still the version the device has
        // already been running, so carry the existing stamp over instead of resetting it.
        // A null versionId is never treated as "the same version" as another null — without a
        // real identifier there is no way to know the content didn't change, so it must reset.
        // "previous" is read from disk, not from the frozen `syncMetadata` field: a second
        // genuine fetch within the same process (e.g. a foreground-triggered resync) must see
        // the first fetch's result, which `syncMetadata` — frozen at construction — never holds.
        val previous = readMetadataFromDisk()
        val newVersionId = fetchResult.versionId
        val firstApplied = if (newVersionId != null && previous?.versionId == newVersionId) {
            previous.firstApplied
        } else {
            null
        }
        val metadata = RemoteConfigSyncMetadata(
            configId = remoteConfigurationId,
            versionId = fetchResult.versionId,
            lastModified = fetchResult.lastModified,
            lastSynced = timeProvider.getDeviceTimestampMillis(),
            firstApplied = firstApplied,
            syncId = UUID.randomUUID().toString()
        )
        // Deliberately does not update `syncMetadata`: same reasoning as `fetchAndCache()` —
        // a fetch completing mid-session must not change what this process considers applied.
        writeMetadata(metadata)
    }

    @WorkerThread
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
            data = metadata.toJson().toString().toByteArray(Charsets.UTF_8),
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
        } catch (e: JsonParseException) {
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
            // Generated enum readers use entries.first { } which throws NoSuchElementException
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
    private fun deleteRemoteConfigFiles() {
        configFile.deleteSafe(internalLogger)
        metadataFile.deleteSafe(internalLogger)
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
            // Delete both files: this config is unusable, and any paired metadata for it would
            // be stale/misleading if kept around (see deleteRemoteConfigFiles()).
            deleteRemoteConfigFiles()
            null
        } catch (e: NoSuchElementException) {
            // Generated enum readers use entries.first { } which throws NoSuchElementException
            // for unknown enum values — treat the same as a parse failure.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_PARSE },
                e
            )
            deleteRemoteConfigFiles()
            null
        }
    }

    internal companion object {
        internal const val API_VERSION = "v1"
        internal const val SYNC_OPERATION_NAME = "remote config sync"
        internal const val STAMP_FIRST_APPLIED_OPERATION_NAME = "rc stamp first applied"
        internal const val ERROR_PARSE = "Failed to parse remote configuration"
        internal const val ERROR_PARSE_METADATA = "Failed to parse remote configuration sync metadata"
        internal const val REMOTE_CONFIG_FEATURE_NAME = "remote_config"
    }
}
