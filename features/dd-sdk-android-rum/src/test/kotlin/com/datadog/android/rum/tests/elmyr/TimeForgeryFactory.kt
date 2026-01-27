/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.datadog.android.rum.internal.domain.Time
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TimeForgeryFactory : ForgeryFactory<Time> {
    override fun getForgery(forge: Forge): Time {
        return Time(
            timestamp = forge.aLong(min = 0),
            nanoTime = forge.aLong(min = 0)
        )
    }
}
