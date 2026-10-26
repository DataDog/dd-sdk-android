/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRumContextProviderTest {

    @Test
    fun `M preserve snapshot and notify each new view W onContextUpdate {core reuses mutable map}`(
        @Forgery fakeRumContext: RumContext,
        @Forgery fakeFirstViewId: UUID,
        @Forgery fakeSecondViewId: UUID
    ) {
        // Given
        val fakeNotifications = mutableListOf<String>()
        lateinit var testedProvider: SessionReplayRumContextProvider
        testedProvider = SessionReplayRumContextProvider {
            fakeNotifications.add(testedProvider.getRumContext().viewId)
        }
        val fakeContext = mutableMapOf<String, Any?>(
            FeatureContextKeys.RUM_CONTEXT to fakeRumContext.copy(viewId = fakeFirstViewId.toString())
        )
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)

        // When
        fakeContext.clear()
        fakeContext[FeatureContextKeys.RUM_CONTEXT] = fakeRumContext.copy(viewId = fakeSecondViewId.toString())

        // Then
        assertThat(testedProvider.getRumContext().viewId).isEqualTo(fakeFirstViewId.toString())
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        assertThat(fakeNotifications).containsExactly(fakeFirstViewId.toString(), fakeSecondViewId.toString())
        assertThat(testedProvider.getRumContext().viewId).isEqualTo(fakeSecondViewId.toString())
    }

    @Test
    fun `M update metadata without notifying W onContextUpdate {same view}`(
        @Forgery fakeRumContext: RumContext,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 1) fakeViewTimestampOffset: Long
    ) {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }
        val fakeSnapshot = fakeRumContext.copy(
            viewId = fakeViewId.toString(),
            viewTimestampOffset = fakeViewTimestampOffset - 1
        )
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to fakeSnapshot)
        )

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to fakeSnapshot.copy(viewTimestampOffset = fakeViewTimestampOffset))
        )

        // Then
        assertThat(notificationCount).isEqualTo(1)
        assertThat(testedProvider.getRumContext().viewTimeOffsetMs).isEqualTo(fakeViewTimestampOffset)
    }

    @Test
    fun `M not notify view change W onContextUpdate {invalid view}`(@Forgery fakeRumContext: RumContext) {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext.copy(viewId = RumContext.NULL_UUID))
        )
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())

        // Then
        assertThat(notificationCount).isZero()
    }

    @Test
    fun `M map snapshot W getRumContext()`(
        @BoolForgery fakeHasViewUrl: Boolean,
        @Forgery fakeRumContext: RumContext,
        @LongForgery fakeViewTimestampOffset: Long
    ) {
        // Given
        val fakeSnapshot = fakeRumContext.copy(
            viewTimestampOffset = fakeViewTimestampOffset,
            viewUrl = if (fakeHasViewUrl) fakeRumContext.viewUrl else null
        )
        val testedProvider = SessionReplayRumContextProvider()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to fakeSnapshot)
        )

        // When
        val context = testedProvider.getRumContext()

        // Then
        assertThat(context).isEqualTo(
            SessionReplayRumContext(
                applicationId = fakeSnapshot.applicationId,
                sessionId = fakeSnapshot.sessionId,
                viewId = fakeSnapshot.viewId ?: RumContext.NULL_UUID,
                viewTimeOffsetMs = fakeViewTimestampOffset,
                viewUrl = fakeSnapshot.viewUrl
            )
        )
    }

    @Test
    fun `M ignore unrelated feature W onContextUpdate`(
        @StringForgery fakeFeatureName: String,
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val testedProvider = SessionReplayRumContextProvider()
        val fakeContext = mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext)

        // When
        testedProvider.onContextUpdate(fakeFeatureName, fakeContext)

        // Then
        assertThat(testedProvider.getRumContext()).isEqualTo(
            SessionReplayRumContext(RumContext.NULL_UUID, RumContext.NULL_UUID, RumContext.NULL_UUID, 0L)
        )
    }

    @Test
    fun `M clear snapshot W onContextUpdate {no RUM context}`(@Forgery fakeRumContext: RumContext) {
        // Given
        val testedProvider = SessionReplayRumContextProvider()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext)
        )

        // When
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())

        // Then
        assertThat(testedProvider.getRumContext()).isEqualTo(
            SessionReplayRumContext(RumContext.NULL_UUID, RumContext.NULL_UUID, RumContext.NULL_UUID, 0L)
        )
    }
}
