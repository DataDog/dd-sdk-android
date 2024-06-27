/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileWriter
import com.datadog.android.core.internal.persistence.datastore.DatastoreFileWriter.Companion.FAILED_TO_SERIALIZE_DATA_ERROR
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataStoreFileWriterTest {
    private lateinit var testedDatastoreFileWriter: DatastoreFileWriter

    @Mock
    lateinit var mockDataStoreFileHelper: DataStoreFileHelper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFileReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockDataStoreWriteCallback: DataStoreWriteCallback

    @Mock
    lateinit var mockDataStoreDirectory: File

    @Mock
    lateinit var mockDataStoreFile: File

    @TempDir
    lateinit var mockStorageDir: File

    @StringForgery
    lateinit var fakeFeatureName: String

    @StringForgery
    lateinit var fakeDataString: String

    @StringForgery
    lateinit var fakeKey: String

    private lateinit var fakeDataBytes: ByteArray

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
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(fakeDataString)

        testedDatastoreFileWriter = DatastoreFileWriter(
            dataStoreFileHelper = mockDataStoreFileHelper,
            featureName = fakeFeatureName,
            internalLogger = mockInternalLogger,
            storageDir = mockStorageDir,
            fileReaderWriter = mockFileReaderWriter
        )
    }

    @Test
    fun `M not write to file W write() { unable to serialize data }`() {
        // Given
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(null)

        // When
        testedDatastoreFileWriter.write(
            key = fakeKey,
            serializer = mockSerializer,
            data = fakeDataString,
            callback = mockDataStoreWriteCallback,
            version = 0
        )

        // Then
        verifyNoInteractions(mockFileReaderWriter)
    }

    @Test
    fun `M log error W write() { unable to serialize data }`() {
        // Given
        whenever(mockSerializer.serialize(fakeDataString)).thenReturn(null)

        // When
        testedDatastoreFileWriter.write(
            key = fakeKey,
            data = fakeDataString,
            serializer = mockSerializer,
            callback = mockDataStoreWriteCallback,
            version = 0
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
        testedDatastoreFileWriter.write(
            key = fakeKey,
            data = fakeDataString,
            serializer = mockSerializer,
            callback = mockDataStoreWriteCallback,
            version = 0
        )

        // Then
        verify(mockFileReaderWriter).writeData(
            eq(mockDataStoreFile),
            any(),
            eq(false)
        )
    }

    @Test
    fun `M call deleteSafe W removeValue() { file exists }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(true)

        // When
        testedDatastoreFileWriter.delete(fakeKey, mockDataStoreWriteCallback)

        // Then
        verify(mockDataStoreFile).deleteSafe(mockInternalLogger)
    }

    @Test
    fun `M not call deleteSafe W removeValue() { file does not exist }`() {
        // Given
        whenever(mockDataStoreFile.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        testedDatastoreFileWriter.delete(fakeKey, mockDataStoreWriteCallback)

        // Then
        verify(mockDataStoreFile, never()).deleteSafe(mockInternalLogger)
    }
}
