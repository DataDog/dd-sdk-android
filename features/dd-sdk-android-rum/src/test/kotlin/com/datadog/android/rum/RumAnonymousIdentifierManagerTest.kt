/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.persistence.datastore.DataStoreContent
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

class RumAnonymousIdentifierManagerTest {

    private lateinit var dataStore: DataStoreHandler
    private lateinit var core: FeatureSdkCore

    @Before
    fun setUp() {
        dataStore = mock()
        core = mock()
    }

    @Test
    fun `M generate new anonymous id W id not present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                eq(null),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(null)
        }

        // When
        RumAnonymousIdentifierManager.manageAnonymousId(true, dataStore, core)

        // Then
        verify(dataStore).setValue(eq("anonymous_id_key"), any(), eq(0), eq(null), any())
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W id present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                eq(null),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(DataStoreContent(0, UUID.randomUUID()))
        }

        // When
        RumAnonymousIdentifierManager.manageAnonymousId(true, dataStore, core)

        // Then
        verify(dataStore, never()).setValue(eq("anonymous_id_key"), any(), eq(0), eq(null), any())
        verify(dataStore, never()).removeValue(eq("anonymous_id_key"), eq(null))
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W tracking disabled`() {
        // Given
        val shouldTrack = false

        // When
        RumAnonymousIdentifierManager.manageAnonymousId(shouldTrack, dataStore, core)

        // Then
        verify(dataStore).removeValue(eq("anonymous_id_key"), eq(null))
        verify(core).setAnonymousId(eq(null))
    }
}
