/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.security.Encryption
import java.io.File

internal class EncryptedBatchReaderWriter(
    internal val encryption: Encryption,
    internal val delegate: BatchFileReaderWriter,
    private val internalLogger: InternalLogger
) : BatchFileReaderWriter by delegate {

    override fun serializeToBytes(
        data: RawBatchEvent,
        telemetryContext: TelemetryContext
    ): ByteArray? {
        val encryptedData = encryption.encrypt(data.data)

        if (data.data.isNotEmpty() && encryptedData.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { BAD_ENCRYPTION_RESULT_MESSAGE },
                additionalProperties = telemetryContext.asAttributesMap(bytesLost = data.data.size)
            )
            return null
        }

        val encryptedRawBatchEvent = RawBatchEvent(
            data = encryptedData,
            metadata = encryption.encrypt(data.metadata)
        )
        return delegate.serializeToBytes(encryptedRawBatchEvent, telemetryContext)
    }

    @WorkerThread
    override fun writeBinaryData(
        file: File,
        bytes: ByteArray,
        append: Boolean,
        telemetryContext: TelemetryContext
    ): Boolean {
        return delegate.writeBinaryData(file, bytes, append, telemetryContext)
    }

    @WorkerThread
    override fun readData(
        file: File,
        telemetryContext: TelemetryContext
    ): List<RawBatchEvent> {
        return delegate.readData(file, telemetryContext)
            .map {
                RawBatchEvent(
                    data = if (it.data.isNotEmpty()) encryption.decrypt(it.data) else it.data,
                    metadata = if (it.metadata.isNotEmpty()) encryption.decrypt(it.metadata) else it.metadata
                )
            }
    }

    companion object {
        internal const val BAD_ENCRYPTION_RESULT_MESSAGE = "Encryption of non-empty data produced" +
            " empty result, aborting write operation."
    }
}
