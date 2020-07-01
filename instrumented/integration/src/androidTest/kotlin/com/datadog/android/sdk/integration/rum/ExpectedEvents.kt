/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import com.datadog.android.rum.GlobalRum
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import java.util.concurrent.atomic.AtomicReference

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
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal interface ExpectedEvent {
    val rumContext: ExpectedRumContext
}

internal enum class Gesture(val gestureName: String) {
    TAP("tap"),
    SWIPE("swipe")
}

private fun rumContextValues(): Triple<String, String, String> {
    val rumContextRef: AtomicReference<Any> =
        GlobalRum::class.java.getStaticValue("activeContext")
    val rumContext = rumContextRef.get()
    val appId: String = rumContext.getFieldValue("applicationId")
    val sessionId: String = rumContext.getFieldValue("sessionId")
    val viewId: String? = rumContext.getFieldValue("viewId")
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
