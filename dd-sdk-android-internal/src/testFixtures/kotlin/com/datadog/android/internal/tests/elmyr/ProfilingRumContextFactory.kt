/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.profiling.ProfilingRumContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ProfilingRumContextFactory : ForgeryFactory<ProfilingRumContext> {
    override fun getForgery(forge: Forge): ProfilingRumContext {
        return ProfilingRumContext(
            applicationId = forge.anAlphabeticalString(),
            sessionId = forge.anAlphabeticalString(),
            viewId = forge.aNullable { anAlphabeticalString() },
            viewName = forge.aNullable { anAlphabeticalString() }
        )
    }
}
