/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreCallback
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import com.datadog.android.sessionreplay.internal.resources.ResourcesDataStoreManager.Companion.DATASTORE_EXPIRATION_MS
import com.datadog.android.sessionreplay.internal.resources.ResourcesDataStoreManager.Companion.DATASTORE_HASHES_CONTENT_FILENAME
import com.datadog.android.sessionreplay.internal.resources.ResourcesDataStoreManager.Companion.DATASTORE_HASHES_UPDATE_DATE_FILENAME
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesDataStoreManagerTest {
    private lateinit var testedDataStoreManager: ResourcesDataStoreManager

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockStringSetSerializer: Serializer<Set<String>>

    @Mock
    lateinit var mockStringSetDeserializer: Deserializer<String, Set<String>>

    @Mock
    lateinit var mockLongSerializer: Serializer<Long>

    @Mock
    lateinit var mockLongDeserializer: Deserializer<String, Long>

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

        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )
    }

    @Test
    fun `M return false W wasResourcePreviouslySent() { resource was not already sent }`() {
        // When
        val wasSent = testedDataStoreManager.wasResourcePreviouslySent(fakeHash)

        // Then
        assertThat(wasSent).isFalse()
    }

    @Test
    fun `M return true W wasResourcePreviouslySent() { resource was already sent }`() {
        // Given
        testedDataStoreManager.store(fakeHash)

        // When
        val wasSent = testedDataStoreManager.wasResourcePreviouslySent(fakeHash)

        // Then
        assertThat(wasSent).isTrue()
    }

    @Test
    fun `M write hash to datastore W store()`() {
        // When
        testedDataStoreManager.store(fakeHash)

        // Then
        verify(mockFeatureScope.dataStore).setValue(
            key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
            data = any(),
            version = anyOrNull(),
            serializer = eq(mockStringSetSerializer)
        )
    }

    @Test
    fun `M be threadsafe W store() { store on one thread, read on another }`() {
        // Given
        val writeThread = Runnable {
            testedDataStoreManager.store(fakeHash)
        }

        val readThread = Runnable {
            assertThat(testedDataStoreManager.wasResourcePreviouslySent(fakeHash))
                .isTrue()
        }

        // When
        writeThread.run()

        // Then
        readThread.run()
    }

    // region init

    @Test
    fun `M query updateDate from dataStore W init()`() {
        // When
        verify(mockDataStoreHandler).value(
            key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
            version = anyOrNull(),
            callback = any<DataStoreCallback<Long>>(),
            deserializer = any()
        )
    }

    @Test
    fun `M query hashes from dataStore W init()`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        val validTime = System.currentTimeMillis() - (DATASTORE_EXPIRATION_MS / 2)
        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(validTime)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).value(
            key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
            version = anyOrNull(),
            callback = any<DataStoreCallback<Long>>(),
            deserializer = any()
        )
    }

    @Test
    fun `M create new update file W init() { no stored update file }`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(null)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
            version = anyOrNull(),
            data = any(),
            serializer = any()
        )
    }

    @Test
    fun `M overwrite last update file W init() { datastore expired }`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @IntForgery fakeVersionCode: Int,
        @StringForgery fakeTestKey: String
    ) {
        // Given
        val fakeSetStrings = hashSetOf(fakeTestKey)
        val fakeHashes = DataStoreContent(
            versionCode = fakeVersionCode,
            data = fakeSetStrings
        )

        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        val timestamp = System.currentTimeMillis() - (DATASTORE_EXPIRATION_MS * 2)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(timestamp)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Set<String>>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<HashSet<String>>
            callback.onSuccess(fakeHashes)
        }

        testedDataStoreManager.store(fakeTestKey)
        assertThat(testedDataStoreManager.wasResourcePreviouslySent(fakeTestKey)).isTrue()

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
            data = any(),
            version = anyOrNull(),
            serializer = eq(mockLongSerializer)
        )
    }

    @Test
    fun `M remove stored hashes file W init() { datastore expired }`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @IntForgery fakeVersionCode: Int,
        @StringForgery fakeTestKey: String
    ) {
        // Given
        val fakeSetStrings = hashSetOf(fakeTestKey)
        val fakeHashes = DataStoreContent(
            versionCode = fakeVersionCode,
            data = fakeSetStrings
        )

        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        val timestamp = System.currentTimeMillis() - (DATASTORE_EXPIRATION_MS * 2)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(timestamp)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Set<String>>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<HashSet<String>>
            callback.onSuccess(fakeHashes)
        }

        testedDataStoreManager.store(fakeTestKey)
        assertThat(testedDataStoreManager.wasResourcePreviouslySent(fakeTestKey)).isTrue()

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).removeValue(
            key = DATASTORE_HASHES_CONTENT_FILENAME
        )
    }

    @Test
    fun `M clear stored hashes from cache W init() { datastore expired }`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @IntForgery fakeVersionCode: Int,
        @StringForgery fakeTestKey: String
    ) {
        // Given
        val fakeSetStrings = hashSetOf(fakeTestKey)
        val fakeHashes = DataStoreContent(
            versionCode = fakeVersionCode,
            data = fakeSetStrings
        )

        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        val timestamp = System.currentTimeMillis() - (DATASTORE_EXPIRATION_MS * 2)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(timestamp)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Set<String>>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<HashSet<String>>
            callback.onSuccess(fakeHashes)
        }

        testedDataStoreManager.store(fakeTestKey)
        assertThat(testedDataStoreManager.wasResourcePreviouslySent(fakeTestKey)).isTrue()

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        assertThat(
            testedDataStoreManager.wasResourcePreviouslySent(
                fakeTestKey
            )
        ).isFalse()
    }

    @Test
    fun `M add stored hashes to known set W init() { valid update date }`(
        @Mock mockDataStoreContentUpdateTime: DataStoreContent<Long>,
        @Mock mockDataStoreContentHashes: DataStoreContent<Set<String>>,
        @StringForgery fakeString: String,
        @IntForgery fakeVersionCode: Int
    ) {
        // Given
        val fakeSetStrings = hashSetOf(fakeString)
        val validTime = System.currentTimeMillis() - (DATASTORE_EXPIRATION_MS / 2)
        whenever(mockDataStoreContentUpdateTime.versionCode).thenReturn(fakeVersionCode)
        whenever(mockDataStoreContentUpdateTime.data).thenReturn(validTime)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(mockDataStoreContentUpdateTime)
        }

        whenever(mockDataStoreContentHashes.versionCode).thenReturn(fakeVersionCode)
        whenever(mockDataStoreContentHashes.data).thenReturn(fakeSetStrings)

        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_CONTENT_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Set<String>>
            callback.onSuccess(mockDataStoreContentHashes)
        }

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        assertThat(testedDataStoreManager.wasResourcePreviouslySent(fakeString))
            .isTrue()
    }

    @Test
    fun `M create new update file W init() { failed to get update date }`() {
        // Given
        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onFailure()
        }

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
            version = anyOrNull(),
            data = any(),
            serializer = any()
        )
    }

    @Test
    fun `M create new update file W init() { no data for update date }`() {
        // Given
        whenever(
            mockDataStoreHandler.value(
                key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
                version = anyOrNull(),
                callback = any<DataStoreCallback<Long>>(),
                deserializer = any()
            )
        ) doAnswer {
            @Suppress("UNCHECKED_CAST")
            val callback = it.arguments[2] as DataStoreCallback<Long>
            callback.onSuccess(null)
        }

        // When
        testedDataStoreManager = ResourcesDataStoreManager(
            featureSdkCore = mockFeatureSdkCore,
            resourceHashesSerializer = mockStringSetSerializer,
            resourcesHashesDeserializer = mockStringSetDeserializer,
            updateDateSerializer = mockLongSerializer,
            updateDateDeserializer = mockLongDeserializer
        )

        // Then
        verify(mockDataStoreHandler).setValue(
            key = eq(DATASTORE_HASHES_UPDATE_DATE_FILENAME),
            version = anyOrNull(),
            data = any(),
            serializer = any()
        )
    }

    // endregion
}
