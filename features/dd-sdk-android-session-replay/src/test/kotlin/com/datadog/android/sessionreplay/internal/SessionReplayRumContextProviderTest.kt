/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.NULL_UUID
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.UUID

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRumContextProviderTest {

    private val testedSessionReplayContextProvider = SessionReplayRumContextProvider()

    @Test
    fun `M provide a valid Rum context W getRumContext()`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long
    ) {
        // Given
        testedSessionReplayContextProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                "application_id" to fakeApplicationId.toString(),
                "session_id" to fakeSessionId.toString(),
                "view_id" to fakeViewId.toString(),
                "view_timestamp_offset" to fakeViewTimeOffsetMs
            )
        )

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(context.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(context.viewId).isEqualTo(fakeViewId.toString())
        assertThat(context.viewTimeOffsetMs).isEqualTo(fakeViewTimeOffsetMs)
    }

    @RepeatedTest(10)
    fun `M provide a valid Rum context W getRumContext() { different threads }`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long
    ) {
        // Given
        Thread {
            testedSessionReplayContextProvider.onContextUpdate(
                Feature.RUM_FEATURE_NAME,
                mapOf(
                    "application_id" to fakeApplicationId.toString(),
                    "session_id" to fakeSessionId.toString(),
                    "view_id" to fakeViewId.toString(),
                    "view_timestamp_offset" to fakeViewTimeOffsetMs
                )
            )
        }.apply {
            start()
            join()
        }

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(context.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(context.viewId).isEqualTo(fakeViewId.toString())
        assertThat(context.viewTimeOffsetMs).isEqualTo(fakeViewTimeOffsetMs)
    }

    @RepeatedTest(10)
    fun `M have atomic read W getRumContext() { update while reading }`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long
    ) {
        // Given
        testedSessionReplayContextProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                "application_id" to fakeApplicationId.toString(),
                "session_id" to fakeSessionId.toString(),
                "view_id" to fakeViewId.toString(),
                "view_timestamp_offset" to fakeViewTimeOffsetMs
            )
        )

        // When
        Thread {
            testedSessionReplayContextProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())
        }.apply {
            start()
            join()
        }
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        // should never be a mix of values from two contexts
        assertThat(context).isIn(
            SessionReplayRumContext(
                fakeApplicationId.toString(),
                fakeSessionId.toString(),
                fakeViewId.toString(),
                fakeViewTimeOffsetMs
            ),
            SessionReplayRumContext(NULL_UUID, NULL_UUID, NULL_UUID, 0L)
        )
    }

    @Test
    fun `M provide an invalid Rum context W getRumContext() { no RUM context received }`() {
        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
        assertThat(context.sessionId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
        assertThat(context.viewId).isEqualTo(SessionReplayRumContextProvider.NULL_UUID)
        assertThat(context.viewTimeOffsetMs).isZero()
    }
}
