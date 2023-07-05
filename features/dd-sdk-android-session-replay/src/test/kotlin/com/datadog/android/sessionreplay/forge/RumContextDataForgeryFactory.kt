/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.internal.processor.RumContextData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumContextDataForgeryFactory : ForgeryFactory<RumContextData> {
    override fun getForgery(forge: Forge): RumContextData {
        return RumContextData(
            forge.aLong(),
            forge.getForgery(),
            forge.getForgery()
        )
    }
}
