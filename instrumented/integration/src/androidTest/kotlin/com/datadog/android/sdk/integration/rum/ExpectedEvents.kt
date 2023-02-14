/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import com.datadog.android.Datadog
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.getStaticValue
import com.google.gson.JsonElement

internal data class ExpectedRumContext(
    val applicationId: String,
    val sessionId: String,
    val viewId: String
)

internal data class ExpectedApplicationStart(
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal data class ExpectedGestureEvent(
    val type: Gesture,
    val targetClassName: String,
    val targetResourceId: String,
    override val rumContext: ExpectedRumContext = resolvedRumContext(),
    val extraAttributes: Map<String, Any?> = emptyMap()
) : ExpectedEvent

internal data class ExpectedViewEvent(
    val viewUrl: String,
    val docVersion: Int = 1,
    val viewArguments: Map<String, Any?> = mapOf(),
    val extraAttributes: Map<String, Any?> = mapOf(),
    val extraViewAttributes: Map<String, Any?> = mapOf(),
    val extraViewAttributesWithPredicate: Map<String, (JsonElement) -> Boolean> = mapOf(),
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal data class ExpectedApplicationLaunchViewEvent(
    val docVersion: Int = 0,
    val viewArguments: Map<String, Any?> = mapOf(),
    val extraAttributes: Map<String, Any?> = mapOf(),
    val extraViewAttributes: Map<String, Any?> = mapOf(),
    val extraViewAttributesWithPredicate: Map<String, (JsonElement) -> Boolean> = mapOf(),
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal data class ExpectedResourceEvent(
    val url: String,
    val statusCode: Int,
    val extraAttributes: Map<String, Any?>,
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal data class ExpectedErrorEvent(
    val url: String,
    val extraAttributes: Map<String, Any?>,
    val isCrash: Boolean,
    val source: ErrorSource,
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal interface ExpectedEvent {
    val rumContext: ExpectedRumContext
}

internal enum class Gesture(val gestureName: String) {
    TAP("tap"),
    SWIPE("swipe")
}

internal enum class ErrorSource(val sourceName: String) {
    NETWORK("network")
}

private fun rumContextValues(): Triple<String, String, String> {
    val sdkCore: SdkCore = Datadog::class.java.getStaticValue("globalSdkCore")
    val rumContext: Map<String, Any?> = sdkCore.getFeatureContext("rum")
    val appId: String = rumContext["application_id"] as String
    val sessionId: String = rumContext["session_id"] as String
    val viewId: String? = rumContext["view_id"] as? String
    return Triple(appId, sessionId, viewId ?: "null")
}

private fun resolvedRumContext(): ExpectedRumContext {
    val rumContextValues = rumContextValues()
    return ExpectedRumContext(
        rumContextValues.first,
        rumContextValues.second,
        rumContextValues.third
    )
}
