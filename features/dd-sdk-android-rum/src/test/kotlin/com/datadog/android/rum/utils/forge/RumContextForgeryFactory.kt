/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class RumContextForgeryFactory : ForgeryFactory<RumContext> {
    override fun getForgery(forge: Forge): RumContext {
        return RumContext(
            applicationId = forge.getForgery<UUID>().toString(),
            sessionId = forge.getForgery<UUID>().toString(),
            viewId = forge.aNullable { getForgery<UUID>().toString() },
            viewName = forge.aNullable { forge.anAlphaNumericalString() },
            viewUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/[a-z]+"),
            actionId = forge.aNullable { getForgery<UUID>().toString() },
            viewType = forge.aValueFrom(RumViewScope.RumViewType::class.java)
        )
    }
}
