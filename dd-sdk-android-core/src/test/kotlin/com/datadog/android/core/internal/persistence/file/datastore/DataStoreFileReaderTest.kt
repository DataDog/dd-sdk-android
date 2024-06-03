/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileReader
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileReader.Companion.INVALID_NUMBER_OF_BLOCKS_ERROR
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileReader.Companion.UNEXPECTED_BLOCKS_ORDER_ERROR
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlock
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockType
import com.datadog.android.core.persistence.datastore.DataStoreCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
internal class DataStoreFileReaderTest {
    private lateinit var testedDatastoreFileReader: DatastoreFileReader

    @Mock
    lateinit var mockDataStoreFileHelper: DataStoreFileHelper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTLVBlockFileReader: TLVBlockFileReader

    @Mock
    lateinit var mockDataStoreFile: File

    @TempDir
    lateinit var mockStorageDir: File

    @Mock
    lateinit var mockDataStoreDirectory: File

    @Mock
    lateinit var mockDeserializer: Deserializer<String, ByteArray>

    @StringForgery
    lateinit var fakeFeatureName: String

    @StringForgery
    lateinit var fakeDataString: String

    @StringForgery
    lateinit var fakeKey: String

    private lateinit var fakeDataBytes: ByteArray
    private lateinit var versionBlock: TLVBlock
    private lateinit var dataBlock: TLVBlock
    private lateinit var blocksReturned: ArrayList<TLVBlock>

    @BeforeEach
    fun setup() {
        fakeDataBytes = fakeDataString.toByteArray(Charsets.UTF_8)

        whenever(
            mockDataStoreFileHelper.getDataStoreFile(
                featureName = eq(fakeFeatureName),
                storageDir = eq(mockStorageDir),
                key = any()
            )
        ).thenReturn(mockDataStoreFile)

        whenever(mockDataStoreDirectory.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(mockDeserializer.deserialize(fakeDataString)).thenReturn(fakeDataBytes)

        versionBlock = createVersionBlock(true)
        dataBlock = createDataBlock()
        blocksReturned = arrayListOf(versionBlock, dataBlock)
        whenever(mockTLVBlockFileReader.read(mockDataStoreFile)).thenReturn(blocksReturned)

        testedDatastoreFileReader = DatastoreFileReader(
            dataStoreFileHelper = mockDataStoreFileHelper,
            featureName = fakeFeatureName,
            internalLogger = mockInternalLogger,
            storageDir = mockStorageDir,
            tlvBlockFileReader = mockTLVBlockFileReader
        )
    }

    @Test
    fun `M return noData W read() { datastore file does not exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)
        var gotNoData = false

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = 0,
            callback = object : DataStoreCallback<ByteArray> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    gotNoData = true
                }
            }
        )

        // Then
        Assertions.assertThat(gotNoData).isTrue()
    }

    @Test
    fun `M log error W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()

        val expectedError = INVALID_NUMBER_OF_BLOCKS_ERROR.format(Locale.US, blocksReturned.size)

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = 0,
            callback = object : DataStoreCallback<ByteArray> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
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
                    Assertions.assertThat(1).isEqualTo(2)
                }
            }
        )
    }

    @Test
    fun `M return noData W value() { explicit version and versions don't match }`() {
        // Given
        var noData = false

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            version = 99,
            callback = object : DataStoreCallback<ByteArray> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    noData = true
                }
            },
            deserializer = mockDeserializer
        )

        // Then
        Assertions.assertThat(noData).isTrue()
    }

    @Test
    fun `M return deserialized data W read()`() {
        // Given
        blocksReturned.clear()
        blocksReturned.add(createVersionBlock(true))
        blocksReturned.add(createDataBlock())

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = 0,
            callback = object : DataStoreCallback<ByteArray> {

                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    Assertions.assertThat(dataStoreContent.data).isEqualTo(fakeDataBytes)
                }

                override fun onFailure() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }
            }
        )
    }

    @Test
    fun `M return onFailure W read() { invalid number of blocks }`() {
        // Given
        blocksReturned.removeLast()
        var gotFailure = false

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = 0,
            callback = object : DataStoreCallback<ByteArray> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onNoData() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    gotFailure = true
                }
            }
        )

        // Then
        Assertions.assertThat(gotFailure).isTrue()
    }

    @Test
    fun `M log unexpectedBlocksOrder error W read() { unexpected block order }`() {
        // Given
        blocksReturned = arrayListOf(dataBlock, versionBlock)
        whenever(mockTLVBlockFileReader.read(mockDataStoreFile)).thenReturn(blocksReturned)

        // When
        testedDatastoreFileReader.read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = 0,
            callback = object : DataStoreCallback<ByteArray> {
                override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>) {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }

                override fun onFailure() {
                    mockInternalLogger.verifyLog(
                        target = InternalLogger.Target.MAINTAINER,
                        level = InternalLogger.Level.ERROR,
                        message = UNEXPECTED_BLOCKS_ORDER_ERROR
                    )
                }

                override fun onNoData() {
                    // should not get here
                    Assertions.assertThat(1).isEqualTo(2)
                }
            }
        )
    }

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

    private fun createDataBlock(dataBytes: ByteArray = fakeDataBytes): TLVBlock =
        TLVBlock(
            type = TLVBlockType.DATA,
            data = dataBytes,
            internalLogger = mockInternalLogger
        )
}
