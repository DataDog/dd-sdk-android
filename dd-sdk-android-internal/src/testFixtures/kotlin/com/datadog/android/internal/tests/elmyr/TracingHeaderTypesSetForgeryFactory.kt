/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.TracingHeaderType
import com.datadog.android.internal.telemetry.TracingHeaderTypesSet
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TracingHeaderTypesSetForgeryFactory : ForgeryFactory<TracingHeaderTypesSet> {
    override fun getForgery(forge: Forge): TracingHeaderTypesSet {
        return TracingHeaderTypesSet(
            types = forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        )
    }
}
