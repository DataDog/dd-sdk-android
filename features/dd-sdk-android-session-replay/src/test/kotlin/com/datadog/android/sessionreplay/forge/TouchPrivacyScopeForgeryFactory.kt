/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.TouchPrivacyScope
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TouchPrivacyScopeForgeryFactory : ForgeryFactory<TouchPrivacyScope> {
    override fun getForgery(forge: Forge): TouchPrivacyScope = TouchPrivacyScope(
        rumViewScope = RumViewIdentityScope(forge.anAlphabeticalString()),
        version = forge.aLong(min = 0L, max = Long.MAX_VALUE - 1)
    )
}
