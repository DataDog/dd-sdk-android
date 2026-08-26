/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.InternalLogger
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
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_EXPIRATION_MS
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager.Companion.DATASTORE_VERSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
internal class ResourceDataStoreManagerMigrationTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var resourceHashesSerializer: ResourceHashesEntrySerializer
    private lateinit var resourceHashesDeserializer: ResourceHashesEntryDeserializer
    private lateinit var dataStore: InMemoryDataStoreHandler

    @BeforeEach
    fun setup() {
        resourceHashesSerializer = ResourceHashesEntrySerializer()
        resourceHashesDeserializer = ResourceHashesEntryDeserializer(mockInternalLogger)
        dataStore = InMemoryDataStoreHandler(
            entry = StoredEntry(
                version = LEGACY_DATASTORE_VERSION,
                serializedData = LEGACY_DATASTORE_JSON
            )
        )

        whenever(mockFeatureSdkCore.timeProvider).thenReturn(mockTimeProvider)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(CURRENT_TIMESTAMP_MS)
        whenever(mockFeatureSdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME))
            .thenReturn(mockFeatureScope)
        whenever(mockFeatureScope.dataStore).thenReturn(dataStore)
    }

    @Test
    fun `M migrate persisted data W init { legacy datastore entry }`() {
        // When
        val testedManager = createManager()

        // Then
        assertThat(testedManager.isReady()).isTrue()
        assertThat(testedManager.isPreviouslySentResource(LEGACY_RESOURCE_HASH)).isFalse()
        assertThat(dataStore.entry).isNull()
        assertThat(dataStore.removeCount).isEqualTo(1)

        // When
        testedManager.markResourceAsSentIfNew(NEW_RESOURCE_HASH)

        // Then
        val migratedEntry = checkNotNull(dataStore.entry)
        val migratedData = resourceHashesDeserializer.deserialize(migratedEntry.serializedData)

        assertThat(migratedEntry.version).isEqualTo(DATASTORE_VERSION)
        assertThat(migratedData?.lastUpdateDateNs?.toLong()).isEqualTo(CURRENT_TIMESTAMP_MS)
        assertThat(migratedData?.resourceHashes).containsExactly(NEW_RESOURCE_HASH)

        // When
        val recreatedManager = createManager()

        // Then
        assertThat(recreatedManager.isReady()).isTrue()
        assertThat(recreatedManager.isPreviouslySentResource(NEW_RESOURCE_HASH)).isTrue()
        assertThat(dataStore.removeCount).isEqualTo(1)

        // When
        whenever(mockTimeProvider.getDeviceTimestampMillis())
            .thenReturn(CURRENT_TIMESTAMP_MS + DATASTORE_EXPIRATION_MS + 1)
        val expiredManager = createManager()

        // Then
        assertThat(expiredManager.isReady()).isTrue()
        assertThat(expiredManager.isPreviouslySentResource(NEW_RESOURCE_HASH)).isFalse()
        assertThat(dataStore.entry).isNull()
        assertThat(dataStore.removeCount).isEqualTo(2)
        verify(mockTimeProvider, never()).getDeviceElapsedTimeNanos()
    }

    private fun createManager(): ResourceDataStoreManager {
        return ResourceDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = resourceHashesSerializer,
            resourceHashesDeserializer = resourceHashesDeserializer
        )
    }

    private data class StoredEntry(
        val version: Int,
        val serializedData: String
    )

    private class InMemoryDataStoreHandler(
        var entry: StoredEntry?
    ) : DataStoreHandler {

        var removeCount: Int = 0
            private set

        override fun <T : Any> setValue(
            key: String,
            data: T,
            version: Int,
            callback: DataStoreWriteCallback?,
            serializer: Serializer<T>
        ) {
            val serializedData = serializer.serialize(data)
            if (serializedData == null) {
                callback?.onFailure()
            } else {
                entry = StoredEntry(version, serializedData)
                callback?.onSuccess()
            }
        }

        override fun <T : Any> value(
            key: String,
            version: Int?,
            callback: DataStoreReadCallback<T>,
            deserializer: Deserializer<String, T>
        ) {
            val storedEntry = entry
            if (storedEntry == null || version != null && version != storedEntry.version) {
                callback.onSuccess(null)
            } else {
                callback.onSuccess(
                    DataStoreContent(
                        versionCode = storedEntry.version,
                        data = deserializer.deserialize(storedEntry.serializedData)
                    )
                )
            }
        }

        override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
            removeCount++
            entry = null
            callback?.onSuccess()
        }

        override fun clearAllData() {
            entry = null
        }
    }

    private companion object {
        private const val LEGACY_DATASTORE_VERSION = 0
        private const val LEGACY_RESOURCE_HASH = "legacy-resource-hash"
        private const val NEW_RESOURCE_HASH = "new-resource-hash"
        private const val CURRENT_TIMESTAMP_MS = 1_800_000_000_000L
        private const val LEGACY_DATASTORE_JSON = """
            {
              "last_update_date_ns": 86400000000000,
              "resource_hashes": ["legacy-resource-hash"]
            }
        """
    }
}
