/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MethodOverloading")

package com.datadog.android.sdk.integration.rum

import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions

internal const val TARGET_CLASS_NAME = "action.target.classname"
internal const val TARGET_RESOURCE_ID = "action.target.resource_id"
internal const val VIEW_ARGUMENTS_PREFIX = "view.arguments."
internal const val CONTEXT_KEY = "context"

internal fun rumPayloadToJsonList(payload: String): List<JsonObject> {
    return payload.split(Regex("\n"))
        .map { JsonParser.parseString(it) as JsonObject }
}

internal fun List<JsonObject>.verifyEventMatches(
    expected: List<ExpectedEvent>
) {
    Assertions.assertThat(this.size)
        .withFailMessage(
            "We were expecting ${expected.size} rum " +
                "events instead they were ${this.size}: \n" +
                " -- EXPECTED -- \n" +
                expected.joinToString("\n") { "\t>> $it" } +
                "\n -- ACTUAL -- \n" +
                this.joinToString("\n") { "\t>> $it" }
        )
        .isEqualTo(expected.size)

    this.forEachIndexed { index, event ->
        when (val expectedEvent = expected[index]) {
            is ExpectedApplicationLaunchViewEvent -> event.verifyEventMatches(expectedEvent)
            is ExpectedViewEvent -> event.verifyEventMatches(expectedEvent)
            is ExpectedGestureEvent -> event.verifyEventMatches(expectedEvent)
            is ExpectedApplicationStartActionEvent -> event.verifyEventMatches(expectedEvent)
            is ExpectedResourceEvent -> event.verifyEventMatches(expectedEvent)
            is ExpectedErrorEvent -> event.verifyEventMatches(expectedEvent)
            else -> {
                // Do nothing
            }
        }
    }
}

private fun JsonObject.verifyEventMatches(event: ExpectedApplicationLaunchViewEvent) {
    assertThat(this)
        .hasField("application") {
            hasField("id", event.rumContext.applicationId)
        }
        .hasField("session") {
            hasField("id", event.rumContext.sessionId)
        }
        .hasField("view") {
            hasField("url", "com/datadog/application-launch/view")
        }
        .hasField("_dd") {
            hasField("document_version", event.docVersion)
        }

    assertThat(this).containsAttributes(event.extraAttributes)
    val viewArguments = event.viewArguments
        .map { "$VIEW_ARGUMENTS_PREFIX${it.key}" to it.value }
        .toMap()
    assertThat(this.getAsJsonObject("view"))
        .containsAttributes(event.extraViewAttributes)
    assertThat(this.getAsJsonObject("view"))
        .containsAttributesMatchingPredicate(event.extraViewAttributesWithPredicate)
    if (viewArguments.isNotEmpty()) {
        assertThat(this.getAsJsonObject(CONTEXT_KEY)).containsAttributes(viewArguments)
    }
}

private fun JsonObject.verifyEventMatches(event: ExpectedApplicationStartActionEvent) {
    assertThat(this)
        .hasField("application") {
            hasField("id", event.rumContext.applicationId)
        }
        .hasField("session") {
            hasField("id", event.rumContext.sessionId)
        }
        .hasField("action") {
            hasField("type", "application_start")
        }
}

private fun JsonObject.verifyEventMatches(event: ExpectedGestureEvent) {
    verifyRootMatches(event)
    assertThat(this)
        .hasField("action") {
            hasField("type", event.type.gestureName)
        }
        .hasField(TARGET_CLASS_NAME, event.targetClassName)
        .hasField(TARGET_RESOURCE_ID, event.targetResourceId)
    assertThat(this).containsAttributes(event.extraAttributes)
}

private fun JsonObject.verifyEventMatches(event: ExpectedViewEvent) {
    verifyRootMatches(event)

    assertThat(this)
        .hasField("view") {
            hasField("url", event.viewUrl)
        }
        .hasField("_dd") {
            hasField("document_version", event.docVersion)
        }

    assertThat(this).containsAttributes(event.extraAttributes)
    val viewArguments = event.viewArguments
        .map { "$VIEW_ARGUMENTS_PREFIX${it.key}" to it.value }
        .toMap()
    assertThat(this.getAsJsonObject("view"))
        .containsAttributes(event.extraViewAttributes)
    assertThat(this.getAsJsonObject("view"))
        .containsAttributesMatchingPredicate(event.extraViewAttributesWithPredicate)
    if (viewArguments.isNotEmpty()) {
        assertThat(this.getAsJsonObject(CONTEXT_KEY)).containsAttributes(viewArguments)
    }
    assertThat(this)
        .hasField("session") {
            hasField("is_active", event.sessionIsActive)
        }
}

private fun JsonObject.verifyEventMatches(event: ExpectedResourceEvent) {
    verifyRootMatches(event)

    assertThat(this)
        .hasField("resource") {
            hasField("status_code", event.statusCode)
            hasField("url", event.url)
        }

    if (event.extraAttributes.isNotEmpty()) {
        assertThat(this.getAsJsonObject(CONTEXT_KEY))
            .containsAttributes(event.extraAttributes)
    }
}

private fun JsonObject.verifyEventMatches(event: ExpectedErrorEvent) {
    verifyRootMatches(event)

    assertThat(this)
        .hasField("error") {
            if (has("resource")) {
                hasField("resource") {
                    hasField("url", event.url)
                    hasField("is_crash", event.isCrash)
                    hasField("source", event.source.sourceName)
                }
            }
        }

    if (event.extraAttributes.isNotEmpty()) {
        assertThat(this.getAsJsonObject(CONTEXT_KEY)).containsAttributes(event.extraAttributes)
    }
}

private fun JsonObject.verifyRootMatches(event: ExpectedEvent) {
    assertThat(this)
        .hasField("application") {
            hasField("id", event.rumContext.applicationId)
        }
        .hasField("session") {
            hasField("id", event.rumContext.sessionId)
        }
        .hasField("view") {
            hasField("id", event.rumContext.viewId)
        }
}
