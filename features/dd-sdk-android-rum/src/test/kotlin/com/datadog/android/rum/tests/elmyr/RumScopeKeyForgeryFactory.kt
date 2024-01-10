/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.datadog.android.rum.internal.domain.scope.RumScopeKey
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumScopeKeyForgeryFactory : ForgeryFactory<RumScopeKey> {
    override fun getForgery(forge: Forge): RumScopeKey {
        return RumScopeKey.from(forge.aStringMatching("([a-z]+\\.)+[A-Z][a-z]+"))
    }
}
