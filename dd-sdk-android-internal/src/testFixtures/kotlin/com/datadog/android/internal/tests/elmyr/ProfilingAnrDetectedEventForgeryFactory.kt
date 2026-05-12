/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.profiling.ProfilingThreadDump
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ProfilingAnrDetectedEventForgeryFactory : ForgeryFactory<ProfilingAnrDetectedEvent> {
    override fun getForgery(forge: Forge): ProfilingAnrDetectedEvent {
        return ProfilingAnrDetectedEvent(
            detectedAtMs = forge.aLong(min = 1L),
            anrThreadStack = forge.aList {
                StackTraceElement(
                    forge.anAlphabeticalString(),
                    forge.anAlphabeticalString(),
                    forge.aNullable { anAlphabeticalString() + if (forge.aBool()) ".java" else ".kt" },
                    forge.anInt(min = -2)
                )
            },
            allThreads = forge.aList { getForgery<ProfilingThreadDump>() }
        )
    }
}
