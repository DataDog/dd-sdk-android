/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataStoreFileHandlerTest {

    private lateinit var testedDataStoreHandler: DataStoreFileHandler

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockDataStoreFileReader: DatastoreFileReader

    @Mock
    lateinit var mockDeserializer: Deserializer<String, ByteArray>

    @Mock
    lateinit var mockDatastoreFileWriter: DatastoreFileWriter

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockDataStoreWriteCallback: DataStoreWriteCallback

    @StringForgery
    lateinit var fakeFeatureName: String

    @StringForgery
    lateinit var fakeKey: String

    @StringForgery
    lateinit var fakeDataString: String

    private lateinit var fileCallback: DataStoreReadCallback<ByteArray>

    @BeforeEach
    fun setup() {
        whenever(mockExecutorService.execute(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
        }

        fileCallback = object : DataStoreReadCallback<ByteArray> {
            override fun onSuccess(dataStoreContent: DataStoreContent<ByteArray>?) {}
            override fun onFailure() {}
        }

        testedDataStoreHandler = DataStoreFileHandler(
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            dataStoreFileReader = mockDataStoreFileReader,
            datastoreFileWriter = mockDatastoreFileWriter
        )
    }

    @Test
    fun `M call dataStoreReader with version 0 W value() { default version }`() {
        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            callback = fileCallback
        )

        // Then
        verify(mockDataStoreFileReader).read(
            key = fakeKey,
            deserializer = mockDeserializer,
            callback = fileCallback
        )
    }

    @Test
    fun `M call dataStoreReader W value()`(
        @IntForgery fakeVersion: Int
    ) {
        // When
        testedDataStoreHandler.value(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = fakeVersion,
            callback = fileCallback
        )

        // Then
        verify(mockDataStoreFileReader).read(
            key = fakeKey,
            deserializer = mockDeserializer,
            version = fakeVersion,
            callback = fileCallback
        )
    }

    @Test
    fun `M call dataStoreWriter with version 0 W setValue()`(
        @IntForgery fakeVersion: Int
    ) {
        // When
        testedDataStoreHandler.setValue(
            key = fakeKey,
            data = fakeDataString,
            version = fakeVersion,
            callback = mockDataStoreWriteCallback,
            serializer = mockSerializer
        )

        // Then
        verify(mockDatastoreFileWriter).write(
            key = fakeKey,
            data = fakeDataString,
            serializer = mockSerializer,
            callback = mockDataStoreWriteCallback,
            version = fakeVersion
        )
    }

    @Test
    fun `M call dataStoreWriter W removeValue()`() {
        // When
        testedDataStoreHandler.removeValue(
            key = fakeKey,
            callback = mockDataStoreWriteCallback
        )

        // Then
        verify(mockDatastoreFileWriter).delete(
            key = fakeKey,
            callback = mockDataStoreWriteCallback
        )
    }

    @Test
    fun `M call dataStoreWriter W clearAll()`() {
        // When
        testedDataStoreHandler.clearAllData()

        // Then
        verify(mockDatastoreFileWriter).clearAllData()
    }
}
