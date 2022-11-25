/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class MutationDataForgeryFactory :
    ForgeryFactory<MobileSegment.MobileIncrementalData.MobileMutationData> {
    override fun getForgery(forge: Forge): MobileSegment.MobileIncrementalData.MobileMutationData {
        return MobileSegment.MobileIncrementalData.MobileMutationData(
            adds = forge.aNullable {
                forge.aList {
                    MobileSegment.Add(
                        previousId = forge.aNullable { forge.aLong() },
                        forge.getForgery(MobileSegment.Wireframe::class.java)
                    )
                }
            },
            removes = forge.aNullable { forge.aList { MobileSegment.Remove(forge.aLong()) } },
            updates = forge.aNullable { forge.aList { forge.getForgery() } }
        )
    }
}
