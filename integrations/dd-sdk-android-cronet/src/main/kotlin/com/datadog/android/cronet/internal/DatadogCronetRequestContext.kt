/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.cronet.DatadogCronetEngine
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.rum.internal.net.RumResourceInstrumentation.Companion.buildResourceId
import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.internal.net.RequestTraceState
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Suppress("TooManyFunctions")
internal class DatadogCronetRequestContext private constructor(
    internal var url: String,
    private val executor: Executor,
    private val engine: DatadogCronetEngine,
    private val datadogRequestCallback: DatadogRequestCallback,
    private val requestParams: CronetRequestParams,
    private val additionalAnnotations: MutableMap<Class<*>, Any>
) {
    internal constructor(
        url: String,
        executor: Executor,
        engine: DatadogCronetEngine,
        datadogRequestCallback: DatadogRequestCallback
    ) : this(url, executor, engine, datadogRequestCallback, CronetRequestParams(), mutableMapOf())

    internal val method: String
        get() = requestParams.method

    internal val uploadDataProvider: UploadDataProvider?
        get() = requestParams.uploadDataProviderParams?.uploadDataProvider

    internal val headers: Map<String, List<String>>
        get() = requestParams.headers.toMap()

    internal val networkTracingInstrumentation: NetworkTracingInstrumentation?
        get() = engine.networkTracingInstrumentation

    internal val rumResourceInstrumentation: RumResourceInstrumentation?
        get() = engine.rumResourceInstrumentation

    internal val annotations: List<Any>
        get() = additionalAnnotations.values.toList()

    internal fun addHeader(key: String, vararg values: String) {
        values.forEach { value ->
            requestParams.headers.getOrPut(key) { mutableListOf() }
                .add(value)
        }
    }

    internal fun removeHeader(key: String) = requestParams.headers.remove(key)

    internal fun <T> setTag(type: Class<in T>, tag: T?) {
        if (tag == null) {
            additionalAnnotations.remove(type)
        } else {
            additionalAnnotations[type] = tag
        }
    }

    internal fun disableCache() {
        requestParams.disableCache = true
    }

    internal fun allowDirectExecutor() {
        requestParams.allowDirectExecutor = true
    }

    internal fun setPriority(priority: Int) {
        requestParams.priority = priority
    }

    internal fun bindToNetwork(networkHandle: Long) {
        requestParams.networkHandle = networkHandle
    }

    internal fun setTrafficStatsTag(trafficStatsTag: Int) {
        requestParams.trafficStatsTag = trafficStatsTag
    }

    internal fun setTrafficStatsUid(trafficStatsUid: Int) {
        requestParams.trafficStatsUid = trafficStatsUid
    }

    internal fun setRawCompressionDictionary(
        dictionarySha256Hash: ByteArray?,
        dictionary: ByteBuffer?,
        dictionaryId: String?
    ) {
        requestParams.rawCompressionDictionary = CronetRequestParams.RawCompressionDictionary(
            dictionarySha256Hash,
            dictionary,
            dictionaryId
        )
    }

    internal fun setRequestFinishedListener(listener: RequestFinishedInfo.Listener?) {
        requestParams.listener = listener
    }

    internal fun addRequestAnnotation(annotation: Any) {
        additionalAnnotations[annotation::class.java] = annotation
    }

    internal fun setUploadDataProvider(uploadDataProvider: UploadDataProvider?, executor: Executor?) {
        requestParams.uploadDataProviderParams = CronetRequestParams.UploadDataProviderParams(
            uploadDataProvider,
            executor
        )
    }

    internal fun setHttpMethod(method: String) {
        requestParams.method = method
    }

    internal fun copy() = DatadogCronetRequestContext(
        url = url,
        engine = engine,
        executor = executor,
        requestParams = requestParams.deepCopy(),
        datadogRequestCallback = datadogRequestCallback,
        additionalAnnotations = additionalAnnotations.toMutableMap()
    )

    internal fun buildCronetRequest(requestInfo: CronetHttpRequestInfo, tracingState: RequestTraceState?): UrlRequest =
        engine.newDelegateUrlRequestBuilder(url, datadogRequestCallback, executor)
            .applyRequestParams(requestParams)
            .applyAnnotations(annotations)
            .also {
                it.addRequestAnnotation(requestInfo)
                it.addRequestAnnotation(buildResourceId(requestInfo, generateUuid = true))
                if (tracingState != null) it.addRequestAnnotation(tracingState)
            }
            .build()

    internal fun buildRequestInfo() = CronetHttpRequestInfo(this)
}

private fun UrlRequest.Builder.applyRequestParams(params: CronetRequestParams) = apply {
    setHttpMethod(params.method)
    addHeaders(params.headers)
    if (params.disableCache) disableCache()
    if (params.allowDirectExecutor) allowDirectExecutor()
    params.priority?.let(::setPriority)
    params.networkHandle?.let(::bindToNetwork)
    params.trafficStatsTag?.let(::setTrafficStatsTag)
    params.trafficStatsUid?.let(::setTrafficStatsUid)
    params.listener?.let(::setRequestFinishedListener)
    params.rawCompressionDictionary?.let {
        setRawCompressionDictionary(it.dictionarySha256Hash, it.dictionary, it.dictionaryId)
    }
    params.uploadDataProviderParams?.let {
        setUploadDataProvider(it.uploadDataProvider, it.executor)
    }
}

private fun UrlRequest.Builder.applyAnnotations(annotations: List<Any>) = apply {
    annotations.forEach { addRequestAnnotation(it) }
}

private data class CronetRequestParams(
    var method: String = HttpSpec.Method.GET,
    var headers: MutableMap<String, MutableList<String>> = mutableMapOf(),
    var disableCache: Boolean = false,
    var allowDirectExecutor: Boolean = false,
    var priority: Int? = null,
    var networkHandle: Long? = null,
    var trafficStatsTag: Int? = null,
    var trafficStatsUid: Int? = null,
    var rawCompressionDictionary: RawCompressionDictionary? = null,
    var listener: RequestFinishedInfo.Listener? = null,
    var uploadDataProviderParams: UploadDataProviderParams? = null
) {

    fun deepCopy() = copy(
        headers = headers.mapValues { it.value.toMutableList() }.toMutableMap()
    )

    data class UploadDataProviderParams(
        val uploadDataProvider: UploadDataProvider?,
        val executor: Executor?
    )

    data class RawCompressionDictionary(
        val dictionarySha256Hash: ByteArray?,
        val dictionary: ByteBuffer?,
        val dictionaryId: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RawCompressionDictionary

            if (!dictionarySha256Hash.contentEquals(other.dictionarySha256Hash)) return false
            if (dictionary != other.dictionary) return false
            if (dictionaryId != other.dictionaryId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = dictionarySha256Hash?.contentHashCode() ?: 0
            result = 31 * result + (dictionary?.hashCode() ?: 0)
            result = 31 * result + (dictionaryId?.hashCode() ?: 0)
            return result
        }
    }
}
