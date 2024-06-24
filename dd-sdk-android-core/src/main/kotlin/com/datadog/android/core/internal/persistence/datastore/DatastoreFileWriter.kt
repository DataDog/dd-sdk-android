/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.datastore.ext.toByteArray
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.internal.utils.join
import com.datadog.android.core.persistence.Serializer
import java.io.File

internal class DatastoreFileWriter(
    private val dataStoreFileHelper: DataStoreFileHelper,
    private val featureName: String,
    private val storageDir: File,
    private val internalLogger: InternalLogger,
    private val fileReaderWriter: FileReaderWriter
) {
    @WorkerThread
    internal fun <T : Any> write(
        key: String,
        data: T,
        serializer: Serializer<T>,
        version: Int
    ) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            key = key
        )

        val versionCodeBlock = getVersionCodeBlock(version)
        val dataBlock = getDataBlock(data, serializer)

        // failed to serialize one or more blocks
        if (versionCodeBlock == null || dataBlock == null) {
            return
        }

        val dataToWrite = listOf(versionCodeBlock, dataBlock).join(
            separator = EMPTY_BYTE_ARRAY,
            internalLogger = internalLogger
        )

        fileReaderWriter.writeData(
            file = datastoreFile,
            data = dataToWrite,
            append = false
        )
    }

    @WorkerThread
    internal fun delete(key: String) {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            featureName = featureName,
            storageDir = storageDir,
            key = key
        )

        if (datastoreFile.existsSafe(internalLogger)) {
            datastoreFile.deleteSafe(internalLogger)
        }
    }

    private fun <T : Any> getDataBlock(
        data: T,
        serializer: Serializer<T>
    ): ByteArray? {
        val serializedData = serializer.serialize(data)?.toByteArray()

        if (serializedData == null) {
            logFailedToSerializeDataError()
            return null
        }

        val dataBlock = TLVBlock(
            type = TLVBlockType.DATA,
            data = serializedData,
            internalLogger = internalLogger
        )

        return dataBlock.serialize()
    }

    private fun getVersionCodeBlock(version: Int): ByteArray? {
        val versionCodeByteArray = version.toByteArray()
        val versionBlock = TLVBlock(
            type = TLVBlockType.VERSION_CODE,
            data = versionCodeByteArray,
            internalLogger = internalLogger
        )

        return versionBlock.serialize()
    }

    private fun logFailedToSerializeDataError() {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = { FAILED_TO_SERIALIZE_DATA_ERROR }
        )
    }

    internal companion object {
        internal const val FAILED_TO_SERIALIZE_DATA_ERROR =
            "Write error - Failed to serialize data for the datastore"
        private val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
