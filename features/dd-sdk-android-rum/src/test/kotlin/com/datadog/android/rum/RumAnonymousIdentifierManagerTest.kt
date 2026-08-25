/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.rum.internal.AnonymousIdentifierReadCallback
import com.datadog.android.rum.internal.RumAnonymousIdentifierManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RumAnonymousIdentifierManagerTest {

    @Mock
    lateinit var dataStore: DataStoreHandler

    @Mock
    lateinit var core: FeatureSdkCore

    private lateinit var rumAnonymousIdentifierManager: RumAnonymousIdentifierManager

    @BeforeEach
    fun setUp() {
        rumAnonymousIdentifierManager = RumAnonymousIdentifierManager(dataStore, core)
    }

    @Test
    fun `M generate new anonymous id W id not present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                isNull(),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(null)
        }

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(true)

        // Then
        verify(dataStore).setValue(eq("anonymous_id_key"), any(), eq(0), isNull(), any())
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W id present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                isNull(),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(DataStoreContent(0, UUID.randomUUID()))
        }

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(true)

        // Then
        verify(dataStore, never()).setValue(eq("anonymous_id_key"), any(), eq(0), isNull(), any())
        verify(dataStore, never()).removeValue(eq("anonymous_id_key"), isNull())
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W tracking disabled`() {
        // Given
        val shouldTrack = false

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(shouldTrack)

        // Then
        verify(dataStore).removeValue(eq("anonymous_id_key"), isNull())
        verify(core).setAnonymousId(isNull())
    }
}
