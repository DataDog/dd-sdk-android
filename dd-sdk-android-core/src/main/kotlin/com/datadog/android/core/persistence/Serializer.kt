/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.persistence

import com.datadog.android.v2.api.InternalLogger
import java.util.Locale

/**
 * An interface which can transform an object of type [T] into a formatted String.
 */
interface Serializer<T : Any> {
    /**
     * Serializes the data into a String.
     * @return the String representing the data or null if any exception occurs
     */
    fun serialize(model: T): String?

    companion object {
        internal const val ERROR_SERIALIZING = "Error serializing %s model"
    }
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
fun <T : Any> Serializer<T>.serializeToByteArray(
    model: T,
    internalLogger: InternalLogger
): ByteArray? {
    return try {
        val serialized = serialize(model)
        serialized?.toByteArray(Charsets.UTF_8)
    } catch (e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { Serializer.ERROR_SERIALIZING.format(Locale.US, model.javaClass.simpleName) },
            e
        )
        null
    }
}
