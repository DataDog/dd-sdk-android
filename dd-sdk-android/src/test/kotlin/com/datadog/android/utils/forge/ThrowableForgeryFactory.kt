/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class ThrowableForgeryFactory :
    ForgeryFactory<Throwable> {
    override fun getForgery(forge: Forge): Throwable {
        return forge.aThrowable()
    }
}
