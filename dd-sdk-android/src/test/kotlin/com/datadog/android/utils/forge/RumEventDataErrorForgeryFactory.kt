/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventData
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataErrorForgeryFactory : ForgeryFactory<RumEventData.Error> {

    override fun getForgery(forge: Forge): RumEventData.Error {

        return RumEventData.Error(
            message = forge.anAlphabeticalString(),
            origin = forge.anAlphabeticalString(),
            throwable = forge.aThrowable()
        )
    }
}
