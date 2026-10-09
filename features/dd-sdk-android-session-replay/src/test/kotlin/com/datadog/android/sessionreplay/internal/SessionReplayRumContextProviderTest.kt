/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.NULL_UUID
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_APPLICATION_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_SESSION_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_VIEW_ID_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_VIEW_TIME_OFFSET_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.SessionReplayRumContextProvider.Companion.RUM_VIEW_URL_CONTEXT_KEY
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRumContextProviderTest {

    private val testedSessionReplayContextProvider = SessionReplayRumContextProvider()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve previous view W onContextUpdate {core reuses mutable map}`(fakeSchedulingEnabled: Boolean) {
        // Given
        val fakeFirstViewId = UUID.randomUUID().toString()
        val fakeSecondViewId = UUID.randomUUID().toString()
        val fakeNotifications = mutableListOf<String>()
        lateinit var testedProvider: SessionReplayRumContextProvider
        testedProvider = SessionReplayRumContextProvider(fakeSchedulingEnabled) {
            fakeNotifications.add(testedProvider.getRumContext().viewId)
        }
        val fakeContext = mutableMapOf<String, Any?>(RUM_VIEW_ID_CONTEXT_KEY to fakeFirstViewId)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)

        // When: core changes the same map before notifying receivers.
        fakeContext.clear()
        fakeContext[RUM_VIEW_ID_CONTEXT_KEY] = fakeSecondViewId

        // Then: only the opt-in path isolates reads from mutations before notification.
        assertThat(testedProvider.getRumContext().viewId)
            .isEqualTo(if (fakeSchedulingEnabled) fakeFirstViewId else fakeSecondViewId)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, fakeContext)
        assertThat(fakeNotifications).containsExactlyElementsOf(
            if (fakeSchedulingEnabled) listOf(fakeFirstViewId, fakeSecondViewId) else listOf(fakeFirstViewId)
        )
        assertThat(testedProvider.getRumContext().viewId).isEqualTo(fakeSecondViewId)
    }

    @Test
    fun `M notify first view W onContextUpdate { new valid RUM view }`() {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }
        val viewId = UUID.randomUUID().toString()

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to viewId)
        )

        // Then
        assertThat(notificationCount).isEqualTo(1)
    }

    @Test
    fun `M publish new context before notifying W onContextUpdate { new valid RUM view }`() {
        // Given
        val viewId = UUID.randomUUID().toString()
        lateinit var testedProvider: SessionReplayRumContextProvider
        var notifiedViewId: String? = null
        testedProvider = SessionReplayRumContextProvider {
            notifiedViewId = testedProvider.getRumContext().viewId
        }

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to viewId)
        )

        // Then
        assertThat(notifiedViewId).isEqualTo(viewId)
    }

    @Test
    fun `M notify view transition W onContextUpdate { valid RUM view changes }`() {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to UUID.randomUUID().toString())
        )

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to UUID.randomUUID().toString())
        )

        // Then
        assertThat(notificationCount).isEqualTo(2)
    }

    @Test
    fun `M not notify view change W onContextUpdate { same RUM view }`() {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }
        val viewId = UUID.randomUUID().toString()
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to viewId)
        )

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to viewId, RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to 10L)
        )

        // Then
        assertThat(notificationCount).isEqualTo(1)
    }

    @Test
    fun `M not notify view change W onContextUpdate { invalid view }`() {
        // Given
        var notificationCount = 0
        val testedProvider = SessionReplayRumContextProvider { notificationCount++ }

        // When
        testedProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(RUM_VIEW_ID_CONTEXT_KEY to NULL_UUID)
        )
        testedProvider.onContextUpdate(Feature.RUM_FEATURE_NAME, emptyMap())

        // Then
        assertThat(notificationCount).isZero()
    }

    @Test
    fun `M provide a valid Rum context W getRumContext()`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long,
        @StringForgery fakeViewUrl: String
    ) {
        // Given
        testedSessionReplayContextProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                RUM_APPLICATION_ID_CONTEXT_KEY to fakeApplicationId.toString(),
                RUM_SESSION_ID_CONTEXT_KEY to fakeSessionId.toString(),
                RUM_VIEW_ID_CONTEXT_KEY to fakeViewId.toString(),
                RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to fakeViewTimeOffsetMs,
                RUM_VIEW_URL_CONTEXT_KEY to fakeViewUrl
            )
        )

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.applicationId).isEqualTo(fakeApplicationId.toString())
        assertThat(context.sessionId).isEqualTo(fakeSessionId.toString())
        assertThat(context.viewId).isEqualTo(fakeViewId.toString())
        assertThat(context.viewTimeOffsetMs).isEqualTo(fakeViewTimeOffsetMs)
        assertThat(context.viewUrl).isEqualTo(fakeViewUrl)
    }

    @Test
    fun `M provide null viewUrl W getRumContext() { view_url missing from RUM context }`(
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long
    ) {
        // Given — RUM context update that does NOT include view_url (mirrors the case where
        // no RUM view is active yet).
        testedSessionReplayContextProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                RUM_APPLICATION_ID_CONTEXT_KEY to fakeApplicationId.toString(),
                RUM_SESSION_ID_CONTEXT_KEY to fakeSessionId.toString(),
                RUM_VIEW_ID_CONTEXT_KEY to fakeViewId.toString(),
                RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to fakeViewTimeOffsetMs
            )
        )

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.viewUrl).isNull()
    }

    @Test
    fun `M provide null viewUrl W getRumContext() { view_url is not a String }`(
        forge: Forge,
        @Forgery fakeApplicationId: UUID,
        @Forgery fakeSessionId: UUID,
        @Forgery fakeViewId: UUID,
        @LongForgery(min = 0L) fakeViewTimeOffsetMs: Long
    ) {
        // Given — view_url present but wrong type. We expect a silent null rather than a crash
        // because SR cannot rely on the structure of someone else's feature context.
        testedSessionReplayContextProvider.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(
                RUM_APPLICATION_ID_CONTEXT_KEY to fakeApplicationId.toString(),
                RUM_SESSION_ID_CONTEXT_KEY to fakeSessionId.toString(),
                RUM_VIEW_ID_CONTEXT_KEY to fakeViewId.toString(),
                RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to fakeViewTimeOffsetMs,
                RUM_VIEW_URL_CONTEXT_KEY to forge.anInt()
            )
        )

        // When
        val context = testedSessionReplayContextProvider.getRumContext()

        // Then
        assertThat(context.viewUrl).isNull()
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
                    RUM_APPLICATION_ID_CONTEXT_KEY to fakeApplicationId.toString(),
                    RUM_SESSION_ID_CONTEXT_KEY to fakeSessionId.toString(),
                    RUM_VIEW_ID_CONTEXT_KEY to fakeViewId.toString(),
                    RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to fakeViewTimeOffsetMs
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
                RUM_APPLICATION_ID_CONTEXT_KEY to fakeApplicationId.toString(),
                RUM_SESSION_ID_CONTEXT_KEY to fakeSessionId.toString(),
                RUM_VIEW_ID_CONTEXT_KEY to fakeViewId.toString(),
                RUM_VIEW_TIME_OFFSET_CONTEXT_KEY to fakeViewTimeOffsetMs
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
