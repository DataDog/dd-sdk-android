/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.profiling.ProfilingThreadDump
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ProfilingThreadDumpForgeryFactory : ForgeryFactory<ProfilingThreadDump> {
    override fun getForgery(forge: Forge): ProfilingThreadDump {
        return ProfilingThreadDump(
            name = forge.anAlphaNumericalString(),
            state = forge.aValueFrom(Thread.State::class.java),
            stack = forge.aList {
                StackTraceElement(
                    forge.anAlphabeticalString(),
                    forge.anAlphabeticalString(),
                    forge.aNullable { anAlphabeticalString() + if (forge.aBool()) ".java" else ".kt" },
                    forge.anInt(min = -2)
                )
            }.joinToString(separator = "\n") { "at $it" }
        )
    }
}
