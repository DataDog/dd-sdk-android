/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import com.datadog.android.rum.RumAttributes
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions

internal fun rumPayloadToJsonList(payload: String): List<JsonObject> {
    return payload.split(Regex("\n"))
        .map { JsonParser.parseString(it) as JsonObject }
}

internal fun List<JsonObject>.assertMatches(
    expected: List<ExpectedEvent>
) {
    Assertions.assertThat(this.size)
        .withFailMessage(
            "We were expecting ${expected.size} rum " +
                    "events instead they were ${this.size}"
        )
        .isEqualTo(expected.size)

    this.forEachIndexed { index, event ->
        when (val expectedEvent = expected[index]) {
            is ExpectedViewEvent -> event.assertMatches(expectedEvent)
            is ExpectedGestureEvent -> event.assertMatches(expectedEvent)
            else -> {
                // Do nothing
            }
        }
    }
}

private fun JsonObject.assertMatches(event: ExpectedGestureEvent) {
    assertMatchesRoot(event)
    JsonObjectAssert.assertThat(this)
        .hasField(RumAttributes.ACTION_TYPE, event.type.gestureName)
        .hasField(RumAttributes.ACTION_TARGET_CLASS_NAME, event.targetClassName)
        .hasField(RumAttributes.ACTION_TARGET_RESOURCE_ID, event.targetResourceId)
    JsonObjectAssert.assertThat(this).bundlesMap(event.extraAttributes)
}

private fun JsonObject.assertMatches(event: ExpectedViewEvent) {
    assertMatchesRoot(event)
    JsonObjectAssert.assertThat(this)
        .hasField(RumAttributes.VIEW_URL, event.viewUrl)
    JsonObjectAssert.assertThat(this).bundlesMap(event.viewArguments, "view.arguments.")
    JsonObjectAssert.assertThat(this).bundlesMap(event.extraAttributes)
}

private fun JsonObject.assertMatchesRoot(event: ExpectedEvent) {
    JsonObjectAssert.assertThat(this)
        .hasField(RumAttributes.APPLICATION_ID, event.rumContext.applicationId)
        .hasField(RumAttributes.SESSION_ID, event.rumContext.sessionId)
        .hasField(RumAttributes.VIEW_ID, event.rumContext.viewId)
}
