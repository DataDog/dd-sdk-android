/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package forge

import com.datadog.benchmark.internal.model.BenchmarkContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class BenchmarkContextForgeryFactory : ForgeryFactory<BenchmarkContext> {
    override fun getForgery(forge: Forge): BenchmarkContext {
        return BenchmarkContext(
            deviceModel = forge.anAsciiString(),
            osVersion = forge.anAsciiString(),
            run = forge.anAsciiString(),
            applicationId = forge.anAsciiString(),
            intervalInSeconds = forge.aLong(),
            scenario = forge.anAsciiString(),
            env = forge.anAsciiString()
        )
    }
}
