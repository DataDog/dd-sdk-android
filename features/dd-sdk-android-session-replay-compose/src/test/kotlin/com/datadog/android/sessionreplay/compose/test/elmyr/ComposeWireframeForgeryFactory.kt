/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ComposeWireframeForgeryFactory : ForgeryFactory<ComposeWireframe> {
    override fun getForgery(forge: Forge): ComposeWireframe {
        return ComposeWireframe(
            forge.getForgery(),
            forge.aNullable { getForgery() }
        )
    }
}
