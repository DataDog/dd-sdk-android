/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ErrorSentForgeryFactory : ForgeryFactory<RumRawEvent.ErrorSent> {
    override fun getForgery(forge: Forge) = RumRawEvent.ErrorSent(
        viewId = forge.aString(),
        resourceId = forge.aNullable { aString() },
        resourceEndTimestampInNanos = forge.aNullable { aPositiveLong() }
    )
}
