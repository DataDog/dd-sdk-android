/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.v2.api.SDKCore
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SessionReplayRecordCallbackTest {

    @Mock
    lateinit var mockDatadogCore: SDKCore
    lateinit var testedRecordCallback: SessionReplayRecordCallback

    @BeforeEach
    fun `set up`() {
        testedRecordCallback = SessionReplayRecordCallback(mockDatadogCore)
    }

    @Test
    fun `M update session replay context W onStartRecording`() {
        // When
        testedRecordCallback.onStartRecording()

        // Then
        verify(mockDatadogCore).updateFeatureContext(
            SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME,
            mapOf(SessionReplayFeature.IS_RECORDING_CONTEXT_KEY to true)
        )
    }

    @Test
    fun `M update session replay context W onStopRecording`() {
        // When
        testedRecordCallback.onStopRecording()

        // Then
        verify(mockDatadogCore).updateFeatureContext(
            SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME,
            mapOf(SessionReplayFeature.IS_RECORDING_CONTEXT_KEY to false)
        )
    }
}
