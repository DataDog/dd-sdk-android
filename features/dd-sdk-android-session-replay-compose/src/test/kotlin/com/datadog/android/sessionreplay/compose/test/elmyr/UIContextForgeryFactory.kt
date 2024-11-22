/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

internal class UIContextForgeryFactory : ForgeryFactory<UiContext> {
    override fun getForgery(forge: Forge): UiContext {
        return UiContext(
            parentContentColor = forge.anAlphabeticalString(),
            density = forge.aFloat(0.01f, 100f),
            imagePrivacy = forge.aValueFrom(ImagePrivacy::class.java),
            textAndInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java),
            isInUserInputLayout = forge.aBool(),
            imageWireframeHelper = mock()
        )
    }
}
