/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.DataStoreHandler.Companion.CURRENT_DATASTORE_VERSION
import com.datadog.android.core.internal.persistence.datastore.ext.toByteArray
import com.datadog.android.core.internal.persistence.datastore.ext.toInt
import com.datadog.android.core.internal.persistence.datastore.ext.toLong
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.createNewFileSafe
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.tlvformat.FileTLVBlockReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.Serializer
import java.io.File
import java.util.Locale

@Suppress("TooManyFunctions")
internal class FileDataStoreHandler(
    private val storageDir: File,
    private val internalLogger: InternalLogger,
    private val fileReaderWriter: FileReaderWriter,
    private val fileTLVBlockReader: FileTLVBlockReader,
    private val dataStoreFileHelper: DataStoreFileHelper
) : DataStoreHandler {
    @WorkerThread
    override fun <T : Any> write(
        dataStoreFileName: String,
        featureName: String,
        serializer: Serializer<T>,
        data: T
    ) {
        val dataStoreDirectory = createDataStoreDirectoryIfNecessary(featureName)
        val dataStoreFile = createDataStoreFileIfNecessary(dataStoreDirectory, dataStoreFileName)

        val lastUpdateBlock = getLastUpdateDateBlock()
        val versionCodeBlock = getVersionCodeBlock()
        val dataBlock = getDataBlock(data, serializer)

        if (lastUpdateBlock == null || versionCodeBlock == null || dataBlock == null) return

        writeToFile(
            dataStoreFile,
            lastUpdateBlock + versionCodeBlock + dataBlock
        )
    }

    @WorkerThread
    override fun <T : Any> read(
        dataStoreFileName: String,
        featureName: String,
        deserializer: Deserializer<String, T>,
        version: Int
    ): T? {
        val dataStoreDirectory = dataStoreFileHelper.getDataStoreDirectory(
            featureName = featureName,
            folderName = DATASTORE_FOLDER_NAME.format(Locale.US, version),
            storageDir = storageDir
        )

        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            dataStoreDirectory = dataStoreDirectory,
            dataStoreFileName = dataStoreFileName
        )

        if (!datastoreFile.existsSafe(internalLogger)) {
            return null // no datastore file found
        }

        return readFromDataStoreFile(datastoreFile, deserializer, fileTLVBlockReader, version)
    }

    @WorkerThread
    private fun writeToFile(dataStoreFile: File, data: ByteArray) {
        fileReaderWriter.writeData(
            file = dataStoreFile,
            data = data,
            append = false
        )
    }

    @WorkerThread
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
            data = serializedData
        )

        return dataBlock.serialize()
    }

    @WorkerThread
    private fun getLastUpdateDateBlock(): ByteArray? {
        val now = System.currentTimeMillis()
        val lastUpdateDateByteArray = now.toByteArray()
        val lastUpdateDateBlock = TLVBlock(
            type = TLVBlockType.LAST_UPDATE_DATE,
            data = lastUpdateDateByteArray
        )

        return lastUpdateDateBlock.serialize()
    }

    @WorkerThread
    private fun getVersionCodeBlock(): ByteArray? {
        val versionCodeByteArray = CURRENT_DATASTORE_VERSION.toByteArray()
        val versionBlock = TLVBlock(
            type = TLVBlockType.VERSION_CODE,
            data = versionCodeByteArray
        )

        return versionBlock.serialize()
    }

    @WorkerThread
    @Suppress("ReturnCount")
    private fun <T : Any> readFromDataStoreFile(
        datastoreFile: File,
        deserializer: Deserializer<String, T>,
        fileTLVBlockReader: FileTLVBlockReader,
        requestedVersion: Int
    ): T? {
        val tlvBlocks = fileTLVBlockReader.all(datastoreFile)

        // there should be as many blocks read as there are block types
        if (tlvBlocks.size != TLVBlockType.values().size) {
            logInvalidNumberOfBlocksError(tlvBlocks.size)
            return null
        }

        val dataStoreContents = tryToMapToDataStoreContents(deserializer, tlvBlocks)
            ?: return null

        val fileVersionIsWrong = dataStoreContents.versionCode != requestedVersion
        val fileIsTooOld = isDataStoreTooOld(dataStoreContents.lastUpdateDate)

        // the version check is a double redundancy. Since we store each file in a folder
        // whose name contains the version it should be impossible to read a file
        // that contains the wrong version.
        if (fileVersionIsWrong) {
            logInvalidVersionError()
        }

        return if (fileIsTooOld || fileVersionIsWrong) {
            datastoreFile.deleteSafe(internalLogger)
            null
        } else {
            dataStoreContents.data
        }
    }

    private fun <T : Any> tryToMapToDataStoreContents(
        deserializer: Deserializer<String, T>,
        tlvBlocks: List<TLVBlock>
    ): DataStoreContents<T>? {
        // map the blocks to the actual types
        val typesToBlocks = mutableMapOf<Any, TLVBlock>()
        for (block in tlvBlocks) {
            val type = block.type
            val blockTypeAlreadyExists = typesToBlocks[type] != null

            // verify that the same block doesn't appear more than once
            if (blockTypeAlreadyExists) {
                logSameBlockAppearsTwiceError(type)
                return null
            }

            typesToBlocks[type] = block
        }

        val lastUpdateBlock = typesToBlocks[TLVBlockType.LAST_UPDATE_DATE]
        val versionCodeBlock = typesToBlocks[TLVBlockType.VERSION_CODE]
        val dataBlock = typesToBlocks[TLVBlockType.DATA]
        return if (lastUpdateBlock == null || versionCodeBlock == null || dataBlock == null) {
            null // this should never happen as we know by this stage that these cannot be null
        } else {
            DataStoreContents(
                lastUpdateDate = lastUpdateBlock.data.toLong(),
                versionCode = versionCodeBlock.data.toInt(),
                data = deserializer.deserialize(String(dataBlock.data))
            )
        }
    }

    private fun isDataStoreTooOld(lastUpdateDate: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastUpdateDate > DATASTORE_EXPIRE_TIME
    }

    private fun logSameBlockAppearsTwiceError(type: TLVBlockType) {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = { SAME_BLOCK_APPEARS_TWICE_ERROR.format(Locale.US, type) }
        )
    }

    private fun logInvalidVersionError() {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { INVALID_VERSION_ERROR }
        )
    }

    private fun logInvalidNumberOfBlocksError(numberOfBlocks: Int) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, numberOfBlocks) }
        )
    }

    private fun logFailedToSerializeDataError() {
        internalLogger.log(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            messageBuilder = { FAILED_TO_SERIALIZE_DATA_ERROR }
        )
    }

    private fun createDataStoreDirectoryIfNecessary(featureName: String): File {
        val dataStoreDirectory = dataStoreFileHelper.getDataStoreDirectory(
            featureName = featureName,
            folderName = DATASTORE_FOLDER_NAME.format(Locale.US, CURRENT_DATASTORE_VERSION),
            storageDir = storageDir
        )

        if (!dataStoreDirectory.existsSafe(internalLogger)) {
            dataStoreDirectory.mkdirsSafe(internalLogger)
        }

        return dataStoreDirectory
    }

    private fun createDataStoreFileIfNecessary(
        dataStoreDirectory: File,
        dataStoreFileName: String
    ): File {
        val datastoreFile = dataStoreFileHelper.getDataStoreFile(
            dataStoreDirectory = dataStoreDirectory,
            dataStoreFileName = dataStoreFileName
        )

        if (!datastoreFile.existsSafe(internalLogger)) {
            datastoreFile.createNewFileSafe(internalLogger)
        }

        return datastoreFile
    }

    internal companion object {
        internal const val DATASTORE_FOLDER_NAME = "datastore_v%s"
        private const val DATASTORE_EXPIRE_TIME = DateUtils.DAY_IN_MILLIS * 30 // 30 days

        internal const val FAILED_TO_SERIALIZE_DATA_ERROR =
            "Write error - Failed to serialize data for the datastore"

        internal const val INVALID_VERSION_ERROR =
            "Read error - datastore file contains wrong version! This should never happen"
        internal const val INVALID_NUMBER_OF_BLOCKS_ERROR =
            "Read error - datastore file contains an invalid number of blocks. Was: %s"
        internal const val SAME_BLOCK_APPEARS_TWICE_ERROR =
            "Read error - same block appears twice in the datastore. Type: %s"
    }
}
