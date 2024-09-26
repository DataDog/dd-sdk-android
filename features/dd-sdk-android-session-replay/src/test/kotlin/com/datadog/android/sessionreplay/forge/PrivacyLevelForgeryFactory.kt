/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.PrivacyLevel
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import kotlin.random.Random

internal class PrivacyLevelForgeryFactory :
    ForgeryFactory<PrivacyLevel> {
    override fun getForgery(forge: Forge): PrivacyLevel {
        return when (Random.nextInt(3)) {
            0 -> forge.aValueFrom(ImagePrivacy::class.java)
            1 -> forge.aValueFrom(TouchPrivacy::class.java)
            2 -> forge.aValueFrom(TextAndInputPrivacy::class.java)
            else -> forge.aValueFrom(ImagePrivacy::class.java) // should never happen
        }
    }
}
