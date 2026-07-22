/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.telemetry.TelemetryContext
import java.nio.ByteBuffer
import java.util.Locale

internal class TLVBlock(
    val type: TLVBlockType,
    val data: ByteArray,
    val internalLogger: InternalLogger
) {
    @Suppress("ReturnCount")
    internal fun serialize(
        telemetryContext: TelemetryContext,
        maxEntrySize: Int = MAXIMUM_DATA_SIZE_MB
    ): ByteArray? {
        if (data.isEmpty()) return null

        val typeFieldSize = Short.SIZE_BYTES
        val dataLengthFieldSize = Int.SIZE_BYTES
        val dataFieldSize = data.size

        val entrySize = typeFieldSize + dataLengthFieldSize + dataFieldSize

        if (entrySize > maxEntrySize) {
            logEntrySizeExceededError(entrySize, maxEntrySize, telemetryContext)
            return null
        }

        val tlvTypeAsShort = type.rawValue.toShort()

        // capacity is not a negative integer, buffer is not read only,
        // has sufficient capacity and is backed by an array
        @Suppress("UnsafeThirdPartyFunctionCall")
        return ByteBuffer
            .allocate(entrySize)
            .putShort(tlvTypeAsShort)
            .putInt(dataFieldSize)
            .put(data)
            .array()
    }

    private fun logEntrySizeExceededError(
        entrySize: Int,
        maxEntrySize: Int,
        telemetryContext: TelemetryContext
    ) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { BYTE_LENGTH_EXCEEDED_ERROR.format(Locale.US, maxEntrySize, entrySize) },
            additionalProperties = telemetryContext.asAttributesMap(bytesLost = entrySize)
        )
    }

    internal companion object {
        // The maximum length of data (Value) in TLV block defining key data.
        internal const val MAXIMUM_DATA_SIZE_MB = 10 * 1024 * 1024 // 10 mb
        internal const val BYTE_LENGTH_EXCEEDED_ERROR =
            "DataBlock length exceeds limit of %s bytes, was %s"
    }
}
