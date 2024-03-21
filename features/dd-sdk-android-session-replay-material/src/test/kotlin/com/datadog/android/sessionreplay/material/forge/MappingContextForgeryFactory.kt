/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.forge

import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

internal class MappingContextForgeryFactory : ForgeryFactory<MappingContext> {
    override fun getForgery(forge: Forge): MappingContext {
        return MappingContext(
            systemInformation = forge.getForgery(),
            imageWireframeHelper = mock(),
            hasOptionSelectorParent = forge.aBool()
        )
    }
}
