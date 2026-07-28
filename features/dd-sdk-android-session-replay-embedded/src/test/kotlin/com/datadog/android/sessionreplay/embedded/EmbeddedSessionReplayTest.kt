/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.sessionreplay.embedded.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedRecordsWriter
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedReplayFeature
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedViewRegistry
import com.datadog.android.sessionreplay.embedded.internal.rum.EmbeddedRumEventContextProvider
import com.datadog.android.sessionreplay.embedded.internal.rum.RumContext
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedSessionReplayTest {

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @Mock
    lateinit var mockSessionReplayFeature: StorageBackedFeature

    @Mock
    lateinit var mockSessionReplayRequestFactory: RequestFactory

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    @StringForgery
    lateinit var fakeViewId: String

    lateinit var fakeRecords: List<JsonObject>

    private val engineKey = Any()

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRecords = forge.aList(size = forge.anInt(min = 1, max = 5)) { getForgery<JsonObject>() }
        fakeRumContext = fakeRumContext.copy(sessionState = RumContext.SESSION_TRACKED_STATE)
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to mapOf(
                    "application_id" to fakeRumContext.applicationId,
                    "session_id" to fakeRumContext.sessionId,
                    "session_state" to fakeRumContext.sessionState
                ),
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                    EmbeddedRecordsWriter.SESSION_REPLAY_ENABLED_KEY to true
                )
            )
        )

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(
            mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
        ) doReturn mockSessionReplayFeatureScope
        whenever(
            mockSessionReplayFeatureScope.unwrap<StorageBackedFeature>()
        ) doReturn mockSessionReplayFeature
        whenever(mockSessionReplayFeature.requestFactory) doReturn mockSessionReplayRequestFactory

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
    }

    @AfterEach
    fun `tear down`() {
        EmbeddedViewRegistry.unregister(engineKey)
    }

    @Test
    fun `M register a new embedded replay feature W writeRecords() { not previously registered }`() {
        // Given
        EmbeddedSessionReplay.register(mockView, engineKey)
        whenever(
            mockSdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
        ) doReturn null

        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        val captor = argumentCaptor<com.datadog.android.api.feature.Feature>()
        verify(mockSdkCore).registerFeature(captor.capture())
        assertThat(captor.firstValue).isInstanceOf(EmbeddedReplayFeature::class.java)
        assertThat((captor.firstValue as EmbeddedReplayFeature).requestFactory)
            .isSameAs(mockSessionReplayRequestFactory)
    }

    @Test
    fun `M drop the records and log a warning W writeRecords() { engineKey not registered }`() {
        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedSessionReplay.UNREGISTERED_ENGINE_KEY_WARNING_MESSAGE
        )
        verifyNoInteractions(mockSessionReplayFeatureScope)
    }

    @Test
    fun `M log an info and drop the records W writeRecords() { session replay feature missing }`() {
        // Given
        EmbeddedSessionReplay.register(mockView, engineKey)
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn null

        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            EmbeddedSessionReplay.SESSION_REPLAY_FEATURE_MISSING_INFO
        )
    }

    @Test
    fun `M only log the info once W writeRecords() { called repeatedly, session replay feature missing }`() {
        // Given
        EmbeddedSessionReplay.register(mockView, engineKey)
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)) doReturn null

        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            EmbeddedSessionReplay.SESSION_REPLAY_FEATURE_MISSING_INFO,
            mode = times(1)
        )
    }

    @Test
    fun `M reuse an already registered embedded replay feature W writeRecords()`() {
        // Given
        EmbeddedSessionReplay.register(mockView, engineKey)
        val mockEmbeddedReplayFeatureScope: FeatureScope = mock()
        whenever(
            mockSdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
        ) doReturn mockEmbeddedReplayFeatureScope
        val existingFeature = EmbeddedReplayFeature(mockSdkCore, mockSessionReplayRequestFactory)
        whenever(mockEmbeddedReplayFeatureScope.unwrap<StorageBackedFeature>()) doReturn existingFeature
        whenever(
            mockEmbeddedReplayFeatureScope.withWriteContext(any(), any())
        ) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        verify(mockSdkCore, times(0)).registerFeature(any())
    }

    @Test
    fun `M only log the RUM warning once W writeRecords() { called repeatedly, RUM not initialized }`() {
        // Given
        EmbeddedSessionReplay.register(mockView, engineKey)
        val mockEmbeddedReplayFeatureScope: FeatureScope = mock()
        whenever(
            mockSdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
        ) doReturn mockEmbeddedReplayFeatureScope
        val existingFeature = EmbeddedReplayFeature(mockSdkCore, mockSessionReplayRequestFactory)
        whenever(mockEmbeddedReplayFeatureScope.unwrap<StorageBackedFeature>()) doReturn existingFeature
        val fakeContextWithoutRum = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                    EmbeddedRecordsWriter.SESSION_REPLAY_ENABLED_KEY to true
                )
            )
        )
        whenever(
            mockEmbeddedReplayFeatureScope.withWriteContext(any(), any())
        ) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeContextWithoutRum, mockEventWriteScope)
        }

        // When
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)
        EmbeddedSessionReplay.writeRecords(engineKey, fakeViewId, fakeRecords, mockSdkCore)

        // Then
        verifyNoInteractions(mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            EmbeddedRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE,
            mode = times(1)
        )
    }
}
