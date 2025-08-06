/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.ViewEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class AccessibilityForgeryFactory : ForgeryFactory<ViewEvent.Accessibility> {
    override fun getForgery(forge: Forge): ViewEvent.Accessibility {
        return ViewEvent.Accessibility(
            textSize = forge.aNullable { forge.aString() },
            rtlEnabled = forge.aNullable { forge.aBool() },
            screenReaderEnabled = forge.aNullable { forge.aBool() },
            increaseContrastEnabled = forge.aNullable { forge.aBool() },
            reducedAnimationsEnabled = forge.aNullable { forge.aBool() },
            invertColorsEnabled = forge.aNullable { forge.aBool() },
            singleAppModeEnabled = forge.aNullable { forge.aBool() }
        )
    }
}
