/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.core.internal.net.HttpSpec
import org.chromium.net.UploadDataProvider

internal data class CronetRequestInfo(
    override val url: String,
    override val method: String,
    override val headers: Map<String, List<String>>,
    private val uploadDataProvider: UploadDataProvider?,
    private val annotations: List<Any>
) : RequestInfo {

    override val contentType: String? get() = headers[HttpSpec.Headers.CONTENT_TYPE]?.firstOrNull()

    @Suppress("UNCHECKED_CAST")
    override fun <T> tag(type: Class<out T>): T? = annotations.firstOrNull { type.isInstanceOf(it) } as? T

    override fun contentLength(): Long? = headers[HttpSpec.Headers.CONTENT_LENGTH]
        ?.firstOrNull()
        ?.toLongOrNull()
        ?: uploadDataProvider?.length?.takeIf { it >= 0 }

    private fun Class<*>.isInstanceOf(value: Any) = when (this) {
        Boolean::class.javaPrimitiveType -> value is Boolean
        Byte::class.javaPrimitiveType -> value is Byte
        Char::class.javaPrimitiveType -> value is Char
        Short::class.javaPrimitiveType -> value is Short
        Int::class.javaPrimitiveType -> value is Int
        Long::class.javaPrimitiveType -> value is Long
        Float::class.javaPrimitiveType -> value is Float
        Double::class.javaPrimitiveType -> value is Double
        else -> this.isInstance(value)
    }
}
