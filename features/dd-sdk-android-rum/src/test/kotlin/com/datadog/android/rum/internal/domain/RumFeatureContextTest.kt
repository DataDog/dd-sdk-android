/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureContextTest {

    @Test
    fun `M publish typed snapshot and preserve legacy fields W setRumContext()`(
        @Forgery fakeRumContext: RumContext,
        forge: Forge
    ) {
        // Given
        val context = fakeRumContext.copy(
            viewTimestamp = forge.aLong(),
            viewTimestampOffset = forge.aLong(),
            hasReplay = forge.aBool()
        )

        // When
        val featureContext = mutableMapOf<String, Any?>()
        featureContext.setRumContext(context)

        // Then
        assertThat(featureContext[FeatureContextKeys.RUM_CONTEXT]).isSameAs(context)
        assertThat(featureContext).isEqualTo(
            mapOf(
                FeatureContextKeys.RUM_CONTEXT to context,
                "application_id" to context.applicationId,
                "session_id" to context.sessionId,
                "session_active" to context.isSessionActive,
                "session_state" to context.sessionState.asString,
                "session_start_reason" to context.sessionStartReason.asString,
                "view_id" to context.viewId,
                "view_name" to context.viewName,
                "view_url" to context.viewUrl,
                "view_type" to context.viewType.asString,
                "action_id" to context.actionId,
                "synthetics_test_id" to context.syntheticsTestId,
                "synthetics_result_id" to context.syntheticsResultId,
                "view_timestamp" to context.viewTimestamp,
                "view_timestamp_offset" to context.viewTimestampOffset,
                "view_has_replay" to context.hasReplay,
                "session_has_timeseries" to context.hasTimeseries,
                "session_sample_rate" to context.sessionSampleRate
            )
        )
    }

    @Test
    fun `M clear the previous snapshot and legacy fields W setRumContext() {no context}`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val featureContext = mutableMapOf<String, Any?>()
        featureContext.setRumContext(fakeRumContext)

        // When
        featureContext.setRumContext(null)

        // Then
        assertThat(featureContext).isEmpty()
    }
}
