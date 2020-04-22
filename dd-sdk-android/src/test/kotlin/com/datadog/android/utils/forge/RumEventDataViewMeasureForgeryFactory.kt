/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventDataViewMeasureForgeryFactory : ForgeryFactory<RumEventData.View.Measures> {

    override fun getForgery(forge: Forge): RumEventData.View.Measures {

        return RumEventData.View.Measures(
            errorCount = forge.anInt(0, forge.aSmallInt()),
            resourceCount = forge.anInt(0, forge.aSmallInt()),
            userActionCount = forge.anInt(0, forge.aSmallInt())
        )
    }
}
