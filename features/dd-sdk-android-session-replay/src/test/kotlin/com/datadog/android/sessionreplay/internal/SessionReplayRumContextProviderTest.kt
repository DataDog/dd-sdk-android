/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRumContextProviderTest {

    lateinit var testedSessionReplayContextProvider: SessionReplayRumContextProvider

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        testedSessionReplayContextProvider = SessionReplayRumContextProvider(mockSdkCore)
    }

    @Test
    fun `M provide a valid Rum context W getRumContext()`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID
    ) {
        // When
        whenever(mockSdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeApplicationId.toString(),
            "session_id" to fakeSessionId.toString(),
            "view_id" to fakeViewId.toString()
        )
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(context.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(context.viewId).isEqualTo(fakeViewId.toString())
    }

    @Test
    fun `M provide an invalid Rum context W getRumContext() { RUM not initialized }`() {
        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
        assertThat(context.sessionId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
        assertThat(context.viewId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
    }
}
