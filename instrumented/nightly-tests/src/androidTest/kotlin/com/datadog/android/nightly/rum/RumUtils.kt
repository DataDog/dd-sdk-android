/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.INITIALIZE_RUMMONITOR_TEST_METHOD_NAME
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.utils.aResourceKey
import com.datadog.android.nightly.utils.aResourceMethod
import com.datadog.android.nightly.utils.aViewKey
import com.datadog.android.nightly.utils.aViewName
import com.datadog.android.nightly.utils.anActionName
import com.datadog.android.nightly.utils.anErrorMessage
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.executeInsideView
import com.datadog.android.nightly.utils.measure
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge

fun measureRumMonitorInitialize(codeBlock: () -> RumMonitor): RumMonitor {
    return measure(INITIALIZE_RUMMONITOR_TEST_METHOD_NAME, codeBlock)
}

fun sendRandomRumEvent(
    forge: Forge,
    sdkCore: SdkCore,
    testMethodName: String,
    parentViewEventName: String = testMethodName
) {
    when (forge.anInt(min = 0, max = 4)) {
        0 -> {
            val aViewKey = forge.aViewKey()
            GlobalRumMonitor.get(sdkCore).startView(
                aViewKey,
                forge.aViewName(),
                defaultTestAttributes(testMethodName)
            )
            GlobalRumMonitor.get(sdkCore).stopView(aViewKey, defaultTestAttributes(testMethodName))
        }
        1 -> {
            val aResourceKey = forge.aResourceKey()
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName, sdkCore) {
                GlobalRumMonitor.get(sdkCore).startResource(
                    aResourceKey,
                    forge.aResourceMethod(),
                    aResourceKey,
                    defaultTestAttributes(testMethodName)
                )
                GlobalRumMonitor.get(sdkCore).stopResource(
                    aResourceKey,
                    forge.anInt(min = 200, max = 500),
                    forge.aLong(min = 1),
                    forge.aValueFrom(RumResourceKind::class.java),
                    defaultTestAttributes(testMethodName)
                )
            }
        }
        2 -> {
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName, sdkCore) {
                GlobalRumMonitor.get(sdkCore).addError(
                    forge.anErrorMessage(),
                    forge.aValueFrom(RumErrorSource::class.java),
                    forge.aNullable { forge.aThrowable() },
                    defaultTestAttributes(testMethodName)
                )
            }
        }
        3 -> {
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName, sdkCore) {
                GlobalRumMonitor.get(sdkCore).addAction(
                    forge.aValueFrom(RumActionType::class.java),
                    forge.anActionName(),
                    defaultTestAttributes(testMethodName)
                )
            }
        }
    }
}

/**
 * Send all RUM events: View + Action + Resource + Error + LongTask
 */
fun sendAllRumEvents(
    forge: Forge,
    sdkCore: SdkCore,
    testMethodName: String
) {
    val aViewKey = forge.aViewKey()
    GlobalRumMonitor.get(sdkCore).startView(
        aViewKey,
        forge.aViewName(),
        defaultTestAttributes(testMethodName)
    )

    listOf(::sendResourceEvent, ::sendActionEvent, ::sendErrorEvent, ::sendLongTaskEvent)
        .shuffled()
        .forEach {
            it.invoke(forge, sdkCore, testMethodName)
        }

    GlobalRumMonitor.get(sdkCore).stopView(aViewKey, defaultTestAttributes(testMethodName))
}

private fun sendResourceEvent(forge: Forge, sdkCore: SdkCore, testMethodName: String) {
    val aResourceKey = forge.aResourceKey()
    GlobalRumMonitor.get(sdkCore).startResource(
        aResourceKey,
        forge.aResourceMethod(),
        aResourceKey,
        defaultTestAttributes(testMethodName)
    )
    GlobalRumMonitor.get(sdkCore).stopResource(
        aResourceKey,
        forge.anInt(min = 200, max = 500),
        forge.aLong(min = 1),
        forge.aValueFrom(RumResourceKind::class.java),
        defaultTestAttributes(testMethodName)
    )
}

private fun sendErrorEvent(forge: Forge, sdkCore: SdkCore, testMethodName: String) {
    GlobalRumMonitor.get(sdkCore).addError(
        forge.anErrorMessage(),
        forge.aValueFrom(RumErrorSource::class.java),
        forge.aNullable { forge.aThrowable() },
        defaultTestAttributes(testMethodName)
    )
}

private fun sendActionEvent(forge: Forge, sdkCore: SdkCore, testMethodName: String) {
    GlobalRumMonitor.get(sdkCore).addAction(
        forge.aValueFrom(RumActionType::class.java),
        forge.anActionName(),
        defaultTestAttributes(testMethodName)
    )
}

@Suppress("UNUSED_PARAMETER")
private fun sendLongTaskEvent(forge: Forge, sdkCore: SdkCore, testMethodName: String) {
    GlobalRumMonitor.get(sdkCore).addAttribute(TEST_METHOD_NAME_KEY, testMethodName)
    Handler(Looper.getMainLooper()).post {
        Thread.sleep(100)
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
