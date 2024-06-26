/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_EXPIRATION_NS
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_HASHES_ENTRY_NAME
import com.datadog.android.sessionreplay.model.ResourceHashesEntry
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceDataStoreManagerTest {
    private lateinit var testedDataStoreManager: ResourceDataStoreManager

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockResourceHashesEntrySerializer: Serializer<ResourceHashesEntry>

    @Mock
    lateinit var mockResourceHashesEntryDeserializer: Deserializer<String, ResourceHashesEntry>

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockDataStoreHandler: DataStoreHandler

    @StringForgery
    lateinit var fakeHash: String

    @BeforeEach
    fun setup() {
        whenever(mockFeatureSdkCore.getFeature(SESSION_REPLAY_RESOURCES_FEATURE_NAME))
            .thenReturn(mockFeatureScope)

        whenever(mockFeatureScope.dataStore).thenReturn(mockDataStoreHandler)

        whenever(
            mockDataStoreHandler.removeValue(any(), any())
        ).doAnswer {
            val callback = it.arguments[1] as DataStoreWriteCallback
            callback.onSuccess()
        }

        whenever(
            mockDataStoreHandler.setValue(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                data = any(),
                version = anyOrNull(),
                callback = any(),
                serializer = eq(mockResourceHashesEntrySerializer)
            )
        ).doAnswer {
            val callback = it.arguments[3] as DataStoreWriteCallback
            callback.onSuccess()
        }
    }

    @Test
    fun `M return false W isPreviouslySentResource() { resource was not already sent }`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // When
        val wasSent = testedDataStoreManager.isPreviouslySentResource(fakeHash)

        // Then
        assertThat(wasSent).isFalse()
    }

    @Test
    fun `M return true W isPreviouslySentResource() { resource was already sent }`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )
        testedDataStoreManager.cacheResourceHash(fakeHash)

        // When
        val wasSent = testedDataStoreManager.isPreviouslySentResource(fakeHash)

        // Then
        assertThat(wasSent).isTrue()
    }

    @Test
    fun `M write hash to datastore W cacheResourceHash()`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // When
        testedDataStoreManager.cacheResourceHash(fakeHash)

        // Then
        verify(mockFeatureScope.dataStore).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = any(),
            version = anyOrNull(),
            callback = any(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )
    }

    @Test
    fun `M be threadsafe W cacheResourceHash() { store on one thread, read on another }`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )
        val writeThread = Runnable {
            testedDataStoreManager.cacheResourceHash(fakeHash)
        }

        val readThread = Runnable {
            assertThat(testedDataStoreManager.isPreviouslySentResource(fakeHash))
                .isTrue()
        }

        // When
        writeThread.run()

        // Then
        readThread.run()
    }

    // region init

    @Test
    fun `M query entry from dataStore W init()`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        verify(mockDataStoreHandler).value(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            version = anyOrNull(),
            callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
            deserializer = any()
        )
    }

    @Test
    fun `M overwrite last update date W init() { datastore expired }`(
        @Mock mockDataStoreContentEntry: DataStoreContent<ResourceHashesEntry>,
        @Mock mockResourceHashesEntry: ResourceHashesEntry,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        val fakeHashes = DataStoreContent(
            versionCode = fakeVersionCode,
            data = mockResourceHashesEntry
        )

        whenever(mockDataStoreContentEntry.versionCode).thenReturn(fakeVersionCode)
        val expiredTimestamp = System.nanoTime() - (DATASTORE_EXPIRATION_NS * 2)
        whenever(mockResourceHashesEntry.lastUpdateDateNs).thenReturn(expiredTimestamp)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(mockDataStoreContentEntry)
        }

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(fakeHashes)
        }

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        testedDataStoreManager.cacheResourceHash(fakeHash)

        // Then
        val resourceHashesEntryCaptor = argumentCaptor<ResourceHashesEntry>()
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = resourceHashesEntryCaptor.capture(),
            version = anyOrNull(),
            callback = any(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )

        assertThat(resourceHashesEntryCaptor.firstValue.lastUpdateDateNs).isNotEqualTo(expiredTimestamp)
    }

    @Test
    fun `M remove stored hashes file W init() { datastore expired }`(
        @Mock mockDataStoreContentEntry: DataStoreContent<ResourceHashesEntry>,
        @Mock mockResourceHashesEntry: ResourceHashesEntry,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        val fakeHashes = DataStoreContent(
            versionCode = fakeVersionCode,
            data = mockResourceHashesEntry
        )

        whenever(mockDataStoreContentEntry.versionCode).thenReturn(fakeVersionCode)
        val expiredTime = System.nanoTime() - (DATASTORE_EXPIRATION_NS * 2)
        whenever(mockResourceHashesEntry.lastUpdateDateNs).thenReturn(expiredTime)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(mockDataStoreContentEntry)
        }

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(fakeHashes)
        }

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        verify(mockDataStoreHandler).removeValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            callback = any()
        )
    }

    @Test
    fun `M add stored hashes to known set W init() { valid update date }`(
        @Mock mockDataStoreContentEntry: DataStoreContent<ResourceHashesEntry>,
        @Mock mockResourceHashesEntry: ResourceHashesEntry,
        @StringForgery fakeString: String,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        val fakeSetStrings = hashSetOf(fakeString)
        val validTime = System.nanoTime() - (DATASTORE_EXPIRATION_NS / 2)
        whenever(mockDataStoreContentEntry.versionCode).thenReturn(fakeVersionCode)
        whenever(mockDataStoreContentEntry.data).thenReturn(mockResourceHashesEntry)
        whenever(mockResourceHashesEntry.resourceHashes).thenReturn(fakeSetStrings.toList())
        whenever(mockResourceHashesEntry.lastUpdateDateNs).thenReturn(validTime)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(mockDataStoreContentEntry)
        }

        whenever(mockDataStoreContentEntry.versionCode).thenReturn(fakeVersionCode)
        whenever(mockResourceHashesEntry.resourceHashes).thenReturn(fakeSetStrings.toList())

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onSuccess(mockDataStoreContentEntry)
        }

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isPreviouslySentResource(fakeString))
            .isTrue()
    }

    @Test
    fun `M do nothing W init() { failed to get datastore entry }`() {
        // Given
        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreReadCallback<ResourceHashesEntry>
            callback.onFailure()
        }

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        verify(
            mockDataStoreHandler
        ).value(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            version = anyOrNull(),
            callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
            deserializer = any()
        )

        verifyNoMoreInteractions(mockFeatureScope.dataStore)
    }

    // endregion
}
