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
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal data class ExpectedViewEvent(
    val viewUrl: String,
    val docVersion: Int = 1,
    override val rumContext: ExpectedRumContext = resolvedRumContext()
) : ExpectedEvent

internal interface ExpectedEvent {
    val rumContext: ExpectedRumContext
}

internal enum class Gesture(val gestureName: String) {
    TAP("TapEvent")
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
