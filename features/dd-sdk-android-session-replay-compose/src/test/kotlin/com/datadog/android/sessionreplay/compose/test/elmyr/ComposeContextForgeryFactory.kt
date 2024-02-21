/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.test.elmyr

import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.Parameter
import com.datadog.android.sessionreplay.compose.internal.data.SourceLocationInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ComposeContextForgeryFactory : ForgeryFactory<ComposeContext> {

    override fun getForgery(forge: Forge): ComposeContext {
        return ComposeContext(
            name = forge.aNullable { anAlphabeticalString() },
            sourceFile = forge.aNullable { anAlphabeticalString() },
            packageHash = forge.anInt(),
            locations = forge.aList {
                SourceLocationInfo(
                    forge.aNullable { forge.anInt() },
                    forge.aNullable { forge.anInt() },
                    forge.aNullable { forge.anInt() }
                )
            },
            repeatOffset = forge.anInt(),
            parameters = forge.aNullable {
                aList {
                    Parameter(anInt(), aNullable { anAlphabeticalString() })
                }
            },
            isCall = forge.aBool(),
            isInline = forge.aBool()
        )
    }
}
