/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.SdkCore
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionReplayRumContextProviderTest {

    lateinit var testedSessionReplayContextProvider: SessionReplayRumContextProvider

    @Mock
    lateinit var mockSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        testedSessionReplayContextProvider = SessionReplayRumContextProvider(mockSdkCore)
    }

    @Test
    fun `M provide a valid Rum context W getRumContext()`(
        @Forgery fakeRumContext: RumContext,
        forgery: Forge
    ) {
        // When
        val fakeViewId = forgery.getForgery<UUID>().toString()
        whenever(mockSdkCore.getFeatureContext(RumFeature.RUM_FEATURE_NAME)) doReturn mapOf(
            "application_id" to fakeRumContext.applicationId,
            "session_id" to fakeRumContext.sessionId,
            "view_id" to fakeViewId
        )
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(context.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(context.viewId).isEqualTo(fakeViewId)
    }

    @Test
    fun `M provide an invalid Rum context W getRumContext() { RUM not initialized }`() {
        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.viewId).isEqualTo(RumContext.NULL_UUID)
    }
}
