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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRumContextProviderTest {

    @StringForgery
    private lateinit var fakeApplicationId: String

    @Test
    fun `M preserve snapshot and notify each new view W onContextUpdate {core reuses mutable map}`(
        @StringForgery fakeFirstViewId: String,
        @StringForgery fakeSecondViewId: String
    ) {
        // Given
        val fakeNotifications = mutableListOf<String>()
        lateinit var testedProvider: SessionReplayRumContextProvider
        testedProvider = SessionReplayRumContextProvider {
            fakeNotifications.add(testedProvider.getRumContext().viewId)
        }
        val fakeContext = mutableMapOf<String, Any?>(
            FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId = fakeApplicationId, viewId = fakeFirstViewId)
        )
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)

        // When
        fakeContext.clear()
        fakeContext[FeatureContextKeys.RUM_CONTEXT] =
            RumContext(applicationId = fakeApplicationId, viewId = fakeSecondViewId)

        // Then
        assertThat(testedProvider.getRumContext().viewId).isEqualTo(fakeFirstViewId)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        assertThat(fakeNotifications).containsExactly(fakeFirstViewId, fakeSecondViewId)
        assertThat(testedProvider.getRumContext().viewId).isEqualTo(fakeSecondViewId)
    }

    @Test
    fun `M not notify view change W onContextUpdate {invalid view}`() {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_CONTEXT to
                    RumContext(applicationId = fakeApplicationId, viewId = RumContext.NULL_UUID)
            )
        )
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())

        // Then
        assertThat(notificationCount).isZero()
    }

    @Test
    fun `M map snapshot W getRumContext()`(
        @Forgery fakeReplayContext: SessionReplayRumContext,
        forge: Forge
    ) {
        // Given
        val fakeViewUrl = forge.aNullable { anAlphabeticalString() }
        val testedProvider = SessionReplayRumContextProvider()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                FeatureContextKeys.RUM_CONTEXT to RumContext(
                    applicationId = fakeReplayContext.applicationId,
                    sessionId = fakeReplayContext.sessionId,
                    viewId = fakeReplayContext.viewId,
                    viewTimestampOffset = fakeReplayContext.viewTimeOffsetMs,
                    viewUrl = fakeViewUrl
                )
            )
        )

        // When
        val context = testedProvider.getRumContext()

        // Then
        assertThat(context).isEqualTo(fakeReplayContext.copy(viewUrl = fakeViewUrl))
    }

    @Test
    fun `M ignore unrelated feature W onContextUpdate`(@StringForgery fakeFeatureName: String) {
        // Given
        val testedProvider = SessionReplayRumContextProvider()
        val fakeContext = mapOf(
            FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId = fakeApplicationId, viewId = fakeFeatureName)
        )

        // When
        testedProvider.onContextUpdate(fakeFeatureName, fakeContext)

        // Then
        assertThat(testedProvider.getRumContext()).isEqualTo(
            SessionReplayRumContext(RumContext.NULL_UUID, RumContext.NULL_UUID, RumContext.NULL_UUID, 0L)
        )
    }

    @Test
    fun `M clear snapshot W onContextUpdate {no RUM context}`(@StringForgery fakeViewId: String) {
        // Given
        val testedProvider = SessionReplayRumContextProvider()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId = fakeApplicationId, viewId = fakeViewId))
        )

        // When
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())

        // Then
        assertThat(testedProvider.getRumContext()).isEqualTo(
            SessionReplayRumContext(RumContext.NULL_UUID, RumContext.NULL_UUID, RumContext.NULL_UUID, 0L)
        )
    }

    @Test
    fun `M retain application without recording context W getRumContext {no session}`() {
        // Given
        val testedProvider = SessionReplayRumContextProvider()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(FeatureContextKeys.RUM_CONTEXT to RumContext(applicationId = fakeApplicationId))
        )

        // When
        val context = testedProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
        assertThat(context.sessionId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.viewId).isEqualTo(RumContext.NULL_UUID)
        assertThat(context.isValid()).isFalse()
    }
}
