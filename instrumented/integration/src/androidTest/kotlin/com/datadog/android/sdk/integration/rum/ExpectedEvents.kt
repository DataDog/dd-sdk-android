/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import com.datadog.android.rum.GlobalRum
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal data class ExpectedRumContext(
    val applicationId: String,
    val sessionId: String,
    val viewId: String
)

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
    val extras: Map<String, Any?> = mapOf(),
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal interface ExpectedEvent {
    val rumContext: ExpectedRumContext
}

internal enum class Gesture(val gestureName: String) {
    TAP("Tap"),
    SWIPE("Swipe")
}

private fun rumContextValues(): Triple<String, String, String> {
    val rumContextRef: AtomicReference<Any> =
        GlobalRum::class.java.getStaticValue("activeContext")
    val rumContext = rumContextRef.get()
    val appUUID: UUID = rumContext.getFieldValue("applicationId")
    val sessionUUID: UUID = rumContext.getFieldValue("sessionId")
    val viewUUID: UUID? = rumContext.getFieldValue("viewId")
    return Triple(appUUID.toString(), sessionUUID.toString(), viewUUID?.toString() ?: "null")
}

private fun resolvedRumContext(): ExpectedRumContext {
    val rumContextValues = rumContextValues()
    return ExpectedRumContext(
        rumContextValues.first,
        rumContextValues.second,
        rumContextValues.third
    )
}
