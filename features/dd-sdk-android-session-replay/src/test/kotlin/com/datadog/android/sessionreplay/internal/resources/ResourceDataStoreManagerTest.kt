/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_EXPIRATION_MS
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_HASHES_ENTRY_NAME
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_VERSION
import com.datadog.android.sessionreplay.model.ResourceHashesEntry
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery
    lateinit var fakeHash: String

    @LongForgery(min = DATASTORE_EXPIRATION_MS + 1, max = Long.MAX_VALUE - 1)
    var fakeCurrentTimestampMs: Long = 0L

    @BeforeEach
    fun setup() {
        whenever(mockFeatureSdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME))
            .thenReturn(mockFeatureScope)

        whenever(mockFeatureScope.dataStore).thenReturn(mockDataStoreHandler)
        whenever(mockFeatureSdkCore.timeProvider).thenReturn(mockTimeProvider)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeCurrentTimestampMs)

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
        testedDataStoreManager.markResourceAsSentIfNew(fakeHash)

        // When
        val wasSent = testedDataStoreManager.isPreviouslySentResource(fakeHash)

        // Then
        assertThat(wasSent).isTrue()
    }

    @Test
    fun `M write to datastore W markResourceAsSentIfNew`() {
        // Given
        setFetchDataSuccess(null)
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // When
        testedDataStoreManager.markResourceAsSentIfNew(fakeHash)

        // Then
        verify(mockFeatureScope.dataStore).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = any(),
            version = eq(DATASTORE_VERSION),
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )
    }

    @Test
    fun `M mark resource once W markResourceAsSentIfNew { concurrent calls }`() {
        // Given
        setFetchDataSuccess(null)
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )
        val concurrentCallCount = 20
        val executor = Executors.newFixedThreadPool(concurrentCallCount)
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(concurrentCallCount)
        val results = ConcurrentLinkedQueue<Boolean>()

        try {
            repeat(concurrentCallCount) {
                executor.execute {
                    startLatch.await()
                    results += testedDataStoreManager.markResourceAsSentIfNew(fakeHash)
                    completionLatch.countDown()
                }
            }

            // When
            startLatch.countDown()

            // Then
            assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(results.count { it }).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M persist pending resource W init { resource marked before fetch completes }`() {
        // Given
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )
        val fetchCallback = argumentCaptor<DataStoreReadCallback<ResourceHashesEntry>>().also {
            verify(mockDataStoreHandler).value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = it.capture(),
                deserializer = any()
            )
        }.firstValue
        assertThat(testedDataStoreManager.markResourceAsSentIfNew(fakeHash)).isTrue()
        verify(mockDataStoreHandler, never()).setValue(
            key = any(),
            data = any<ResourceHashesEntry>(),
            version = any(),
            callback = anyOrNull(),
            serializer = any()
        )

        // When
        fetchCallback.onSuccess(null)

        // Then
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = any(),
            version = eq(DATASTORE_VERSION),
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )
    }

    @Test
    fun `M persist stored and pending hashes W init { resource marked before valid fetch completes }`(
        forge: Forge
    ) {
        // Given
        val storedHash = "stored-$fakeHash"
        val storedContent = generateDataStoreContent(
            forge = forge,
            isExpired = false,
            currentTime = fakeCurrentTimestampMs,
            resourceHashes = listOf(storedHash)
        )
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )
        val fetchCallback = argumentCaptor<DataStoreReadCallback<ResourceHashesEntry>>().also {
            verify(mockDataStoreHandler).value(
                key = eq(DATASTORE_HASHES_ENTRY_NAME),
                version = anyOrNull(),
                callback = it.capture(),
                deserializer = any()
            )
        }.firstValue
        assertThat(testedDataStoreManager.markResourceAsSentIfNew(fakeHash)).isTrue()

        // When
        fetchCallback.onSuccess(storedContent)

        // Then
        val entryCaptor = argumentCaptor<ResourceHashesEntry>()
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = entryCaptor.capture(),
            version = eq(DATASTORE_VERSION),
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )
        assertThat(entryCaptor.firstValue.resourceHashes)
            .containsExactlyInAnyOrder(storedHash, fakeHash)
    }

    @Test
    fun `M do not use expired date W markResourceAsSentIfNew { datastore expired }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true, fakeCurrentTimestampMs)
        setFetchDataSuccess(mockDataStoreContent)

        // When
        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        testedDataStoreManager.markResourceAsSentIfNew(fakeHash)

        // Then
        val resourceHashesEntryCaptor = argumentCaptor<ResourceHashesEntry>()
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = resourceHashesEntryCaptor.capture(),
            version = eq(DATASTORE_VERSION),
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )

        assertThat(
            resourceHashesEntryCaptor.firstValue.lastUpdateDateNs.toLong()
        ).isEqualTo(fakeCurrentTimestampMs)
    }

    @Test
    fun `M refresh stored date W markResourceAsSentIfNew { valid entry was loaded }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(
            forge = forge,
            isExpired = false,
            currentTime = fakeCurrentTimestampMs,
            storedTimestamp = fakeCurrentTimestampMs - 1
        )
        setFetchDataSuccess(mockDataStoreContent)

        testedDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockResourceHashesEntrySerializer,
            resourceHashesDeserializer = mockResourceHashesEntryDeserializer
        )

        // When
        testedDataStoreManager.markResourceAsSentIfNew(fakeHash)

        // Then
        val resourceHashesEntryCaptor = argumentCaptor<ResourceHashesEntry>()
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_ENTRY_NAME),
            data = resourceHashesEntryCaptor.capture(),
            version = eq(DATASTORE_VERSION),
            callback = anyOrNull(),
            serializer = eq(mockResourceHashesEntrySerializer)
        )

        assertThat(
            resourceHashesEntryCaptor.firstValue.lastUpdateDateNs.toLong()
        ).isEqualTo(fakeCurrentTimestampMs)
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
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true, fakeCurrentTimestampMs)
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
    fun `M remove datastore entry W init { legacy datastore version }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(
            forge = forge,
            isExpired = false,
            currentTime = fakeCurrentTimestampMs,
            version = 0
        )
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
        mockDataStoreContent.data?.resourceHashes?.forEach {
            assertThat(testedDataStoreManager.isPreviouslySentResource(it)).isFalse()
        }
    }

    @Test
    fun `M remove datastore entry W init { timestamp is in the future }`(
        forge: Forge
    ) {
        // Given
        val mockDataStoreContent = generateDataStoreContent(
            forge = forge,
            isExpired = false,
            currentTime = fakeCurrentTimestampMs,
            storedTimestamp = fakeCurrentTimestampMs + 1
        )
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
        val mockDataStoreContentEntry = generateDataStoreContent(forge, isExpired = false, fakeCurrentTimestampMs)
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
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = false, fakeCurrentTimestampMs)
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
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true, fakeCurrentTimestampMs)
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
        val mockDataStoreContent = generateDataStoreContent(forge, isExpired = true, fakeCurrentTimestampMs)
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
        isExpired: Boolean,
        currentTime: Long,
        version: Int = DATASTORE_VERSION,
        storedTimestamp: Long? = null,
        resourceHashes: List<String> = forge.aList { aString() }.distinct()
    ): DataStoreContent<ResourceHashesEntry> {
        val entryTime = storedTimestamp ?: if (isExpired) {
            currentTime - DATASTORE_EXPIRATION_MS - 1
        } else {
            currentTime
        }

        val mockResourceHashesEntry: ResourceHashesEntry = mock {
            whenever(it.resourceHashes).thenReturn(resourceHashes)
            whenever(it.lastUpdateDateNs).thenReturn(entryTime)
        }
        val mockDataStoreContentEntry: DataStoreContent<ResourceHashesEntry> = mock {
            whenever(it.versionCode).thenReturn(version)
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
