/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import com.datadog.android.nightly.INITIALIZE_RUMMONITOR_TEST_METHOD_NAME
import com.datadog.android.nightly.aResourceKey
import com.datadog.android.nightly.aResourceMethod
import com.datadog.android.nightly.aViewKey
import com.datadog.android.nightly.aViewName
import com.datadog.android.nightly.anActionName
import com.datadog.android.nightly.anErrorMessage
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.executeInsideView
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge

fun measureRumMonitorInitialize(codeBlock: () -> RumMonitor): RumMonitor {
    return measure(INITIALIZE_RUMMONITOR_TEST_METHOD_NAME, codeBlock)
}

fun sendRandomRumEvent(
    forge: Forge,
    testMethodName: String,
    parentViewEventName: String = testMethodName
) {
    when (forge.anInt(min = 0, max = 4)) {
        0 -> {
            val aViewKey = forge.aViewKey()
            GlobalRum.get().startView(
                aViewKey,
                forge.aViewName(),
                defaultTestAttributes(testMethodName)
            )
            GlobalRum.get().stopView(aViewKey, defaultTestAttributes(testMethodName))
        }
        1 -> {
            val aResourceKey = forge.aResourceKey()
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName) {
                GlobalRum.get().startResource(
                    aResourceKey,
                    forge.aResourceMethod(),
                    aResourceKey,
                    defaultTestAttributes(testMethodName)
                )
                GlobalRum.get().stopResource(
                    aResourceKey,
                    forge.anInt(min = 200, max = 500),
                    forge.aLong(min = 1),
                    forge.aValueFrom(RumResourceKind::class.java),
                    defaultTestAttributes(testMethodName)
                )
            }
        }
        2 -> {
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName) {
                GlobalRum.get().addError(
                    forge.anErrorMessage(),
                    forge.aValueFrom(RumErrorSource::class.java),
                    forge.aNullable { forge.aThrowable() },
                    defaultTestAttributes(testMethodName)
                )
            }
        }
        3 -> {
            executeInsideView(forge.aViewKey(), forge.aViewName(), parentViewEventName) {
                GlobalRum.get().addUserAction(
                    forge.aValueFrom(RumActionType::class.java),
                    forge.anActionName(),
                    defaultTestAttributes(testMethodName)
                )
                sendRandomActionOutcomeEvent(forge)
            }
        }
    }
}
