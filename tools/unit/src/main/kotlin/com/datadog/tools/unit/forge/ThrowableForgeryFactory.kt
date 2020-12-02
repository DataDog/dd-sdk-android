/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

/**
 *  A [ForgeryFactory] generating a random [Throwable] instance with a forged message.
 */
class ThrowableForgeryFactory :
    ForgeryFactory<Throwable> {
    /** @inheritDoc */
    override fun getForgery(forge: Forge): Throwable {
        return forge.aThrowable()
    }
}
