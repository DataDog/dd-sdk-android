/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.remote.config

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.remote.config.model.RemoteConfigState
import com.datadog.android.internal.utils.DDCoreResult
import com.datadog.android.internal.utils.allowThreadDiskReads
import com.google.gson.JsonParseException
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.Executor

internal interface RemoteConfigService {

    fun syncWithRemote()

    fun getCurrentConfig(): RemoteConfigState?

    companion object {
        fun create(
            remoteConfigurationId: String,
            remoteConfigurationEndpoint: HttpUrl,
            networkService: RemoteConfigNetworkService,
            storageDir: File,
            executor: Executor,
            internalLogger: InternalLogger
        ): RemoteConfigService {
            return RemoteConfigServiceImpl(
                remoteConfigurationId = remoteConfigurationId,
                remoteConfigurationEndpoint = remoteConfigurationEndpoint,
                networkService = networkService,
                storageDir = storageDir,
                executor = executor,
                internalLogger = internalLogger
            )
        }
    }
}

internal class RemoteConfigServiceImpl(
    remoteConfigurationId: String,
    remoteConfigurationEndpoint: HttpUrl,
    private val networkService: RemoteConfigNetworkService,
    storageDir: File,
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val fileReaderWriter: FileReaderWriter = FileReaderWriter.create(internalLogger, null)
) : RemoteConfigService {

    private val configFile: File = File(storageDir, "$remoteConfigurationId.json")

    private val configUrl: HttpUrl = remoteConfigurationEndpoint.newBuilder()
        .addPathSegment(API_VERSION)
        .addPathSegment("$remoteConfigurationId.json")
        .build()

    @Volatile
    private var cachedConfig: RemoteConfigState? = null

    init {
        cachedConfig = allowThreadDiskReads {
            readConfigFromDisk()
        }
    }

    override fun getCurrentConfig(): RemoteConfigState? {
        return cachedConfig
    }

    override fun syncWithRemote() {
        executor.executeSafe(SYNC_OPERATION_NAME, internalLogger) {
            fetchAndCache()
        }
    }

    @WorkerThread
    private fun fetchAndCache() {
        when (val result = networkService.fetch(configUrl)) {
            is DDCoreResult.Result -> persist(result.result)
            is DDCoreResult.Error -> logFetchError(result.error)
        }
    }

    private fun logFetchError(error: RemoteConfigError) {
        when (error) {
            is RemoteConfigError.ServerError -> logFailure(code = error.code, throwable = null)
            is RemoteConfigError.ClientError -> logFailure(code = error.code, throwable = null)
            is RemoteConfigError.UnknownError -> logFailure(code = null, throwable = error.exception)
            is RemoteConfigError.IOError -> {}
        }
    }

    @WorkerThread
    private fun persist(rawConfig: String) {
        val config = try {
            RemoteConfigState.fromJson(rawConfig)
        } catch (e: JsonParseException) {
            logParseError(e)
            return
        }
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
    private fun readConfigFromDisk(): RemoteConfigState? {
        if (!configFile.existsSafe(internalLogger)) {
            return null
        }
        val bytes = fileReaderWriter.readData(configFile)
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            RemoteConfigState.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: JsonParseException) {
            logParseError(e)
            null
        }
    }

    private fun logFailure(code: Int?, throwable: Throwable?) {
        val attributes = buildMap<String, Any?> {
            code?.let { put(ATTR_RESPONSE_CODE, it) }
        }
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { MESSAGE_FETCH_FAILED },
            throwable = throwable,
            additionalProperties = attributes
        )
    }

    private fun logParseError(throwable: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { MESSAGE_PARSE_FAILED },
            throwable = throwable
        )
    }

    companion object {
        private const val API_VERSION = "v1"
        private const val SYNC_OPERATION_NAME = "remote config sync"

        internal const val MESSAGE_FETCH_FAILED = "remote_config_fetch_failed"
        internal const val MESSAGE_PARSE_FAILED = "failed_to_parse_remote_config_json"
        internal const val ATTR_RESPONSE_CODE = "response_code"
    }
}
