/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Suppress("TooManyFunctions") // The number of functions depends on Cronet implementation.
internal class DatadogUrlRequestBuilder(
    private val requestContext: DatadogCronetRequestContext,
    internal val cronetInstrumentationStateHolder: CronetInstrumentationStateHolder
) : UrlRequest.Builder() {

    override fun setHttpMethod(method: String): UrlRequest.Builder = apply {
        requestContext.setHttpMethod(method)
    }

    override fun addHeader(header: String, value: String): UrlRequest.Builder = apply {
        requestContext.addHeader(header, value)
    }

    override fun setUploadDataProvider(
        uploadDataProvider: UploadDataProvider?,
        executor: Executor?
    ): UrlRequest.Builder = apply {
        requestContext.setUploadDataProvider(uploadDataProvider, executor)
    }

    override fun disableCache(): UrlRequest.Builder = apply {
        requestContext.disableCache()
    }

    override fun setPriority(priority: Int): UrlRequest.Builder = apply {
        requestContext.setPriority(priority)
    }

    override fun allowDirectExecutor(): UrlRequest.Builder = apply {
        requestContext.allowDirectExecutor()
    }

    override fun addRequestAnnotation(annotation: Any?): UrlRequest.Builder = apply {
        requestContext.addRequestAnnotation(annotation ?: return@apply)
    }

    override fun bindToNetwork(networkHandle: Long): UrlRequest.Builder = apply {
        requestContext.bindToNetwork(networkHandle)
    }

    override fun setTrafficStatsTag(tag: Int): UrlRequest.Builder = apply {
        requestContext.setTrafficStatsTag(tag)
    }

    override fun setTrafficStatsUid(uid: Int): UrlRequest.Builder = apply {
        requestContext.setTrafficStatsUid(uid)
    }

    override fun setRequestFinishedListener(listener: RequestFinishedInfo.Listener?): UrlRequest.Builder = apply {
        requestContext.setRequestFinishedListener(listener)
    }

    @UrlRequest.Experimental
    override fun setRawCompressionDictionary(
        dictionarySha256Hash: ByteArray?,
        dictionary: ByteBuffer?,
        dictionaryId: String?
    ): UrlRequest.Builder = apply {
        requestContext.setRawCompressionDictionary(
            dictionarySha256Hash,
            dictionary,
            dictionaryId
        )
    }

    override fun build() = DatadogUrlRequest(
        requestContext = requestContext,
        cronetInstrumentationStateHolder = cronetInstrumentationStateHolder
    )
}
