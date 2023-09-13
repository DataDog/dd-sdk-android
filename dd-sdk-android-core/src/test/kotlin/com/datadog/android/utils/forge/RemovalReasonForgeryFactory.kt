/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.metrics.RemovalReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RemovalReasonForgeryFactory : ForgeryFactory<RemovalReason> {

    override fun getForgery(forge: Forge): RemovalReason {
        return forge.anElementFrom(
            listOf(
                forge.getForgery(RemovalReason.Purged::class.java),
                forge.getForgery(RemovalReason.Obsolete::class.java),
                forge.getForgery(RemovalReason.Flushed::class.java),
                forge.getForgery(RemovalReason.Invalid::class.java),
                forge.getForgery(RemovalReason.IntakeCode::class.java)
            )
        )
    }
}
