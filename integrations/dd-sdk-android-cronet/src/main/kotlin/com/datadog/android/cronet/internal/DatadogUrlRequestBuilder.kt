/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.core.internal.net.HttpSpec
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.rum.resource.buildResourceId
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Suppress("TooManyFunctions") // The number of functions depends on Cronet implementation.
internal class DatadogUrlRequestBuilder(
    internal val url: String,
    internal val delegate: UrlRequest.Builder,
    internal val rumResourceInstrumentation: RumResourceInstrumentation
) : UrlRequest.Builder() {

    internal var method: String = HttpSpec.Method.GET
    internal val annotations = mutableListOf<Any>()
    internal val headers: MutableMap<String, List<String>> = mutableMapOf()
    internal var uploadDataProvider: UploadDataProvider? = null

    override fun setHttpMethod(method: String): UrlRequest.Builder = apply {
        this.method = method
        delegate.setHttpMethod(method)
    }

    override fun addHeader(header: String, value: String?): UrlRequest.Builder = apply {
        headers[header] = listOfNotNull(value)
        delegate.addHeader(header, value)
    }

    override fun setUploadDataProvider(
        uploadDataProvider: UploadDataProvider?,
        executor: Executor?
    ): UrlRequest.Builder = apply {
        this.uploadDataProvider = uploadDataProvider
        delegate.setUploadDataProvider(uploadDataProvider, executor)
    }

    override fun disableCache(): UrlRequest.Builder = apply {
        delegate.disableCache()
    }

    override fun setPriority(priority: Int): UrlRequest.Builder = apply {
        delegate.setPriority(priority)
    }

    override fun allowDirectExecutor(): UrlRequest.Builder = apply {
        delegate.allowDirectExecutor()
    }

    override fun addRequestAnnotation(annotation: Any?): UrlRequest.Builder = apply {
        annotations.add(annotation ?: return@apply)
        delegate.addRequestAnnotation(annotation)
    }

    override fun bindToNetwork(networkHandle: Long): UrlRequest.Builder = apply {
        delegate.bindToNetwork(networkHandle)
    }

    override fun setTrafficStatsTag(tag: Int): UrlRequest.Builder = apply {
        delegate.setTrafficStatsTag(tag)
    }

    override fun setTrafficStatsUid(uid: Int): UrlRequest.Builder = apply {
        delegate.setTrafficStatsUid(uid)
    }

    override fun setRequestFinishedListener(listener: RequestFinishedInfo.Listener?): UrlRequest.Builder = apply {
        delegate.setRequestFinishedListener(listener)
    }

    @UrlRequest.Experimental
    override fun setRawCompressionDictionary(
        dictionarySha256Hash: ByteArray?,
        dictionary: ByteBuffer?,
        dictionaryId: String?
    ): UrlRequest.Builder = apply {
        delegate.setRawCompressionDictionary(dictionarySha256Hash, dictionary, dictionaryId)
    }

    override fun build(): UrlRequest {
        val requestInfo = CronetRequestInfo(
            url = url,
            method = method,
            headers = headers,
            uploadDataProvider = uploadDataProvider,
            annotations = annotations
        )

        addRequestAnnotation(requestInfo)
        addRequestAnnotation(requestInfo.buildResourceId(generateUuid = true))

        return DatadogUrlRequest(
            info = requestInfo,
            delegate = delegate.build(),
            rumResourceInstrumentation = rumResourceInstrumentation
        )
    }
}
