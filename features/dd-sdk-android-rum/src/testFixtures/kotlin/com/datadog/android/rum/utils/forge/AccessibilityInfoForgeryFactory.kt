/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.accessibility.AccessibilityInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class AccessibilityInfoForgeryFactory : ForgeryFactory<AccessibilityInfo> {
    override fun getForgery(forge: Forge): AccessibilityInfo {
        return AccessibilityInfo(
            textSize = forge.aString(),
            isColorInversionEnabled = forge.aBool(),
            isClosedCaptioningEnabled = forge.aBool(),
            isReducedAnimationsEnabled = forge.aBool(),
            isScreenReaderEnabled = forge.aBool(),
            isScreenPinningEnabled = forge.aBool(),
            isRtlEnabled = forge.aBool()
        )
    }
}
