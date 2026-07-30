/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentEvent
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SessionReplayInternalProxyTest {

    private lateinit var testedBuilder: SessionReplayConfiguration.Builder

    private lateinit var testedProxy: _SessionReplayInternalProxy

    @Mock
    lateinit var mockInternalCallback: SessionReplayInternalCallback

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @FloatForgery
    var fakeSampleRate: Float = 0f

    @Test
    fun `M return the same builder W setInternalCallback`() {
        // Given
        testedBuilder = SessionReplayConfiguration.Builder(fakeSampleRate)
        testedProxy = _SessionReplayInternalProxy(testedBuilder)

        // When
        val result = testedProxy.setInternalCallback(mockInternalCallback)
        val sessionReplayConfiguration = result.build()

        // Then
        assertThat(result).isEqualTo(testedBuilder)
        assertThat(sessionReplayConfiguration.internalCallback).isEqualTo(mockInternalCallback)
    }

    @Test
    fun `M set slot tag W setEmbeddedContentSlotId`() {
        val registrationCaptor = argumentCaptor<EmbeddedContentSlotRegistration>()
        try {
            // When
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(mockView, FAKE_SLOT_ID)

            // Then
            verify(mockView).setTag(R.id.datadog_session_replay_slot_id, FAKE_SLOT_ID)
            verify(mockView).setTag(
                eq(R.id.datadog_session_replay_slot_registration),
                registrationCaptor.capture()
            )
            verify(mockView).postInvalidateOnAnimation()
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            registrationCaptor.allValues.firstOrNull()?.let {
                EmbeddedContentSlotRegistry.notifySlotChanged(it, null)
            }
        }
    }

    @Test
    fun `M unregister previous slot W setEmbeddedContentSlotId { slot is cleared }`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        whenever(mockView.getTag(R.id.datadog_session_replay_slot_id))
            .thenReturn(FAKE_SLOT_ID)
        whenever(mockView.getTag(R.id.datadog_session_replay_slot_registration))
            .thenReturn(fakeRegistration)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeRegistration)

        // When
        _SessionReplayInternalProxy.setEmbeddedContentSlotId(mockView, null)

        // Then
        assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isFalse()
        verify(mockView).setTag(R.id.datadog_session_replay_slot_id, null)
        verify(mockView).setTag(R.id.datadog_session_replay_slot_registration, null)
        verify(mockView).postInvalidateOnAnimation()
    }

    @Test
    fun `M do nothing W setEmbeddedContentSlotId { slot is already assigned }`() {
        // Given
        whenever(mockView.getTag(R.id.datadog_session_replay_slot_id))
            .thenReturn(null, FAKE_SLOT_ID)
        val registrationCaptor = argumentCaptor<EmbeddedContentSlotRegistration>()

        try {
            // When
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(mockView, FAKE_SLOT_ID)
            _SessionReplayInternalProxy.setEmbeddedContentSlotId(mockView, FAKE_SLOT_ID)

            // Then
            verify(mockView, times(1)).setTag(
                R.id.datadog_session_replay_slot_id,
                FAKE_SLOT_ID
            )
            verify(mockView, times(1)).setTag(
                eq(R.id.datadog_session_replay_slot_registration),
                registrationCaptor.capture()
            )
            verify(mockView, times(1)).postInvalidateOnAnimation()
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            registrationCaptor.allValues.firstOrNull()?.let {
                EmbeddedContentSlotRegistry.notifySlotChanged(it, null)
            }
        }
    }

    @Test
    fun `M send record event W addEmbeddedContentRecords`() {
        // Given
        val nestedData = mutableMapOf<String, Any?>(FAKE_RECORD_VALUE_KEY to 10L)
        val record = mutableMapOf<String, Any?>(FAKE_RECORD_DATA_KEY to nestedData)
        val records = mutableListOf<Map<String, Any?>>(record)
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn mockFeatureScope

        // When
        _SessionReplayInternalProxy.addEmbeddedContentRecords(
            records = records,
            slotId = FAKE_SLOT_ID,
            viewId = FAKE_VIEW_ID,
            sdkCore = mockSdkCore
        )
        nestedData[FAKE_RECORD_VALUE_KEY] = 20L
        record[FAKE_RECORD_TYPE_KEY] = 11L
        records.clear()

        // Then
        argumentCaptor<Any> {
            verify(mockFeatureScope).sendEvent(capture())
            val event = firstValue as EmbeddedContentEvent.RecordBatch
            assertThat(event.records).containsExactly(
                mapOf(FAKE_RECORD_DATA_KEY to mapOf(FAKE_RECORD_VALUE_KEY to 10L))
            )
            assertThat(event.slotId).isEqualTo(FAKE_SLOT_ID)
            assertThat(event.viewId).isEqualTo(FAKE_VIEW_ID)
        }
    }

    @Test
    fun `M send resource event W addEmbeddedContentResource`() {
        // Given
        val resourceData = byteArrayOf(1, 2, 3)
        val expectedResourceData = resourceData.copyOf()
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn mockFeatureScope

        // When
        _SessionReplayInternalProxy.addEmbeddedContentResource(
            identifier = FAKE_RESOURCE_ID,
            resourceData = resourceData,
            mimeType = FAKE_MIME_TYPE,
            sdkCore = mockSdkCore
        )
        resourceData.fill(0)

        // Then
        argumentCaptor<Any> {
            verify(mockFeatureScope).sendEvent(capture())
            val event = firstValue as EmbeddedContentEvent.Resource
            assertThat(event.identifier).isEqualTo(FAKE_RESOURCE_ID)
            assertThat(event.data).isEqualTo(expectedResourceData)
            assertThat(event.data).isNotSameAs(resourceData)
            assertThat(event.mimeType).isEqualTo(FAKE_MIME_TYPE)
        }
    }

    private companion object {
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_VIEW_ID = "view-id"
        const val FAKE_RESOURCE_ID = "resource-id"
        const val FAKE_MIME_TYPE = "image/png"
        const val FAKE_RECORD_DATA_KEY = "data"
        const val FAKE_RECORD_VALUE_KEY = "value"
        const val FAKE_RECORD_TYPE_KEY = "type"
    }
}
