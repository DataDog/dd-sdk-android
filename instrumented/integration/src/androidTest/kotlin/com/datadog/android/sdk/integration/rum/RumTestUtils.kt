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

internal fun List<JsonObject>.verifyViewEventsMatches(
    expected: List<ExpectedViewEvent>
) {
    Assertions.assertThat(this.size)
        .withFailMessage(
            "We were expecting ${expected.size} rum " +
                "view events instead they were ${this.size}: \n" +
                " -- EXPECTED -- \n" +
                expected.joinToString("\n") { "\t>> $it" } +
                "\n -- ACTUAL -- \n" +
                this.joinToString("\n") { "\t>> $it" }
        )
        .isEqualTo(expected.size)
    // in case of views because they are reduced by their document version and they are not going to follow
    // the exact order of the expected events, we need to match them by their context and view id
    expected.forEach { expectedEvent ->
        val matchingEvent = this.find { actualEvent ->
            val viewId = actualEvent
                .getAsJsonObject("view")
                .getAsJsonPrimitive("id").asString
            val applicationId = actualEvent
                .getAsJsonObject("application")
                .getAsJsonPrimitive("id").asString
            val sessionId = actualEvent
                .getAsJsonObject("session")
                .getAsJsonPrimitive("id").asString
            expectedEvent.rumContext.viewId == viewId &&
                expectedEvent.rumContext.applicationId == applicationId &&
                expectedEvent.rumContext.sessionId == sessionId
        }
        checkNotNull(matchingEvent) {
            "No matching event found for $expectedEvent"
        }
        matchingEvent.verifyEventMatches(expectedEvent)
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
    this.getAsJsonObject("_dd").apply {
        val documentVersion = getAsJsonPrimitive("document_version").asInt
        Assertions.assertThat(documentVersion)
            .withFailMessage(
                "Expected document version for view with url: ${event.viewUrl} " +
                    "to be greater than or equal to ${event.docVersion} but instead was $documentVersion"
            )
            .isGreaterThanOrEqualTo(event.docVersion)
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
    val applicationId = getAsJsonObject("application")
        .getAsJsonPrimitive("id").asString
    val sessionId = getAsJsonObject("session")
        .getAsJsonPrimitive("id").asString
    val viewId = getAsJsonObject("view")
        .getAsJsonPrimitive("id").asString
    Assertions.assertThat(applicationId)
        .withFailMessage(
            "Expected event \n $this \n to have same application " +
                "id as \n $event \n but instead was $applicationId"
        )
        .isEqualTo(event.rumContext.applicationId)
    Assertions.assertThat(sessionId)
        .withFailMessage(
            "Expected event \n $this \n to have same session " +
                "id as \n $event \n but instead was $sessionId"
        )
        .isEqualTo(event.rumContext.sessionId)
    Assertions.assertThat(viewId)
        .withFailMessage(
            "Expected event \n $this \n to have same view " +
                "id as \n $event \n but instead was $viewId"
        )
        .isEqualTo(event.rumContext.viewId)
}
