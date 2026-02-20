/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.lint.InternalApi

/**
 * An interface which can transform an object of type [T] into a formatted String.
 */
interface Serializer<T : Any> {
    /**
     * Serializes the data into a String.
     * @return the String representing the data or null if any exception occurs
     */
    fun serialize(model: T): String?

}

/**
 * A utility class to serialize a model to a ByteArray safely.
 * If an exception is thrown while serializing the data, null is returned, and a
 * message will be sent to the internalLogger.
 *
 * @param T Data type to serialize.
 * @param model Data object to serialize.
 * @param internalLogger Internal logger.
 */
@Suppress("TooGenericExceptionCaught")
@InternalApi
fun <T : Any> Serializer<T>.serializeToByteArray(
    model: T,
    internalLogger: InternalLogger
): ByteArray? {
    return try {
        val serialized = serialize(model)
        serialized?.toByteArray(Charsets.UTF_8)
    } catch (e: Throwable) {
        DdSdkAndroidCoreLogger(internalLogger).logErrorSerializing(
            modelName = model.javaClass.simpleName,
            throwable = e
        )
        null
    }
}
