/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.core.feature.event.ThreadDump
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ThreadDumpForgeryFactory : ForgeryFactory<ThreadDump> {
    override fun getForgery(forge: Forge): ThreadDump {
        return ThreadDump(
            name = forge.anAlphaNumericalString(),
            state = forge.getForgery<Thread.State>().name.lowercase(),
            crashed = forge.aBool(),
            stack = forge.aList {
                StackTraceElement(
                    // declaring class
                    forge.anAlphabeticalString(),
                    // method name
                    forge.anAlphabeticalString(),
                    // file name
                    forge.aNullable { anAlphabeticalString() + if (forge.aBool()) ".java" else ".kt" },
                    // line number
                    // A value of -2 indicates that the method containing the execution point is a native method
                    forge.anInt(min = -2)
                )
            }.toTypedArray().joinToString(separator = "\n") { "at $it" }
        )
    }
}
