/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.telemetry

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.TelemetryTestRule
import com.datadog.tools.unit.ConditionWatcher
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

internal open class TelemetryTest {

    @get:Rule
    val mockServerRule = TelemetryTestRule(
        debugMessage = TELEMETRY_DEBUG_MESSAGE,
        errorMessage = TELEMETRY_ERROR_MESSAGE
    )

    @Test
    fun verifyTelemetryIsSent() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val expectedTelemetry = listOf(
            ExpectedTelemetryEvent.Debug(TELEMETRY_DEBUG_MESSAGE),
            ExpectedTelemetryEvent.Error(TELEMETRY_ERROR_MESSAGE, withThrowable = false),
            ExpectedTelemetryEvent.Error(TELEMETRY_ERROR_MESSAGE, withThrowable = true)
        )

        ConditionWatcher {
            verifyContainsOnlyExpectedTelemetry(
                handledRequests = mockServerRule.getRequests(),
                expectedTelemetry
            )
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)
    }

    // region Internal

    private fun verifyContainsOnlyExpectedTelemetry(
        handledRequests: List<HandledRequest>,
        expectedTelemetry: List<ExpectedTelemetryEvent>
    ) {
        val telemetryEvents = handledRequests
            .mapNotNull {
                it.textBody?.split(Regex("\n"))
                    ?.map { event -> JsonParser.parseString(event) as JsonObject }
            }
            .flatten()
            .filter {
                it.getAsJsonPrimitive("type")?.asString == "telemetry"
            }

        Assertions.assertThat(telemetryEvents.size)
            .overridingErrorMessage(
                "Recorded telemetry expected at least ${expectedTelemetry.size} event, " +
                    " got (${telemetryEvents.size}) which is less than expected."
            )
            .isGreaterThanOrEqualTo(expectedTelemetry.size)

        telemetryEvents.forEachIndexed { index, recordedEvent ->
            // Okay to get more telemetry, these just have to be the first 3
            if (index < expectedTelemetry.size) {
                verifyTelemetry(recordedEvent, expectedTelemetry[index])
            }
        }
    }

    private fun verifyTelemetry(recordedEvent: JsonObject, expectedEvent: ExpectedTelemetryEvent) {
        assertThat(recordedEvent)
            .hasField("telemetry") {
                hasField(
                    "status",
                    when (expectedEvent) {
                        is ExpectedTelemetryEvent.Debug -> "debug"
                        is ExpectedTelemetryEvent.Error -> "error"
                    }
                )
                hasField("message", expectedEvent.message)
                if (expectedEvent is ExpectedTelemetryEvent.Error && expectedEvent.withThrowable) {
                    hasField("error") {
                        hasNonEmptyField("kind")
                        hasNonEmptyField("stack")
                    }
                } else {
                    doesNotHaveField("error")
                }
            }
    }

    sealed class ExpectedTelemetryEvent(val message: String) {
        class Debug(message: String) : ExpectedTelemetryEvent(message)
        class Error(message: String, val withThrowable: Boolean) : ExpectedTelemetryEvent(message)
    }

    companion object {
        const val TELEMETRY_DEBUG_MESSAGE = "should-be-debug"
        const val TELEMETRY_ERROR_MESSAGE = "should-be-error"
        val FINAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
    }

    // endregion
}
