/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.persistence.datastore.DataStoreHandler
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import com.datadog.android.sessionreplay.internal.resources.ResourcesDataStoreManager.Companion.DATASTORE_FILENAME
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
    lateinit var mockStringSetSerializer: StringSetSerializer

    @Mock
    lateinit var mockStringSetDeserializer: StringSetDeserializer

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
            serializer = mockStringSetSerializer,
            deserializer = mockStringSetDeserializer
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
    fun `M read from disk W wasResourcePreviouslySent() { first access }`() {
        // When
        testedDataStoreManager.wasResourcePreviouslySent(fakeHash)

        // Then
        verify(mockFeatureScope.dataStore).value(
            key = eq(DATASTORE_FILENAME),
            version = anyOrNull(),
            callback = any(),
            deserializer = eq(mockStringSetDeserializer)
        )
    }

    @Test
    fun `M read from disk W store() { first access }`(
        @StringForgery fakeHash: String
    ) {
        // When
        testedDataStoreManager.store(fakeHash)

        // Then

        verify(mockFeatureScope.dataStore).value(
            key = eq(DATASTORE_FILENAME),
            version = anyOrNull(),
            callback = any(),
            deserializer = eq(mockStringSetDeserializer)
        )
    }

    @Test
    fun `M write hash to datastore W store()`() {
        // When
        testedDataStoreManager.store(fakeHash)

        // Then
        verify(mockFeatureScope.dataStore).setValue(
            key = eq(DATASTORE_FILENAME),
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
}
