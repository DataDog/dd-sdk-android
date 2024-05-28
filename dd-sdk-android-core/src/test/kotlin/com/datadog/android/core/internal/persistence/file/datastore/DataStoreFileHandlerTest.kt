/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHandler
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHandler.Companion.FAILED_TO_SERIALIZE_DATA_ERROR
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHandler.Companion.INVALID_NUMBER_OF_BLOCKS_ERROR
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHandler.Companion.SAME_BLOCK_APPEARS_TWICE_ERROR
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.core.persistence.datastore.DataStoreHandler.Companion.CURRENT_DATASTORE_VERSION
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
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataStoreFileHandlerTest {

    private lateinit var testedDataStoreHandler: DataStoreFileHandler

    @Mock
    lateinit var mockFileReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDataStoreDirectory: File

    @Mock
    lateinit var mockDataStoreFile: File

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockDeserializer: Deserializer<String, ByteArray>

    @Mock
    lateinit var mockTLVBlockFileReader: TLVBlockFileReader

    @Mock
    lateinit var mockDataStoreFileHelper: DataStoreFileHelper

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @TempDir
    lateinit var mockStorageDir: File

    @StringForgery
    lateinit var fakeKey: String

    @StringForgery
    lateinit var fakeDataString: String

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var fakeDataBytes: ByteArray
    private lateinit var blocksReturned: ArrayList<TLVBlock>

    @BeforeEach
    fun setup() {
        fakeDataBytes = fakeDataString.toByteArray(Charsets.UTF_8)

        whenever(mockExecutorService.submit(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
            StubFuture()
        }

        whenever(
            mockDataStoreFileHelper.getDataStoreFile(
                featureName = eq(fakeFeatureName),
                storageDir = eq(mockStorageDir),
                internalLogger = eq(mockInternalLogger),
                key = any()
            )
        ).thenReturn(mockDataStoreFile)

        whenever(mockDataStoreDirectory.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(fakeDataString)
        whenever(mockDeserializer.deserialize(fakeDataString)).thenReturn(fakeDataBytes)

        val versionBlock = createVersionBlock(true)
        val lastUpdateDateBlock = createLastUpdateDateBlock()
        val dataBlock = createDataBlock()
        blocksReturned = arrayListOf(versionBlock, lastUpdateDateBlock, dataBlock)
        whenever(mockTLVBlockFileReader.read(mockDataStoreFile)).thenReturn(blocksReturned)

        testedDataStoreHandler = DataStoreFileHandler(
            executorService = mockExecutorService,
            fileReaderWriter = mockFileReaderWriter,
            featureName = fakeFeatureName,
            internalLogger = mockInternalLogger,
            storageDir = mockStorageDir,
            tlvBlockFileReader = mockTLVBlockFileReader,
            dataStoreFileHelper = mockDataStoreFileHelper
        )
    }

    // region read

    @Test
    fun `M return null W read() { datastore file does not exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)
        var gotNoData = false

        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = CURRENT_DATASTORE_VERSION,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    gotNoData = true
                }
            }
        )

        // Then
        assertThat(gotNoData).isTrue()
    }

    @Test
    fun `M return null W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()
        var gotFailure = false

        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = CURRENT_DATASTORE_VERSION,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    gotFailure = true
                }
            }
        )

        // Then
        assertThat(gotFailure).isTrue()
    }

    @Test
    fun `M log error W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()

        val expectedError = INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, blocksReturned.size)

        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = CURRENT_DATASTORE_VERSION,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    mockInternalLogger.verifyLog(
                        target = InternalLogger.Target.MAINTAINER,
                        level = InternalLogger.Level.ERROR,
                        message = expectedError
                    )
                }

                override fun onNoData() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }
            }
        )
    }

    @Test
    fun `M return no data W value() { explicit version and versions don't match }`() {
        // Given
        var noData = false

        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            version = 99,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    noData = true
                }
            },
            deserializer = mockDeserializer
        )

        // Then
        assertThat(noData).isTrue()
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
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = CURRENT_DATASTORE_VERSION,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    mockInternalLogger.verifyLog(
                        level = InternalLogger.Level.ERROR,
                        target = InternalLogger.Target.MAINTAINER,
                        message = expectedError
                    )
                }

                override fun onNoData() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }
            }
        )
    }

    @Test
    fun `M return deserialized data W read()`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createLastUpdateDateBlock())
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = CURRENT_DATASTORE_VERSION,
            callback = object : DataStoreCallback {
                override fun <T : Any> onSuccess(dataStoreContent: DataStoreContent<T>) {
                    assertThat(dataStoreContent.data).isEqualTo(fakeDataBytes)
                }

                override fun onFailure() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    // should not get here
                    assertThat(1).isEqualTo(2)
                }
            }
        )
    }

    // endregion

    // region write

    @Test
    fun `M not write to file W write() { unable to serialize data }`() {
        // Given
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(null)

        // When
        testedDataStoreHandler.setValue(
            key = fakeKey,
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
        testedDataStoreHandler.setValue(
            key = fakeKey,
            data = fakeDataString,
            serializer = mockSerializer
        )

        // Then
        mockInternalLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            message = FAILED_TO_SERIALIZE_DATA_ERROR
        )
    }

    @Test
    fun `M write to file W setValue()`() {
        // When
        testedDataStoreHandler.setValue(
            key = fakeKey,
            data = fakeDataString,
            serializer = mockSerializer
        )

        // Then
        verify(mockFileReaderWriter).writeData(
            eq(mockDataStoreFile),
            any(),
            eq(false)
        )
    }

    // endregion

    // region removeValue

    @Test
    fun `M call deleteSafe W removeValue() { file exists }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(true)

        // When
        testedDataStoreHandler.removeValue(fakeKey)

        // Then
        verify(mockDataStoreFile).deleteSafe(mockInternalLogger)
    }

    @Test
    fun `M not call deleteSafe W removeValue() { file does not exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        testedDataStoreHandler.removeValue(fakeKey)

        // Then
        verify(mockDataStoreFile, never()).deleteSafe(mockInternalLogger)
    }

    // endregion

    private fun createVersionBlock(valid: Boolean, newVersion: Int = 0): TLVBlock {
        return if (valid) {
            TLVBlock(
                type = TLVBlockType.VERSION_CODE,
                data = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(newVersion).array(),
                internalLogger = mockInternalLogger
            )
        } else {
            TLVBlock(
                type = TLVBlockType.VERSION_CODE,
                data = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(newVersion - 1).array(),
                internalLogger = mockInternalLogger
            )
        }
    }

    private fun createLastUpdateDateBlock(): TLVBlock =
        TLVBlock(
            type = TLVBlockType.LAST_UPDATE_DATE,
            data = ByteBuffer.allocate(Long.SIZE_BYTES)
                .putLong(System.currentTimeMillis())
                .array(),
            internalLogger = mockInternalLogger
        )

    private fun createDataBlock(dataBytes: ByteArray = fakeDataBytes): TLVBlock =
        TLVBlock(
            type = TLVBlockType.DATA,
            data = dataBytes,
            internalLogger = mockInternalLogger
        )

    private class StubFuture : Future<Any> {
        override fun cancel(mayInterruptIfRunning: Boolean) =
            error("Not supposed to be called")

        override fun isCancelled(): Boolean = error("Not supposed to be called")
        override fun isDone(): Boolean = error("Not supposed to be called")
        override fun get(): Any = error("Not supposed to be called")
        override fun get(timeout: Long, unit: TimeUnit?): Any =
            error("Not supposed to be called")
    }
}
