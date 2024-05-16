/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.CURRENT_DATASTORE_VERSION
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler.Companion.DATASTORE_FOLDER_NAME
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler.Companion.FAILED_TO_SERIALIZE_DATA_ERROR
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler.Companion.INVALID_NUMBER_OF_BLOCKS_ERROR
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler.Companion.INVALID_VERSION_ERROR
import com.datadog.android.core.internal.persistence.datastore.FileDataStoreHandler.Companion.SAME_BLOCK_APPEARS_TWICE_ERROR
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.createNewFileSafe
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.tlvformat.FileTLVBlockReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FileDataStoreHandlerTest {
    private lateinit var testedDataStoreHandler: FileDataStoreHandler

    @Mock
    lateinit var mockFileReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockStorageDir: File

    @Mock
    lateinit var mockDataStoreDirectory: File

    @Mock
    lateinit var mockDataStoreFile: File

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockDeserializer: Deserializer<String, ByteArray>

    @Mock
    lateinit var mockFileTLVBlockReader: FileTLVBlockReader

    @Mock
    lateinit var mockDataStoreFileHelper: DataStoreFileHelper

    @StringForgery
    lateinit var fakeSdkInstanceId: String

    @StringForgery
    lateinit var fakeDataStoreFileName: String

    @StringForgery
    lateinit var fakeDataString: String

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var fakeDataBytes: ByteArray
    private lateinit var blocksReturned: ArrayList<TLVBlock>

    @BeforeEach
    fun setup() {
        fakeDataBytes = fakeDataString.toByteArray(Charsets.UTF_8)

        whenever(mockDataStoreDirectory.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(
            mockDataStoreFileHelper.getDataStoreDirectory(
                sdkInstanceId = fakeSdkInstanceId,
                featureName = fakeFeatureName,
                folderName = DATASTORE_FOLDER_NAME.format(Locale.US, CURRENT_DATASTORE_VERSION),
                storageDir = mockStorageDir
            )
        ).thenReturn(mockDataStoreDirectory)
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(
            mockDataStoreFileHelper.getDataStoreFile(
                dataStoreDirectory = mockDataStoreDirectory,
                dataStoreFileName = fakeDataStoreFileName
            )
        ).thenReturn(mockDataStoreFile)

        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(fakeDataString)
        whenever(mockDeserializer.deserialize(fakeDataString)).thenReturn(fakeDataBytes)

        val versionBlock = createVersionBlock(true)
        val lastUpdateDateBlock = createLastUpdateDateBlock(true)
        val dataBlock = createDataBlock()
        blocksReturned = arrayListOf(versionBlock, lastUpdateDateBlock, dataBlock)
        whenever(mockFileTLVBlockReader.all(mockDataStoreFile)).thenReturn(blocksReturned)

        testedDataStoreHandler = FileDataStoreHandler(
            sdkInstanceId = fakeSdkInstanceId,
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger,
            storageDir = mockStorageDir,
            fileTLVBlockReader = mockFileTLVBlockReader,
            dataStoreFileHelper = mockDataStoreFileHelper
        )
    }

    // region read

    @Test
    fun `M return null W read() { datastore file does not exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        val result = testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()

        // When
        val result = testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M log error W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()

        val expectedError = INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, blocksReturned.size)

        // When
        testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        mockInternalLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            message = expectedError
        )
    }

    @Test
    fun `M log error W read() { same block appears twice }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        val expectedError = SAME_BLOCK_APPEARS_TWICE_ERROR.format(Locale.US, TLVBlockType.VERSION_CODE)

        // When
        testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            message = expectedError
        )
    }

    @Test
    fun `M log error W read() { version too old }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(true))
        blocksReturned.add(createVersionBlock(false))
        blocksReturned.add(createDataBlock())

        // When
        testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            message = INVALID_VERSION_ERROR
        )
    }

    @Test
    fun `M delete datastore file W read() { version too old }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(true))
        blocksReturned.add(createVersionBlock(false))
        blocksReturned.add(createDataBlock())

        // When
        testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        verify(mockDataStoreFile).deleteSafe(mockInternalLogger)
    }

    @Test
    fun `M return null W read() { version too old }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(true))
        blocksReturned.add(createVersionBlock(false))
        blocksReturned.add(createDataBlock())

        // When
        val result = testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W read() { last update over threshold }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(false))
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        // When
        val result = testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M delete datastore file W read() { last update over threshold }`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(false))
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        // When
        testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        verify(mockDataStoreFile).deleteSafe(mockInternalLogger)
    }

    @Test
    fun `M return deserialized data W read()`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock(true))
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        // When
        val result = testedDataStoreHandler.read(
            dataStoreFileName = fakeDataStoreFileName,
            deserializer = mockDeserializer,
            featureName = fakeFeatureName,
            version = CURRENT_DATASTORE_VERSION
        )

        // Then
        assertThat(result).isEqualTo(fakeDataBytes)
    }

    // endregion

    // region write

    @Test
    fun `M not write to file W write() { unable to serialize data }`() {
        // Given
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(null)

        // When
        testedDataStoreHandler.write(
            dataStoreFileName = fakeDataStoreFileName,
            featureName = fakeFeatureName,
            serializer = mockSerializer,
            data = fakeDataString
        )

        // Then
        verifyNoInteractions(mockFileReaderWriter)
    }

    @Test
    fun `M log error W write() { unable to serialize data }`() {
        // Given
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(null)

        // When
        testedDataStoreHandler.write(
            dataStoreFileName = fakeDataStoreFileName,
            featureName = fakeFeatureName,
            serializer = mockSerializer,
            data = fakeDataString
        )

        // Then
        mockInternalLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            message = FAILED_TO_SERIALIZE_DATA_ERROR
        )
    }

    @Test
    fun `M create directory paths W write() { directory does not already exist }`() {
        // Given
        whenever(mockDataStoreDirectory.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        testedDataStoreHandler.write(
            dataStoreFileName = fakeDataStoreFileName,
            featureName = fakeFeatureName,
            serializer = mockSerializer,
            data = fakeDataString
        )

        // Then
        verify(mockDataStoreDirectory).mkdirsSafe(mockInternalLogger)
    }

    @Test
    fun `M create new datastore file W write() { file does not already exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        testedDataStoreHandler.write(
            dataStoreFileName = fakeDataStoreFileName,
            featureName = fakeFeatureName,
            serializer = mockSerializer,
            data = fakeDataString
        )

        // Then
        verify(mockDataStoreFile).createNewFileSafe(mockInternalLogger)
    }

    // endregion

    private fun createVersionBlock(valid: Boolean): TLVBlock {
        return if (valid) {
            TLVBlock(
                type = TLVBlockType.VERSION_CODE,
                data = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(CURRENT_DATASTORE_VERSION).array()
            )
        } else {
            TLVBlock(
                type = TLVBlockType.VERSION_CODE,
                data = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(CURRENT_DATASTORE_VERSION - 1).array()
            )
        }
    }

    private fun createLastUpdateDateBlock(valid: Boolean): TLVBlock {
        return if (valid) {
            TLVBlock(
                type = TLVBlockType.LAST_UPDATE_DATE,
                data = ByteBuffer.allocate(Long.SIZE_BYTES)
                    .putLong(System.currentTimeMillis())
                    .array()
            )
        } else {
            TLVBlock(
                type = TLVBlockType.LAST_UPDATE_DATE,
                data = ByteBuffer.allocate(Long.SIZE_BYTES)
                    .putLong(0)
                    .array()
            )
        }
    }

    private fun createDataBlock(): TLVBlock =
        TLVBlock(
            type = TLVBlockType.DATA,
            data = fakeDataBytes
        )
}
