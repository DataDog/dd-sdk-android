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
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

        setRemoveDataSuccess()
    }

    @Test
    fun `M return false W isPreviouslySentResource { resource was not already sent }`() {
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
    fun `M return true W isPreviouslySentResource { resource was already sent }`() {
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
    fun `M write to datastore W cacheResourceHash`() {
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
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )
    }

    @Test
    fun `M do not use expired date W cacheResourceHash { datastore expired }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true)
        setFetchDataSuccess(mockDataStoreContent)

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
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )

        assertThat(
            resourceHashesEntryCaptor.firstValue.lastUpdateDateNs
        ).isNotEqualTo(mockDataStoreContent.data?.lastUpdateDateNs)
    }

    // region init

    @Test
    fun `M fetch stored entry dataStore W init`() {
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
    fun `M remove datastore entry W init { datastore expired }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true)
        setFetchDataSuccess(mockDataStoreContent)

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        verify(mockDataStoreHandler).removeValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            callback = anyOrNull()
        )
    }

    @Test
    fun `M add stored hashes to known set W init { valid update date }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContentEntry = generateDataStoreContent(forge, isExpired = false)
        setFetchDataSuccess(mockDataStoreContentEntry)

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        mockDataStoreContentEntry.data?.resourceHashes?.forEach {
            assertThat(testedDataStoreManager.isPreviouslySentResource(it)).isTrue()
        }
    }

    @Test
    fun `M return isReady true W init { no data to fetch }`() {
        // Given
        setFetchDataSuccess(null)

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isReady()).isTrue()
    }

    @Test
    fun `M return isReady true W init { fetched entry }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = false)
        setFetchDataSuccess(mockDataStoreContent)

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isReady()).isTrue()
    }

    @Test
    fun `M return isReady true W init { failed to fetch entry }`() {
        // Given
        setFetchDataFailure()

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isReady()).isTrue()
    }

    @Test
    fun `M return isReady true W init { got expired entry, succeeded deleting }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true)
        setFetchDataSuccess(mockDataStoreContent)
        setRemoveDataSuccess()

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isReady()).isTrue()
    }

    @Test
    fun `M return isReady true W init { got expired entry, failed deleting }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true)
        setFetchDataSuccess(mockDataStoreContent)
        setRemoveDataFailure()

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.isReady()).isTrue()
    }

    // endregion

    private fun generateDataStoreContent(
        forge: Forge,
        isExpired: Boolean
    ): DataStoreContent<ResourceHashesEntry> {
        val resourceHashes = forge.aList { aString() }.distinct()
        val fakeVersionCode = forge.anInt(min = 0)
        val entryTime = if (isExpired) {
            System.nanoTime() - DATASTORE_EXPIRATION_NS
        } else {
            System.nanoTime()
        }

        val mockResourceHashesEntry: ResourceHashesEntry = mock {
            whenever(it.resourceHashes).thenReturn(resourceHashes)
            whenever(it.lastUpdateDateNs).thenReturn(entryTime)
        }
        val mockDataStoreContentEntry: DataStoreContent<ResourceHashesEntry> = mock {
            whenever(it.versionCode).thenReturn(fakeVersionCode)
            whenever(it.data).thenReturn(mockResourceHashesEntry)
        }

        return mockDataStoreContentEntry
    }

    private fun setFetchDataSuccess(returnValue: DataStoreContent<ResourceHashesEntry>?) {
        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            val callback = it.getArgument<DataStoreReadCallback<ResourceHashesEntry>>(2)
            callback.onSuccess(returnValue)
        }
    }

    private fun setFetchDataFailure() {
        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = any<DataStoreReadCallback<ResourceHashesEntry>>(),
                deserializer = any()
            )
        ) doAnswer {
            val callback = it.getArgument<DataStoreReadCallback<ResourceHashesEntry>>(2)
            callback.onFailure()
        }
    }

    private fun setRemoveDataSuccess() {
        whenever(
            mockDataStoreHandler.removeValue(any(), any())
        ).doAnswer {
            val callback = it.arguments[1] as DataStoreWriteCallback
            callback.onSuccess()
        }
    }

    private fun setRemoveDataFailure() {
        whenever(
            mockDataStoreHandler.removeValue(any(), any())
        ).doAnswer {
            val callback = it.arguments[1] as DataStoreWriteCallback
            callback.onFailure()
        }
    }
}
