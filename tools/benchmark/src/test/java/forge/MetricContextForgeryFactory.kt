/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package forge

import com.datadog.benchmark.internal.model.MetricContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class MetricContextForgeryFactory : ForgeryFactory<MetricContext> {
    override fun getForgery(forge: Forge): MetricContext {
        return MetricContext(
            deviceModel = forge.aString(),
            osVersion = forge.aString(),
            run = forge.aString(),
            applicationId = forge.aString(),
            intervalInSeconds = forge.aLong()
        )
    }
}
