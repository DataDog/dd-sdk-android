/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.core.integration.tests.utils.poll
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RumViewUpdateTest : BaseRumViewTest() {

    @Test
    fun must_report2Actions_when_2ClickActionsAdded() {
        runBlocking {
            // Given
            val viewKey = UUID.randomUUID().toString()

            // When
            val rumMonitor = GlobalRumMonitor.get(sdkCore)
            rumMonitor.startView(key = viewKey, name = VIEW_NAME)
            delay(1000)
            rumMonitor.addAction(RumActionType.CUSTOM, "click1", emptyMap())
            delay(5000)
            rumMonitor.addFeatureFlagEvaluation("flag_bool", true)
            delay(1000)
            rumMonitor.addFeatureFlagEvaluation("flag_int", 42)
            delay(1000)
            val resourceKey = UUID.randomUUID().toString()
            rumMonitor.startResource(resourceKey, RumResourceMethod.GET, "https://httpbin.org/get")
            delay(1000)
            rumMonitor.stopResource(resourceKey, 200, null, RumResourceKind.FETCH)
            delay(5000)
            rumMonitor.addFeatureFlagEvaluation("flag_bool", false)
            delay(1000)
            rumMonitor.addFeatureFlagEvaluation("flag_int", 100)
            delay(1000)
            rumMonitor.addAction(RumActionType.CUSTOM, "click2", emptyMap())
            delay(5000)
            rumMonitor.stopView(viewKey)
            delay(5000)

            // Then
            val response = poll(
                block = {
                    datadogApiClient.getRumViewEvent(
                        name = VIEW_NAME,
                        contextAttributes = mapOf(
                            "test_view_uuid" to testViewUuid,
                            "test_view_index" to 8
                        )
                    )
                },
                predicate = { it.optionalResult?.data?.firstOrNull() != null },
                interval = POLLING_INTERVAL_MS.milliseconds,
                timeout = POLLING_TIMEOUT_MS.milliseconds
            )

            val viewEvent = checkNotNull(response?.optionalResult?.data?.firstOrNull())

            RumSearchResponseViewEventAssert.assertThat(viewEvent).apply {
                hasActionCount(2)
                hasResourceCount(1)
                hasFeatureFlagBoolean("flag_bool", false)
                hasFeatureFlagInt("flag_int", 100)
            }
        }
    }

    companion object {
        private const val VIEW_NAME = "rum-view-update-test"
        private const val POLLING_TIMEOUT_MS = 30_000L
        private const val POLLING_INTERVAL_MS = 5_000L
    }
}
