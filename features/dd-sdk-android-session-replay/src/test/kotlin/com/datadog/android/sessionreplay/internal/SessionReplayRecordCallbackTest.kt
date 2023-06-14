/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.v2.api.FeatureSdkCore
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRecordCallbackTest {

    @Mock
    lateinit var mockDatadogCore: FeatureSdkCore
    lateinit var testedRecordCallback: SessionReplayRecordCallback

    @BeforeEach
    fun `set up`() {
        testedRecordCallback = SessionReplayRecordCallback(mockDatadogCore)
    }

    @Test
    fun `M update session replay context W onRecordForViewSent`(forge: Forge) {
        // Given
        val fakeViewId = forge.getForgery<UUID>().toString()

        // When
        testedRecordCallback.onRecordForViewSent(fakeViewId)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockDatadogCore).updateFeatureContext(
                eq(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )

            val featureContext = mutableMapOf<String, Any?>()
            lastValue.invoke(featureContext)

            assertThat(
                featureContext[fakeViewId] as? Boolean
            ).isTrue
        }
    }
}
