/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions

internal const val EVENT_NAME = "evt.name"
internal const val TARGET_CLASS_NAME = "target.classname"
internal const val TARGET_RESOURCE_ID = "target.resourceId"
internal const val APPLICATION_ID = "application_id"
internal const val SESSION_ID = "session_id"
internal const val VIEW_ID = "view.id"

internal const val VIEW_URL = "view.url"
internal const val RUM_DOC_VERSION = "rum.document_version"
internal const val VIEW_ARGUMENTS_PREFIX = "view.arguments."

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
        .hasField(EVENT_NAME, event.type.gestureName)
        .hasField(TARGET_CLASS_NAME, event.targetClassName)
        .hasField(TARGET_RESOURCE_ID, event.targetResourceId)
    JsonObjectAssert.assertThat(this).bundlesMap(event.extraAttributes)
}

private fun JsonObject.assertMatches(event: ExpectedViewEvent) {
    assertMatchesRoot(event)
    JsonObjectAssert.assertThat(this)
        .hasField(VIEW_URL, event.viewUrl)
        .hasField(RUM_DOC_VERSION, event.docVersion)
    JsonObjectAssert.assertThat(this).bundlesMap(event.viewArguments, VIEW_ARGUMENTS_PREFIX)
    JsonObjectAssert.assertThat(this).bundlesMap(event.extraAttributes)
}

private fun JsonObject.assertMatchesRoot(event: ExpectedEvent) {
    JsonObjectAssert.assertThat(this)
        .hasField(APPLICATION_ID, event.rumContext.applicationId)
        .hasField(SESSION_ID, event.rumContext.sessionId)
        .hasField(VIEW_ID, event.rumContext.viewId)
}
